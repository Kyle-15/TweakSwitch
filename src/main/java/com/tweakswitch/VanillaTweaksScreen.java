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
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
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
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(6));
		footer.addChild(
			Button.builder(Component.translatable("tweakswitch.vt.selectShown"), button -> this.toggleShown())
				.tooltip(Tooltip.create(Component.translatable("tweakswitch.vt.selectShown.tooltip")))
				.width(90)
				.build()
		);
		footer.addChild(this.createDownloadButton());
		footer.addChild(Button.builder(Component.translatable("tweakswitch.website"), ConfirmLinkScreen.confirmLink(this, VanillaTweaks.SITE)).width(70).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(70).build());
	}

	@Override
	protected void fillPackList(final OptionsList list) {
		if (packs == null) {
			list.addHeader(loadError != null
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
			list.addHeader(Component.translatable("tweakswitch.noMatches").withStyle(ChatFormatting.GRAY));
		}

		byCategory.forEach((category, categoryPacks) -> {
			long picked = categoryPacks.stream().filter(pack -> this.selected.containsKey(pack.id())).count();
			String count = categoryPacks.size() + (picked > 0 ? ", " + picked + " picked" : "");
			this.addCategory(list, EXPANDED, false, category, count, () -> {
				List<AbstractWidget> widgets = new ArrayList<>();
				for (VanillaTweaks.VtPack pack : categoryPacks) {
					widgets.add(this.createRow(pack, downloaded));
				}
				list.addSmall(widgets);
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
	protected String switchCategory(final VanillaTweaks.VtPack pack) {
		return pack.category();
	}

	@Override
	protected String imageUrl(final VanillaTweaks.VtPack pack) {
		return VanillaTweaks.previewUrl(pack);
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
		return text;
	}

	@Override
	protected void download(final VanillaTweaks.VtPack pack, final Path folder) throws Exception {
		VanillaTweaks.download(pack, folder);
	}
}
