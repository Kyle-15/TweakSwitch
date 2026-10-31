package com.tweakswitch;

import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.tweakswitch.ui.PackTile;
import com.tweakswitch.ui.Theme;
import java.util.function.BooleanSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** Lists every Vanilla Tweaks resource pack for this Minecraft version and downloads the chosen ones one zip per pack. */
public class VanillaTweaksScreen extends DownloadScreen<VanillaTweaks.VtPack> {
	private static final Logger LOGGER = LogUtils.getLogger();

	// Kept for the session so reopening the screen doesn't fetch the list again
	private static @Nullable List<VanillaTweaks.VtPack> packs;
	private static @Nullable String loadError;
	private static boolean loading;
	/** Categories the player opened; all start closed. Kept for the session. */
	private static final Set<String> EXPANDED = new HashSet<>();

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
		this.addFooterButtons(
			this.button(Component.translatable("tweakswitch.vt.selectShown"), 90, button -> this.toggleShown())
				.withTooltip(Component.translatable("tweakswitch.vt.selectShown.tooltip")),
			this.createDownloadButton(),
			this.button(Component.translatable("tweakswitch.website"), 70, ConfirmLinkScreen.confirmLink(this, VanillaTweaks.SITE)),
			this.button(CommonComponents.GUI_DONE, 70, button -> this.onClose())
		);
	}

	@Override
	protected void fillPackList(final PackList list) {
		if (packs == null) {
			list.addText(loadError != null
				? Component.translatable("tweakswitch.loadFailed", loadError).withStyle(ChatFormatting.RED)
				: Component.translatable("tweakswitch.loading").withStyle(ChatFormatting.GRAY));
			return;
		}

		Set<String> downloaded = this.downloadedFiles();
		Map<String, List<VanillaTweaks.VtPack>> byCategory = new LinkedHashMap<>();
		for (VanillaTweaks.VtPack pack : this.shownPacks()) {
			byCategory.computeIfAbsent(pack.category(), category -> new ArrayList<>()).add(pack);
		}

		if (byCategory.isEmpty()) {
			list.addText(Component.translatable("tweakswitch.noMatches").withStyle(ChatFormatting.GRAY));
		}

		byCategory.forEach((category, categoryPacks) -> {
			long picked = categoryPacks.stream().filter(pack -> this.selected.containsKey(pack.id())).count();
			String count = categoryPacks.size() + (picked > 0 ? ", " + picked + " picked" : "");
			this.addCategory(list, EXPANDED, false, category, count, () -> {
				List<AbstractWidget> widgets = new ArrayList<>();
				for (VanillaTweaks.VtPack pack : categoryPacks) {
					widgets.add(this.createRow(pack, downloaded));
				}
				list.addTiles(widgets, PackTile.MIN_WIDTH, PackTile.HEIGHT);
			});
		});
	}

	private List<VanillaTweaks.VtPack> shownPacks() {
		return packs == null ? List.of() : packs.stream().filter(pack -> this.matches(pack.display(), pack.description(), pack.category())).toList();
	}

	/** Selects every shown pack not downloaded yet, or clears them if they are all selected already. */
	private void toggleShown() {
		Set<String> downloaded = this.downloadedFiles();
		List<VanillaTweaks.VtPack> candidates = this.shownPacks().stream().filter(pack -> !downloaded.contains(pack.fileName())).toList();
		if (candidates.stream().allMatch(pack -> this.selected.containsKey(pack.id()))) {
			candidates.forEach(pack -> this.selected.remove(pack.id()));
		} else {
			candidates.forEach(pack -> this.selected.put(pack.id(), pack));
		}
		this.refreshList();
		this.updateDownloadButton();
	}

	@Override
	protected Theme theme() {
		return Theme.VANILLA_TWEAKS;
	}

	/** "Vanilla Tweaks" in white with the version in their orange, like the site's header; short to leave room for search. */
	@Override
	protected Component styledTitle() {
		return Component.literal("Vanilla Tweaks ").withStyle(style -> style.withBold(true))
			.append(Component.literal(VanillaTweaks.siteVersion()).withColor(Theme.VANILLA_TWEAKS.accent()));
	}

	/** A tile with the pack's icon from the site and its name, like their picker. */
	@Override
	protected AbstractWidget createPackWidget(final VanillaTweaks.VtPack pack, final boolean done, final BooleanSupplier picked, final Runnable toggle) {
		String iconUrl = VanillaTweaks.iconUrl(pack);
		return new PackTile(this.theme(), Component.literal(pack.display()), () -> {
			RemoteImages.Image icon = RemoteImages.getIcon(iconUrl);
			return icon.textureAt(0);
		}, picked, done, toggle);
	}

	@Override
	protected String id(final VanillaTweaks.VtPack pack) {
		return pack.id();
	}

	@Override
	protected String displayName(final VanillaTweaks.VtPack pack) {
		return pack.display();
	}

	@Override
	protected String fileName(final VanillaTweaks.VtPack pack) {
		return pack.fileName();
	}

	@Override
	protected PackIndex.Entry indexEntry(final VanillaTweaks.VtPack pack) {
		return new PackIndex.Entry(pack.category(), PackIndex.VANILLA_TWEAKS, pack.id());
	}

	@Override
	protected List<String> imageUrls(final VanillaTweaks.VtPack pack) {
		return List.of(VanillaTweaks.previewUrl(pack));
	}

	@Override
	protected Component details(final VanillaTweaks.VtPack pack) {
		MutableComponent text = Component.literal(pack.description());
		if (!pack.incompatible().isEmpty() && packs != null) {
			Map<String, String> names = new HashMap<>();
			packs.forEach(other -> names.put(other.id(), other.display()));
			List<String> conflicts = pack.incompatible().stream().map(id -> names.getOrDefault(id, id)).limit(6).toList();
			String more = pack.incompatible().size() > conflicts.size() ? ", …" : "";
			if (!pack.description().isEmpty()) {
				text.append("\n");
			}
			text.append(Component.translatable("tweakswitch.vt.conflicts", String.join(", ", conflicts) + more).withStyle(ChatFormatting.GOLD));
		}
		if (pack.warning() != null) {
			text.append(Component.literal("\n⚠ " + pack.warning()).withStyle(ChatFormatting.YELLOW));
		}
		return text;
	}

	@Override
	protected void download(final VanillaTweaks.VtPack pack, final Path folder) throws Exception {
		VanillaTweaks.download(pack, folder);
	}
}
