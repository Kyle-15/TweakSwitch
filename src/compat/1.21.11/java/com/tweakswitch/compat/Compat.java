package com.tweakswitch.compat;

import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.util.Util;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import java.util.List;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The few game calls whose names differ between Minecraft versions, for Minecraft 1.21.11. The rest of TweakSwitch calls these, and
 * the build picks this file or another version's (see build.gradle and versions/).
 */
public final class Compat {
	private Compat() {
	}

	public static void setScreen(final Minecraft minecraft, final @Nullable Screen screen) {
		minecraft.setScreen(screen);
	}

	public static @Nullable Screen screen(final Minecraft minecraft) {
		return minecraft.screen;
	}

	public static ToastManager toasts(final Minecraft minecraft) {
		return minecraft.getToastManager();
	}

	/** Whether a loading overlay (like the resource reload screen) is up. */
	public static boolean loading(final Minecraft minecraft) {
		return minecraft.getOverlay() != null;
	}

	/** Opens a folder in the system file manager. */
	public static void openFolder(final Path folder) {
		Util.getPlatform().openPath(folder);
	}

	/** The input type keybinds use for keyboard keys. */
	public static InputConstants.Type keyboard() {
		return InputConstants.Type.KEYSYM;
	}

	/** A texture that scales smoothly when drawn bigger or smaller, for photo-like previews. */
	public static DynamicTexture smoothTexture(final Supplier<String> label, final NativeImage image) {
		return new DynamicTexture(label, image) {
			{
				this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			}
		};
	}

	/** A file at the root of a pack (like pack.png), or null if it has none. */
	public static byte @Nullable [] readPackRoot(final Pack pack, final String file) throws Exception {
		try (var resources = pack.open()) {
			IoSupplier<InputStream> resource = resources.getRootResource(file);
			if (resource == null) {
				return null;
			}
			try (InputStream stream = resource.get()) {
				return stream.readAllBytes();
			}
		}
	}

	/** Registers a keybind with Fabric API so it shows in Controls. */
	public static KeyMapping registerKey(final KeyMapping mapping) {
		return KeyBindingHelper.registerKeyBinding(mapping);
	}

	/** A screen's widget list, to add a button to a vanilla screen after it opens. */
	public static List<AbstractWidget> screenWidgets(final Screen screen) {
		return Screens.getButtons(screen);
	}

	/** Re-lays out the screen after the GUI scale or window size changes. */
	public static void resizeGui(final Minecraft minecraft) {
		minecraft.resizeDisplay();
	}
}
