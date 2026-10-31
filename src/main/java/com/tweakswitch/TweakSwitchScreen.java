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
import java.util.HashMap;
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
	/** Categories the player collapsed; kept for the session. */
	private static final Set<String> COLLAPSED = new HashSet<>();

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
			try {
				return valueOf(Settings.get().groupBy);
			} catch (IllegalArgumentException e) {
				return SOURCE;
			}
		}
	}

	/** Where each downloaded pack came from, read when the list is built. */
	private Map<String, PackIndex.Entry> index = Map.of();
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
			GroupBy[] all = GroupBy.values();
			Settings settings = Settings.get();
			settings.groupBy = all[(GroupBy.fromSettings().ordinal() + 1) % all.length].name();
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

	@Override
	protected void addFooter() {
		this.addFooterButtons(
			this.button(Component.translatable("tweakswitch.getVanillaTweaks"), 90, button -> this.minecraft.gui.setScreen(new VanillaTweaksScreen(this)))
				.withTooltip(Component.translatable("tweakswitch.getVanillaTweaks.tooltip")),
			this.button(Component.translatable("tweakswitch.getModrinth"), 70, button -> this.minecraft.gui.setScreen(new ModrinthScreen(this)))
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
		List<Pack> ours = this.ourPacks().stream()
			.sorted(Comparator.comparing(pack -> pack.getTitle().getString().toLowerCase(Locale.ROOT)))
			.toList();
		// Search also looks at the file name and what's inside, so "flame" finds a pack that changes the flame particle
		List<Pack> shown = ours.stream().filter(pack -> {
			PackContents.Info contents = PackContents.cached(path(pack));
			return this.matches(pack.getTitle().getString(), pack.getDescription().getString(), TweakSwitchPackSource.fileName(pack),
				contents != null ? contents.searchText() : "");
		}).toList();

		if (shown.isEmpty()) {
			list.addText(Component.translatable(ours.isEmpty() ? "tweakswitch.empty" : "tweakswitch.noMatches").withStyle(ChatFormatting.GRAY));
			return;
		}

		this.index = PackIndex.load();
		Collection<String> selected = this.repository.getSelectedIds();
		GroupBy groupBy = GroupBy.fromSettings();
		String last = groupBy == GroupBy.SOURCE ? Component.translatable("tweakswitch.otherPacks").getString() : Component.translatable("tweakswitch.group.unknown").getString();
		// Sorted by name, with the catch-all group ("Other Packs", "Not looked at yet") last
		Map<String, List<Pack>> groups = new TreeMap<>(Comparator.comparing((String name) -> name.equals(last)).thenComparing(Comparator.naturalOrder()));
		for (Pack pack : shown) {
			groups.computeIfAbsent(this.groupOf(pack, groupBy, selected, last), name -> new ArrayList<>()).add(pack);
		}

		groups.forEach((group, packs) -> {
			long on = packs.stream().filter(pack -> this.isOn(pack, selected)).count();
			String count = groupBy == GroupBy.STATUS ? Integer.toString(packs.size()) : on + "/" + packs.size() + " on";
			this.addCategory(list, COLLAPSED, true, group, count, () -> packs.forEach(pack -> {
				SwitchRow row = this.createSwitch(pack);
				this.switchRows.put(row, pack);
				list.addRow(row, SwitchRow.HEIGHT);
			}));
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
						Identifier location = Identifier.fromNamespaceAndPath(TweakSwitch.MOD_ID, "pack_icon/" + Integer.toHexString(id.hashCode()) + "_" + PACK_ICONS.size());
						Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(location::toString, NativeImage.read(stream)));
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
