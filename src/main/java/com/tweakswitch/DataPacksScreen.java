package com.tweakswitch;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * Vanilla Tweaks data packs and crafting tweaks for a world being created, opened from the More tab. Kept apart from the
 * resource pack screens: these change gameplay in one world, not how the game looks. Picked packs download into the new
 * world's data pack folder and are switched on when you go back. The Modrinth tab opens {@link ModrinthDataPacksScreen}.
 */
public class DataPacksScreen extends VanillaTweaksScreen {
	private static final List<VanillaTweaks.Kind> KINDS = List.of(VanillaTweaks.Kind.DATA_PACKS, VanillaTweaks.Kind.CRAFTING_TWEAKS);

	private final WorldDataPacks session;

	DataPacksScreen(final WorldDataPacks session, final VanillaTweaks.Kind kind) {
		super(session.world, kind, Component.translatable("tweakswitch.dp.title"));
		this.session = session;
	}

	/** Opens the window over the world creation screen that's showing. */
	public static void openForNewWorld() {
		WorldDataPacks.openForNewWorld();
	}

	/** "Data Packs (57)", "Crafting Tweaks (27)" and "Modrinth", the showing one highlighted. */
	@Override
	protected List<AbstractWidget> createTabs() {
		return this.session.tabs(this.theme(), () -> WorldDataPacks.tabOf(this.kind),
			tab -> tab == WorldDataPacks.Tab.MODRINTH ? 0 : packCount(tab == WorldDataPacks.Tab.CRAFTING_TWEAKS ? VanillaTweaks.Kind.CRAFTING_TWEAKS : VanillaTweaks.Kind.DATA_PACKS),
			tab -> {
				if (tab == WorldDataPacks.Tab.MODRINTH) {
					return false;
				}
				// The two Vanilla Tweaks tabs switch in place
				this.kind = tab == WorldDataPacks.Tab.CRAFTING_TWEAKS ? VanillaTweaks.Kind.CRAFTING_TWEAKS : VanillaTweaks.Kind.DATA_PACKS;
				this.loadIfNeeded();
				this.refreshList();
				if (this.list != null) {
					this.list.setScrollAmount(0.0);
				}
				return true;
			});
	}

	/** Lists load in the background; the other tab's count shows as soon as it's known. */
	@Override
	protected void init() {
		super.init();
		VanillaTweaks.Kind showing = this.kind;
		for (VanillaTweaks.Kind tab : KINDS) {
			this.kind = tab;
			this.loadIfNeeded();
		}
		this.kind = showing;
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
	protected void afterDownload(final List<VanillaTweaks.VtPack> succeeded) {
		succeeded.forEach(pack -> this.session.added(pack.fileName()));
	}

	@Override
	public void onClose() {
		this.session.finish(this.minecraft);
	}
}
