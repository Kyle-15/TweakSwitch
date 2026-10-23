package com.tweakswitch;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Shared parts of the pack download screens: pick packs with row buttons, see a preview panel on hover, then download the
 * picked ones one at a time into the TweakSwitch folder.
 *
 * @param <T> one downloadable pack from the source
 */
public abstract class DownloadScreen<T> extends PackListScreen {
	private static final Logger LOGGER = LogUtils.getLogger();
	/** Pause between packs so a big batch doesn't hammer the site. */
	private static final long DELAY_BETWEEN_DOWNLOADS_MS = 500;
	private static final int PANEL_WIDTH = 200;
	private static final int PANEL_PADDING = 6;
	private static final int LINE_HEIGHT = 10;

	/** Text for the download button while a batch runs; one batch at a time across all sources. */
	private static @Nullable String progress;

	/** Picked packs by id, in the order they were picked. */
	protected final Map<String, T> selected = new LinkedHashMap<>();
	/** Which pack each row button in the list belongs to, for the hover preview. */
	private final Map<AbstractWidget, T> rows = new HashMap<>();
	private @Nullable Button downloadButton;

	protected DownloadScreen(final @Nullable Screen lastScreen, final Component title) {
		super(lastScreen, title);
	}

	protected abstract String id(T pack);

	protected abstract String displayName(T pack);

	/** The file the pack is saved as in the TweakSwitch folder; used to tell which packs are already downloaded. */
	protected abstract String fileName(T pack);

	/** The category its switch goes under on the TweakSwitch screen. */
	protected abstract String switchCategory(T pack);

	protected abstract @Nullable String imageUrl(T pack);

	/** Text under the name in the hover panel. */
	protected abstract Component details(T pack);

	/** Downloads one pack into {@code folder}. Runs off the render thread. */
	protected abstract void download(T pack, Path folder) throws Exception;

	@Override
	protected void fillList(final net.minecraft.client.gui.components.OptionsList list) {
		this.rows.clear();
		this.fillPackList(list);
	}

	/** Adds the rows; use {@link #createRow} for each pack. */
	protected abstract void fillPackList(net.minecraft.client.gui.components.OptionsList list);

	protected Button createDownloadButton() {
		this.downloadButton = Button.builder(Component.empty(), button -> this.startDownload()).width(110).build();
		this.updateDownloadButton();
		return this.downloadButton;
	}

	protected void updateDownloadButton() {
		if (this.downloadButton != null) {
			this.downloadButton.setMessage(progress != null ? Component.literal(progress) : Component.translatable("tweakswitch.download", this.selected.size()));
			this.downloadButton.active = progress == null && !this.selected.isEmpty();
		}
	}

	/** A button that picks or unpicks the pack, or a green ✔ if it's downloaded already. */
	protected AbstractWidget createRow(final T pack, final Set<String> downloaded) {
		AbstractWidget row;
		if (downloaded.contains(this.fileName(pack))) {
			row = Button.builder(Component.literal("✔ " + this.displayName(pack)).withStyle(ChatFormatting.GREEN), b -> {}).build();
			row.active = false;
		} else {
			row = Button.builder(this.rowLabel(pack), button -> {
				if (this.selected.remove(this.id(pack)) == null) {
					this.selected.put(this.id(pack), pack);
				}
				button.setMessage(this.rowLabel(pack));
				this.updateDownloadButton();
				// Updates counts shown elsewhere in the list
				this.queueRefresh();
			}).build();
		}
		this.rows.put(row, pack);
		return row;
	}

	/** Picked packs are yellow with a ✚, so the whole button width is left for the name. */
	private Component rowLabel(final T pack) {
		return this.selected.containsKey(this.id(pack))
			? Component.literal("✚ " + this.displayName(pack)).withStyle(ChatFormatting.YELLOW)
			: Component.literal(this.displayName(pack));
	}

	protected Set<String> downloadedFiles() {
		Set<String> names = new HashSet<>();
		try (Stream<Path> files = Files.list(TweakSwitch.packsDir())) {
			files.forEach(file -> names.add(file.getFileName().toString()));
		} catch (Exception e) {
			// Folder doesn't exist yet: nothing downloaded
		}
		return names;
	}

	private void startDownload() {
		if (this.selected.isEmpty() || progress != null) {
			return;
		}

		List<T> queue = new ArrayList<>(this.selected.values());
		this.selected.clear();
		Path folder = TweakSwitch.packsDir();
		progress = Component.translatable("tweakswitch.progress", 0, queue.size()).getString();
		this.updateDownloadButton();

		Thread thread = new Thread(() -> {
			int failed = 0;
			for (int i = 0; i < queue.size(); i++) {
				T pack = queue.get(i);
				String step = Component.translatable("tweakswitch.progress", i + 1, queue.size()).getString();
				this.minecraft.execute(() -> {
					progress = step;
					this.updateDownloadButton();
				});
				try {
					Files.createDirectories(folder);
					this.download(pack, folder);
					PackCategories.put(this.fileName(pack), this.switchCategory(pack));
				} catch (Exception e) {
					LOGGER.warn("Failed to download pack {}", this.id(pack), e);
					failed++;
				}
				if (i < queue.size() - 1) {
					try {
						Thread.sleep(DELAY_BETWEEN_DOWNLOADS_MS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			}

			int failures = failed;
			int done = queue.size() - failed;
			this.minecraft.execute(() -> {
				progress = null;
				SystemToast.addOrUpdate(
					this.minecraft.gui.toastManager(),
					SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					Component.translatable(done == 1 ? "tweakswitch.doneOne" : "tweakswitch.done", done),
					failures > 0 ? Component.translatable("tweakswitch.someFailed", failures).withStyle(ChatFormatting.RED) : Component.translatable("tweakswitch.doneHint")
				);
				this.refreshIfShown();
			});
		}, "TweakSwitch downloads");
		thread.setDaemon(true);
		thread.start();
	}

	protected void refreshIfShown() {
		if (this.minecraft.gui.screen() == this) {
			this.refreshList();
			this.updateDownloadButton();
		}
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		AbstractWidget hovered = this.hoveredListWidget(mouseX, mouseY);
		T pack = hovered != null ? this.rows.get(hovered) : null;
		if (pack != null) {
			this.extractPreviewPanel(graphics, pack, mouseX, mouseY);
		}
	}

	/** A tooltip-like panel with the pack's picture, name and details. */
	private void extractPreviewPanel(final GuiGraphicsExtractor graphics, final T pack, final int mouseX, final int mouseY) {
		int panelWidth = Math.min(PANEL_WIDTH, this.width - 2 * PANEL_PADDING - 8);
		String url = this.imageUrl(pack);
		RemoteImages.Image image = url != null ? RemoteImages.get(url) : RemoteImages.Image.FAILED;
		int imageHeight = 0;
		if (image.texture() != null) {
			imageHeight = Math.min(this.height / 2, image.height() * panelWidth / Math.max(1, image.width()));
		} else if (!image.failed()) {
			imageHeight = 9;
		}
		int imageWidth = image.texture() != null ? Math.min(panelWidth, image.width() * imageHeight / Math.max(1, image.height())) : panelWidth;

		List<FormattedCharSequence> lines = new ArrayList<>(this.font.split(Component.literal(this.displayName(pack)).withStyle(ChatFormatting.BOLD), panelWidth));
		lines.addAll(this.font.split(this.details(pack), panelWidth));
		int boxWidth = panelWidth + 2 * PANEL_PADDING;
		int boxHeight = imageHeight + (imageHeight > 0 ? PANEL_PADDING : 0) + lines.size() * LINE_HEIGHT + 2 * PANEL_PADDING;

		// Beside the cursor, flipped to the other side if it would run off screen
		int x = mouseX + 14;
		if (x + boxWidth > this.width - 4) {
			x = mouseX - 14 - boxWidth;
		}
		x = Math.max(4, x);
		int y = Math.max(4, Math.min(mouseY - 12, this.height - boxHeight - 4));

		graphics.nextStratum();
		graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xF0100010);
		graphics.outline(x, y, boxWidth, boxHeight, 0xFF5000FF);
		int contentY = y + PANEL_PADDING;
		if (image.texture() != null) {
			int imageX = x + PANEL_PADDING + (panelWidth - imageWidth) / 2;
			graphics.blit(image.texture(), imageX, contentY, imageX + imageWidth, contentY + imageHeight, 0.0F, 1.0F, 0.0F, 1.0F);
			contentY += imageHeight + PANEL_PADDING;
		} else if (!image.failed()) {
			graphics.text(this.font, Component.translatable("tweakswitch.previewLoading").withStyle(ChatFormatting.GRAY), x + PANEL_PADDING, contentY, -1);
			contentY += imageHeight + PANEL_PADDING;
		}
		for (FormattedCharSequence line : lines) {
			graphics.text(this.font, line, x + PANEL_PADDING, contentY, -1);
			contentY += LINE_HEIGHT;
		}
	}
}
