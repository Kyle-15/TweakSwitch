package com.tweakswitch.mixin;

import com.tweakswitch.DataPacksScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a Vanilla Tweaks Data Packs button under Game Rules, Experiments and Data Packs on world creation's More tab. */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$MoreTab")
public abstract class CreateWorldMoreTabMixin extends GridLayoutTab {
	private CreateWorldMoreTabMixin(final Component title) {
		super(title);
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void tweakswitch$addDataPacksButton(final CallbackInfo info) {
		// The tab's three buttons fill rows 0 to 2 of its one-column grid
		this.layout.addChild(
			Button.builder(Component.translatable("tweakswitch.dp.button"), button -> DataPacksScreen.openForNewWorld())
				.tooltip(Tooltip.create(Component.translatable("tweakswitch.dp.button.tooltip")))
				.width(210)
				.build(),
			3, 0
		);
	}
}
