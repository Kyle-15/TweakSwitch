package com.tweakswitch;

import com.tweakswitch.compat.Compat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * After TweakSwitch downloads mods: which ones landed in the mods folder, links for any it couldn't find, and a reminder
 * that they only load after restarting Minecraft. Continue goes on to download the packs.
 */
public class ModsInstalledScreen extends PackListScreen {
	private final Modrinth.ModInstallResult result;
	private final Runnable onContinue;

	public ModsInstalledScreen(final Screen lastScreen, final Modrinth.ModInstallResult result, final Runnable onContinue) {
		super(lastScreen, Component.translatable(result.installed().isEmpty() ? "tweakswitch.mods.installed.none" : "tweakswitch.mods.installed.title"));
		this.result = result;
		this.onContinue = onContinue;
	}

	/** Green when mods were saved, amber when none could be. */
	@Override
	protected Component styledTitle() {
		int color = this.result.installed().isEmpty() ? 0xFFFFC857 : 0xFF5BE37D;
		return this.title.copy().withStyle(style -> style.withBold(true).withColor(color));
	}

	@Override
	protected boolean hasPreviews() {
		return false;
	}

	@Override
	protected boolean hasSearch() {
		return false;
	}

	@Override
	protected void fillList(final PackList list) {
		if (!this.result.installed().isEmpty()) {
			list.addText(Component.translatable("tweakswitch.mods.installed.list").withStyle(ChatFormatting.GRAY));
			for (String mod : this.result.installed()) {
				list.addText(Component.literal("✔ " + mod).withColor(this.theme().text()));
			}
			list.addText(Component.translatable("tweakswitch.mods.restart").withStyle(style -> style.withBold(true).withColor(0xFFFFC857)));
		}
		if (!this.result.failed().isEmpty()) {
			list.addText(Component.translatable("tweakswitch.mods.installed.failed").withStyle(ChatFormatting.GRAY));
			list.addGrid(ModsNeededScreen.linkButtons(this, this.result.failed()), 2);
		}
	}

	@Override
	protected void addFooter() {
		this.addFooterButtons(
			this.primaryButton(Component.translatable("tweakswitch.mods.continue"), 110, button -> {
				Compat.setScreen(this.minecraft, this.lastScreen);
				this.onContinue.run();
			}).withTooltip(Component.translatable("tweakswitch.mods.continue.tooltip")),
			this.button(Component.translatable("tweakswitch.mods.openFolder"), 100, button -> Compat.openFolder(Modrinth.modsDir()))
		);
	}

	/** Esc continues too, so the packs still download. */
	@Override
	public void onClose() {
		Compat.setScreen(this.minecraft, this.lastScreen);
		this.onContinue.run();
	}
}
