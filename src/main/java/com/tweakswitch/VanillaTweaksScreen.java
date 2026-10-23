package com.tweakswitch;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** Lists every Vanilla Tweaks resource pack for this Minecraft version and downloads the chosen ones one zip per pack. */
public class VanillaTweaksScreen extends PackListScreen {
	private static final Logger LOGGER = LogUtils.getLogger();
	/** Pause between packs so a big batch doesn't hammer the site. */
	private static final long DELAY_BETWEEN_DOWNLOADS_MS = 500;

	// Kept for the session so reopening the screen doesn't fetch the list again
	private static @Nullable List<VanillaTweaks.VtPack> packs;
	private static @Nullable String loadError;
	private static boolean loading;
	private static @Nullable String progress;

	private static final int PREVIEW_WIDTH = 200;
	private static final int PANEL_PADDING = 6;
	private static final int LINE_HEIGHT = 10;
	/** Categories the player opened; all start closed. Kept for the session. */
	private static final Set<String> EXPANDED = new HashSet<>();

	private final Set<String> selected = new LinkedHashSet<>();
	/** Which pack each visible row button belongs to, for the hover preview. */
	private final Map<AbstractWidget, VanillaTweaks.VtPack> rows = new HashMap<>();
	private @Nullable Button downloadButton;

	public VanillaTweaksScreen(final @Nullable Screen lastScreen) {
		super(lastScreen, Component.translatable("tweakswitch.vt.title", VanillaTweaks.siteVersion()));
	}

	@Override
	protected void init() {
		super.init();
		if (packs == null && !loading) {
			this.loadPacks();
		}
	}

	private void loadPacks() {
		loading = true;
		loadError = null;
		Thread thread = new Thread(() -> {
			List<VanillaTweaks.VtPack> result = null;
			String error = null;
			try {
				result = VanillaTweaks.fetchPacks();
			} catch (Exception e) {
				LOGGER.warn("Failed to load the Vanilla Tweaks pack list", e);
				error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			}
			List<VanillaTweaks.VtPack> loaded = result;
			String failure = error;
			this.minecraft.execute(() -> {
				packs = loaded;
				loadError = failure;
				loading = false;
				this.refreshIfShown();
			});
		}, "TweakSwitch pack list");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(6));
		footer.addChild(
			Button.builder(Component.translatable("tweakswitch.vt.selectShown"), button -> this.toggleShown())
				.tooltip(Tooltip.create(Component.translatable("tweakswitch.vt.selectShown.tooltip")))
				.width(90)
				.build()
		);
		this.downloadButton = footer.addChild(Button.builder(Component.empty(), button -> this.startDownload()).width(110).build());
		footer.addChild(Button.builder(Component.translatable("tweakswitch.vt.website"), ConfirmLinkScreen.confirmLink(this, VanillaTweaks.SITE)).width(70).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(70).build());
		this.updateDownloadButton();
	}

	private void updateDownloadButton() {
		if (this.downloadButton != null) {
			this.downloadButton.setMessage(progress != null ? Component.literal(progress) : Component.translatable("tweakswitch.vt.download", this.selected.size()));
			this.downloadButton.active = progress == null && !this.selected.isEmpty();
		}
	}

	@Override
	protected void fillList(final OptionsList list) {
		this.rows.clear();
		if (packs == null) {
			list.addHeader(loadError != null
				? Component.translatable("tweakswitch.vt.loadFailed", loadError).withStyle(ChatFormatting.RED)
				: Component.translatable("tweakswitch.vt.loading").withStyle(ChatFormatting.GRAY));
			return;
		}

		Set<String> downloaded = this.downloadedFiles();
		Map<String, List<VanillaTweaks.VtPack>> byCategory = new LinkedHashMap<>();
		for (VanillaTweaks.VtPack pack : this.shownPacks()) {
			byCategory.computeIfAbsent(pack.category(), category -> new ArrayList<>()).add(pack);
		}

		if (byCategory.isEmpty()) {
			list.addHeader(Component.translatable("tweakswitch.noMatches").withStyle(ChatFormatting.GRAY));
		}

		byCategory.forEach((category, categoryPacks) -> {
			long picked = categoryPacks.stream().filter(pack -> this.selected.contains(pack.id())).count();
			String count = categoryPacks.size() + (picked > 0 ? ", " + picked + " picked" : "");
			this.addCategory(list, EXPANDED, false, category, count, () -> {
				List<AbstractWidget> widgets = new ArrayList<>();
				for (VanillaTweaks.VtPack pack : categoryPacks) {
					AbstractWidget row = this.createRow(pack, downloaded.contains(pack.fileName()));
					this.rows.put(row, pack);
					widgets.add(row);
				}
				list.addSmall(widgets);
			});
		});
	}

	private List<VanillaTweaks.VtPack> shownPacks() {
		return packs == null ? List.of() : packs.stream().filter(pack -> this.matches(pack.display(), pack.description(), pack.category())).toList();
	}

	private AbstractWidget createRow(final VanillaTweaks.VtPack pack, final boolean downloaded) {
		if (downloaded) {
			Button button = Button.builder(Component.literal("✔ " + pack.display()).withStyle(ChatFormatting.GREEN), b -> {}).build();
			button.active = false;
			return button;
		}

		return Button.builder(this.rowLabel(pack), button -> {
			if (!this.selected.remove(pack.id())) {
				this.selected.add(pack.id());
			}
			button.setMessage(this.rowLabel(pack));
			this.updateDownloadButton();
			// Updates the "picked" count on the category row
			this.queueRefresh();
		}).build();
	}

	/** Picked packs are yellow with a ✚, so the whole button width is left for the name. */
	private Component rowLabel(final VanillaTweaks.VtPack pack) {
		return this.selected.contains(pack.id())
			? Component.literal("✚ " + pack.display()).withStyle(ChatFormatting.YELLOW)
			: Component.literal(pack.display());
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		AbstractWidget hovered = this.hoveredListWidget(mouseX, mouseY);
		VanillaTweaks.VtPack pack = hovered != null ? this.rows.get(hovered) : null;
		if (pack != null) {
			this.extractPreviewPanel(graphics, pack, mouseX, mouseY);
		}
	}

	/** A tooltip-like panel with the pack's preview picture, description and conflicts. */
	private void extractPreviewPanel(final GuiGraphicsExtractor graphics, final VanillaTweaks.VtPack pack, final int mouseX, final int mouseY) {
		int panelWidth = Math.min(PREVIEW_WIDTH, this.width - 2 * PANEL_PADDING - 8);
		VanillaTweaksPreviews.Preview preview = VanillaTweaksPreviews.get(pack);
		int imageHeight = 0;
		if (preview.texture() != null) {
			imageHeight = Math.min(this.height / 2, preview.height() * panelWidth / Math.max(1, preview.width()));
		} else if (!preview.failed()) {
			imageHeight = 9;
		}
		int imageWidth = preview.texture() != null ? Math.min(panelWidth, preview.width() * imageHeight / Math.max(1, preview.height())) : panelWidth;

		List<FormattedCharSequence> lines = new ArrayList<>(this.font.split(Component.literal(pack.display()).withStyle(ChatFormatting.BOLD), panelWidth));
		lines.addAll(this.font.split(this.describe(pack), panelWidth));
		int textHeight = lines.size() * LINE_HEIGHT;
		int boxWidth = panelWidth + 2 * PANEL_PADDING;
		int boxHeight = imageHeight + (imageHeight > 0 ? PANEL_PADDING : 0) + textHeight + 2 * PANEL_PADDING;

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
		if (preview.texture() != null) {
			int imageX = x + PANEL_PADDING + (panelWidth - imageWidth) / 2;
			graphics.blit(preview.texture(), imageX, contentY, imageX + imageWidth, contentY + imageHeight, 0.0F, 1.0F, 0.0F, 1.0F);
			contentY += imageHeight + PANEL_PADDING;
		} else if (!preview.failed()) {
			graphics.text(this.font, Component.translatable("tweakswitch.vt.previewLoading").withStyle(ChatFormatting.GRAY), x + PANEL_PADDING, contentY, -1);
			contentY += imageHeight + PANEL_PADDING;
		}
		for (FormattedCharSequence line : lines) {
			graphics.text(this.font, line, x + PANEL_PADDING, contentY, -1);
			contentY += LINE_HEIGHT;
		}
	}

	private Component describe(final VanillaTweaks.VtPack pack) {
		MutableComponent text = Component.literal(pack.description().isEmpty() ? pack.display() : pack.description());
		if (!pack.incompatible().isEmpty() && packs != null) {
			Map<String, String> names = new HashMap<>();
			packs.forEach(other -> names.put(other.id(), other.display()));
			List<String> conflicts = pack.incompatible().stream().map(id -> names.getOrDefault(id, id)).limit(6).toList();
			String more = pack.incompatible().size() > conflicts.size() ? ", …" : "";
			text.append(Component.literal("\n").append(Component.translatable("tweakswitch.vt.conflicts", String.join(", ", conflicts) + more).withStyle(ChatFormatting.GOLD)));
		}
		return text;
	}

	/** Selects every shown pack not downloaded yet, or clears them if they are all selected already. */
	private void toggleShown() {
		Set<String> downloaded = this.downloadedFiles();
		List<String> candidates = this.shownPacks().stream().filter(pack -> !downloaded.contains(pack.fileName())).map(VanillaTweaks.VtPack::id).toList();
		if (this.selected.containsAll(candidates)) {
			candidates.forEach(this.selected::remove);
		} else {
			this.selected.addAll(candidates);
		}
		this.refreshList();
		this.updateDownloadButton();
	}

	private Set<String> downloadedFiles() {
		Set<String> names = new HashSet<>();
		try (var files = Files.list(TweakSwitch.packsDir())) {
			files.forEach(file -> names.add(file.getFileName().toString()));
		} catch (Exception e) {
			// Folder doesn't exist yet: nothing downloaded
		}
		return names;
	}

	private void startDownload() {
		if (packs == null || this.selected.isEmpty() || progress != null) {
			return;
		}

		List<VanillaTweaks.VtPack> queue = packs.stream().filter(pack -> this.selected.contains(pack.id())).toList();
		this.selected.clear();
		Path target = TweakSwitch.packsDir();
		progress = Component.translatable("tweakswitch.vt.progress", 0, queue.size()).getString();
		this.updateDownloadButton();

		Thread thread = new Thread(() -> {
			int failed = 0;
			for (int i = 0; i < queue.size(); i++) {
				VanillaTweaks.VtPack pack = queue.get(i);
				String step = Component.translatable("tweakswitch.vt.progress", i + 1, queue.size()).getString();
				this.minecraft.execute(() -> {
					progress = step;
					this.updateDownloadButton();
				});
				try {
					VanillaTweaks.download(pack, target);
				} catch (Exception e) {
					LOGGER.warn("Failed to download Vanilla Tweaks pack {}", pack.id(), e);
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
					Component.translatable("tweakswitch.vt.done", done),
					failures > 0 ? Component.translatable("tweakswitch.vt.someFailed", failures).withStyle(ChatFormatting.RED) : Component.translatable("tweakswitch.vt.doneHint")
				);
				this.refreshIfShown();
			});
		}, "TweakSwitch downloads");
		thread.setDaemon(true);
		thread.start();
	}

	private void refreshIfShown() {
		if (this.minecraft.gui.screen() == this) {
			this.refreshList();
			this.updateDownloadButton();
		}
	}
}
