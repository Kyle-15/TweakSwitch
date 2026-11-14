package com.tweakswitch;

import com.tweakswitch.compat.Compat;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

public class TweakSwitch implements ClientModInitializer {
	public static final String MOD_ID = "tweakswitch";

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
	private static KeyMapping openKey;
	private static KeyMapping nextPresetKey;
	private static int ticks;

	/** Packs dropped here get a switch in the TweakSwitch screen. */
	public static Path packsDir() {
		return FabricLoader.getInstance().getGameDir().resolve(MOD_ID).resolve("packs");
	}

	private static boolean isDataPackScreen(final Screen screen) {
		return screen.getTitle().getContents() instanceof TranslatableContents title && title.getKey().equals("dataPack.title");
	}

	@Override
	public void onInitializeClient() {
		openKey = Compat.registerKey(new KeyMapping("key.tweakswitch.open", Compat.keyboard(), InputConstants.KEY_K, CATEGORY));

		// No key by default: set one under Controls to step through your presets while playing
		nextPresetKey = Compat.registerKey(new KeyMapping("key.tweakswitch.nextPreset", Compat.keyboard(), InputConstants.UNKNOWN.getValue(), CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (openKey.consumeClick()) {
				if (Compat.screen(minecraft) == null) {
					Compat.setScreen(minecraft, new TweakSwitchScreen(null));
				}
			}
			while (nextPresetKey.consumeClick()) {
				if (Compat.screen(minecraft) == null && !Compat.loading(minecraft)) {
					String preset = Presets.applyNext(minecraft);
					SystemToast.addOrUpdate(Compat.toasts(minecraft), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
						preset != null ? Component.translatable("tweakswitch.presets.switched", preset) : Component.translatable("tweakswitch.presets.none"),
						preset != null ? null : Component.translatable("tweakswitch.presets.none.hint"));
				}
			}
			// Pack files that were locked when deleted (Windows) go once they're no longer in use
			if (++ticks % 40 == 0) {
				TweakSwitchScreen.deleteLeftovers(minecraft);
			}
		});

		// Shortcut from the vanilla Resource Packs screen (not the Data Packs one, which uses the same screen class)
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof TitleScreen) {
				StartupCheck.run(minecraft);
			}
			if (screen instanceof PackSelectionScreen && !isDataPackScreen(screen)) {
				Compat.screenWidgets(screen).add(Button.builder(Component.translatable("tweakswitch.title"), button -> {
					screen.onClose();
					Compat.setScreen(minecraft, new TweakSwitchScreen(Compat.screen(minecraft)));
				}).bounds(6, 6, 80, 20).build());
			}
		});
	}
}
