package com.tweakswitch;

import com.mojang.datafixers.util.Pair;
import com.tweakswitch.compat.Compat;
import com.tweakswitch.mixin.CreateWorldScreenAccessor;
import com.tweakswitch.ui.Theme;
import com.tweakswitch.ui.ThemedButton;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import org.jspecify.annotations.Nullable;

/**
 * One visit to the data pack window for a world being created. The window has three tabs on two screens (Vanilla Tweaks
 * data packs and crafting tweaks on {@link DataPacksScreen}, Modrinth on {@link ModrinthDataPacksScreen}); this holds
 * what they share: the world, its data pack folder, and the files added so far.
 */
public final class WorldDataPacks {
	public enum Tab {
		DATA_PACKS("tweakswitch.dp.tab.dataPacks"),
		CRAFTING_TWEAKS("tweakswitch.dp.tab.craftingTweaks"),
		MODRINTH("tweakswitch.dp.tab.modrinth");

		final String key;

		Tab(final String key) {
			this.key = key;
		}
	}

	/** The tab that was showing last, for the session. */
	private static Tab shown = Tab.DATA_PACKS;

	final CreateWorldScreen world;
	final Path folder;
	/** Files downloaded since the window opened, to switch on when it closes. */
	private final Set<String> added = new LinkedHashSet<>();

	private WorldDataPacks(final CreateWorldScreen world, final Path folder) {
		this.world = world;
		this.folder = folder;
	}

	/** Opens the window over the world creation screen that's showing, on the tab used last. */
	public static void openForNewWorld() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(Compat.screen(minecraft) instanceof CreateWorldScreen world)) {
			return;
		}
		CreateWorldScreenAccessor access = (CreateWorldScreenAccessor) world;
		Pair<Path, PackRepository> settings = access.tweakswitch$dataPackSettings(access.tweakswitch$uiState().getSettings().dataConfiguration());
		if (settings != null) {
			WorldDataPacks session = new WorldDataPacks(world, settings.getFirst());
			Compat.setScreen(minecraft, session.screenFor(shown));
		}
	}

	Screen screenFor(final Tab tab) {
		return tab == Tab.MODRINTH ? new ModrinthDataPacksScreen(this) : new DataPacksScreen(this, tab == Tab.CRAFTING_TWEAKS ? VanillaTweaks.Kind.CRAFTING_TWEAKS : VanillaTweaks.Kind.DATA_PACKS);
	}

	void added(final String fileName) {
		this.added.add(fileName);
	}

	static void remember(final Tab tab) {
		shown = tab;
	}

	/**
	 * The tab buttons, the showing one highlighted.
	 *
	 * @param current  the tab the calling screen is showing
	 * @param count    how many packs a tab lists, or 0 if unknown, for labels like "Data Packs (57)"
	 * @param onSelect what the calling screen does when a tab is picked: switch in place, or return false to have the
	 *                 other screen opened
	 */
	List<AbstractWidget> tabs(final Theme theme, final java.util.function.Supplier<Tab> current, final Function<Tab, Integer> count, final Function<Tab, Boolean> onSelect) {
		List<AbstractWidget> tabs = new ArrayList<>();
		for (Tab tab : Tab.values()) {
			ThemedButton button = new ThemedButton(theme, ThemedButton.Kind.SECONDARY, Component.empty(), tab == Tab.MODRINTH ? 80 : 130, pressed -> {
				shown = tab;
				if (!onSelect.apply(tab)) {
					Compat.setScreen(Minecraft.getInstance(), this.screenFor(tab));
				}
			}) {
				@Override
				public Component getMessage() {
					int packs = count.apply(tab);
					return packs > 0 ? Component.translatable(tab.key).append(" (" + packs + ")") : Component.translatable(tab.key);
				}
			};
			tabs.add(button.highlightWhen(() -> current.get() == tab));
		}
		return tabs;
	}

	/** Back to world creation, switching on what was downloaded the way its own Data Packs screen does. */
	void finish(final Minecraft minecraft) {
		if (this.added.isEmpty()) {
			Compat.setScreen(minecraft, this.world);
			return;
		}
		CreateWorldScreenAccessor access = (CreateWorldScreenAccessor) this.world;
		Pair<Path, PackRepository> settings = access.tweakswitch$dataPackSettings(access.tweakswitch$uiState().getSettings().dataConfiguration());
		if (settings == null) {
			Compat.setScreen(minecraft, this.world);
			return;
		}
		PackRepository repository = settings.getSecond();
		// Picks up the new files; packs already switched on stay on
		repository.reload();
		List<String> ids = new ArrayList<>(repository.getSelectedIds());
		for (String file : this.added) {
			String id = "file/" + file;
			if (repository.getAvailableIds().contains(id) && !ids.contains(id)) {
				ids.add(id);
			}
		}
		repository.setSelected(ids);
		this.added.clear();
		access.tweakswitch$applyDataPacks(repository, true, configuration -> Compat.setScreen(minecraft, this.world));
	}

	static @Nullable Tab tabOf(final VanillaTweaks.Kind kind) {
		return kind == VanillaTweaks.Kind.CRAFTING_TWEAKS ? Tab.CRAFTING_TWEAKS : kind == VanillaTweaks.Kind.DATA_PACKS ? Tab.DATA_PACKS : null;
	}
}
