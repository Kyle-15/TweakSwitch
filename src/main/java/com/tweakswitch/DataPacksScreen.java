package com.tweakswitch;

import com.mojang.datafixers.util.Pair;
import com.tweakswitch.compat.Compat;
import com.tweakswitch.mixin.CreateWorldScreenAccessor;
import com.tweakswitch.ui.ThemedButton;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Vanilla Tweaks data packs and crafting tweaks for a world being created, opened from the More tab. Kept apart from the
 * resource pack screens: these change gameplay in one world, not how the game looks. Picked packs download into the new
 * world's data pack folder and are switched on when you go back.
 */
public class DataPacksScreen extends VanillaTweaksScreen {
	private static final List<VanillaTweaks.Kind> TABS = List.of(VanillaTweaks.Kind.DATA_PACKS, VanillaTweaks.Kind.CRAFTING_TWEAKS);
	/** The tab that was showing last, for the session. */
	private static VanillaTweaks.Kind shownKind = VanillaTweaks.Kind.DATA_PACKS;

	private final CreateWorldScreen world;
	private final Path folder;
	/** Files downloaded since this screen opened, to switch on when it closes. */
	private final Set<String> added = new LinkedHashSet<>();

	private DataPacksScreen(final CreateWorldScreen world, final Path folder) {
		super(world, shownKind, Component.translatable("tweakswitch.dp.title"));
		this.world = world;
		this.folder = folder;
	}

	/** Opens the screen over the world creation screen that's showing. */
	public static void openForNewWorld() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(Compat.screen(minecraft) instanceof CreateWorldScreen world)) {
			return;
		}
		CreateWorldScreenAccessor access = (CreateWorldScreenAccessor) world;
		Pair<Path, PackRepository> settings = access.tweakswitch$dataPackSettings(access.tweakswitch$uiState().getSettings().dataConfiguration());
		if (settings != null) {
			Compat.setScreen(minecraft, new DataPacksScreen(world, settings.getFirst()));
		}
	}

	/** "Data Packs (57)" and "Crafting Tweaks (27)", the showing one highlighted. */
	@Override
	protected List<AbstractWidget> createTabs() {
		List<AbstractWidget> tabs = new ArrayList<>();
		for (VanillaTweaks.Kind tab : TABS) {
			String key = tab == VanillaTweaks.Kind.DATA_PACKS ? "tweakswitch.dp.tab.dataPacks" : "tweakswitch.dp.tab.craftingTweaks";
			ThemedButton button = new ThemedButton(this.theme(), ThemedButton.Kind.SECONDARY, Component.empty(), 130, b -> {
				shownKind = tab;
				this.kind = tab;
				this.loadIfNeeded();
				this.refreshList();
				if (this.list != null) {
					this.list.setScrollAmount(0.0);
				}
			}) {
				@Override
				public Component getMessage() {
					int count = packCount(tab);
					return count > 0 ? Component.translatable(key).append(" (" + count + ")") : Component.translatable(key);
				}
			};
			tabs.add(button.highlightWhen(() -> this.kind == tab));
		}
		return tabs;
	}

	/** Lists load in the background; the other tab's count shows as soon as it's known. */
	@Override
	protected void init() {
		super.init();
		VanillaTweaks.Kind showing = this.kind;
		for (VanillaTweaks.Kind tab : TABS) {
			this.kind = tab;
			this.loadIfNeeded();
		}
		this.kind = showing;
	}

	@Override
	protected Path targetFolder() {
		return this.folder;
	}

	@Override
	protected boolean indexed() {
		return false;
	}

	@Override
	protected String downloadLabelKey() {
		return "tweakswitch.dp.download";
	}

	@Override
	protected Component doneHint() {
		return Component.translatable("tweakswitch.dp.doneHint");
	}

	@Override
	protected void afterDownload(final List<VanillaTweaks.VtPack> succeeded) {
		succeeded.forEach(pack -> this.added.add(pack.fileName()));
	}

	/** Back to world creation, switching on what was downloaded the way its own Data Packs screen does. */
	@Override
	public void onClose() {
		if (this.added.isEmpty()) {
			Compat.setScreen(this.minecraft, this.world);
			return;
		}
		CreateWorldScreenAccessor access = (CreateWorldScreenAccessor) this.world;
		Pair<Path, PackRepository> settings = access.tweakswitch$dataPackSettings(access.tweakswitch$uiState().getSettings().dataConfiguration());
		if (settings == null) {
			Compat.setScreen(this.minecraft, this.world);
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
		access.tweakswitch$applyDataPacks(repository, true, configuration -> Compat.setScreen(this.minecraft, this.world));
	}
}
