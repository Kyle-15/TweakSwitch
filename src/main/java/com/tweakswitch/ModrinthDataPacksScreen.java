package com.tweakswitch;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * Modrinth's data packs for a world being created: the third tab of the data pack window. Search, sort, filters and
 * previews work as on the resource pack screen, but picked packs go into the new world's data pack folder and are
 * switched on for that world. A resource pack one of them needs is saved with your switches instead.
 */
public class ModrinthDataPacksScreen extends ModrinthScreen {
	private final WorldDataPacks session;

	ModrinthDataPacksScreen(final WorldDataPacks session) {
		super(session.world, Modrinth.Type.DATA_PACKS, Component.translatable("tweakswitch.dp.title"));
		this.session = session;
	}

	@Override
	protected List<AbstractWidget> createTabs() {
		return this.session.tabs(this.theme(), () -> WorldDataPacks.Tab.MODRINTH,
			tab -> tab == WorldDataPacks.Tab.MODRINTH ? 0 : VanillaTweaksScreen.packCount(tab == WorldDataPacks.Tab.CRAFTING_TWEAKS ? VanillaTweaks.Kind.CRAFTING_TWEAKS : VanillaTweaks.Kind.DATA_PACKS),
			tab -> tab == WorldDataPacks.Tab.MODRINTH);
	}

	@Override
	protected Path targetFolder() {
		return this.session.folder;
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
	protected void afterDownload(final List<Modrinth.Project> succeeded) {
		// Resource packs that came along as requirements are switches, not part of the world
		succeeded.stream().filter(project -> !this.resourcePackDependencies.contains(project.id())).forEach(project -> this.session.added(project.fileName()));
	}

	@Override
	public void onClose() {
		this.session.finish(this.minecraft);
	}
}
