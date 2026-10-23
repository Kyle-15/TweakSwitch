package com.tweakswitch;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** One on/off switch per pack in the TweakSwitch folder. Changes apply, with one resource reload, when the screen closes. */
public class TweakSwitchScreen extends OptionsSubScreen {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final URI VANILLA_TWEAKS_URI = URI.create("https://vanillatweaks.net/picker/resource-packs/");
	private static final int HEADER_HEIGHT = 4 + 9 + 6 + 15 + 6;

	private final PackRepository repository;
	/** Switch state the player has chosen but not yet applied, by pack id. */
	private final Map<String, Boolean> pending = new HashMap<>();
	private @Nullable EditBox search;

	public TweakSwitchScreen(final @Nullable Screen lastScreen) {
		super(lastScreen, Minecraft.getInstance().options, Component.translatable("tweakswitch.title"));
		this.repository = Minecraft.getInstance().getResourcePackRepository();
		// Pick up packs added to the folder since the last reload
		this.repository.reload();
	}

	@Override
	protected void addTitle() {
		this.layout.setHeaderHeight(HEADER_HEIGHT);
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(6));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font));
		String previousSearch = this.search != null ? this.search.getValue() : "";
		this.search = header.addChild(new EditBox(this.font, 0, 0, 200, 15, Component.translatable("tweakswitch.search")));
		this.search.setHint(Component.translatable("tweakswitch.search").withStyle(EditBox.SEARCH_HINT_STYLE));
		this.search.setValue(previousSearch);
		this.search.setResponder(value -> this.fillList());
	}

	@Override
	protected void addContents() {
		this.list = this.layout.addToContents(new PackList(this.minecraft, this.width, this));
		this.addOptions();
	}

	@Override
	protected void addOptions() {
		this.fillList();
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(
			Button.builder(Component.translatable("tweakswitch.getPacks"), ConfirmLinkScreen.confirmLink(this, VANILLA_TWEAKS_URI))
				.tooltip(Tooltip.create(Component.translatable("tweakswitch.getPacks.tooltip")))
				.width(100)
				.build()
		);
		footer.addChild(Button.builder(Component.translatable("tweakswitch.openFolder"), button -> Blaze3D.openPath(this.packsDirCreated())).width(100).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(100).build());
	}

	@Override
	protected void setInitialFocus() {
		if (this.search != null) {
			this.setInitialFocus(this.search);
		} else {
			super.setInitialFocus();
		}
	}

	private void fillList() {
		if (!(this.list instanceof PackList packList)) {
			return;
		}

		packList.clear();
		String query = this.search != null ? this.search.getValue().toLowerCase(Locale.ROOT) : "";
		List<Pack> ours = this.repository.getAvailablePacks().stream()
			.filter(TweakSwitchPackSource::isOurs)
			.sorted(Comparator.comparing(pack -> pack.getTitle().getString().toLowerCase(Locale.ROOT)))
			.toList();
		List<Pack> shown = ours.stream()
			.filter(pack -> query.isBlank()
				|| pack.getTitle().getString().toLowerCase(Locale.ROOT).contains(query)
				|| pack.getDescription().getString().toLowerCase(Locale.ROOT).contains(query))
			.toList();

		if (shown.isEmpty()) {
			packList.addHeader(Component.translatable(ours.isEmpty() ? "tweakswitch.empty" : "tweakswitch.noMatches").withStyle(ChatFormatting.GRAY));
		}

		for (Pack pack : shown) {
			packList.addBig(this.createSwitch(pack));
		}
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
			.create(name, (button, value) -> this.pending.put(id, value));
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

	private static class PackList extends OptionsList {
		PackList(final Minecraft minecraft, final int width, final OptionsSubScreen screen) {
			super(minecraft, width, screen);
		}

		void clear() {
			this.clearEntries();
			this.setScrollAmount(0.0);
		}
	}
}
