package com.tweakswitch;

import com.tweakswitch.compat.Compat;
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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;
import com.tweakswitch.ui.Draw;
import com.tweakswitch.ui.ThemedButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
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

	/** The name its switch shows on the TweakSwitch screen. */
	protected String switchTitle(final T pack) {
		return this.displayName(pack);
	}

	/** Where the downloaded file came from, saved in {@link PackIndex} for the switch screen and for updates. */
	protected abstract PackIndex.Entry indexEntry(T pack);

	/** Pictures for the hover panel, best first; the panel steps through them. Animated GIFs play. */
	protected abstract List<String> imageUrls(T pack);

	/** Text under the name in the hover panel. */
	protected abstract Component details(T pack);

	/** Downloads one pack into {@code folder}. Runs off the render thread. */
	protected abstract void download(T pack, Path folder) throws Exception;

	@Override
	protected void fillList(final PackList list) {
		this.rows.clear();
		this.fillPackList(list);
	}

	/** Adds the rows; use {@link #createRow} for each pack. */
	protected abstract void fillPackList(PackList list);

	protected Button createDownloadButton() {
		this.downloadButton = this.primaryButton(Component.empty(), 110, button -> this.startDownload());
		this.updateDownloadButton();
		return this.downloadButton;
	}

	/** Translation key for the download button, given the number of picked packs. */
	protected String downloadLabelKey() {
		return "tweakswitch.download";
	}

	protected void updateDownloadButton() {
		if (this.downloadButton != null) {
			this.downloadButton.setMessage(progress != null ? Component.literal(progress) : Component.translatable(this.downloadLabelKey(), this.selected.size()));
			this.downloadButton.active = progress == null && !this.selected.isEmpty();
		}
	}

	/** Whether the pack needs nothing doing: by default, whether its file is in the folder already. */
	protected boolean isDone(final T pack, final Set<String> files) {
		return files.contains(this.fileName(pack));
	}

	/** The row (or tile, or card) for a pack: picks or unpicks it when clicked, or shows it's {@linkplain #isDone done} already. */
	protected AbstractWidget createRow(final T pack, final Set<String> files) {
		boolean done = this.isDone(pack, files);
		AbstractWidget row = this.createPackWidget(pack, done, () -> this.selected.containsKey(this.id(pack)), () -> {
			if (this.selected.remove(this.id(pack)) == null) {
				this.selected.put(this.id(pack), pack);
			}
			this.updateDownloadButton();
			// Updates counts shown elsewhere in the list
			this.queueRefresh();
		});
		this.rows.put(row, pack);
		return row;
	}

	/**
	 * Draws one pack; each source styles it like its website. {@code picked} is read every frame, so the widget doesn't
	 * need rebuilding when it's clicked. The default is a plain themed button.
	 */
	protected AbstractWidget createPackWidget(final T pack, final boolean done, final BooleanSupplier picked, final Runnable toggle) {
		ThemedButton button = new ThemedButton(this.theme(), ThemedButton.Kind.SECONDARY, Component.literal(this.displayName(pack)), 150, b -> toggle.run()) {
			@Override
			public Component getMessage() {
				return Component.literal((done ? "✔ " : picked.getAsBoolean() ? "✚ " : "") + DownloadScreen.this.displayName(pack));
			}
		};
		button.active = !done;
		return button.highlightWhen(picked);
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
		this.beforeDownload(new ArrayList<>(this.selected.values()), this::download);
	}

	/**
	 * Runs before a batch starts, e.g. to warn about missing mods. Call {@code proceed} with the packs to download (it can
	 * add to the list), or don't call it to cancel.
	 */
	protected void beforeDownload(final List<T> queue, final Consumer<List<T>> proceed) {
		proceed.accept(queue);
	}

	/** Runs on the render thread after a batch, with the packs that downloaded fine. */
	protected void afterDownload(final List<T> succeeded) {
	}

	/** Shows a status like "Checking..." on the download button while {@link #beforeDownload} works; null clears it. */
	protected void setProgress(final @Nullable String text) {
		progress = text;
		this.updateDownloadButton();
	}

	private void download(final List<T> queue) {
		queue.forEach(pack -> this.selected.remove(this.id(pack)));
		Path folder = TweakSwitch.packsDir();
		progress = Component.translatable("tweakswitch.progress", 0, queue.size()).getString();
		this.updateDownloadButton();

		Thread thread = new Thread(() -> {
			int failed = 0;
			List<T> succeeded = new ArrayList<>();
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
					PackIndex.put(this.fileName(pack), this.indexEntry(pack).withTitle(this.switchTitle(pack)));
					succeeded.add(pack);
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
					Compat.toasts(this.minecraft),
					SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					Component.translatable(done == 1 ? "tweakswitch.doneOne" : "tweakswitch.done", done),
					failures > 0 ? Component.translatable("tweakswitch.someFailed", failures).withStyle(ChatFormatting.RED) : Component.translatable("tweakswitch.doneHint")
				);
				this.afterDownload(succeeded);
				this.refreshIfShown();
			});
		}, "TweakSwitch downloads");
		thread.setDaemon(true);
		thread.start();
	}

	protected void refreshIfShown() {
		if (Compat.screen(this.minecraft) == this) {
			this.refreshList();
			this.updateDownloadButton();
		}
	}

	/** The hovered pack's pictures from its website, its name, and its details. */
	@Override
	protected @Nullable Preview previewFor(final AbstractWidget hovered) {
		T pack = this.rows.get(hovered);
		return pack == null ? null : new Preview(pack, Component.literal(this.displayName(pack)), this.details(pack), this.imageUrls(pack), List.of());
	}
}
