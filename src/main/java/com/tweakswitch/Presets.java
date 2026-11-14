package com.tweakswitch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Named sets of switches ("PvP", "Building"), kept in tweakswitch/presets.json. A preset lists the pack files that are
 * on, lowest priority first; applying one switches every other TweakSwitch pack off.
 */
public final class Presets {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	/** Pack ids of files in the TweakSwitch folder start with this (see {@link TweakSwitchPackSource}). */
	static final String ID_PREFIX = "tweakswitch/";

	private Presets() {
	}

	private static Path file() {
		return TweakSwitch.packsDir().getParent().resolve("presets.json");
	}

	/** Preset name → pack file names, in the order they were saved. */
	public static synchronized Map<String, List<String>> load() {
		try {
			return Files.exists(file()) ? parse(Files.readString(file())) : new LinkedHashMap<>();
		} catch (Exception e) {
			LOGGER.warn("Failed to read {}", file(), e);
			return new LinkedHashMap<>();
		}
	}

	static Map<String, List<String>> parse(final String json) {
		Map<String, List<String>> presets = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : JsonParser.parseString(json).getAsJsonObject().entrySet()) {
			List<String> files = new ArrayList<>();
			entry.getValue().getAsJsonArray().forEach(file -> files.add(file.getAsString()));
			presets.put(entry.getKey(), files);
		}
		return presets;
	}

	private static void save(final Map<String, List<String>> presets) {
		JsonObject json = new JsonObject();
		presets.forEach((name, files) -> json.add(name, GSON.toJsonTree(files)));
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(json));
		} catch (Exception e) {
			LOGGER.warn("Failed to write {}", file(), e);
		}
	}

	public static synchronized void put(final String name, final List<String> files) {
		Map<String, List<String>> presets = load();
		presets.put(name, List.copyOf(files));
		save(presets);
	}

	public static synchronized void remove(final String name) {
		Map<String, List<String>> presets = load();
		if (presets.remove(name) != null) {
			save(presets);
		}
	}

	/**
	 * The pack selection after applying a preset: packs from elsewhere (the game's own, the resourcepacks folder) stay where
	 * they are, every TweakSwitch pack is dropped, and the preset's packs that still exist go on top in the preset's order.
	 */
	public static List<String> selectionFor(final Collection<String> selected, final List<String> presetFiles, final Collection<String> available) {
		List<String> result = new ArrayList<>();
		for (String id : selected) {
			if (!id.startsWith(ID_PREFIX)) {
				result.add(id);
			}
		}
		for (String file : presetFiles) {
			String id = ID_PREFIX + file;
			if (available.contains(id) && !result.contains(id)) {
				result.add(id);
			}
		}
		return result;
	}

	/**
	 * Puts the TweakSwitch packs in {@code selected} into the order of {@code wanted} (lowest priority first). They keep
	 * the slots TweakSwitch packs had, so packs from elsewhere don't move. Packs not named in {@code wanted} come after.
	 */
	public static List<String> reorder(final List<String> selected, final List<String> wanted) {
		List<String> ours = new ArrayList<>();
		for (String id : wanted) {
			if (id.startsWith(ID_PREFIX) && selected.contains(id) && !ours.contains(id)) {
				ours.add(id);
			}
		}
		for (String id : selected) {
			if (id.startsWith(ID_PREFIX) && !ours.contains(id)) {
				ours.add(id);
			}
		}
		List<String> result = new ArrayList<>(selected.size());
		int next = 0;
		for (String id : selected) {
			result.add(id.startsWith(ID_PREFIX) ? ours.get(next++) : id);
		}
		return result;
	}

	/** Switches on exactly the preset's packs and reloads resources if anything changed. */
	public static void apply(final Minecraft minecraft, final String name, final List<String> files) {
		PackRepository repository = minecraft.getResourcePackRepository();
		repository.reload();
		repository.setSelected(selectionFor(List.copyOf(repository.getSelectedIds()), files, repository.getAvailableIds()));
		Settings settings = Settings.get();
		settings.lastPreset = name;
		settings.save();
		minecraft.options.updateResourcePacks(repository);
	}

	/** Applies the preset after the one applied last, wrapping around; returns its name, or null if there are none. */
	public static @Nullable String applyNext(final Minecraft minecraft) {
		Map<String, List<String>> presets = load();
		if (presets.isEmpty()) {
			return null;
		}
		List<String> names = new ArrayList<>(presets.keySet());
		String next = names.get((names.indexOf(Settings.get().lastPreset) + 1) % names.size());
		apply(minecraft, next, presets.get(next));
		return next;
	}
}
