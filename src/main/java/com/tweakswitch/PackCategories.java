package com.tweakswitch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;

/** Remembers which Vanilla Tweaks category each downloaded pack file came from, in tweakswitch/categories.json. */
public final class PackCategories {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private PackCategories() {
	}

	private static Path file() {
		return TweakSwitch.packsDir().getParent().resolve("categories.json");
	}

	/** Pack file name → category, e.g. "VT - Steve Zombies.zip" → "Aesthetic › More Zombies". */
	public static synchronized Map<String, String> load() {
		try {
			if (Files.exists(file())) {
				Map<String, String> map = GSON.fromJson(Files.readString(file()), new TypeToken<TreeMap<String, String>>() {}.getType());
				if (map != null) {
					return map;
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to read {}", file(), e);
		}
		return new TreeMap<>();
	}

	public static synchronized void put(final String fileName, final String category) {
		Map<String, String> map = load();
		map.put(fileName, category);
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(map));
		} catch (IOException e) {
			LOGGER.warn("Failed to write {}", file(), e);
		}
	}
}
