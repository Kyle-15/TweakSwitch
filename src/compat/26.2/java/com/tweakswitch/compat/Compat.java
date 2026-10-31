package com.tweakswitch.compat;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.util.Util;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The few game calls whose names differ between Minecraft versions, for Minecraft 26.2. The rest of TweakSwitch calls these, and
 * the build picks this file or another version's (see build.gradle and versions/).
 */
public final class Compat {
	private Compat() {
	}

	public static void setScreen(final Minecraft minecraft, final @Nullable Screen screen) {
		minecraft.gui.setScreen(screen);
	}

	public static @Nullable Screen screen(final Minecraft minecraft) {
		return minecraft.gui.screen();
	}

	public static ToastManager toasts(final Minecraft minecraft) {
		return minecraft.gui.toastManager();
	}

	/** Whether a loading overlay (like the resource reload screen) is up. */
	public static boolean loading(final Minecraft minecraft) {
		return minecraft.gui.overlay() != null;
	}

	/** Opens a folder in the system file manager. */
	public static void openFolder(final Path folder) {
		Util.getPlatform().openPath(folder);
	}

	/** The input type keybinds use for keyboard keys. */
	public static InputConstants.Type keyboard() {
		return InputConstants.Type.KEYSYM;
	}

	/** A texture sampler that scales smoothly, for photo-like previews. */
	public static GpuSampler smoothSampler() {
		return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
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
}
