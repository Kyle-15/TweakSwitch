package com.tweakswitch;

import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
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
		super(lastScreen, Component.translatable("tweakswitch.mods.title"));
		this.missing = missing;
		this.onDownload = onDownload;
	}

	/** The title in amber, as a warning. */
	@Override
	protected Component styledTitle() {
		return this.title.copy().withStyle(style -> style.withBold(true).withColor(0xFFFFC857));
	}

	@Override
	protected boolean hasSearch() {
		return false;
	}

	@Override
	protected void fillList(final PackList list) {
		list.addText(Component.translatable("tweakswitch.mods.explain").withStyle(ChatFormatting.GRAY));
		this.missing.forEach((pack, mods) -> {
			list.addText(Component.translatable("tweakswitch.mods.needs", pack).withColor(this.theme().text()));
			List<AbstractWidget> links = mods.stream()
				.<AbstractWidget>map(mod -> this.button(Component.literal(mod.title() + " ↗"), 150, button ->
						ConfirmLinkScreen.confirmLinkNow(this, mod.downloadPage(), true))
					.withTooltip(Component.literal(mod.downloadPage().toString())))
				.toList();
			list.addGrid(links, 2);
		});
	}

	@Override
	protected void addFooter() {
		this.addFooterButtons(
			this.primaryButton(Component.translatable("tweakswitch.mods.downloadAnyway"), 150, button -> {
				this.minecraft.gui.setScreen(this.lastScreen);
				this.onDownload.run();
			}),
			this.button(CommonComponents.GUI_BACK, 100, button -> this.onClose())
		);
	}
}
