package com.tweakswitch;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class TweakSwitch implements ClientModInitializer {
	public static final String MOD_ID = "tweakswitch";

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
	private static KeyMapping openKey;

	/** Packs dropped here get a switch in the TweakSwitch screen. */
	public static Path packsDir() {
		return FabricLoader.getInstance().getGameDir().resolve(MOD_ID).resolve("packs");
	}

	@Override
	public void onInitializeClient() {
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tweakswitch.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (openKey.consumeClick()) {
				if (minecraft.gui.screen() == null) {
					minecraft.gui.setScreen(new TweakSwitchScreen(null));
				}
			}
		});

		// Shortcut from the vanilla Resource Packs screen
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof PackSelectionScreen) {
				Screens.getWidgets(screen).add(Button.builder(Component.translatable("tweakswitch.title"), button -> {
					screen.onClose();
					minecraft.gui.setScreen(new TweakSwitchScreen(minecraft.gui.screen()));
				}).bounds(6, 6, 80, 20).build());
			}
		});
	}
}
