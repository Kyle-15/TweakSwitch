package com.tweakswitch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import net.minecraft.SharedConstants;
import org.jspecify.annotations.Nullable;

/** Searches Modrinth's public API (https://docs.modrinth.com) for resource packs and downloads them. Calls block. */
public final class Modrinth {
	public static final URI SITE = URI.create("https://modrinth.com/resourcepacks");
	private static final String API = "https://api.modrinth.com/v2";
	public static final String FILE_PREFIX = "MR - ";
	public static final int PAGE_SIZE = 30;

	public enum Sort {
		RELEVANCE("relevance"),
		DOWNLOADS("downloads"),
		FOLLOWS("follows"),
		NEWEST("newest"),
		UPDATED("updated");

		final String index;

		Sort(final String index) {
			this.index = index;
		}
	}

	public record Project(
		String id, String slug, String title, String description, String author, long downloads, List<String> categories,
		@Nullable String iconUrl, @Nullable String imageUrl
	) {
		public String fileName() {
			return FILE_PREFIX + title.replaceAll("[\\\\/:*?\"<>|]", "_") + ".zip";
		}

		public URI page() {
			return URI.create("https://modrinth.com/resourcepack/" + slug);
		}
	}

	public record Page(List<Project> projects, int totalHits) {
	}

	private Modrinth() {
	}

	public static String gameVersion() {
		return SharedConstants.getCurrentVersion().name();
	}

	/** One page of resource packs that list this Minecraft version. */
	public static Page search(final String query, final Sort sort, final int offset) throws IOException, InterruptedException {
		String facets = "[[\"project_type:resourcepack\"],[\"versions:" + gameVersion() + "\"]]";
		String url = API + "/search?limit=" + PAGE_SIZE + "&offset=" + offset + "&index=" + sort.index
			+ "&facets=" + encode(facets) + (query.isBlank() ? "" : "&query=" + encode(query.trim()));
		JsonObject result = JsonParser.parseString(WebClient.getString(URI.create(url))).getAsJsonObject();
		List<Project> projects = new ArrayList<>();
		for (JsonElement element : result.getAsJsonArray("hits")) {
			JsonObject hit = element.getAsJsonObject();
			List<String> categories = new ArrayList<>();
			if (hit.has("display_categories")) {
				hit.getAsJsonArray("display_categories").forEach(category -> categories.add(category.getAsString()));
			}
			String icon = string(hit, "icon_url");
			String gallery = string(hit, "featured_gallery");
			if (gallery == null && hit.has("gallery") && !hit.getAsJsonArray("gallery").isEmpty()) {
				gallery = hit.getAsJsonArray("gallery").get(0).getAsString();
			}
			projects.add(new Project(
				hit.get("project_id").getAsString(),
				hit.get("slug").getAsString(),
				hit.get("title").getAsString(),
				string(hit, "description") != null ? string(hit, "description") : "",
				string(hit, "author") != null ? string(hit, "author") : "",
				hit.has("downloads") ? hit.get("downloads").getAsLong() : 0,
				categories,
				icon,
				gallery != null ? gallery : icon
			));
		}
		return new Page(projects, result.get("total_hits").getAsInt());
	}

	/** Downloads the newest version of the project made for this Minecraft version, checking its SHA-1. */
	public static Path download(final Project project, final Path folder) throws IOException, InterruptedException {
		String url = API + "/project/" + project.id() + "/version?game_versions=" + encode("[\"" + gameVersion() + "\"]") + "&loaders=" + encode("[\"minecraft\"]");
		JsonArray versions = JsonParser.parseString(WebClient.getString(URI.create(url))).getAsJsonArray();
		if (versions.isEmpty()) {
			throw new IOException(project.title() + " has no version for Minecraft " + gameVersion());
		}

		// Newest first; use its primary file, or the first file if none is marked primary
		JsonArray files = versions.get(0).getAsJsonObject().getAsJsonArray("files");
		JsonObject file = null;
		for (JsonElement element : files) {
			if (file == null || element.getAsJsonObject().get("primary").getAsBoolean()) {
				file = element.getAsJsonObject();
			}
		}
		if (file == null) {
			throw new IOException(project.title() + " has no files");
		}

		Path temp = Files.createTempFile("tweakswitch-", ".zip");
		try {
			WebClient.download(URI.create(file.get("url").getAsString()), temp);
			String expected = file.getAsJsonObject("hashes").get("sha1").getAsString();
			String actual = sha1(temp);
			if (!expected.equalsIgnoreCase(actual)) {
				throw new IOException("Checksum mismatch for " + project.title() + ": expected " + expected + ", got " + actual);
			}
			Path target = folder.resolve(project.fileName());
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			return target;
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	private static String sha1(final Path file) throws IOException {
		try (InputStream input = Files.newInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			byte[] buffer = new byte[8192];
			int read;
			while ((read = input.read(buffer)) > 0) {
				digest.update(buffer, 0, read);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static @Nullable String string(final JsonObject object, final String key) {
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
	}

	private static String encode(final String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
