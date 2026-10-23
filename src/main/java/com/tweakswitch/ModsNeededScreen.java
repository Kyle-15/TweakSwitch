package com.tweakswitch;

import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Shown before downloading packs that need mods you don't have: a link to each mod, then download anyway or go back. */
public class ModsNeededScreen extends PackListScreen {
	private final Map<String, List<Modrinth.LinkedProject>> missing;
	private final Runnable onDownload;

	/** @param missing pack name → the mods it needs that aren't installed */
	public ModsNeededScreen(final Screen lastScreen, final Map<String, List<Modrinth.LinkedProject>> missing, final Runnable onDownload) {
		super(lastScreen, Component.translatable("tweakswitch.mods.title").withStyle(ChatFormatting.GOLD));
		this.missing = missing;
		this.onDownload = onDownload;
	}

	@Override
	protected boolean hasSearch() {
		return false;
	}

	@Override
	protected void fillList(final PackList list) {
		list.addText(Component.translatable("tweakswitch.mods.explain").withStyle(ChatFormatting.GRAY));
		this.missing.forEach((pack, mods) -> {
			list.addText(Component.translatable("tweakswitch.mods.needs", pack));
			List<AbstractWidget> links = mods.stream()
				.<AbstractWidget>map(mod -> Button.builder(Component.literal(mod.title() + " ↗").withStyle(ChatFormatting.AQUA), button ->
						ConfirmLinkScreen.confirmLinkNow(this, mod.downloadPage(), true))
					.tooltip(Tooltip.create(Component.literal(mod.downloadPage().toString())))
					.build())
				.toList();
			list.addGrid(links, 2);
		});
	}

	@Override
	protected void addFooter() {
		this.addFooterButtons(
			Button.builder(Component.translatable("tweakswitch.mods.downloadAnyway"), button -> {
				this.minecraft.gui.setScreen(this.lastScreen);
				this.onDownload.run();
			}).width(150).build(),
			Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).width(100).build()
		);
	}
}
