package com.tweakswitch;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Works out what a resource pack changes from the files inside it, for packs whose name and description don't say: which
 * parts of the game it touches, which textures to show as a preview, and words to search by.
 */
public final class PackContents {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int PREVIEW_TEXTURES = 16;
	/** Textures are shown in cells about 37 GUI pixels wide; bigger ones are shrunk to keep memory low. */
	private static final int MAX_TEXTURE_SIZE = 64;

	/** The parts of the game a pack can change, in the order they're listed. */
	public enum Kind {
		PARTICLES("Particles"),
		GUI("GUI"),
		FONTS("Fonts"),
		BLOCKS("Blocks"),
		ITEMS("Items"),
		ENTITIES("Mobs & Entities"),
		ENVIRONMENT("Sky & Environment"),
		SOUNDS("Sounds"),
		SHADERS("Shaders"),
		LANGUAGE("Language"),
		OTHER("Other");

		public final String label;

		Kind(final String label) {
			this.label = label;
		}
	}

	/**
	 * @param counts     files per part of the game it changes
	 * @param textures   paths inside the pack of textures to preview
	 * @param searchText lowercase file names inside the pack, so searching "flame" finds packs that change the flame particle
	 */
	public record Info(Map<Kind, Integer> counts, List<String> textures, String searchText) {
		/** What it changes most, e.g. {@link Kind#PARTICLES}. */
		public Kind primary() {
			return this.counts.entrySet().stream()
				.filter(entry -> entry.getKey() != Kind.OTHER)
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(Kind.OTHER);
		}

		/** "Changes: Particles (12), Sounds (3)". */
		public Component summary() {
			if (this.counts.isEmpty()) {
				return Component.translatable("tweakswitch.contents.nothing");
			}
			String parts = this.counts.entrySet().stream()
				.sorted(Map.Entry.<Kind, Integer>comparingByValue().reversed())
				.map(entry -> entry.getKey().label + " (" + entry.getValue() + ")")
				.collect(Collectors.joining(", "));
			return Component.translatable("tweakswitch.contents.changes", parts);
		}
	}

	private record Key(Path path, long modified, long size) {
	}

	private static final Map<Key, Info> INFO = new ConcurrentHashMap<>();
	private static final Map<Key, List<Identifier>> TEXTURES = new ConcurrentHashMap<>();
	private static final AtomicInteger NEXT_ID = new AtomicInteger();
	private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "TweakSwitch pack contents");
		thread.setDaemon(true);
		return thread;
	});

	private PackContents() {
	}

	/** What's inside the pack if it's been looked at already (and hasn't changed since), else null. */
	public static @Nullable Info cached(final Path pack) {
		Key key = key(pack);
		return key != null ? INFO.get(key) : null;
	}

	/** Looks inside every pack not looked at yet, in the background, then runs {@code done} on the render thread. */
	public static void analyzeAll(final Collection<Path> packs, final Runnable done) {
		List<Path> todo = packs.stream().filter(pack -> cached(pack) == null).toList();
		if (todo.isEmpty()) {
			return;
		}
		LOADER.execute(() -> {
			for (Path pack : todo) {
				Key key = key(pack);
				if (key != null && !INFO.containsKey(key)) {
					INFO.put(key, analyze(pack));
				}
			}
			Minecraft.getInstance().execute(done);
		});
	}

	/** The pack's preview textures, loading them in the background the first time (empty until then). */
	public static List<Identifier> textures(final Path pack) {
		Key key = key(pack);
		Info info = key != null ? INFO.get(key) : null;
		if (info == null || info.textures().isEmpty()) {
			return List.of();
		}
		List<Identifier> loaded = TEXTURES.get(key);
		if (loaded != null) {
			return loaded;
		}
		TEXTURES.put(key, List.of());
		LOADER.execute(() -> {
			List<NativeImage> images = new ArrayList<>();
			readEach(pack, info.textures(), stream -> {
				try {
					images.add(fitForPreview(NativeImage.read(stream)));
				} catch (IOException e) {
					// Not a readable PNG: just leave it out
				}
			});
			Minecraft.getInstance().execute(() -> {
				List<Identifier> ids = new ArrayList<>();
				for (NativeImage image : images) {
					Identifier id = Identifier.fromNamespaceAndPath(TweakSwitch.MOD_ID, "pack_texture/" + NEXT_ID.incrementAndGet());
					Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
					ids.add(id);
				}
				TEXTURES.put(key, List.copyOf(ids));
			});
		});
		return List.of();
	}

	private static @Nullable Key key(final Path pack) {
		try {
			return new Key(pack, Files.getLastModifiedTime(pack).toMillis(), Files.isDirectory(pack) ? 0 : Files.size(pack));
		} catch (IOException e) {
			return null;
		}
	}

	private static Info analyze(final Path pack) {
		List<String> paths;
		try {
			paths = listFiles(pack);
		} catch (IOException e) {
			LOGGER.warn("Couldn't look inside pack {}", pack, e);
			return new Info(Map.of(), List.of(), "");
		}

		Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
		Map<Kind, List<String>> texturesByKind = new EnumMap<>(Kind.class);
		StringBuilder search = new StringBuilder();
		for (String path : paths) {
			String lower = path.toLowerCase(Locale.ROOT);
			if (!lower.startsWith("assets/") || lower.endsWith("/")) {
				continue;
			}
			Kind kind = classify(lower);
			counts.merge(kind, 1, Integer::sum);
			String name = lower.substring(lower.lastIndexOf('/') + 1);
			int dot = name.indexOf('.');
			if (search.length() < 8000) {
				search.append(dot > 0 ? name.substring(0, dot) : name).append(' ');
			}
			if (lower.contains("/textures/") && lower.endsWith(".png")) {
				texturesByKind.computeIfAbsent(kind, k -> new ArrayList<>()).add(path);
			}
		}

		// Preview textures: from what it changes most first, then the rest, spread evenly through each list
		List<String> textures = new ArrayList<>();
		counts.entrySet().stream()
			.sorted(Map.Entry.<Kind, Integer>comparingByValue().reversed())
			.forEach(entry -> {
				List<String> ofKind = texturesByKind.getOrDefault(entry.getKey(), List.of());
				int room = PREVIEW_TEXTURES - textures.size();
				if (room <= 0 || ofKind.isEmpty()) {
					return;
				}
				List<String> sorted = ofKind.stream().sorted().toList();
				int take = Math.min(room, sorted.size());
				for (int i = 0; i < take; i++) {
					textures.add(sorted.get(i * sorted.size() / take));
				}
			});
		for (Kind kind : counts.keySet()) {
			search.append(kind.label.toLowerCase(Locale.ROOT)).append(' ');
		}
		return new Info(counts, List.copyOf(textures), search.toString());
	}

	/** Which part of the game a file under assets/ changes, from where it sits. */
	private static Kind classify(final String path) {
		if (path.contains("/textures/particle/") || path.contains("/particles/")) {
			return Kind.PARTICLES;
		}
		if (path.contains("/textures/font/") || path.contains("/font/")) {
			return Kind.FONTS;
		}
		if (path.contains("/textures/gui/") || path.contains("/textures/mob_effect/") || path.contains("/textures/map/")) {
			return Kind.GUI;
		}
		if (path.contains("/textures/block/") || path.contains("/blockstates/") || path.contains("/models/block/")) {
			return Kind.BLOCKS;
		}
		if (path.contains("/textures/item/") || path.contains("/models/item/") || path.contains("/items/") || path.contains("/textures/trims/")) {
			return Kind.ITEMS;
		}
		if (path.contains("/textures/entity/") || path.contains("/optifine/cem/") || path.contains("/optifine/random/") || path.contains("/models/entity/")) {
			return Kind.ENTITIES;
		}
		if (path.contains("/textures/environment/") || path.contains("/optifine/sky/") || path.contains("/textures/painting/") || path.contains("/colormap/")) {
			return Kind.ENVIRONMENT;
		}
		if (path.contains("/sounds/") || path.endsWith("/sounds.json")) {
			return Kind.SOUNDS;
		}
		if (path.contains("/shaders/") || path.contains("/post_effect/")) {
			return Kind.SHADERS;
		}
		if (path.contains("/lang/")) {
			return Kind.LANGUAGE;
		}
		return Kind.OTHER;
	}

	private static List<String> listFiles(final Path pack) throws IOException {
		if (Files.isDirectory(pack)) {
			try (Stream<Path> files = Files.walk(pack)) {
				return files.filter(Files::isRegularFile).map(file -> pack.relativize(file).toString().replace('\\', '/')).toList();
			}
		}
		try (ZipFile zip = new ZipFile(pack.toFile())) {
			return zip.stream().map(ZipEntry::getName).toList();
		}
	}

	private interface StreamConsumer {
		void accept(InputStream stream) throws IOException;
	}

	private static void readEach(final Path pack, final List<String> paths, final StreamConsumer consumer) {
		try {
			if (Files.isDirectory(pack)) {
				for (String path : paths) {
					try (InputStream stream = Files.newInputStream(pack.resolve(path))) {
						consumer.accept(stream);
					}
				}
				return;
			}
			try (ZipFile zip = new ZipFile(pack.toFile())) {
				for (String path : paths) {
					ZipEntry entry = zip.getEntry(path);
					if (entry != null) {
						try (InputStream stream = zip.getInputStream(entry)) {
							consumer.accept(stream);
						}
					}
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Couldn't read textures from pack {}", pack, e);
		}
	}

	/**
	 * Animated textures are a tall strip of frames: keep the first square. Big ones are shrunk by sampling, which keeps pixel
	 * art crisp.
	 */
	private static NativeImage fitForPreview(final NativeImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		int side = height > width && height % width == 0 ? width : height;
		int outWidth = Math.min(width, MAX_TEXTURE_SIZE);
		int outHeight = Math.max(1, Math.min(side, side * outWidth / Math.max(1, width)));
		if (outWidth == width && outHeight == height) {
			return image;
		}
		NativeImage out = new NativeImage(outWidth, outHeight, false);
		for (int y = 0; y < outHeight; y++) {
			for (int x = 0; x < outWidth; x++) {
				out.setPixel(x, y, image.getPixel(x * width / outWidth, y * side / outHeight));
			}
		}
		image.close();
		return out;
	}
}
