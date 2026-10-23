package com.tweakswitch;

import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Shown before downloading packs that need mods you don't have: a link to each mod, then download anyway or go back. */
public class ModsNeededScreen extends OptionsSubScreen {
	private final Map<String, List<Modrinth.LinkedProject>> missing;
	private final Runnable onDownload;

	/** @param missing pack name → the mods it needs that aren't installed */
	public ModsNeededScreen(final Screen lastScreen, final Map<String, List<Modrinth.LinkedProject>> missing, final Runnable onDownload) {
		super(lastScreen, Minecraft.getInstance().options, Component.translatable("tweakswitch.mods.title").withStyle(ChatFormatting.GOLD));
		this.missing = missing;
		this.onDownload = onDownload;
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}
		// Headers are one line each, so wrap the explanation by hand
		for (var line : this.font.getSplitter().splitLines(Component.translatable("tweakswitch.mods.explain"), 300, Style.EMPTY)) {
			this.list.addHeader(Component.literal(line.getString()).withStyle(ChatFormatting.GRAY));
		}
		this.missing.forEach((pack, mods) -> {
			this.list.addHeader(Component.translatable("tweakswitch.mods.needs", pack));
			this.list.addSmall(mods.stream()
				.<net.minecraft.client.gui.components.AbstractWidget>map(mod -> Button.builder(Component.literal(mod.title() + " ↗").withStyle(ChatFormatting.AQUA), button ->
					ConfirmLinkScreen.confirmLinkNow(this, mod.downloadPage(), true))
					.tooltip(Tooltip.create(Component.literal(mod.downloadPage().toString())))
					.build())
				.toList());
		});
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("tweakswitch.mods.downloadAnyway"), button -> {
			this.minecraft.gui.setScreen(this.lastScreen);
			this.onDownload.run();
		}).width(150).build());
		footer.addChild(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).width(100).build());
	}
}
