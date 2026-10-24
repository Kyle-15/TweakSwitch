package com.tweakswitch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Remembers where each downloaded pack file came from, in tweakswitch/downloads.json: its category for the switch screen,
 * and its source and id so the Updates screen can fetch it again.
 */
public final class PackIndex {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	public static final String VANILLA_TWEAKS = "vanillatweaks";
	public static final String MODRINTH = "modrinth";

	/**
	 * @param source {@link #VANILLA_TWEAKS} or {@link #MODRINTH}; id is that site's id for the pack
	 * @param title  the pack's real name, which file names can't always hold (e.g. a ":")
	 */
	public record Entry(String category, @Nullable String source, @Nullable String id, @Nullable String title) {
		public Entry(final String category, final @Nullable String source, final @Nullable String id) {
			this(category, source, id, null);
		}

		public Entry withTitle(final String title) {
			return new Entry(this.category, this.source, this.id, title);
		}
	}

	private PackIndex() {
	}

	private static Path file() {
		return TweakSwitch.packsDir().getParent().resolve("downloads.json");
	}

	/** Pack file name → entry, e.g. "VT - Steve Zombies.zip" → Aesthetic › More Zombies from Vanilla Tweaks, id MZSteve. */
	public static synchronized Map<String, Entry> load() {
		Map<String, Entry> map = new TreeMap<>();
		try {
			if (Files.exists(file())) {
				for (Map.Entry<String, JsonElement> element : JsonParser.parseString(Files.readString(file())).getAsJsonObject().entrySet()) {
					map.put(element.getKey(), GSON.fromJson(element.getValue(), Entry.class));
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to read {}", file(), e);
		}
		return map;
	}

	public static synchronized void put(final String fileName, final Entry entry) {
		Map<String, Entry> map = load();
		map.put(fileName, entry);
		JsonObject json = new JsonObject();
		map.forEach((name, value) -> json.add(name, GSON.toJsonTree(value)));
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(json));
		} catch (IOException e) {
			LOGGER.warn("Failed to write {}", file(), e);
		}
	}
}
