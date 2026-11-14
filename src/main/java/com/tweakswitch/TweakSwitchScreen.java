package com.tweakswitch;

import com.tweakswitch.compat.Compat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.mojang.blaze3d.platform.NativeImage;
import com.tweakswitch.ui.SectionHeader;
import com.tweakswitch.ui.ActionRow;
import com.tweakswitch.ui.Draw;
import com.tweakswitch.ui.SwitchRow;
import com.tweakswitch.ui.Ui;
import com.tweakswitch.render.Gfx;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import java.net.URI;
import java.util.concurrent.CopyOnWriteArrayList;
import com.tweakswitch.ui.ThemedButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** One on/off switch per pack in the TweakSwitch folder. Changes apply, with one resource reload, when the screen closes. */
public class TweakSwitchScreen extends PackListScreen {
	private static final Logger LOGGER = LogUtils.getLogger();

	private final PackRepository repository;
	private static final Identifier UNKNOWN_PACK_ICON = Identifier.withDefaultNamespace("textures/misc/unknown_pack.png");
	private static final Map<String, Identifier> PACK_ICONS = new HashMap<>();
	/** SHA-1 of each pack's pack.png, by pack id, to spot packs that all share one logo. */
	private static final Map<String, String> PACK_ICON_HASHES = new HashMap<>();
	/** Packs sharing an icon with this many others or more use one of their textures as the icon instead. */
	private static final int SHARED_ICON_THRESHOLD = 3;
	/** Categories the player collapsed; kept for the session. */
	private static final Set<String> COLLAPSED = new HashSet<>();

	/** Packs you added yourself and packs the mod downloaded each get their own tab, so it's clear where each came from. */
	public enum Tab {
		/** Dropped onto the screen or put in the folder by hand. */
		IMPORTED("tweakswitch.tab.imported", "tweakswitch.empty.imported"),
		VANILLA_TWEAKS("tweakswitch.tab.vanillaTweaks", "tweakswitch.empty.vanillaTweaks"),
		MODRINTH("tweakswitch.tab.modrinth", "tweakswitch.empty.modrinth");

		final String key;
		final String emptyKey;

		Tab(final String key, final String emptyKey) {
			this.key = key;
			this.emptyKey = emptyKey;
		}

		static Tab fromSettings() {
			try {
				return valueOf(Settings.get().tab);
			} catch (IllegalArgumentException e) {
				return IMPORTED;
			}
		}

		/** The tab that's showing, for the screenshot test. */
		public static Tab fromSettingsForTest() {
			return fromSettings();
		}
	}

	/** The ways the switches can be grouped; the "Group" button beside the search box cycles through them. */
	public enum GroupBy {
		/** Vanilla Tweaks category, "Modrinth", or "Other Packs". */
		SOURCE("tweakswitch.group.source"),
		/** What the files inside change most: Particles, GUI, Blocks… */
		CHANGES("tweakswitch.group.changes"),
		/** Switched on, then off. */
		STATUS("tweakswitch.group.status"),
		/** By first letter, for long lists. */
		NAME("tweakswitch.group.name");

		final String key;

		GroupBy(final String key) {
			this.key = key;
		}

		static GroupBy fromSettings() {
			GroupBy chosen;
			try {
				chosen = valueOf(Settings.get().groupBy);
			} catch (IllegalArgumentException e) {
				chosen = SOURCE;
			}
			// "Category" means Vanilla Tweaks' categories, so the other tabs use "Changes" instead
			return chosen == SOURCE && Tab.fromSettings() != Tab.VANILLA_TWEAKS ? CHANGES : chosen;
		}

		/** The next mode for the Group button, skipping "Category" outside the Vanilla Tweaks tab. */
		GroupBy next() {
			GroupBy next = values()[(this.ordinal() + 1) % values().length];
			return next == SOURCE && Tab.fromSettings() != Tab.VANILLA_TWEAKS ? next.next() : next;
		}
	}

	/** How switches are ordered inside each group; the "Sort" button beside the tabs cycles through them. */
	public enum SortBy {
		NAME("tweakswitch.sort.name"),
		/** Newest file first. */
		RECENT("tweakswitch.sort.recent"),
		/** Switched-on packs first, then by name. */
		ON_FIRST("tweakswitch.sort.onFirst");

		final String key;

		SortBy(final String key) {
			this.key = key;
		}

		static SortBy fromSettings() {
			try {
				return valueOf(Settings.get().sortBy);
			} catch (IllegalArgumentException | NullPointerException e) {
				return NAME;
			}
		}
	}

	/** A small menu at the mouse, opened by right-clicking a switch. */
	private record Menu(int x, int y, int width, List<MenuItem> items) {
		static final int ITEM_HEIGHT = 14;

		int height() {
			return this.items.size() * ITEM_HEIGHT + 4;
		}

		int itemAt(final double mouseX, final double mouseY) {
			if (mouseX < this.x || mouseX >= this.x + this.width || mouseY < this.y + 2 || mouseY >= this.y + 2 + this.items.size() * ITEM_HEIGHT) {
				return -1;
			}
			return (int) ((mouseY - this.y - 2) / ITEM_HEIGHT);
		}
	}

	private record MenuItem(Component label, boolean danger, Runnable action) {
	}

	/** Where each downloaded pack came from, read when the list is built. */
	private Map<String, PackIndex.Entry> index = Map.of();
	/** Between a group and its sub-group in "Changes" mode. */
	private static final String SUBGROUP = " · ";
	/** Icon hashes used by {@link #SHARED_ICON_THRESHOLD} or more packs, worked out when the list is built. */
	private Set<String> sharedIconHashes = Set.of();
	/** How many packs are on each tab, for the tab labels. */
	private Map<Tab, Integer> tabCounts = Map.of();
	/** Which pack each switch row shows, for the Shift preview. */
	private final Map<AbstractWidget, Pack> switchRows = new HashMap<>();
	/** Switch state the player has chosen but not yet applied, by pack id, in the order chosen. */
	private final Map<String, Boolean> pending = new LinkedHashMap<>();
	/** The order the switched-on packs should end up in (pack ids, lowest priority first), from an imported file; null for no change. */
	private @Nullable List<String> wantedOrder;
	/** True once the pack selection was changed without reloading resources yet; Done reloads. */
	private boolean dirty;
	private @Nullable Menu menu;
	/** Vanilla Tweaks pack id → ids it clashes with, from the list saved by the picker (read in the background). */
	private static volatile Map<String, List<String>> savedConflicts = Map.of();
	/** Files that couldn't be deleted because the game still had them open; tried again after resources reload. */
	private static final List<Path> DELETE_LATER = new CopyOnWriteArrayList<>();
	/** Packs being re-downloaded right now (damaged files, or packs from an imported list). */
	private static final Set<String> FETCHING = java.util.concurrent.ConcurrentHashMap.newKeySet();
	/** Files just downloaded with "Switch On" set, to show switched on next time this screen shows. */
	private static final List<String> SWITCH_ON = new ArrayList<>();

	/**
	 * Switches the files on (pending, like a click) the next time the screen shows. Listed in download order; added in
	 * reverse, so packs pulled in as requirements end up below the packs that need them.
	 */
	public static void switchOnWhenShown(final List<String> fileNames) {
		SWITCH_ON.addAll(fileNames);
	}

	public TweakSwitchScreen(final @Nullable Screen lastScreen) {
		super(lastScreen, Component.translatable("tweakswitch.title"));
		this.repository = Minecraft.getInstance().getResourcePackRepository();
	}

	@Override
	public void added() {
		// Runs every time the screen is shown, including coming back from the downloader: pick up new packs in the folder
		this.repository.reload();
		for (int i = SWITCH_ON.size() - 1; i >= 0; i--) {
			String id = TweakSwitchPackSource.idFor(SWITCH_ON.get(i));
			if (this.repository.getAvailableIds().contains(id) && !this.repository.getSelectedIds().contains(id)) {
				this.pending.put(id, true);
			}
		}
		SWITCH_ON.clear();
		this.refreshList();
		// Look inside every pack in the background, for grouping by what it changes, searching its files and previews
		List<Path> files = this.ourPacks().stream().map(TweakSwitchScreen::path).toList();
		PackContents.analyzeAll(files, () -> {
			if (Compat.screen(this.minecraft) == this) {
				this.refreshList();
			}
		});
		loadSavedConflicts();
	}

	/** Reads which Vanilla Tweaks packs clash from the list the picker saved on disk. Never goes online. */
	private static void loadSavedConflicts() {
		Thread thread = new Thread(() -> {
			List<VanillaTweaks.VtPack> packs = VanillaTweaks.savedPacks(VanillaTweaks.Kind.RESOURCE_PACKS);
			if (packs != null) {
				Map<String, List<String>> conflicts = new HashMap<>();
				packs.stream().filter(pack -> !pack.incompatible().isEmpty()).forEach(pack -> conflicts.put(pack.id(), pack.incompatible()));
				savedConflicts = conflicts;
			}
		}, "TweakSwitch clash list");
		thread.setDaemon(true);
		thread.start();
	}

	/** A fresh copy of this screen with the same unsaved switches, for when the UI size or color changed under it. */
	public TweakSwitchScreen rebuilt() {
		TweakSwitchScreen copy = new TweakSwitchScreen(this.lastScreen);
		copy.pending.putAll(this.pending);
		copy.wantedOrder = this.wantedOrder;
		copy.dirty = this.dirty;
		return copy;
	}

	private List<Pack> ourPacks() {
		return this.repository.getAvailablePacks().stream()
			.filter(TweakSwitchPackSource::isOurs)
			.filter(pack -> !DELETE_LATER.contains(path(pack)))
			.toList();
	}

	private static Path path(final Pack pack) {
		return TweakSwitch.packsDir().resolve(TweakSwitchPackSource.fileName(pack));
	}

	/** "Group: Source" beside the search box; each click moves to the next way of grouping. */
	@Override
	protected List<AbstractWidget> createHeaderExtras() {
		ThemedButton group = new ThemedButton(this.theme(), ThemedButton.Kind.SECONDARY, Component.empty(), 104, button -> {
			Settings settings = Settings.get();
			settings.groupBy = GroupBy.fromSettings().next().name();
			settings.save();
			this.refreshList();
		}) {
			@Override
			public Component getMessage() {
				return Component.translatable("tweakswitch.group", Component.translatable(GroupBy.fromSettings().key));
			}
		};
		return List.of(group);
	}

	/** "Imported (62)", "Vanilla Tweaks (2)", "Modrinth (3)": the selected one is highlighted. */
	@Override
	protected List<AbstractWidget> createTabs() {
		List<AbstractWidget> tabs = new ArrayList<>();
		for (Tab tab : Tab.values()) {
			ThemedButton button = new ThemedButton(this.theme(), ThemedButton.Kind.SECONDARY, Component.empty(), 100, b -> {
				Settings settings = Settings.get();
				settings.tab = tab.name();
				settings.save();
				this.refreshList();
				if (this.list != null) {
					this.list.setScrollAmount(0.0);
				}
			}) {
				@Override
				public Component getMessage() {
					return Component.translatable(tab.key).append(" (" + TweakSwitchScreen.this.tabCounts.getOrDefault(tab, 0) + ")");
				}
			};
			tabs.add(button.highlightWhen(() -> Tab.fromSettings() == tab));
		}
		// "Sort: Name" at the end of the tab row
		ThemedButton sort = new ThemedButton(this.theme(), ThemedButton.Kind.SECONDARY, Component.empty(), 86, button -> {
			Settings settings = Settings.get();
			settings.sortBy = SortBy.values()[(SortBy.fromSettings().ordinal() + 1) % SortBy.values().length].name();
			settings.save();
			this.refreshList();
		}) {
			@Override
			public Component getMessage() {
				return Component.translatable("tweakswitch.sort", Component.translatable(SortBy.fromSettings().key));
			}
		};
		tabs.add(sort.withTooltip(Component.translatable("tweakswitch.sort.tooltip")));
		return tabs;
	}

	/** Which tab a pack belongs on: where downloads.json says it came from, or Imported if it isn't in there. */
	private Tab tabOf(final Pack pack) {
		return this.tabOf(TweakSwitchPackSource.fileName(pack));
	}

	private Tab tabOf(final String fileName) {
		PackIndex.Entry entry = this.index.get(fileName);
		if (entry != null && PackIndex.VANILLA_TWEAKS.equals(entry.source())) {
			return Tab.VANILLA_TWEAKS;
		}
		if (entry != null && PackIndex.MODRINTH.equals(entry.source())) {
			return Tab.MODRINTH;
		}
		return Tab.IMPORTED;
	}

	@Override
	protected void addFooter() {
		this.addFooterButtons(
			this.button(Component.translatable("tweakswitch.getVanillaTweaks"), 94, button -> Compat.setScreen(this.minecraft, new VanillaTweaksScreen(this)))
				.withTooltip(Component.translatable("tweakswitch.getVanillaTweaks.tooltip")),
			this.button(Component.translatable("tweakswitch.getModrinth"), 64, button -> Compat.setScreen(this.minecraft, new ModrinthScreen(this)))
				.withTooltip(Component.translatable("tweakswitch.getModrinth.tooltip")),
			new ThemedButton(this.theme(), ThemedButton.Kind.SECONDARY, Component.empty(), 70, button -> Compat.setScreen(this.minecraft, new UpdatesScreen(this))) {
				@Override
				public Component getMessage() {
					// The start-up check's count, until the Updates screen has looked for itself
					int known = Settings.get().knownPackUpdates;
					return known > 0 ? Component.translatable("tweakswitch.getUpdates.count", known) : Component.translatable("tweakswitch.getUpdates");
				}
			}.withTooltip(Component.translatable("tweakswitch.getUpdates.tooltip")),
			this.button(Component.translatable("tweakswitch.presets"), 48, button -> Compat.setScreen(this.minecraft, new PresetsScreen(this)))
				.withTooltip(Component.translatable("tweakswitch.presets.tooltip")),
			this.button(Component.translatable("tweakswitch.order"), 38, button -> this.openOrder())
				.withTooltip(Component.translatable("tweakswitch.order.tooltip")),
			this.button(Component.literal("⚙"), 18, button -> Compat.setScreen(this.minecraft, new SettingsScreen(this)))
				.withTooltip(Component.translatable("tweakswitch.settings.tooltip")),
			this.primaryButton(CommonComponents.GUI_DONE, 40, button -> this.onClose())
		);

	}

	@Override
	protected void fillList(final PackList list) {
		this.switchRows.clear();
		this.index = PackIndex.load();
		Tab tab = Tab.fromSettings();
		List<Pack> all = this.ourPacks();
		List<String> broken = TweakSwitchPackSource.brokenFiles();
		Map<Tab, Integer> counts = new EnumMap<>(Tab.class);
		all.forEach(pack -> counts.merge(this.tabOf(pack), 1, Integer::sum));
		broken.forEach(file -> counts.merge(this.tabOf(file), 1, Integer::sum));
		this.tabCounts = counts;
		Collection<String> selected = this.repository.getSelectedIds();
		List<Pack> ours = all.stream()
			.filter(pack -> this.tabOf(pack) == tab)
			.sorted(this.order(SortBy.fromSettings(), selected))
			.toList();
		// Search also looks at the file name and what's inside, so "flame" finds a pack that changes the flame particle
		List<Pack> shown = ours.stream().filter(pack -> {
			PackContents.Info contents = PackContents.cached(path(pack));
			return this.matches(pack.getTitle().getString(), pack.getDescription().getString(), TweakSwitchPackSource.fileName(pack),
				contents != null ? contents.searchText() : "");
		}).toList();
		List<String> shownBroken = broken.stream().filter(file -> this.tabOf(file) == tab && this.matches(file)).toList();

		// A newer TweakSwitch, if the start-up check found one
		String newVersion = StartupCheck.newerVersion();
		if (newVersion != null && !this.isSearching()) {
			list.addCentered(this.primaryButton(Component.translatable("tweakswitch.modUpdate", newVersion), 230,
				ConfirmLinkScreen.confirmLink(this, StartupCheck.DOWNLOAD_PAGE)).withTooltip(Component.translatable("tweakswitch.modUpdate.tooltip")));
		}

		if (shown.isEmpty() && shownBroken.isEmpty()) {
			list.addText(Component.translatable(ours.isEmpty() && broken.stream().noneMatch(file -> this.tabOf(file) == tab) ? tab.emptyKey : "tweakswitch.noMatches")
				.withStyle(ChatFormatting.GRAY));
			return;
		}

		// Damaged zips first: the game can't read them, so they'd otherwise just be missing
		if (!shownBroken.isEmpty()) {
			this.addCategory(list, COLLAPSED, true, Component.translatable("tweakswitch.broken.group").getString(), Integer.toString(shownBroken.size()),
				() -> shownBroken.forEach(file -> list.addRow(this.createBrokenRow(file))));
		}

		ours.forEach(TweakSwitchScreen::packIcon);
		Map<String, Long> iconUses = ours.stream().map(pack -> PACK_ICON_HASHES.get(pack.getId())).filter(java.util.Objects::nonNull)
			.collect(Collectors.groupingBy(hash -> hash, Collectors.counting()));
		this.sharedIconHashes = iconUses.entrySet().stream().filter(use -> use.getValue() >= SHARED_ICON_THRESHOLD).map(Map.Entry::getKey).collect(Collectors.toSet());
		GroupBy groupBy = GroupBy.fromSettings();
		String pinnedGroup = Component.translatable("tweakswitch.group.pinned").getString();
		String last = groupBy == GroupBy.SOURCE ? Component.translatable("tweakswitch.otherPacks").getString() : Component.translatable("tweakswitch.group.unknown").getString();
		// Pinned packs on top, then groups by name, with the catch-all group ("Other Packs", "Looking inside…") last
		Map<String, List<Pack>> groups = new TreeMap<>(Comparator.comparingInt((String name) -> name.equals(pinnedGroup) ? 0 : name.equals(last) ? 2 : 1)
			.thenComparing(Comparator.naturalOrder()));
		List<String> pinned = Settings.get().pinned;
		// In "Changes" mode, packs about the same thing get their own sub-group ("Particles · Dripping") when there are two or more
		Map<String, Long> subjects = groupBy != GroupBy.CHANGES ? Map.of() : shown.stream()
			.filter(pack -> !pinned.contains(TweakSwitchPackSource.fileName(pack)))
			.map(pack -> PackContents.cached(path(pack)))
			.filter(contents -> contents != null && !contents.subject().isEmpty())
			.collect(Collectors.groupingBy(contents -> contents.primary().label + SUBGROUP + contents.subject(), Collectors.counting()));
		for (Pack pack : shown) {
			if (pinned.contains(TweakSwitchPackSource.fileName(pack))) {
				groups.computeIfAbsent(pinnedGroup, name -> new ArrayList<>()).add(pack);
				continue;
			}
			String group = this.groupOf(pack, groupBy, selected, last);
			PackContents.Info contents = groupBy == GroupBy.CHANGES ? PackContents.cached(path(pack)) : null;
			if (contents != null && subjects.getOrDefault(group + SUBGROUP + contents.subject(), 0L) >= 2) {
				group = group + SUBGROUP + contents.subject();
			}
			groups.computeIfAbsent(group, name -> new ArrayList<>()).add(pack);
		}

		groups.forEach((group, packs) -> {
			long on = packs.stream().filter(pack -> this.isOn(pack, selected)).count();
			String count = groupBy == GroupBy.STATUS && !group.equals(pinnedGroup) ? Integer.toString(packs.size()) : on + "/" + packs.size() + " on";
			// The header's own switch turns the whole group on, or off if it's all on already
			SectionHeader.BulkSwitch bulk = new SectionHeader.BulkSwitch(
				() -> packs.stream().allMatch(pack -> this.isOn(pack, this.repository.getSelectedIds())),
				() -> {
					boolean allOn = packs.stream().allMatch(pack -> this.isOn(pack, this.repository.getSelectedIds()));
					packs.forEach(pack -> this.pending.put(pack.getId(), !allOn));
					this.queueRefresh();
				});
			this.addCategory(list, COLLAPSED, true, group, count, () -> packs.forEach(pack -> {
				SwitchRow row = this.createSwitch(pack);
				this.switchRows.put(row, pack);
				list.addRow(row);
			}), bulk);
		});
	}

	/** The order of switches inside a group. */
	private Comparator<Pack> order(final SortBy sortBy, final Collection<String> selected) {
		Comparator<Pack> byName = Comparator.comparing(pack -> pack.getTitle().getString().toLowerCase(Locale.ROOT));
		return switch (sortBy) {
			case NAME -> byName;
			case ON_FIRST -> Comparator.comparing((Pack pack) -> !this.isOn(pack, selected)).thenComparing(byName);
			case RECENT -> {
				Map<String, Long> modified = new HashMap<>();
				yield Comparator.comparing((Pack pack) -> -modified.computeIfAbsent(pack.getId(), id -> {
					try {
						return Files.getLastModifiedTime(path(pack)).toMillis();
					} catch (IOException e) {
						return 0L;
					}
				})).thenComparing(byName);
			}
		};
	}

	/** A zip the game couldn't read: named in red, with a click to download it again if its source is known, or delete it. */
	private ActionRow createBrokenRow(final String file) {
		PackIndex.Entry entry = this.index.get(file);
		boolean redownloadable = entry != null && entry.source() != null && entry.id() != null;
		String title = entry != null && entry.title() != null ? entry.title() : file;
		boolean busy = FETCHING.contains(file);
		Runnable delete = () -> this.confirmDelete(file, title, false);
		Runnable redownload = () -> {
			if (FETCHING.add(file)) {
				this.queueRefresh();
				this.fetchInBackground(List.of(new PackShare.Shared(file, entry.title(), entry.category(), entry.source(), entry.id(), false)), false);
			}
		};
		String action = Component.translatable(busy ? "tweakswitch.broken.fetching" : redownloadable ? "tweakswitch.broken.redownload" : "tweakswitch.delete").getString();
		List<ActionRow.Extra> extras = redownloadable
			? List.of(new ActionRow.Extra("✕", Component.translatable("tweakswitch.delete"), delete, true))
			: List.of();
		ActionRow row = new ActionRow(this.theme(), () -> UNKNOWN_PACK_ICON, title, Component.translatable("tweakswitch.broken.note").getString(), action, true,
			busy ? () -> { } : redownloadable ? redownload : delete, extras);
		return row.withTooltip(Component.translatable(redownloadable ? "tweakswitch.broken.tooltip.redownload" : "tweakswitch.broken.tooltip.delete", file));
	}

	private boolean isOn(final Pack pack, final Collection<String> selected) {
		return this.pending.getOrDefault(pack.getId(), selected.contains(pack.getId()));
	}

	private String groupOf(final Pack pack, final GroupBy groupBy, final Collection<String> selected, final String fallback) {
		return switch (groupBy) {
			case SOURCE -> {
				PackIndex.Entry entry = this.index.get(TweakSwitchPackSource.fileName(pack));
				yield entry != null ? entry.category() : fallback;
			}
			case CHANGES -> {
				PackContents.Info contents = PackContents.cached(path(pack));
				yield contents != null ? contents.primary().label : fallback;
			}
			case STATUS -> Component.translatable(this.isOn(pack, selected) ? "tweakswitch.group.on" : "tweakswitch.group.off").getString();
			case NAME -> {
				String title = pack.getTitle().getString().strip();
				char first = title.isEmpty() ? '#' : Character.toUpperCase(title.charAt(0));
				yield Character.isLetter(first) ? String.valueOf(first) : "#";
			}
		};
	}

	/**
	 * With Shift previews on: pictures from the pack's website if it came from Vanilla Tweaks or Modrinth, otherwise the
	 * textures inside it, so even a pack with no proper name or description shows what it does.
	 */
	@Override
	protected @Nullable Preview previewFor(final AbstractWidget hovered) {
		Pack pack = this.switchRows.get(hovered);
		if (pack == null) {
			return null;
		}
		Path file = path(pack);
		PackContents.Info contents = PackContents.cached(file);
		MutableComponent details = Component.empty();
		if (!pack.getDescription().getString().isBlank()) {
			details.append(pack.getDescription().copy().withStyle(ChatFormatting.WHITE)).append("\n");
		}
		if (contents != null) {
			details.append(contents.summary().copy().withColor(this.theme().accent())).append("\n");
		}
		List<Pack> clashes = this.clashesWith(pack, true);
		if (!clashes.isEmpty()) {
			String names = clashes.stream().map(other -> other.getTitle().getString()).collect(Collectors.joining(", "));
			details.append(Component.translatable("tweakswitch.clash.line", names).withStyle(ChatFormatting.GOLD)).append("\n");
		}
		details.append(Component.literal(TweakSwitchPackSource.fileName(pack)).withStyle(ChatFormatting.DARK_GRAY));

		List<String> websitePictures = List.of();
		PackIndex.Entry entry = this.index.get(TweakSwitchPackSource.fileName(pack));
		if (entry != null && entry.id() != null && PackIndex.VANILLA_TWEAKS.equals(entry.source())) {
			// The pack list isn't loaded here, so try both of the site's preview formats; the one that fails is skipped
			websitePictures = List.of(VanillaTweaks.previewUrl(entry.id(), "gif"), VanillaTweaks.previewUrl(entry.id(), "png"));
		} else if (entry != null && entry.id() != null && PackIndex.MODRINTH.equals(entry.source())) {
			websitePictures = Modrinth.images(new Modrinth.Project(entry.id(), "", pack.getTitle().getString(), "", "", 0, List.of(), null, List.of()));
		}
		List<Identifier> textures = websitePictures.isEmpty() ? PackContents.textures(file) : List.of();
		return new Preview(pack.getId(), pack.getTitle(), details, websitePictures, textures);
	}

	private SwitchRow createSwitch(final Pack pack) {
		String id = pack.getId();
		boolean compatible = pack.getCompatibility().isCompatible();
		Component description = compatible
			? pack.getDescription()
			: Component.empty().append(pack.getDescription()).append("\n").append(Component.translatable("tweakswitch.incompatible").withStyle(ChatFormatting.RED));
		PackContents.Info contents = PackContents.cached(path(pack));
		String tag = contents != null ? contents.primary().label : "";
		boolean on = this.isOn(pack, this.repository.getSelectedIds());
		SwitchRow row = new SwitchRow(this.theme(), this.iconFor(pack), pack.getTitle(), description, tag, !compatible,
			() -> this.pending.getOrDefault(id, this.repository.getSelectedIds().contains(id)),
			() -> this.toggle(pack));
		return row.pinned(Settings.get().pinned.contains(TweakSwitchPackSource.fileName(pack))).clash(on && !this.clashesWith(pack, true).isEmpty());
	}

	/** Flips one switch. Turning on a Vanilla Tweaks pack that clashes with one that's on asks what to do first. */
	private void toggle(final Pack pack) {
		String id = pack.getId();
		boolean turningOn = !this.isOn(pack, this.repository.getSelectedIds());
		List<Pack> clashes = turningOn && Settings.get().conflictWarnings ? this.clashesWith(pack, true) : List.of();
		if (clashes.isEmpty()) {
			this.pending.put(id, turningOn);
			// Updates the "x/y on" counts
			this.queueRefresh();
			return;
		}
		String names = clashes.stream().map(other -> other.getTitle().getString()).collect(Collectors.joining(", "));
		Compat.setScreen(this.minecraft, new ConfirmScreen(turnOthersOff -> {
			this.pending.put(id, true);
			if (turnOthersOff) {
				clashes.forEach(other -> this.pending.put(other.getId(), false));
			}
			Compat.setScreen(this.minecraft, this);
		}, Component.translatable("tweakswitch.clash.title"), Component.translatable("tweakswitch.clash.message", pack.getTitle(), names),
			Component.translatable(clashes.size() == 1 ? "tweakswitch.clash.turnOff" : "tweakswitch.clash.turnOffAll"), Component.translatable("tweakswitch.clash.keepBoth")));
	}

	/** The Vanilla Tweaks ids a pack clashes with: saved with the download, or from the picker's saved list. */
	private List<String> conflictIds(final PackIndex.@Nullable Entry entry) {
		if (entry == null || entry.id() == null || !PackIndex.VANILLA_TWEAKS.equals(entry.source())) {
			return List.of();
		}
		return entry.conflicts() != null ? entry.conflicts() : savedConflicts.getOrDefault(entry.id(), List.of());
	}

	/**
	 * Vanilla Tweaks packs that clash with this one, e.g. two crosshairs: only one of them can show.
	 *
	 * @param onlyOn true to count just the ones switched on
	 */
	private List<Pack> clashesWith(final Pack pack, final boolean onlyOn) {
		PackIndex.Entry entry = this.index.get(TweakSwitchPackSource.fileName(pack));
		if (entry == null || entry.id() == null || !PackIndex.VANILLA_TWEAKS.equals(entry.source())) {
			return List.of();
		}
		List<String> mine = this.conflictIds(entry);
		Collection<String> selected = this.repository.getSelectedIds();
		List<Pack> clashes = new ArrayList<>();
		for (Pack other : this.ourPacks()) {
			PackIndex.Entry otherEntry = this.index.get(TweakSwitchPackSource.fileName(other));
			if (other == pack || otherEntry == null || otherEntry.id() == null || !PackIndex.VANILLA_TWEAKS.equals(otherEntry.source())) {
				continue;
			}
			boolean clash = mine.contains(otherEntry.id()) || this.conflictIds(otherEntry).contains(entry.id());
			if (clash && (!onlyOn || this.isOn(other, selected))) {
				clashes.add(other);
			}
		}
		return clashes;
	}

	/**
	 * Vanilla Tweaks zips all carry the same Vanilla Tweaks logo, so their switches use each tweak's icon from the site
	 * (the logo until it loads). Everything else uses its own pack.png.
	 */
	private Supplier<Identifier> iconFor(final Pack pack) {
		Identifier own = packIcon(pack);
		PackIndex.Entry entry = this.index.get(TweakSwitchPackSource.fileName(pack));
		if (entry == null || !PackIndex.VANILLA_TWEAKS.equals(entry.source()) || entry.id() == null) {
			// Packs split from one big pack all carry its logo, and some have no icon at all; for those, a texture the pack
			// changes tells them apart
			String hash = PACK_ICON_HASHES.get(pack.getId());
			if (own == UNKNOWN_PACK_ICON || hash != null && this.sharedIconHashes.contains(hash)) {
				Path file = path(pack);
				return () -> {
					List<Identifier> textures = PackContents.textures(file);
					return textures.isEmpty() ? own : textures.get(0);
				};
			}
			return () -> own;
		}
		String url = VanillaTweaks.iconUrl(entry.id());
		return () -> {
			Identifier site = RemoteImages.getIcon(url).textureAt(0);
			return site != null ? site : own;
		};
	}

	/** The pack's own pack.png as a texture, loaded once per session; vanilla's "unknown pack" picture if it has none. */
	static Identifier packIcon(final Pack pack) {
		return PACK_ICONS.computeIfAbsent(pack.getId(), id -> {
			try {
				byte[] bytes = Compat.readPackRoot(pack, "pack.png");
				if (bytes != null) {
					PACK_ICON_HASHES.put(id, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes)));
					Identifier location = Identifier.fromNamespaceAndPath(TweakSwitch.MOD_ID, "pack_icon/" + Integer.toHexString(id.hashCode()) + "_" + PACK_ICONS.size());
					Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(location::toString, NativeImage.read(bytes)));
					return location;
				}
			} catch (Exception e) {
				LOGGER.debug("No icon for pack {}", id, e);
			}
			return UNKNOWN_PACK_ICON;
		});
	}

	@Override
	public void onClose() {
		this.applyChanges();
		Compat.setScreen(this.minecraft, this.lastScreen);
	}

	/** Moves the switches the player flipped into the pack selection, without reloading resources yet. */
	private void commit() {
		boolean changed = false;
		for (Map.Entry<String, Boolean> change : this.pending.entrySet()) {
			changed |= change.getValue() ? this.repository.addPack(change.getKey()) : this.repository.removePack(change.getKey());
		}
		this.pending.clear();
		if (this.wantedOrder != null) {
			List<String> before = List.copyOf(this.repository.getSelectedIds());
			List<String> after = Presets.reorder(before, this.wantedOrder);
			if (!after.equals(before)) {
				this.repository.setSelected(after);
				changed = true;
			}
			this.wantedOrder = null;
		}
		this.dirty |= changed;
	}

	private void applyChanges() {
		this.commit();
		if (this.dirty) {
			this.dirty = false;
			// Saves the new pack list to options.txt and reloads resources
			this.minecraft.options.updateResourcePacks(this.repository);
		}
	}

	/** The pack ids that are (or will be, once Done is clicked) switched on, lowest priority first. */
	public List<String> onIds() {
		List<String> ids = new ArrayList<>(this.repository.getSelectedIds());
		this.pending.forEach((id, on) -> {
			ids.remove(id);
			if (on) {
				ids.add(id);
			}
		});
		List<String> ours = ids.stream().filter(id -> id.startsWith(Presets.ID_PREFIX)).toList();
		return this.wantedOrder == null ? ours : Presets.reorder(ours, this.wantedOrder);
	}

	/** Switches on exactly the preset's packs, right away, with one reload. */
	public void applyPreset(final String name, final List<String> files) {
		this.pending.clear();
		this.wantedOrder = null;
		this.dirty = false;
		Presets.apply(this.minecraft, name, files);
	}

	/** Opens the order screen. The flipped switches are put into the selection first, so it lists what will be on. */
	private void openOrder() {
		this.commit();
		Compat.setScreen(this.minecraft, new PackOrderScreen(this, this.repository, () -> this.dirty = true));
	}

	// ---- Right-click menu: pin, split, delete

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (this.menu != null) {
			int item = this.menu.itemAt(event.x(), event.y());
			Menu open = this.menu;
			this.menu = null;
			if (item >= 0 && event.button() == 0) {
				open.items().get(item).action().run();
			}
			return true;
		}
		if (event.button() == 1) {
			Pack pack = this.switchRows.get(this.hoveredListWidget(event.x(), event.y()));
			if (pack != null) {
				this.openMenu(pack, (int) event.x(), (int) event.y());
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** With a switch focused by keyboard: Delete removes the pack, P pins it, and the Menu key opens its menu. Esc closes the menu. */
	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (this.menu != null) {
			this.menu = null;
			return true;
		}
		Pack focused = this.switchRows.get(this.focusedListWidget());
		if (focused != null) {
			if (event.key() == InputConstants.KEY_DELETE) {
				this.confirmDelete(TweakSwitchPackSource.fileName(focused), focused.getTitle().getString(), this.repository.getSelectedIds().contains(focused.getId()));
				return true;
			}
			if (event.key() == InputConstants.KEY_P) {
				this.togglePin(focused);
				return true;
			}
			if (event.key() == 348) {
				// GLFW's Menu key
				AbstractWidget widget = this.focusedListWidget();
				this.openMenu(focused, widget.getX() + 20, widget.getY() + widget.getHeight());
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private void openMenu(final Pack pack, final int x, final int y) {
		String file = TweakSwitchPackSource.fileName(pack);
		List<MenuItem> items = new ArrayList<>();
		boolean pinned = Settings.get().pinned.contains(file);
		items.add(new MenuItem(Component.translatable(pinned ? "tweakswitch.menu.unpin" : "tweakswitch.menu.pin"), false, () -> this.togglePin(pack)));
		List<String> tweaks = VanillaTweaks.combinedPackIds(path(pack));
		if (!tweaks.isEmpty()) {
			items.add(new MenuItem(Component.translatable("tweakswitch.menu.split", tweaks.size()), false, () -> this.splitCombined(tweaks)));
		}
		items.add(new MenuItem(Component.translatable("tweakswitch.openFolder"), false, () -> Compat.openFolder(this.packsDirCreated())));
		items.add(new MenuItem(Component.translatable("tweakswitch.menu.delete"), true,
			() -> this.confirmDelete(file, pack.getTitle().getString(), this.repository.getSelectedIds().contains(pack.getId()))));
		int width = items.stream().mapToInt(item -> this.font.width(item.label())).max().orElse(60) + 14;
		Menu sized = new Menu(0, 0, width, items);
		this.menu = new Menu(Math.max(2, Math.min(x, this.width - width - 2)), Math.max(2, Math.min(y, this.height - sized.height() - 2)), width, items);
	}

	@Override
	protected void drawPopups(final Gfx graphics, final int mouseX, final int mouseY) {
		Menu open = this.menu;
		if (open == null) {
			return;
		}
		graphics.nextStratum();
		Draw.roundRectOutlined(graphics, open.x(), open.y(), open.width(), open.height(), 4, this.theme().bar(), this.theme().accent());
		int hovered = open.itemAt(mouseX, mouseY);
		for (int i = 0; i < open.items().size(); i++) {
			MenuItem item = open.items().get(i);
			int itemY = open.y() + 2 + i * Menu.ITEM_HEIGHT;
			if (i == hovered) {
				Draw.roundRect(graphics, open.x() + 2, itemY, open.width() - 4, Menu.ITEM_HEIGHT, 3, this.theme().cardHover());
			}
			graphics.text(this.font, item.label(), open.x() + 7, itemY + 3, item.danger() ? 0xFFFF6B6B : this.theme().text(), false);
		}
	}

	private void togglePin(final Pack pack) {
		Settings settings = Settings.get();
		String file = TweakSwitchPackSource.fileName(pack);
		if (!settings.pinned.remove(file)) {
			settings.pinned.add(file);
		}
		settings.save();
		this.queueRefresh();
	}

	/** Opens the Vanilla Tweaks picker with a combined zip's tweaks already picked, ready to download as separate switches. */
	private void splitCombined(final List<String> tweakIds) {
		VanillaTweaksScreen.preselect(tweakIds);
		Compat.setScreen(this.minecraft, new VanillaTweaksScreen(this));
	}

	// ---- Deleting packs

	private void confirmDelete(final String file, final String title, final boolean on) {
		Compat.setScreen(this.minecraft, new ConfirmScreen(confirmed -> {
			if (confirmed) {
				this.delete(file);
			}
			Compat.setScreen(this.minecraft, this);
		}, Component.translatable("tweakswitch.delete.title", title),
			Component.translatable(on ? "tweakswitch.delete.messageOn" : "tweakswitch.delete.message", file),
			Component.translatable("tweakswitch.delete"), CommonComponents.GUI_CANCEL));
	}

	/** Removes a pack's file from the TweakSwitch folder, switching it off first if it's on. */
	private void delete(final String file) {
		String id = TweakSwitchPackSource.idFor(file);
		this.pending.remove(id);
		if (this.repository.getSelectedIds().contains(id)) {
			this.repository.removePack(id);
			this.dirty = true;
		}
		Path target = TweakSwitch.packsDir().resolve(file);
		if (!deleteFile(target)) {
			// Windows keeps a pack's zip locked while it's in use; it goes once resources have reloaded without it
			DELETE_LATER.add(target);
		}
		PackIndex.remove(file);
		Settings settings = Settings.get();
		if (settings.pinned.remove(file)) {
			settings.save();
		}
	}

	private static boolean deleteFile(final Path target) {
		try {
			if (Files.isDirectory(target)) {
				try (Stream<Path> paths = Files.walk(target)) {
					for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
						Files.deleteIfExists(path);
					}
				}
			} else {
				Files.deleteIfExists(target);
			}
			return true;
		} catch (IOException e) {
			LOGGER.debug("Couldn't delete {} yet", target, e);
			return false;
		}
	}

	/** Tries again to delete files that were locked. Called now and then from the client tick, not while resources load. */
	public static void deleteLeftovers(final Minecraft minecraft) {
		if (DELETE_LATER.isEmpty() || Compat.loading(minecraft)) {
			return;
		}
		Collection<String> selected = minecraft.getResourcePackRepository().getSelectedIds();
		DELETE_LATER.removeIf(file -> !selected.contains(TweakSwitchPackSource.idFor(file.getFileName().toString())) && deleteFile(file));
	}

	// ---- Export and import

	/** Saves the pack list and switch states to a file in tweakswitch/exports and shows it in the file manager. */
	public void exportSwitches() {
		this.exportSwitches(true);
	}

	/**
	 * Saves the pack list and switch states to a file in tweakswitch/exports.
	 *
	 * @param reveal true opens the folder afterwards
	 * @return the file, or null if it couldn't be written
	 */
	public @Nullable Path exportSwitches(final boolean reveal) {
		this.index = PackIndex.load();
		List<String> on = this.onIds();
		List<PackShare.Shared> packs = new ArrayList<>();
		Map<String, Pack> byId = new HashMap<>();
		this.ourPacks().forEach(pack -> byId.put(pack.getId(), pack));
		List<Pack> ordered = new ArrayList<>();
		on.stream().map(byId::get).filter(java.util.Objects::nonNull).forEach(ordered::add);
		this.ourPacks().stream().filter(pack -> !on.contains(pack.getId())).forEach(ordered::add);
		for (Pack pack : ordered) {
			String file = TweakSwitchPackSource.fileName(pack);
			PackIndex.Entry entry = this.index.get(file);
			packs.add(new PackShare.Shared(file, pack.getTitle().getString(), entry != null ? entry.category() : null, entry != null ? entry.source() : null,
				entry != null ? entry.id() : null, on.contains(pack.getId())));
		}
		try {
			Path file = PackShare.write(new PackShare.Export(Modrinth.gameVersion(), packs));
			toast(Component.translatable("tweakswitch.export.done", packs.size()), Component.literal(file.getFileName().toString()));
			if (reveal) {
				Compat.openFolder(PackShare.dir());
			}
			return file;
		} catch (IOException e) {
			LOGGER.warn("Couldn't write the export", e);
			toast(Component.translatable("tweakswitch.export.failed"), Component.literal(String.valueOf(e.getMessage())));
			return null;
		}
	}

	private void toast(final Component title, final Component body) {
		SystemToast.addOrUpdate(Compat.toasts(this.minecraft), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, body);
	}

	/**
	 * Reads an exported list: packs you have are switched to match, and ones you don't have that came from Vanilla Tweaks
	 * or Modrinth can be downloaded. Asks first, saying how many of each.
	 */
	public void importSwitches(final Path file) {
		PackShare.Export export;
		try {
			export = PackShare.parse(Files.readString(file));
		} catch (IOException e) {
			export = null;
		}
		if (export == null) {
			this.toast(Component.translatable("tweakswitch.import.notExport"), Component.literal(file.getFileName().toString()));
			return;
		}
		Set<String> have = this.ourPacks().stream().map(TweakSwitchPackSource::fileName).collect(Collectors.toSet());
		List<PackShare.Shared> matching = export.packs().stream().filter(pack -> have.contains(pack.file())).toList();
		List<PackShare.Shared> downloadable = export.packs().stream().filter(pack -> !have.contains(pack.file()) && pack.downloadable()).toList();
		long skipped = export.packs().size() - matching.size() - downloadable.size();
		PackShare.Export chosen = export;
		Compat.setScreen(this.minecraft, new ConfirmScreen(confirmed -> {
			Compat.setScreen(this.minecraft, this);
			if (confirmed) {
				this.applyImport(chosen, matching, downloadable);
			}
		}, Component.translatable("tweakswitch.import.title"),
			Component.translatable("tweakswitch.import.message", matching.size(), downloadable.size(), skipped),
			Component.translatable(downloadable.isEmpty() ? "tweakswitch.import.apply" : "tweakswitch.import.applyAndDownload"), CommonComponents.GUI_CANCEL));
	}

	private void applyImport(final PackShare.Export export, final List<PackShare.Shared> matching, final List<PackShare.Shared> downloadable) {
		// Packs not in the list are left alone
		matching.forEach(pack -> this.pending.put(TweakSwitchPackSource.idFor(pack.file()), pack.on()));
		this.wantedOrder = export.packs().stream().filter(PackShare.Shared::on).map(pack -> TweakSwitchPackSource.idFor(pack.file())).collect(Collectors.toCollection(ArrayList::new));
		this.refreshList();
		if (!downloadable.isEmpty()) {
			downloadable.forEach(pack -> FETCHING.add(pack.file()));
			this.fetchInBackground(downloadable, true);
		}
	}

	/**
	 * Downloads packs again from where they came from, one at a time, then shows them. Used for damaged files and for
	 * packs named in an imported list.
	 *
	 * @param applyStates true to give each downloaded pack the on/off state its entry asks for
	 */
	private void fetchInBackground(final List<PackShare.Shared> packs, final boolean applyStates) {
		Thread thread = new Thread(() -> {
			Map<PackShare.Shared, String> saved = new LinkedHashMap<>();
			int failed = 0;
			for (PackShare.Shared pack : packs) {
				try {
					String name = PackShare.fetch(pack.source(), pack.id(), pack.title());
					saved.put(pack, name);
					if (!name.equals(pack.file())) {
						// It came back under another name (the site renamed it): the damaged original would be a duplicate
						Path old = TweakSwitch.packsDir().resolve(pack.file());
						if (TweakSwitchPackSource.brokenFiles().contains(pack.file()) && deleteFile(old)) {
							PackIndex.remove(pack.file());
						}
					}
				} catch (Exception e) {
					LOGGER.warn("Couldn't download {} again", pack.file(), e);
					failed++;
				} finally {
					FETCHING.remove(pack.file());
				}
			}
			int failures = failed;
			this.minecraft.execute(() -> {
				if (applyStates) {
					saved.forEach((pack, name) -> {
						this.pending.put(TweakSwitchPackSource.idFor(name), pack.on());
						if (this.wantedOrder != null) {
							int at = this.wantedOrder.indexOf(TweakSwitchPackSource.idFor(pack.file()));
							if (at >= 0) {
								this.wantedOrder.set(at, TweakSwitchPackSource.idFor(name));
							}
						}
					});
				}
				this.toast(Component.translatable(saved.size() == 1 ? "tweakswitch.doneOne" : "tweakswitch.done", saved.size()),
					failures > 0 ? Component.translatable("tweakswitch.someFailed", failures).withStyle(ChatFormatting.RED) : Component.translatable("tweakswitch.fetch.doneHint"));
				if (Compat.screen(this.minecraft) == this) {
					this.repository.reload();
					this.refreshList();
				}
			});
		}, "TweakSwitch pack re-download");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public void onFilesDrop(final List<Path> files) {
		// An exported switch list, not a pack
		for (Path file : files) {
			if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
				this.importSwitches(file);
				return;
			}
		}
		String names = files.stream().map(path -> path.getFileName().toString()).collect(Collectors.joining(", "));
		// A combined zip from the Vanilla Tweaks website can come in as one switch per tweak instead
		for (Path file : files) {
			List<String> tweaks = files.size() == 1 ? VanillaTweaks.combinedPackIds(file) : List.of();
			if (!tweaks.isEmpty()) {
				Compat.setScreen(this.minecraft, new ConfirmScreen(split -> {
					if (split) {
						this.splitCombined(tweaks);
					} else {
						this.importDropped(files);
						Compat.setScreen(this.minecraft, this);
					}
				}, Component.translatable("tweakswitch.split.title"), Component.translatable("tweakswitch.split.message", names, tweaks.size()),
					Component.translatable("tweakswitch.split.yes", tweaks.size()), Component.translatable("tweakswitch.split.no")));
				return;
			}
		}
		Compat.setScreen(this.minecraft, new ConfirmScreen(confirmed -> {
			if (confirmed) {
				this.importDropped(files);
			}

			Compat.setScreen(this.minecraft, this);
		}, Component.translatable("tweakswitch.dropConfirm"), Component.literal(names)));
	}

	private void importDropped(final List<Path> files) {
		this.importPacks(files);
		this.repository.reload();
		// Dropped packs are imported ones, so show that tab
		Settings settings = Settings.get();
		settings.tab = Tab.IMPORTED.name();
		settings.save();
	}

	private void importPacks(final List<Path> files) {
		Path target = this.packsDirCreated();
		boolean failed = false;
		for (Path source : files) {
			try {
				if (Files.isDirectory(source)) {
					copyDirectory(source, target.resolve(source.getFileName().toString()));
				} else {
					Files.copy(source, target.resolve(source.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException e) {
				LOGGER.warn("Failed to import pack {}", source, e);
				failed = true;
			}
		}

		if (failed) {
			SystemToast.onPackCopyFailure(this.minecraft, target.toString());
		}
	}

	private static void copyDirectory(final Path source, final Path target) throws IOException {
		try (Stream<Path> paths = Files.walk(source)) {
			for (Path path : (Iterable<Path>) paths::iterator) {
				Path destination = target.resolve(source.relativize(path).toString());
				if (Files.isDirectory(path)) {
					Files.createDirectories(destination);
				} else {
					Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private Path packsDirCreated() {
		Path dir = TweakSwitch.packsDir();
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			LOGGER.warn("Failed to create {}", dir, e);
		}
		return dir;
	}
}
