package com.tweakswitch;

import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.tweakswitch.compat.Compat;
import com.tweakswitch.ui.PackTile;
import com.tweakswitch.ui.Ui;
import net.minecraft.client.gui.components.toasts.SystemToast;
import com.tweakswitch.ui.Ui;
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

/**
 * Lists every Vanilla Tweaks pack of one kind for this Minecraft version and downloads the chosen ones one zip per pack.
 * This screen does resource packs; {@link DataPacksScreen} reuses it for data packs and crafting tweaks.
 */
public class VanillaTweaksScreen extends DownloadScreen<VanillaTweaks.VtPack> {
	private static final Logger LOGGER = LogUtils.getLogger();

	// Kept for the session so reopening the screen doesn't fetch the list again
	private static final Map<VanillaTweaks.Kind, List<VanillaTweaks.VtPack>> PACKS = new EnumMap<>(VanillaTweaks.Kind.class);
	private static final Map<VanillaTweaks.Kind, Component> LOAD_ERRORS = new EnumMap<>(VanillaTweaks.Kind.class);
	/** Resource pack ids to pick as soon as the list is up: the tweaks inside a combined zip being split. */
	private static final Set<String> PRESELECT = new HashSet<>();

	/** Picks these packs the next time the resource pack picker shows its list. */
	public static void preselect(final List<String> packIds) {
		PRESELECT.addAll(packIds);
	}
	private static final Set<VanillaTweaks.Kind> LOADING = EnumSet.noneOf(VanillaTweaks.Kind.class);
	/** Categories the player opened; all start closed. Kept for the session. */
	private static final Set<String> EXPANDED = new HashSet<>();

	/** Which of the site's pickers is showing. */
	protected VanillaTweaks.Kind kind;

	public VanillaTweaksScreen(final @Nullable Screen lastScreen) {
		this(lastScreen, VanillaTweaks.Kind.RESOURCE_PACKS, Component.translatable("tweakswitch.vt.title", VanillaTweaks.siteVersion()));
	}

	protected VanillaTweaksScreen(final @Nullable Screen lastScreen, final VanillaTweaks.Kind kind, final Component title) {
		super(lastScreen, title);
		this.kind = kind;
	}

	/** Whether the list for that kind has loaded (or failed to), for the screenshot test. */
	public static boolean loadedForTest(final VanillaTweaks.Kind kind) {
		return PACKS.containsKey(kind) || LOAD_ERRORS.containsKey(kind);
	}

	@Override
	protected void init() {
		super.init();
		this.loadIfNeeded();
	}

	protected void loadIfNeeded() {
		if (!PACKS.containsKey(this.kind) && !LOADING.contains(this.kind)) {
			this.loadPacks(this.kind, false);
		}
	}

	/** Asks the site for the list again, after a failure or to replace a saved copy. */
	private void retry() {
		if (!LOADING.contains(this.kind)) {
			PACKS.remove(this.kind);
			this.loadPacks(this.kind, true);
			this.refreshList();
		}
	}

	/** Picks the tweaks of a combined zip, opens their categories and says so. Runs once the list is loaded. */
	private void applyPreselect() {
		List<VanillaTweaks.VtPack> packs = PACKS.get(VanillaTweaks.Kind.RESOURCE_PACKS);
		if (PRESELECT.isEmpty() || packs == null || this.kind != VanillaTweaks.Kind.RESOURCE_PACKS) {
			return;
		}
		Set<String> downloaded = this.downloadedFiles();
		int picked = 0;
		for (VanillaTweaks.VtPack pack : packs) {
			if (PRESELECT.contains(pack.id()) && !downloaded.contains(pack.fileName())) {
				this.selected.put(this.id(pack), pack);
				EXPANDED.add(pack.category());
				picked++;
			}
		}
		int missing = PRESELECT.size() - picked;
		PRESELECT.clear();
		SystemToast.addOrUpdate(Compat.toasts(this.minecraft), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
			Component.translatable("tweakswitch.split.picked", picked), Component.translatable(missing > 0 ? "tweakswitch.split.pickedSome" : "tweakswitch.split.pickedHint", missing));
		this.updateDownloadButton();
	}

	/** The list for the showing kind, or null while it loads. */
	protected @Nullable List<VanillaTweaks.VtPack> packs() {
		return PACKS.get(this.kind);
	}

	protected static int packCount(final VanillaTweaks.Kind kind) {
		List<VanillaTweaks.VtPack> packs = PACKS.get(kind);
		return packs == null ? 0 : packs.size();
	}

	private void loadPacks(final VanillaTweaks.Kind kind, final boolean refresh) {
		LOADING.add(kind);
		LOAD_ERRORS.remove(kind);
		Thread thread = new Thread(() -> {
			List<VanillaTweaks.VtPack> result = null;
			Component error = null;
			try {
				result = VanillaTweaks.fetchPacks(kind, refresh);
			} catch (Exception e) {
				LOGGER.warn("Failed to load the Vanilla Tweaks list for {}", kind, e);
				error = WebErrors.describe(e);
			}
			List<VanillaTweaks.VtPack> loaded = result;
			Component failure = error;
			this.minecraft.execute(() -> {
				if (loaded != null) {
					PACKS.put(kind, loaded);
				} else {
					LOAD_ERRORS.put(kind, failure);
				}
				LOADING.remove(kind);
				this.refreshIfShown();
			});
		}, "TweakSwitch pack list");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	protected void addFooter() {
		List<AbstractWidget> buttons = new ArrayList<>();
		buttons.add(this.button(Component.translatable("tweakswitch.vt.selectShown"), 90, button -> this.toggleShown())
			.withTooltip(Component.translatable("tweakswitch.vt.selectShown.tooltip")));
		buttons.add(this.createDownloadButton());
		if (this.switchesOnDownloads()) {
			buttons.add(this.createSwitchOnToggle());
		}
		buttons.add(this.button(Component.translatable("tweakswitch.website"), 70, button -> ConfirmLinkScreen.confirmLinkNow(this, this.kind.site, true)));
		buttons.add(this.button(CommonComponents.GUI_DONE, 70, button -> this.onClose()));
		this.addFooterButtons(buttons.toArray(AbstractWidget[]::new));
	}

	@Override
	protected void fillPackList(final PackList list) {
		if (this.packs() == null) {
			Component loadError = LOAD_ERRORS.get(this.kind);
			if (loadError != null && !LOADING.contains(this.kind)) {
				this.addError(list, loadError, this::retry);
			} else {
				list.addText(Component.translatable("tweakswitch.loading").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		this.applyPreselect();
		if (VanillaTweaks.listIsStale(this.kind) && !this.isSearching()) {
			this.addStaleNote(list, this::retry);
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
			long picked = categoryPacks.stream().filter(pack -> this.selected.containsKey(this.id(pack))).count();
			String count = categoryPacks.size() + (picked > 0 ? ", " + picked + " picked" : "");
			this.addCategory(list, EXPANDED, false, category, count, () -> {
				List<AbstractWidget> widgets = new ArrayList<>();
				for (VanillaTweaks.VtPack pack : categoryPacks) {
					widgets.add(this.createRow(pack, downloaded));
				}
				list.addTiles(widgets, PackTile.MIN_WIDTH, Ui.tile());
			});
		});
	}

	private List<VanillaTweaks.VtPack> shownPacks() {
		List<VanillaTweaks.VtPack> packs = this.packs();
		return packs == null ? List.of() : packs.stream().filter(pack -> this.matches(pack.display(), pack.description(), pack.category())).toList();
	}

	/** Selects every shown pack not downloaded yet, or clears them if they are all selected already. */
	private void toggleShown() {
		Set<String> downloaded = this.downloadedFiles();
		List<VanillaTweaks.VtPack> candidates = this.shownPacks().stream().filter(pack -> !downloaded.contains(pack.fileName())).toList();
		if (candidates.stream().allMatch(pack -> this.selected.containsKey(this.id(pack)))) {
			candidates.forEach(pack -> this.selected.remove(this.id(pack)));
		} else {
			candidates.forEach(pack -> this.selected.put(this.id(pack), pack));
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
		}, picked, done, () -> {
			toggle.run();
			this.warnAboutClash(pack);
		});
	}

	/** Picking a pack that clashes with another picked one (two crosshairs, say) says so straight away. */
	private void warnAboutClash(final VanillaTweaks.VtPack pack) {
		if (!this.selected.containsKey(this.id(pack)) || !Settings.get().conflictWarnings) {
			return;
		}
		List<String> clashes = this.selected.values().stream()
			.filter(other -> other != pack && other.kind() == pack.kind() && (pack.incompatible().contains(other.id()) || other.incompatible().contains(pack.id())))
			.map(VanillaTweaks.VtPack::display)
			.toList();
		if (!clashes.isEmpty()) {
			SystemToast.addOrUpdate(Compat.toasts(this.minecraft), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
				Component.translatable("tweakswitch.clash.toast", pack.display()), Component.literal(String.join(", ", clashes)));
		}
	}

	/** Back on the TweakSwitch screen, show the tab the new packs landed on. */
	@Override
	protected void afterDownload(final List<VanillaTweaks.VtPack> succeeded) {
		if (!succeeded.isEmpty()) {
			Settings settings = Settings.get();
			settings.tab = TweakSwitchScreen.Tab.VANILLA_TWEAKS.name();
			settings.save();
		}
	}

	/** Data packs and crafting tweaks can share names, and both can be picked at once, so their ids say which they are. */
	@Override
	protected String id(final VanillaTweaks.VtPack pack) {
		return pack.kind() == VanillaTweaks.Kind.RESOURCE_PACKS ? pack.id() : pack.kind().name() + ":" + pack.id();
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
		return new PackIndex.Entry(pack.category(), PackIndex.VANILLA_TWEAKS, pack.id()).withConflicts(pack.incompatible());
	}

	@Override
	protected List<String> imageUrls(final VanillaTweaks.VtPack pack) {
		String preview = VanillaTweaks.previewUrl(pack);
		return preview == null ? List.of() : List.of(preview);
	}

	@Override
	protected Component details(final VanillaTweaks.VtPack pack) {
		MutableComponent text = Component.literal(pack.description());
		List<VanillaTweaks.VtPack> packs = PACKS.get(pack.kind());
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
