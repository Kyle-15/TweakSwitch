package com.tweakswitch;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

/**
 * Keeps downloaded pictures (icons, previews, animated GIFs) on disk, so screens show them straight away next time and the
 * websites are asked less often. A picture is fetched again after a week; if that fails, the old copy is used.
 */
public final class ImageCache {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Duration MAX_AGE = Duration.ofDays(7);
	/** Oldest pictures are deleted once the cache passes this size, down to {@link #TRIM_TO_BYTES}. */
	private static final long MAX_BYTES = 256L * 1024 * 1024;
	private static final long TRIM_TO_BYTES = 192L * 1024 * 1024;
	private static final int WRITES_BETWEEN_TRIMS = 50;

	private static final AtomicBoolean TRIMMED_THIS_SESSION = new AtomicBoolean();
	private static final AtomicInteger WRITES = new AtomicInteger();

	private ImageCache() {
	}

	public static Path dir() {
		return FabricLoader.getInstance().getGameDir().resolve(TweakSwitch.MOD_ID).resolve("cache").resolve("images");
	}

	/** The picture's bytes: from disk if fresh, otherwise downloaded and saved. Blocks. */
	public static byte[] get(final String url) throws IOException, InterruptedException {
		if (TRIMMED_THIS_SESSION.compareAndSet(false, true)) {
			trim();
		}
		Path file = dir().resolve(key(url));
		boolean cached = Files.isRegularFile(file);
		if (cached && isFresh(file)) {
			try {
				return Files.readAllBytes(file);
			} catch (IOException e) {
				LOGGER.debug("Couldn't read cached image {}", file, e);
			}
		}
		byte[] bytes;
		try {
			bytes = WebClient.getBytes(URI.create(url));
		} catch (IOException e) {
			if (cached) {
				// Offline or the site is down: an old picture beats none
				return Files.readAllBytes(file);
			}
			throw e;
		}
		save(file, bytes);
		return bytes;
	}

	private static boolean isFresh(final Path file) {
		try {
			return Files.getLastModifiedTime(file).toInstant().plus(MAX_AGE).isAfter(java.time.Instant.now());
		} catch (IOException e) {
			return false;
		}
	}

	/** Written to a temporary file first, so a half-written picture is never read back. */
	private static void save(final Path file, final byte[] bytes) {
		try {
			Files.createDirectories(file.getParent());
			Path temp = Files.createTempFile(file.getParent(), "download-", ".tmp");
			try {
				Files.write(temp, bytes);
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} finally {
				Files.deleteIfExists(temp);
			}
			if (WRITES.incrementAndGet() % WRITES_BETWEEN_TRIMS == 0) {
				trim();
			}
		} catch (IOException e) {
			LOGGER.debug("Couldn't cache image {}", file, e);
		}
	}

	/** Deletes the oldest pictures while the cache is over its size limit, plus any leftover temporary files. */
	static synchronized void trim() {
		Path dir = dir();
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> stream = Files.list(dir)) {
			List<Path> files = stream.filter(Files::isRegularFile).toList();
			long total = 0;
			for (Path file : files) {
				if (file.getFileName().toString().endsWith(".tmp")) {
					Files.deleteIfExists(file);
				} else {
					total += Files.size(file);
				}
			}
			if (total <= MAX_BYTES) {
				return;
			}
			List<Path> oldestFirst = files.stream()
				.filter(Files::exists)
				.sorted(Comparator.comparing(ImageCache::modified))
				.toList();
			for (Path file : oldestFirst) {
				if (total <= TRIM_TO_BYTES) {
					break;
				}
				long size = Files.size(file);
				Files.deleteIfExists(file);
				total -= size;
			}
		} catch (IOException e) {
			LOGGER.debug("Couldn't trim the image cache", e);
		}
	}

	private static FileTime modified(final Path file) {
		try {
			return Files.getLastModifiedTime(file);
		} catch (IOException e) {
			return FileTime.fromMillis(0);
		}
	}

	/** A file name from the URL: its SHA-1, so any URL becomes a safe, fixed-length name. */
	static String key(final String url) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8))) + ".img";
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
