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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.LinearLayout;
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
	/** Categories the player collapsed; kept for the session. */
	private static final Set<String> COLLAPSED = new HashSet<>();

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
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(6));
		footer.addChild(
			Button.builder(Component.translatable("tweakswitch.getVanillaTweaks"), button -> this.minecraft.gui.setScreen(new VanillaTweaksScreen(this)))
				.tooltip(Tooltip.create(Component.translatable("tweakswitch.getVanillaTweaks.tooltip")))
				.width(90)
				.build()
		);
		footer.addChild(
			Button.builder(Component.translatable("tweakswitch.getModrinth"), button -> this.minecraft.gui.setScreen(new ModrinthScreen(this)))
				.tooltip(Tooltip.create(Component.translatable("tweakswitch.getModrinth.tooltip")))
				.width(70)
				.build()
		);
		footer.addChild(Button.builder(Component.translatable("tweakswitch.getUpdates"), button -> this.minecraft.gui.setScreen(new UpdatesScreen(this)))
			.tooltip(Tooltip.create(Component.translatable("tweakswitch.getUpdates.tooltip")))
			.width(70)
			.build());
		footer.addChild(Button.builder(Component.translatable("tweakswitch.openFolder"), button -> Blaze3D.openPath(this.packsDirCreated())).width(80).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(60).build());
	}

	@Override
	protected void fillList(final OptionsList list) {
		List<Pack> ours = this.repository.getAvailablePacks().stream()
			.filter(TweakSwitchPackSource::isOurs)
			.sorted(Comparator.comparing(pack -> pack.getTitle().getString().toLowerCase(Locale.ROOT)))
			.toList();
		List<Pack> shown = ours.stream().filter(pack -> this.matches(pack.getTitle().getString(), pack.getDescription().getString())).toList();

		if (shown.isEmpty()) {
			list.addHeader(Component.translatable(ours.isEmpty() ? "tweakswitch.empty" : "tweakswitch.noMatches").withStyle(ChatFormatting.GRAY));
			return;
		}

		// Vanilla Tweaks downloads remember their category; anything else goes under "Other Packs", listed last
		Map<String, PackIndex.Entry> index = PackIndex.load();
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
			this.addCategory(list, COLLAPSED, true, category, on + "/" + packs.size() + " on", () -> packs.forEach(pack -> list.addBig(this.createSwitch(pack))));
		});
	}

	private CycleButton<Boolean> createSwitch(final Pack pack) {
		String id = pack.getId();
		boolean on = this.pending.getOrDefault(id, this.repository.getSelectedIds().contains(id));
		Component description = pack.getCompatibility().isCompatible()
			? pack.getDescription()
			: Component.empty().append(pack.getDescription()).append("\n").append(Component.translatable("tweakswitch.incompatible").withStyle(ChatFormatting.RED));
		Component name = pack.getCompatibility().isCompatible() ? pack.getTitle() : pack.getTitle().copy().withStyle(ChatFormatting.RED);
		return CycleButton.onOffBuilder(on)
			.withTooltip(value -> Tooltip.create(description))
			.create(name, (button, value) -> {
				this.pending.put(id, value);
				// Updates the "x/y on" counts
				this.queueRefresh();
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
			this.options.updateResourcePacks(this.repository);
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
