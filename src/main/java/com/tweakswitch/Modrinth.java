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
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;
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
		@Nullable String iconUrl, List<String> images
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

	public record Dependency(String projectId, String type) {
	}

	/** One released version of a project, with the file to download. */
	public record Version(String id, String projectId, String number, String fileUrl, String fileName, String sha1, List<Dependency> dependencies) {
	}

	/** A project another pack depends on: usually a mod, sometimes another resource pack. */
	public record LinkedProject(String id, String slug, String title, String projectType) {
		public boolean isResourcePack() {
			return projectType.equals("resourcepack");
		}

		/** Its versions page on Modrinth, filtered to Fabric builds for this Minecraft version. */
		public URI downloadPage() {
			String filter = projectType.equals("mod") ? "?l=fabric&g=" + gameVersion() : "?g=" + gameVersion();
			return URI.create("https://modrinth.com/" + projectType + "/" + slug + "/versions" + filter);
		}

		/** Whether a loaded mod looks like this project, comparing ids and names without punctuation. */
		public boolean isInstalled() {
			String wantedSlug = normalize(slug);
			String wantedTitle = normalize(title);
			return FabricLoader.getInstance().getAllMods().stream().anyMatch(mod -> {
				String id = normalize(mod.getMetadata().getId());
				String name = normalize(mod.getMetadata().getName());
				return id.equals(wantedSlug) || id.equals(wantedTitle) || name.equals(wantedSlug) || name.equals(wantedTitle);
			});
		}

		private static String normalize(final String text) {
			return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		}
	}

	/** What a pack's newest version for this game version needs. */
	public record Requirements(Version version, List<LinkedProject> required, List<LinkedProject> optional) {
		public List<LinkedProject> missingMods() {
			return required.stream().filter(project -> !project.isResourcePack() && !project.isInstalled()).toList();
		}

		public List<LinkedProject> requiredPacks() {
			return required.stream().filter(LinkedProject::isResourcePack).toList();
		}
	}

	private static final Map<String, Requirements> REQUIREMENTS = new ConcurrentHashMap<>();
	private static final Map<String, List<String>> FULL_IMAGES = new ConcurrentHashMap<>();
	private static final Set<String> LOADING_IMAGES = ConcurrentHashMap.newKeySet();
	private static final Set<String> LOADING_REQUIREMENTS = ConcurrentHashMap.newKeySet();
	private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "TweakSwitch Modrinth lookups");
		thread.setDaemon(true);
		return thread;
	});

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
			// Featured picture first, then the rest of the gallery; the icon only if there's no gallery
			List<String> images = new ArrayList<>();
			String featured = string(hit, "featured_gallery");
			if (featured != null) {
				images.add(featured);
			}
			if (hit.has("gallery")) {
				hit.getAsJsonArray("gallery").forEach(picture -> {
					if (!images.contains(picture.getAsString())) {
						images.add(picture.getAsString());
					}
				});
			}
			if (images.isEmpty() && icon != null) {
				images.add(icon);
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
				List.copyOf(images)
			));
		}
		return new Page(projects, result.get("total_hits").getAsInt());
	}

	/**
	 * The project's gallery at full size, featured picture first. Search results only carry 350 px thumbnails, so the first
	 * call returns those and fetches the full-size list in the background for next time.
	 */
	public static List<String> images(final Project project) {
		List<String> full = FULL_IMAGES.get(project.id());
		if (full != null) {
			return full.isEmpty() ? project.images() : full;
		}
		if (LOADING_IMAGES.add(project.id())) {
			LOADER.execute(() -> {
				try {
					JsonObject json = JsonParser.parseString(WebClient.getString(URI.create(API + "/project/" + project.id()))).getAsJsonObject();
					List<String> featured = new ArrayList<>();
					List<String> rest = new ArrayList<>();
					for (JsonElement element : json.getAsJsonArray("gallery")) {
						JsonObject picture = element.getAsJsonObject();
						String url = string(picture, "raw_url") != null ? string(picture, "raw_url") : string(picture, "url");
						if (url != null) {
							(picture.get("featured").getAsBoolean() ? featured : rest).add(url);
						}
					}
					featured.addAll(rest);
					FULL_IMAGES.put(project.id(), List.copyOf(featured));
				} catch (Exception e) {
					FULL_IMAGES.put(project.id(), List.of());
				} finally {
					LOADING_IMAGES.remove(project.id());
				}
			});
		}
		return project.images();
	}

	/** Requirements if already looked up this session, without starting a lookup. */
	public static @Nullable Requirements knownRequirements(final Project project) {
		return REQUIREMENTS.get(project.id());
	}

	/** Requirements if already looked up this session; otherwise starts looking them up and returns null. */
	public static @Nullable Requirements cachedRequirements(final Project project) {
		Requirements requirements = REQUIREMENTS.get(project.id());
		if (requirements == null && LOADING_REQUIREMENTS.add(project.id())) {
			LOADER.execute(() -> {
				try {
					requirements(project);
				} catch (Exception e) {
					// Shown as unknown; looked up again before downloading
				} finally {
					LOADING_REQUIREMENTS.remove(project.id());
				}
			});
		}
		return requirements;
	}

	/** Looks up the newest version for this game version and the projects it depends on. Cached for the session. */
	public static Requirements requirements(final Project project) throws IOException, InterruptedException {
		Requirements cached = REQUIREMENTS.get(project.id());
		if (cached != null) {
			return cached;
		}
		String url = API + "/project/" + project.id() + "/version?game_versions=" + encode("[\"" + gameVersion() + "\"]") + "&loaders=" + encode("[\"minecraft\"]");
		JsonArray versions = JsonParser.parseString(WebClient.getString(URI.create(url))).getAsJsonArray();
		if (versions.isEmpty()) {
			throw new IOException(project.title() + " has no version for Minecraft " + gameVersion());
		}
		// Newest first
		Version version = parseVersion(versions.get(0).getAsJsonObject());

		List<String> ids = version.dependencies().stream().map(Dependency::projectId).distinct().toList();
		Map<String, LinkedProject> linked = new HashMap<>();
		if (!ids.isEmpty()) {
			JsonArray array = new JsonArray();
			ids.forEach(array::add);
			JsonArray projects = JsonParser.parseString(WebClient.getString(URI.create(API + "/projects?ids=" + encode(array.toString())))).getAsJsonArray();
			for (JsonElement element : projects) {
				JsonObject linkedProject = element.getAsJsonObject();
				String id = linkedProject.get("id").getAsString();
				linked.put(id, new LinkedProject(id, linkedProject.get("slug").getAsString(), linkedProject.get("title").getAsString(), linkedProject.get("project_type").getAsString()));
			}
		}
		List<LinkedProject> required = new ArrayList<>();
		List<LinkedProject> optional = new ArrayList<>();
		for (Dependency dependency : version.dependencies()) {
			LinkedProject target = linked.get(dependency.projectId());
			if (target == null) {
				continue;
			}
			if (dependency.type().equals("required") && !required.contains(target)) {
				required.add(target);
			} else if (dependency.type().equals("optional") && !optional.contains(target)) {
				optional.add(target);
			}
		}
		Requirements requirements = new Requirements(version, required, optional);
		REQUIREMENTS.put(project.id(), requirements);
		return requirements;
	}

	/** A bare project for a dependency, enough to download it. */
	public static Project projectFor(final LinkedProject linked) {
		return new Project(linked.id(), linked.slug(), linked.title(), "", "", 0, List.of(), null, List.of());
	}

	/** Downloads the newest version of the project made for this Minecraft version. */
	public static Path download(final Project project, final Path folder) throws IOException, InterruptedException {
		Path target = folder.resolve(project.fileName());
		downloadVersion(requirements(project).version(), target);
		return target;
	}

	/** Downloads a version's file to {@code target}, checking its SHA-1 first so a bad download never replaces a good file. */
	public static void downloadVersion(final Version version, final Path target) throws IOException, InterruptedException {
		Path temp = Files.createTempFile("tweakswitch-", ".zip");
		try {
			WebClient.download(URI.create(version.fileUrl()), temp);
			String actual = sha1(temp);
			if (!version.sha1().equalsIgnoreCase(actual)) {
				throw new IOException("Checksum mismatch for " + version.fileName() + ": expected " + version.sha1() + ", got " + actual);
			}
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	/** Hash → the version that file belongs to, and hash → the newest version for this game version. */
	public record UpdateCheck(Map<String, Version> current, Map<String, Version> latest) {
	}

	/** Finds pack files on Modrinth by their SHA-1. Files Modrinth doesn't know are missing from both maps. */
	public static UpdateCheck checkUpdates(final Collection<String> hashes) throws IOException, InterruptedException {
		JsonArray array = new JsonArray();
		hashes.forEach(array::add);
		JsonObject current = new JsonObject();
		current.add("hashes", array);
		current.addProperty("algorithm", "sha1");
		JsonObject update = current.deepCopy();
		JsonArray loaders = new JsonArray();
		loaders.add("minecraft");
		JsonArray gameVersions = new JsonArray();
		gameVersions.add(gameVersion());
		update.add("loaders", loaders);
		update.add("game_versions", gameVersions);

		return new UpdateCheck(
			parseVersionMap(WebClient.postJson(URI.create(API + "/version_files"), current.toString())),
			parseVersionMap(WebClient.postJson(URI.create(API + "/version_files/update"), update.toString()))
		);
	}

	private static Map<String, Version> parseVersionMap(final String json) {
		Map<String, Version> map = new HashMap<>();
		JsonParser.parseString(json).getAsJsonObject().entrySet().forEach(entry -> map.put(entry.getKey(), parseVersion(entry.getValue().getAsJsonObject())));
		return map;
	}

	private static Version parseVersion(final JsonObject version) {
		// The primary file, or the first file if none is marked primary
		JsonObject file = null;
		for (JsonElement element : version.getAsJsonArray("files")) {
			if (file == null || element.getAsJsonObject().get("primary").getAsBoolean()) {
				file = element.getAsJsonObject();
			}
		}
		List<Dependency> dependencies = new ArrayList<>();
		for (JsonElement element : version.getAsJsonArray("dependencies")) {
			JsonObject dependency = element.getAsJsonObject();
			String projectId = string(dependency, "project_id");
			if (projectId != null) {
				dependencies.add(new Dependency(projectId, dependency.get("dependency_type").getAsString()));
			}
		}
		return new Version(
			version.get("id").getAsString(),
			version.get("project_id").getAsString(),
			version.get("version_number").getAsString(),
			file != null ? file.get("url").getAsString() : "",
			file != null ? file.get("filename").getAsString() : "",
			file != null ? file.getAsJsonObject("hashes").get("sha1").getAsString() : "",
			dependencies
		);
	}

	public static String sha1(final Path file) throws IOException {
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
