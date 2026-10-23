package com.tweakswitch;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import org.jspecify.annotations.Nullable;

/**
 * Talks to vanillatweaks.net the same way its resource pack picker does: reads the pack list, then asks the site to
 * build a zip holding just one pack and downloads it. Calls block, so run them off the render thread.
 */
public final class VanillaTweaks {
	public static final URI SITE = URI.create("https://vanillatweaks.net/picker/resource-packs/");
	private static final String BASE = "https://vanillatweaks.net";
	public static final String FILE_PREFIX = "VT - ";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/**
	 * @param id           the site's internal pack name, e.g. "MZSteve"
	 * @param categoryPath dotted category slugs the site expects, e.g. "aesthetic.more-zombies"
	 * @param category     readable category, e.g. "Aesthetic › More Zombies"
	 */
	public record VtPack(
		String id, String display, String description, String categoryPath, String category, List<String> incompatible, @Nullable String previewExtension,
		@Nullable String warning
	) {
		public String fileName() {
			return FILE_PREFIX + display.replaceAll("[\\\\/:*?\"<>|]", "_") + ".zip";
		}
	}

	private VanillaTweaks() {
	}

	/** The site groups packs by "major.minor", e.g. 26.3 for 26.3.1. */
	public static String siteVersion() {
		String name = SharedConstants.getCurrentVersion().name();
		Matcher matcher = Pattern.compile("^\\d+\\.\\d+").matcher(name);
		return matcher.find() ? matcher.group() : name;
	}

	public static List<VtPack> fetchPacks() throws IOException, InterruptedException {
		String body = WebClient.getString(URI.create(BASE + "/assets/resources/json/" + siteVersion() + "/rpcategories.json"));
		List<VtPack> packs = new ArrayList<>();
		collect(JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("categories"), "", "", null, packs);
		return packs;
	}

	/** @param parentWarning the site's warning on the nearest enclosing category, e.g. that packs need OptiFine-like mods */
	private static void collect(final JsonArray categories, final String parentPath, final String parentLabel, final @Nullable String parentWarning, final List<VtPack> out) {
		for (JsonElement element : categories) {
			JsonObject category = element.getAsJsonObject();
			String name = category.get("category").getAsString();
			String path = parentPath.isEmpty() ? slug(name) : parentPath + "." + slug(name);
			String label = parentLabel.isEmpty() ? name : parentLabel + " › " + name;
			String warning = category.has("warning") && category.getAsJsonObject("warning").has("text")
				? stripHtml(category.getAsJsonObject("warning").get("text").getAsString())
				: parentWarning;
			if (category.has("packs")) {
				for (JsonElement packElement : category.getAsJsonArray("packs")) {
					JsonObject pack = packElement.getAsJsonObject();
					List<String> incompatible = new ArrayList<>();
					if (pack.has("incompatible")) {
						pack.getAsJsonArray("incompatible").forEach(id -> incompatible.add(id.getAsString()));
					}
					out.add(new VtPack(
						pack.get("name").getAsString(),
						pack.get("display").getAsString(),
						pack.has("description") ? stripHtml(pack.get("description").getAsString()) : "",
						path,
						label,
						incompatible,
						pack.has("previewExtension") ? pack.get("previewExtension").getAsString() : null,
						warning
					));
				}
			}
			if (category.has("categories")) {
				collect(category.getAsJsonArray("categories"), path, label, warning, out);
			}
		}
	}

	/** Matches the site's category ids: lower case, every other character becomes a dash ("LGBTQ+ Pride" → "lgbtq--pride"). */
	private static String slug(final String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "-");
	}

	private static String stripHtml(final String html) {
		return html.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]*>", "").replace("&amp;", "&").replace("&nbsp;", " ").trim();
	}

	/** The picture the website shows when you hover a pack. */
	public static String previewUrl(final VtPack pack) {
		String extension = pack.previewExtension() != null ? pack.previewExtension() : "png";
		return BASE + "/assets/resources/previews/resourcepacks/" + siteVersion() + "/" + URLEncoder.encode(pack.id(), StandardCharsets.UTF_8) + "." + extension;
	}

	/** Downloads one pack into {@code targetDir} and returns the saved file. */
	public static Path download(final VtPack pack, final Path targetDir) throws IOException, InterruptedException {
		JsonObject packs = new JsonObject();
		JsonArray ids = new JsonArray();
		ids.add(pack.id());
		packs.add(pack.categoryPath(), ids);
		String form = "packs=" + URLEncoder.encode(packs.toString(), StandardCharsets.UTF_8) + "&version=" + URLEncoder.encode(siteVersion(), StandardCharsets.UTF_8);
		String body = WebClient.postForm(URI.create(BASE + "/assets/server/zipresourcepacks.php"), form);

		JsonObject result = JsonParser.parseString(body).getAsJsonObject();
		if (!"success".equals(result.has("status") ? result.get("status").getAsString() : null) || !result.has("link")) {
			throw new IOException("Vanilla Tweaks refused " + pack.id() + ": " + (result.has("message") ? result.get("message").getAsString() : body));
		}

		Path temp = Files.createTempFile("tweakswitch-", ".zip");
		try {
			WebClient.download(URI.create(BASE + result.get("link").getAsString()), temp);
			setDescription(temp, pack.description());
			Files.createDirectories(targetDir);
			Path target = targetDir.resolve(pack.fileName());
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			return target;
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	/** Every download says "Vanilla Tweaks 26.1 - 26.3"; swap in the pack's own description so the switch tooltip is useful. */
	private static void setDescription(final Path zip, final String description) throws IOException {
		if (description.isEmpty()) {
			return;
		}
		try (FileSystem fs = FileSystems.newFileSystem(zip)) {
			Path mcmeta = fs.getPath("pack.mcmeta");
			if (!Files.exists(mcmeta)) {
				return;
			}
			JsonObject meta = JsonParser.parseString(Files.readString(mcmeta)).getAsJsonObject();
			if (meta.has("pack")) {
				meta.getAsJsonObject("pack").addProperty("description", description);
				Files.writeString(mcmeta, GSON.toJson(meta));
			}
		}
	}
}
