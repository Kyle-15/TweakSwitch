package com.tweakswitch;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Pack lists and search results saved on disk, in tweakswitch/cache/lists, the way {@link ImageCache} saves pictures: a
 * fresh copy is used without going online, and an old copy is used when the site can't be reached.
 */
public final class ListCache {
	private static final Logger LOGGER = LogUtils.getLogger();
	/** Addresses whose last answer was an old saved copy because the site couldn't be reached. */
	private static final Set<String> STALE = ConcurrentHashMap.newKeySet();

	private ListCache() {
	}

	public static Path dir() {
		return FabricLoader.getInstance().getGameDir().resolve(TweakSwitch.MOD_ID).resolve("cache").resolve("lists");
	}

	private static Path file(final String url) {
		return dir().resolve(ImageCache.key(url).replace(".img", ".json"));
	}

	/**
	 * The text at {@code url}: the saved copy if it's younger than {@code maxAge}, otherwise downloaded and saved. If the
	 * download fails and there's a saved copy of any age, that's returned instead. Blocks.
	 *
	 * @param refresh true skips the fresh saved copy and asks the site, for a Retry or Refresh button
	 */
	public static String get(final String url, final Duration maxAge, final boolean refresh) throws IOException, InterruptedException {
		Path file = file(url);
		boolean saved = Files.isRegularFile(file);
		if (saved && !refresh && isFresh(file, maxAge)) {
			try {
				STALE.remove(url);
				return Files.readString(file);
			} catch (IOException e) {
				LOGGER.debug("Couldn't read saved list {}", file, e);
			}
		}
		String body;
		try {
			body = WebClient.getString(URI.create(url));
		} catch (IOException e) {
			if (saved) {
				STALE.add(url);
				return Files.readString(file);
			}
			throw e;
		}
		STALE.remove(url);
		save(file, body);
		return body;
	}

	/** The saved copy however old it is, or null if there's none. Never goes online. */
	public static @Nullable String saved(final String url) {
		Path file = file(url);
		try {
			return Files.isRegularFile(file) ? Files.readString(file) : null;
		} catch (IOException e) {
			return null;
		}
	}

	/** Whether the last {@link #get} for this address had to fall back to an old saved copy. */
	public static boolean wasStale(final String url) {
		return STALE.contains(url);
	}

	private static boolean isFresh(final Path file, final Duration maxAge) {
		try {
			return Files.getLastModifiedTime(file).toInstant().plus(maxAge).isAfter(Instant.now());
		} catch (IOException e) {
			return false;
		}
	}

	private static void save(final Path file, final String body) {
		try {
			Files.createDirectories(file.getParent());
			Path temp = Files.createTempFile(file.getParent(), "download-", ".tmp");
			try {
				Files.writeString(temp, body, StandardCharsets.UTF_8);
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} finally {
				Files.deleteIfExists(temp);
			}
		} catch (IOException e) {
			LOGGER.debug("Couldn't save list {}", file, e);
		}
	}
}
