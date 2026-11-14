package com.tweakswitch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Saves the pack list and switch states to a small file to share with a friend or restore later, and reads one back.
 * The file names each pack and where it came from, not the packs themselves; packs from Vanilla Tweaks or Modrinth can
 * be downloaded again from that.
 */
public final class PackShare {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	/** Marks a file as a TweakSwitch export; the number is the format's version. */
	private static final String MARKER = "tweakswitch_export";

	/**
	 * @param file   the pack's file name in the TweakSwitch folder
	 * @param source where it was downloaded from ({@link PackIndex#VANILLA_TWEAKS} or {@link PackIndex#MODRINTH}), or null
	 * @param id     that site's id for it, or null
	 */
	public record Shared(String file, @Nullable String title, @Nullable String category, @Nullable String source, @Nullable String id, boolean on) {
		/** Whether it can be downloaded again by someone who doesn't have the file. */
		public boolean downloadable() {
			return this.source != null && this.id != null;
		}
	}

	/** @param packs switched-on packs first, lowest priority first, then the ones that are off */
	public record Export(String minecraft, List<Shared> packs) {
	}

	private PackShare() {
	}

	public static Path dir() {
		return TweakSwitch.packsDir().getParent().resolve("exports");
	}

	public static String toJson(final Export export) {
		JsonObject json = new JsonObject();
		json.addProperty(MARKER, 1);
		json.addProperty("minecraft", export.minecraft());
		JsonArray packs = new JsonArray();
		for (Shared pack : export.packs()) {
			JsonObject object = new JsonObject();
			object.addProperty("file", pack.file());
			if (pack.title() != null) {
				object.addProperty("title", pack.title());
			}
			if (pack.category() != null) {
				object.addProperty("category", pack.category());
			}
			if (pack.source() != null) {
				object.addProperty("source", pack.source());
			}
			if (pack.id() != null) {
				object.addProperty("id", pack.id());
			}
			object.addProperty("on", pack.on());
			packs.add(object);
		}
		json.add("packs", packs);
		return GSON.toJson(json);
	}

	/** Reads an export, or returns null if the text isn't one. */
	public static @Nullable Export parse(final String text) {
		try {
			JsonObject json = JsonParser.parseString(text).getAsJsonObject();
			if (!json.has(MARKER) || !json.has("packs")) {
				return null;
			}
			List<Shared> packs = new ArrayList<>();
			for (JsonElement element : json.getAsJsonArray("packs")) {
				JsonObject object = element.getAsJsonObject();
				String file = string(object, "file");
				// File names come from someone else's computer: only a plain name, never a path
				if (file == null || file.isBlank() || file.contains("/") || file.contains("\\") || file.equals("..")) {
					continue;
				}
				packs.add(new Shared(file, string(object, "title"), string(object, "category"), string(object, "source"), string(object, "id"),
					object.has("on") && object.get("on").getAsBoolean()));
			}
			return new Export(string(json, "minecraft") != null ? string(json, "minecraft") : "", packs);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static @Nullable String string(final JsonObject object, final String key) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
	}

	/** Writes the export to tweakswitch/exports and returns the file. */
	public static Path write(final Export export) throws IOException {
		Files.createDirectories(dir());
		Path file = dir().resolve("switches-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")) + ".json");
		Files.writeString(file, toJson(export));
		return file;
	}

	/**
	 * Downloads a pack the index (or an export) says came from Vanilla Tweaks or Modrinth into the TweakSwitch folder, and
	 * records it in the index. Returns the saved file's name. Blocks.
	 */
	public static String fetch(final @Nullable String source, final String id, final @Nullable String title) throws IOException, InterruptedException {
		Path folder = TweakSwitch.packsDir();
		if (PackIndex.VANILLA_TWEAKS.equals(source)) {
			VanillaTweaks.VtPack pack = VanillaTweaks.fetchPacks(VanillaTweaks.Kind.RESOURCE_PACKS).stream()
				.filter(candidate -> candidate.id().equals(id))
				.findFirst()
				.orElseThrow(() -> new IOException("Vanilla Tweaks has no pack " + id + " for this Minecraft version"));
			String name = VanillaTweaks.download(pack, folder).getFileName().toString();
			PackIndex.put(name, new PackIndex.Entry(pack.category(), PackIndex.VANILLA_TWEAKS, pack.id()).withTitle(pack.display()).withConflicts(pack.incompatible()));
			return name;
		}
		if (PackIndex.MODRINTH.equals(source)) {
			String name = title != null && !title.isBlank() ? title : id;
			Modrinth.Project project = new Modrinth.Project(id, "", name, "", "", 0, List.of(), null, List.of());
			String saved = Modrinth.download(project, folder).getFileName().toString();
			PackIndex.put(saved, new PackIndex.Entry("Modrinth", PackIndex.MODRINTH, id).withTitle(name));
			return saved;
		}
		throw new IOException("Don't know where to download " + id + " from");
	}
}
