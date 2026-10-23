package com.tweakswitch;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** Downloads preview images from Vanilla Tweaks on demand and keeps them as textures for the rest of the session. */
public final class VanillaTweaksPreviews {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Map<String, Preview> PREVIEWS = new ConcurrentHashMap<>();
	private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "TweakSwitch previews");
		thread.setDaemon(true);
		return thread;
	});
	/** Only the most recently hovered pack is worth loading; older queued requests are skipped. */
	private static volatile @Nullable String wanted;

	public record Preview(@Nullable Identifier texture, int width, int height) {
		static final Preview LOADING = new Preview(null, 0, 0);
		static final Preview FAILED = new Preview(null, -1, -1);

		public boolean failed() {
			return this == FAILED;
		}
	}

	private VanillaTweaksPreviews() {
	}

	/** Returns the preview if it's ready, or starts loading it and returns {@link Preview#LOADING}. */
	public static Preview get(final VanillaTweaks.VtPack pack) {
		Preview preview = PREVIEWS.get(pack.id());
		if (preview != null) {
			return preview;
		}
		wanted = pack.id();
		PREVIEWS.put(pack.id(), Preview.LOADING);
		LOADER.execute(() -> {
			if (!pack.id().equals(wanted)) {
				// Hovered something else before we got to it; try again next time it's hovered
				PREVIEWS.remove(pack.id(), Preview.LOADING);
				return;
			}
			try {
				NativeImage image = decode(VanillaTweaks.fetchPreview(pack));
				Minecraft.getInstance().execute(() -> {
					Identifier id = Identifier.fromNamespaceAndPath(TweakSwitch.MOD_ID, "vt_preview/" + pack.id().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
					Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
					PREVIEWS.put(pack.id(), new Preview(id, image.getWidth(), image.getHeight()));
				});
			} catch (Throwable e) {
				LOGGER.debug("No preview for Vanilla Tweaks pack {}", pack.id(), e);
				PREVIEWS.put(pack.id(), Preview.FAILED);
			}
		});
		return Preview.LOADING;
	}

	/** PNGs load directly; GIFs (animated previews) are shown as their first frame. */
	private static NativeImage decode(final byte[] bytes) throws IOException {
		boolean png = bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
		if (png) {
			return NativeImage.read(bytes);
		}
		BufferedImage frame = ImageIO.read(new ByteArrayInputStream(bytes));
		if (frame == null) {
			throw new IOException("Unreadable preview image");
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(frame, "png", out);
		return NativeImage.read(out.toByteArray());
	}
}
