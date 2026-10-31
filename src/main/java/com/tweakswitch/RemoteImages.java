package com.tweakswitch;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.tweakswitch.compat.Compat;
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
import java.util.function.Supplier;
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
	/** Previews are decoded at the size the panel shows them on screen (its GUI width times the GUI scale), within these limits. */
	private static final int MIN_PREVIEW_WIDTH = 240;
	private static final int MAX_PREVIEW_WIDTH = 960;
	/** Animations stay a little smaller, since every frame is its own texture. */
	private static final int MAX_ANIMATED_WIDTH = 520;
	private static final int MAX_FRAMES = 150;
	/** Pixels one animation may hold across all its frames (about 24 MB); longer ones keep every n-th frame. */
	private static final long ANIMATION_PIXEL_BUDGET = 6_000_000L;
	/** Pixels kept across all images before the least recently shown are released (about 100 MB). */
	private static final long PIXEL_BUDGET = 25_000_000L;
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

	/** Previews are photos and screenshots, so they're scaled smoothly instead of Minecraft's blocky "nearest pixel". */
	private static final class SmoothTexture extends DynamicTexture {
		SmoothTexture(final Supplier<String> label, final NativeImage image) {
			super(label, image);
			this.sampler = Compat.smoothSampler();
		}
	}

	/** A loaded image: one frame for stills, several for animations. */
	public record Image(List<Frame> frames, int width, int height, int durationMs) {
		static final Image LOADING = new Image(List.of(), 0, 0, 0);
		static final Image FAILED = new Image(List.of(), -1, -1, 0);

		long pixels() {
			return (long) Math.max(0, this.width) * Math.max(0, this.height) * this.frames.size();
		}

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
				Minecraft.getInstance().execute(() -> store(url, frames, true));
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
				Minecraft.getInstance().execute(() -> store(url, frames, false));
			} catch (Throwable e) {
				LOGGER.debug("Couldn't load icon {}", url, e);
				Minecraft.getInstance().execute(() -> IMAGES.put(url, Image.FAILED));
			}
		});
		return Image.LOADING;
	}

	/** @param smooth previews scale smoothly; pixel-art icons stay crisp */
	private static void store(final String url, final List<RawFrame> raw, final boolean smooth) {
		List<Frame> frames = new ArrayList<>();
		int duration = 0;
		for (RawFrame frame : raw) {
			Identifier id = Identifier.fromNamespaceAndPath(TweakSwitch.MOD_ID, "remote_image/" + NEXT_ID.incrementAndGet());
			Minecraft.getInstance().getTextureManager().register(id,
				smooth ? new SmoothTexture(id::toString, frame.image()) : new DynamicTexture(id::toString, frame.image()));
			frames.add(new Frame(id, frame.delayMs()));
			duration += frame.delayMs();
		}
		NativeImage first = raw.get(0).image();
		IMAGES.put(url, new Image(List.copyOf(frames), first.getWidth(), first.getHeight(), duration));
		evict(url);
	}

	/** Releases the least recently shown images until the pixel budget fits, never the one just added. */
	private static void evict(final String keep) {
		synchronized (IMAGES) {
			long total = IMAGES.values().stream().mapToLong(Image::pixels).sum();
			Iterator<Map.Entry<String, Image>> iterator = IMAGES.entrySet().iterator();
			while (total > PIXEL_BUDGET && iterator.hasNext()) {
				Map.Entry<String, Image> entry = iterator.next();
				if (entry.getKey().equals(keep) || !entry.getValue().loaded()) {
					continue;
				}
				entry.getValue().frames().forEach(frame -> Minecraft.getInstance().getTextureManager().release(frame.texture()));
				total -= entry.getValue().pixels();
				iterator.remove();
			}
		}
	}

	/** The preview panel's width in real screen pixels: its GUI width times the GUI scale. */
	private static int previewWidth() {
		int scaled = PackListScreen.PREVIEW_WIDTH * Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		return Math.max(MIN_PREVIEW_WIDTH, Math.min(MAX_PREVIEW_WIDTH, scaled));
	}

	/** PNG, JPEG and GIF through ImageIO (every GIF frame); WebP through the bundled TwelveMonkeys reader. */
	private static List<RawFrame> decode(final byte[] bytes) throws IOException {
		if (bytes.length > 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
			return decodeGif(bytes);
		}
		return List.of(decodeStill(bytes, previewWidth()));
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
		return new RawFrame(resize(image.getRGB(0, 0, width, height, null, 0, width), width, height, maxWidth), 0);
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

			IIOMetadata streamMetadata = reader.getStreamMetadata();
			Node screen = streamMetadata != null ? child(streamMetadata.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor") : null;
			BufferedImage firstImage = reader.read(0);
			int width = screen != null ? intAttribute(screen, "logicalScreenWidth", firstImage.getWidth()) : firstImage.getWidth();
			int height = screen != null ? intAttribute(screen, "logicalScreenHeight", firstImage.getHeight()) : firstImage.getHeight();
			if (width <= 0 || height <= 0) {
				width = firstImage.getWidth();
				height = firstImage.getHeight();
			}

			int maxWidth = Math.min(previewWidth(), MAX_ANIMATED_WIDTH);
			long framePixels = (long) Math.min(width, maxWidth) * Math.max(1, height * Math.min(width, maxWidth) / Math.max(1, width));
			int maxFrames = (int) Math.max(1, Math.min(MAX_FRAMES, ANIMATION_PIXEL_BUDGET / Math.max(1, framePixels)));
			int step = Math.max(1, (count + maxFrames - 1) / maxFrames);
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
					frames.add(new RawFrame(resize(canvas, width, height, maxWidth), pendingDelay));
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

	/**
	 * Shrinks to at most {@code maxWidth} wide by averaging exactly the source area each output pixel covers (so a 957 px
	 * picture becomes a sharp 640 px one, not a blocky 319 px one), straight into a texture image. Never enlarges: smooth
	 * texture scaling handles that. Done by hand to stay clear of AWT graphics on macOS.
	 */
	private static NativeImage resize(final int[] source, final int width, final int height, final int maxWidth) {
		int outWidth = Math.min(width, maxWidth);
		int outHeight = Math.max(1, (int) Math.round((double) height * outWidth / width));
		NativeImage out = new NativeImage(outWidth, outHeight, false);
		if (outWidth == width) {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					out.setPixel(x, y, source[y * width + x]);
				}
			}
			return out;
		}
		double scaleX = (double) width / outWidth;
		double scaleY = (double) height / outHeight;
		for (int y = 0; y < outHeight; y++) {
			double top = y * scaleY;
			double bottom = top + scaleY;
			for (int x = 0; x < outWidth; x++) {
				double left = x * scaleX;
				double right = left + scaleX;
				double a = 0, r = 0, g = 0, b = 0, total = 0;
				for (int sy = (int) top; sy < Math.min(height, Math.ceil(bottom)); sy++) {
					double wy = Math.min(bottom, sy + 1) - Math.max(top, sy);
					for (int sx = (int) left; sx < Math.min(width, Math.ceil(right)); sx++) {
						double weight = wy * (Math.min(right, sx + 1) - Math.max(left, sx));
						int argb = source[sy * width + sx];
						double alpha = (argb >>> 24) * weight;
						// Weight colors by alpha so transparent pixels don't darken edges
						a += alpha;
						r += (argb >> 16 & 0xFF) * alpha;
						g += (argb >> 8 & 0xFF) * alpha;
						b += (argb & 0xFF) * alpha;
						total += weight;
					}
				}
				int outA = (int) Math.round(a / total);
				int outR = a > 0 ? (int) Math.round(r / a) : 0;
				int outG = a > 0 ? (int) Math.round(g / a) : 0;
				int outB = a > 0 ? (int) Math.round(b / a) : 0;
				out.setPixel(x, y, outA << 24 | outR << 16 | outG << 8 | outB);
			}
		}
		return out;
	}
}
