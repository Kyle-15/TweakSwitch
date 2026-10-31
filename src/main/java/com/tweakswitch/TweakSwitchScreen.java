package com.tweakswitch;

import com.mojang.blaze3d.Blaze3D;
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
import com.tweakswitch.ui.SwitchRow;
import com.tweakswitch.ui.ThemedButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.MutableComponent;
import java.io.InputStream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.resources.IoSupplier;
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
	/** Switch state the player has chosen but not yet applied, by pack id. */
	private final Map<String, Boolean> pending = new HashMap<>();

	public TweakSwitchScreen(final @Nullable Screen lastScreen) {
		super(lastScreen, Component.translatable("tweakswitch.title"));
		this.repository = Minecraft.getInstance().getResourcePackRepository();
	}

	@Override
	public void added() {
		// Runs every time the screen is shown, including coming back from the downloader: pick up new packs in the folder
		this.repository.reload();
		this.refreshList();
		// Look inside every pack in the background, for grouping by what it changes, searching its files and previews
		List<Path> files = this.ourPacks().stream().map(TweakSwitchScreen::path).toList();
		PackContents.analyzeAll(files, () -> {
			if (this.minecraft.gui.screen() == this) {
				this.refreshList();
			}
		});
	}

	private List<Pack> ourPacks() {
		return this.repository.getAvailablePacks().stream().filter(TweakSwitchPackSource::isOurs).toList();
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
		return tabs;
	}

	/** Which tab a pack belongs on: where downloads.json says it came from, or Imported if it isn't in there. */
	private Tab tabOf(final Pack pack) {
		PackIndex.Entry entry = this.index.get(TweakSwitchPackSource.fileName(pack));
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
			this.button(Component.translatable("tweakswitch.getVanillaTweaks"), 100, button -> this.minecraft.gui.setScreen(new VanillaTweaksScreen(this)))
				.withTooltip(Component.translatable("tweakswitch.getVanillaTweaks.tooltip")),
			this.button(Component.translatable("tweakswitch.getModrinth"), 80, button -> this.minecraft.gui.setScreen(new ModrinthScreen(this)))
				.withTooltip(Component.translatable("tweakswitch.getModrinth.tooltip")),
			this.button(Component.translatable("tweakswitch.getUpdates"), 70, button -> this.minecraft.gui.setScreen(new UpdatesScreen(this)))
				.withTooltip(Component.translatable("tweakswitch.getUpdates.tooltip")),
			this.button(Component.translatable("tweakswitch.openFolder"), 80, button -> Blaze3D.openPath(this.packsDirCreated())),
			this.primaryButton(CommonComponents.GUI_DONE, 60, button -> this.onClose())
		);

	}

	@Override
	protected void fillList(final PackList list) {
		this.switchRows.clear();
		this.index = PackIndex.load();
		Tab tab = Tab.fromSettings();
		List<Pack> all = this.ourPacks();
		this.tabCounts = all.stream().collect(Collectors.groupingBy(this::tabOf, () -> new EnumMap<>(Tab.class), Collectors.counting()))
			.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().intValue()));
		List<Pack> ours = all.stream()
			.filter(pack -> this.tabOf(pack) == tab)
			.sorted(Comparator.comparing(pack -> pack.getTitle().getString().toLowerCase(Locale.ROOT)))
			.toList();
		// Search also looks at the file name and what's inside, so "flame" finds a pack that changes the flame particle
		List<Pack> shown = ours.stream().filter(pack -> {
			PackContents.Info contents = PackContents.cached(path(pack));
			return this.matches(pack.getTitle().getString(), pack.getDescription().getString(), TweakSwitchPackSource.fileName(pack),
				contents != null ? contents.searchText() : "");
		}).toList();

		if (shown.isEmpty()) {
			list.addText(Component.translatable(ours.isEmpty() ? tab.emptyKey : "tweakswitch.noMatches").withStyle(ChatFormatting.GRAY));
			return;
		}

		ours.forEach(TweakSwitchScreen::packIcon);
		Map<String, Long> iconUses = ours.stream().map(pack -> PACK_ICON_HASHES.get(pack.getId())).filter(java.util.Objects::nonNull)
			.collect(Collectors.groupingBy(hash -> hash, Collectors.counting()));
		this.sharedIconHashes = iconUses.entrySet().stream().filter(use -> use.getValue() >= SHARED_ICON_THRESHOLD).map(Map.Entry::getKey).collect(Collectors.toSet());
		Collection<String> selected = this.repository.getSelectedIds();
		GroupBy groupBy = GroupBy.fromSettings();
		String last = groupBy == GroupBy.SOURCE ? Component.translatable("tweakswitch.otherPacks").getString() : Component.translatable("tweakswitch.group.unknown").getString();
		// Sorted by name, with the catch-all group ("Other Packs", "Not looked at yet") last
		Map<String, List<Pack>> groups = new TreeMap<>(Comparator.comparing((String name) -> name.equals(last)).thenComparing(Comparator.naturalOrder()));
		// In "Changes" mode, packs about the same thing get their own sub-group ("Particles · Dripping") when there are two or more
		Map<String, Long> subjects = groupBy != GroupBy.CHANGES ? Map.of() : shown.stream()
			.map(pack -> PackContents.cached(path(pack)))
			.filter(contents -> contents != null && !contents.subject().isEmpty())
			.collect(Collectors.groupingBy(contents -> contents.primary().label + SUBGROUP + contents.subject(), Collectors.counting()));
		for (Pack pack : shown) {
			String group = this.groupOf(pack, groupBy, selected, last);
			PackContents.Info contents = groupBy == GroupBy.CHANGES ? PackContents.cached(path(pack)) : null;
			if (contents != null && subjects.getOrDefault(group + SUBGROUP + contents.subject(), 0L) >= 2) {
				group = group + SUBGROUP + contents.subject();
			}
			groups.computeIfAbsent(group, name -> new ArrayList<>()).add(pack);
		}

		groups.forEach((group, packs) -> {
			long on = packs.stream().filter(pack -> this.isOn(pack, selected)).count();
			String count = groupBy == GroupBy.STATUS ? Integer.toString(packs.size()) : on + "/" + packs.size() + " on";
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
				list.addRow(row, SwitchRow.HEIGHT);
			}), bulk);
		});
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
		return new SwitchRow(this.theme(), this.iconFor(pack), pack.getTitle(), description, tag, !compatible,
			() -> this.pending.getOrDefault(id, this.repository.getSelectedIds().contains(id)),
			() -> {
				this.pending.put(id, !this.pending.getOrDefault(id, this.repository.getSelectedIds().contains(id)));
				// Updates the "x/y on" counts
				this.queueRefresh();
			});
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
	private static Identifier packIcon(final Pack pack) {
		return PACK_ICONS.computeIfAbsent(pack.getId(), id -> {
			try (PackMetadataResources resources = pack.openMetadata()) {
				IoSupplier<InputStream> png = resources.getRootResource("pack.png");
				if (png != null) {
					try (InputStream stream = png.get()) {
						byte[] bytes = stream.readAllBytes();
						PACK_ICON_HASHES.put(id, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes)));
						Identifier location = Identifier.fromNamespaceAndPath(TweakSwitch.MOD_ID, "pack_icon/" + Integer.toHexString(id.hashCode()) + "_" + PACK_ICONS.size());
						Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(location::toString, NativeImage.read(bytes)));
						return location;
					}
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
		this.minecraft.gui.setScreen(this.lastScreen);
	}

	private void applyChanges() {
		boolean changed = false;
		for (Map.Entry<String, Boolean> change : this.pending.entrySet()) {
			changed |= change.getValue() ? this.repository.addPack(change.getKey()) : this.repository.removePack(change.getKey());
		}

		this.pending.clear();
		if (changed) {
			// Saves the new pack list to options.txt and reloads resources
			this.minecraft.options.updateResourcePacks(this.repository);
		}
	}

	@Override
	public void onFilesDrop(final List<Path> files) {
		String names = files.stream().map(path -> path.getFileName().toString()).collect(Collectors.joining(", "));
		this.minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				this.importPacks(files);
				this.repository.reload();
				// Dropped packs are imported ones, so show that tab
				Settings settings = Settings.get();
				settings.tab = Tab.IMPORTED.name();
				settings.save();
			}

			this.minecraft.gui.setScreen(this);
		}, Component.translatable("tweakswitch.dropConfirm"), Component.literal(names)));
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
