package com.tweakswitch;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** Downloads preview images on demand and keeps them as textures for the rest of the session. */
public final class RemoteImages {
	private static final Logger LOGGER = LogUtils.getLogger();
	/** Bigger images are shrunk; the hover panel is only ~200 GUI pixels wide. */
	private static final int MAX_WIDTH = 480;
	private static final Map<String, Image> IMAGES = new ConcurrentHashMap<>();
	private static final AtomicInteger NEXT_ID = new AtomicInteger();
	private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "TweakSwitch images");
		thread.setDaemon(true);
		return thread;
	});
	/** Only the most recently hovered image is worth loading; older queued requests are skipped. */
	private static volatile @Nullable String wanted;

	public record Image(@Nullable Identifier texture, int width, int height) {
		static final Image LOADING = new Image(null, 0, 0);
		static final Image FAILED = new Image(null, -1, -1);

		public boolean failed() {
			return this == FAILED;
		}
	}

	private RemoteImages() {
	}

	/** Returns the image if it's ready, or starts loading it and returns {@link Image#LOADING}. */
	public static Image get(final String url) {
		Image image = IMAGES.get(url);
		if (image != null) {
			return image;
		}
		wanted = url;
		IMAGES.put(url, Image.LOADING);
		LOADER.execute(() -> {
			if (!url.equals(wanted)) {
				// Hovered something else before we got to it; try again next time it's hovered
				IMAGES.remove(url, Image.LOADING);
				return;
			}
			try {
				NativeImage decoded = toNativeImage(decode(WebClient.getBytes(URI.create(url))));
				Minecraft.getInstance().execute(() -> {
					Identifier id = Identifier.fromNamespaceAndPath(TweakSwitch.MOD_ID, "remote_image/" + NEXT_ID.incrementAndGet());
					Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, decoded));
					IMAGES.put(url, new Image(id, decoded.getWidth(), decoded.getHeight()));
				});
			} catch (Throwable e) {
				LOGGER.debug("Couldn't load image {}", url, e);
				IMAGES.put(url, Image.FAILED);
			}
		});
		return Image.LOADING;
	}

	/** PNG, JPEG and GIF (first frame) through ImageIO; WebP through the bundled TwelveMonkeys reader. */
	private static BufferedImage decode(final byte[] bytes) throws IOException {
		boolean webp = bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
		BufferedImage image;
		if (webp) {
			// Created directly: ImageIO's plugin lookup can't see libraries bundled inside a mod
			ImageReader reader = new WebPImageReaderSpi().createReaderInstance();
			try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
				reader.setInput(input);
				image = reader.read(0);
			} finally {
				reader.dispose();
			}
		} else {
			image = ImageIO.read(new ByteArrayInputStream(bytes));
		}
		if (image == null) {
			throw new IOException("Unreadable image");
		}
		return image;
	}

	/** Box-filter downscale straight into a texture image, done by hand to stay clear of AWT graphics on macOS. */
	private static NativeImage toNativeImage(final BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		int[] source = image.getRGB(0, 0, width, height, null, 0, width);
		int factor = Math.max(1, (width + MAX_WIDTH - 1) / MAX_WIDTH);
		int outWidth = Math.max(1, width / factor);
		int outHeight = Math.max(1, height / factor);
		NativeImage out = new NativeImage(outWidth, outHeight, false);
		int samples = factor * factor;
		for (int y = 0; y < outHeight; y++) {
			for (int x = 0; x < outWidth; x++) {
				long a = 0, r = 0, g = 0, b = 0;
				for (int dy = 0; dy < factor; dy++) {
					for (int dx = 0; dx < factor; dx++) {
						int argb = source[(y * factor + dy) * width + x * factor + dx];
						a += argb >>> 24;
						r += argb >> 16 & 0xFF;
						g += argb >> 8 & 0xFF;
						b += argb & 0xFF;
					}
				}
				out.setPixel(x, y, (int) (a / samples) << 24 | (int) (r / samples) << 16 | (int) (g / samples) << 8 | (int) (b / samples));
			}
		}
		return out;
	}
}
