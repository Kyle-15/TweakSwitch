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

	/** Where each downloaded pack came from, read when the list is built. */
	private Map<String, PackIndex.Entry> index = Map.of();
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
		List<Pack> ours = this.repository.getAvailablePacks().stream()
			.filter(TweakSwitchPackSource::isOurs)
			.sorted(Comparator.comparing(pack -> pack.getTitle().getString().toLowerCase(Locale.ROOT)))
			.toList();
		List<Pack> shown = ours.stream().filter(pack -> this.matches(pack.getTitle().getString(), pack.getDescription().getString())).toList();

		if (shown.isEmpty()) {
			list.addText(Component.translatable(ours.isEmpty() ? "tweakswitch.empty" : "tweakswitch.noMatches").withStyle(ChatFormatting.GRAY));
			return;
		}

		// Vanilla Tweaks downloads remember their category; anything else goes under "Other Packs", listed last
		this.index = PackIndex.load();
		Map<String, PackIndex.Entry> index = this.index;
		String other = Component.translatable("tweakswitch.otherPacks").getString();
		Map<String, List<Pack>> byCategory = new TreeMap<>(Comparator.comparing((String name) -> name.equals(other)).thenComparing(Comparator.naturalOrder()));
		for (Pack pack : shown) {
			PackIndex.Entry entry = index.get(TweakSwitchPackSource.fileName(pack));
			String category = entry != null ? entry.category() : other;
			byCategory.computeIfAbsent(category, name -> new ArrayList<>()).add(pack);
		}

		Collection<String> selected = this.repository.getSelectedIds();
		byCategory.forEach((category, packs) -> {
			long on = packs.stream().filter(pack -> this.pending.getOrDefault(pack.getId(), selected.contains(pack.getId()))).count();
			this.addCategory(list, COLLAPSED, true, category, on + "/" + packs.size() + " on", () -> packs.forEach(pack -> list.addRow(this.createSwitch(pack), SwitchRow.HEIGHT)));
		});
	}

	private SwitchRow createSwitch(final Pack pack) {
		String id = pack.getId();
		boolean compatible = pack.getCompatibility().isCompatible();
		Component description = compatible
			? pack.getDescription()
			: Component.empty().append(pack.getDescription()).append("\n").append(Component.translatable("tweakswitch.incompatible").withStyle(ChatFormatting.RED));
		return new SwitchRow(this.theme(), this.iconFor(pack), pack.getTitle(), description, !compatible,
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
