package com.tweakswitch;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.w3c.dom.Node;

/**
 * Downloads preview images on demand and keeps them as textures. Animated GIFs keep every frame so previews play like they
 * do on the websites. The least recently shown images are dropped once too many frames are held.
 */
public final class RemoteImages {
	private static final Logger LOGGER = LogUtils.getLogger();
	/** Bigger images are shrunk; the hover panel is only ~150 GUI pixels wide. */
	private static final int MAX_WIDTH = 320;
	private static final int MAX_ANIMATED_WIDTH = 240;
	private static final int MAX_FRAMES = 150;
	/** Frames kept across all images before old images are released. */
	private static final int FRAME_BUDGET = 1500;
	/** Browsers play GIF frames marked faster than this at 100 ms, so we do too. */
	private static final int MIN_FRAME_DELAY_MS = 20;
	private static final int DEFAULT_FRAME_DELAY_MS = 100;

	/** Access-ordered, so the first entries are the least recently used. Touched only on the render thread. */
	private static final Map<String, Image> IMAGES = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75F, true));
	private static final AtomicInteger NEXT_ID = new AtomicInteger();
	private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "TweakSwitch images");
		thread.setDaemon(true);
		return thread;
	});
	/** Icons are small and many show at once, so they load in parallel and are never skipped. */
	private static final ExecutorService ICON_LOADER = Executors.newFixedThreadPool(3, runnable -> {
		Thread thread = new Thread(runnable, "TweakSwitch icons");
		thread.setDaemon(true);
		return thread;
	});
	private static final int MAX_ICON_WIDTH = 64;

	/** Only the most recently requested image is worth loading; older queued requests are skipped. */
	private static volatile @Nullable String wanted;

	public record Frame(Identifier texture, int delayMs) {
	}

	/** A loaded image: one frame for stills, several for animations. */
	public record Image(List<Frame> frames, int width, int height, int durationMs) {
		static final Image LOADING = new Image(List.of(), 0, 0, 0);
		static final Image FAILED = new Image(List.of(), -1, -1, 0);

		public boolean loaded() {
			return !this.frames.isEmpty();
		}

		public boolean failed() {
			return this == FAILED;
		}

		/** The frame to show {@code elapsedMs} after the image first appeared, looping. */
		public @Nullable Identifier textureAt(final long elapsedMs) {
			if (this.frames.isEmpty()) {
				return null;
			}
			if (this.frames.size() == 1 || this.durationMs <= 0) {
				return this.frames.get(0).texture();
			}
			long time = elapsedMs % this.durationMs;
			for (Frame frame : this.frames) {
				time -= frame.delayMs();
				if (time < 0) {
					return frame.texture();
				}
			}
			return this.frames.get(this.frames.size() - 1).texture();
		}
	}

	/** Decoded pixels, before they become textures on the render thread. */
	private record RawFrame(NativeImage image, int delayMs) {
	}

	private RemoteImages() {
	}

	/** Returns the image if it's ready, or starts loading it and returns {@link Image#LOADING}. Call on the render thread. */
	public static Image get(final String url) {
		Image image = IMAGES.get(url);
		if (image != null) {
			return image;
		}
		wanted = url;
		IMAGES.put(url, Image.LOADING);
		LOADER.execute(() -> {
			if (!url.equals(wanted)) {
				// Something else was requested before we got to it; try again next time it's asked for
				Minecraft.getInstance().execute(() -> IMAGES.remove(url, Image.LOADING));
				return;
			}
			try {
				List<RawFrame> frames = decode(WebClient.getBytes(URI.create(url)));
				Minecraft.getInstance().execute(() -> store(url, frames));
			} catch (Throwable e) {
				LOGGER.debug("Couldn't load image {}", url, e);
				Minecraft.getInstance().execute(() -> IMAGES.put(url, Image.FAILED));
			}
		});
		return Image.LOADING;
	}

	/** Like {@link #get}, for small icons in a list: every requested icon loads, several at a time. */
	public static Image getIcon(final String url) {
		Image image = IMAGES.get(url);
		if (image != null) {
			return image;
		}
		IMAGES.put(url, Image.LOADING);
		ICON_LOADER.execute(() -> {
			try {
				byte[] bytes = WebClient.getBytes(URI.create(url));
				List<RawFrame> frames = List.of(decodeStill(bytes, MAX_ICON_WIDTH));
				Minecraft.getInstance().execute(() -> store(url, frames));
			} catch (Throwable e) {
				LOGGER.debug("Couldn't load icon {}", url, e);
				Minecraft.getInstance().execute(() -> IMAGES.put(url, Image.FAILED));
			}
		});
		return Image.LOADING;
	}

	private static void store(final String url, final List<RawFrame> raw) {
		List<Frame> frames = new ArrayList<>();
		int duration = 0;
		for (RawFrame frame : raw) {
			Identifier id = Identifier.fromNamespaceAndPath(TweakSwitch.MOD_ID, "remote_image/" + NEXT_ID.incrementAndGet());
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, frame.image()));
			frames.add(new Frame(id, frame.delayMs()));
			duration += frame.delayMs();
		}
		NativeImage first = raw.get(0).image();
		IMAGES.put(url, new Image(List.copyOf(frames), first.getWidth(), first.getHeight(), duration));
		evict(url);
	}

	/** Releases the least recently shown images until the frame budget fits, never the one just added. */
	private static void evict(final String keep) {
		synchronized (IMAGES) {
			int total = IMAGES.values().stream().mapToInt(image -> image.frames().size()).sum();
			Iterator<Map.Entry<String, Image>> iterator = IMAGES.entrySet().iterator();
			while (total > FRAME_BUDGET && iterator.hasNext()) {
				Map.Entry<String, Image> entry = iterator.next();
				if (entry.getKey().equals(keep) || !entry.getValue().loaded()) {
					continue;
				}
				entry.getValue().frames().forEach(frame -> Minecraft.getInstance().getTextureManager().release(frame.texture()));
				total -= entry.getValue().frames().size();
				iterator.remove();
			}
		}
	}

	/** PNG, JPEG and GIF through ImageIO (every GIF frame); WebP through the bundled TwelveMonkeys reader. */
	private static List<RawFrame> decode(final byte[] bytes) throws IOException {
		if (bytes.length > 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
			return decodeGif(bytes);
		}
		return List.of(decodeStill(bytes, MAX_WIDTH));
	}

	/** The first frame of any supported image, shrunk to at most {@code maxWidth} wide. */
	private static RawFrame decodeStill(final byte[] bytes, final int maxWidth) throws IOException {
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
		int width = image.getWidth();
		int height = image.getHeight();
		return new RawFrame(shrink(image.getRGB(0, 0, width, height, null, 0, width), width, height, maxWidth), 0);
	}

	/**
	 * GIF frames only store what changed, at an offset, so each is drawn onto a full canvas the way browsers do, honoring
	 * each frame's disposal method. Long animations keep every n-th frame to stay under {@link #MAX_FRAMES}.
	 */
	private static List<RawFrame> decodeGif(final byte[] bytes) throws IOException {
		ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			reader.setInput(input, false);
			int count = reader.getNumImages(true);
			int step = Math.max(1, (count + MAX_FRAMES - 1) / MAX_FRAMES);

			IIOMetadata streamMetadata = reader.getStreamMetadata();
			Node screen = streamMetadata != null ? child(streamMetadata.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor") : null;
			BufferedImage firstImage = reader.read(0);
			int width = screen != null ? intAttribute(screen, "logicalScreenWidth", firstImage.getWidth()) : firstImage.getWidth();
			int height = screen != null ? intAttribute(screen, "logicalScreenHeight", firstImage.getHeight()) : firstImage.getHeight();
			if (width <= 0 || height <= 0) {
				width = firstImage.getWidth();
				height = firstImage.getHeight();
			}

			int[] canvas = new int[width * height];
			List<RawFrame> frames = new ArrayList<>();
			int pendingDelay = 0;
			for (int i = 0; i < count; i++) {
				BufferedImage frame = i == 0 ? firstImage : reader.read(i);
				Node tree = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
				Node descriptor = child(tree, "ImageDescriptor");
				Node control = child(tree, "GraphicControlExtension");
				int left = descriptor != null ? intAttribute(descriptor, "imageLeftPosition", 0) : 0;
				int top = descriptor != null ? intAttribute(descriptor, "imageTopPosition", 0) : 0;
				String disposal = control != null ? stringAttribute(control, "disposalMethod", "none") : "none";
				int delay = control != null ? intAttribute(control, "delayTime", 0) * 10 : 0;
				delay = delay < MIN_FRAME_DELAY_MS ? DEFAULT_FRAME_DELAY_MS : delay;

				int[] before = disposal.equals("restoreToPrevious") ? canvas.clone() : null;
				int frameWidth = frame.getWidth();
				int frameHeight = frame.getHeight();
				int[] pixels = frame.getRGB(0, 0, frameWidth, frameHeight, null, 0, frameWidth);
				for (int y = 0; y < frameHeight; y++) {
					int canvasY = top + y;
					if (canvasY < 0 || canvasY >= height) {
						continue;
					}
					for (int x = 0; x < frameWidth; x++) {
						int canvasX = left + x;
						int argb = pixels[y * frameWidth + x];
						if (canvasX >= 0 && canvasX < width && argb >>> 24 != 0) {
							canvas[canvasY * width + canvasX] = argb;
						}
					}
				}

				pendingDelay += delay;
				if (i % step == 0 || i == count - 1) {
					frames.add(new RawFrame(shrink(canvas, width, height, MAX_ANIMATED_WIDTH), pendingDelay));
					pendingDelay = 0;
				}

				if (disposal.equals("restoreToBackgroundColor")) {
					for (int y = Math.max(0, top); y < Math.min(height, top + frameHeight); y++) {
						for (int x = Math.max(0, left); x < Math.min(width, left + frameWidth); x++) {
							canvas[y * width + x] = 0;
						}
					}
				} else if (before != null) {
					canvas = before;
				}
			}
			return frames;
		} finally {
			reader.dispose();
		}
	}

	private static @Nullable Node child(final Node parent, final String name) {
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node.getNodeName().equals(name)) {
				return node;
			}
		}
		return null;
	}

	private static String stringAttribute(final Node node, final String name, final String fallback) {
		Node attribute = node.getAttributes() != null ? node.getAttributes().getNamedItem(name) : null;
		return attribute != null ? attribute.getNodeValue() : fallback;
	}

	private static int intAttribute(final Node node, final String name, final int fallback) {
		try {
			return Integer.parseInt(stringAttribute(node, name, Integer.toString(fallback)));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** Box-filter downscale straight into a texture image, done by hand to stay clear of AWT graphics on macOS. */
	private static NativeImage shrink(final int[] source, final int width, final int height, final int maxWidth) {
		int factor = Math.max(1, (width + maxWidth - 1) / maxWidth);
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
