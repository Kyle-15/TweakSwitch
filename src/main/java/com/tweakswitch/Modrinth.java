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
import java.time.Duration;
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

	/** The two kinds of project TweakSwitch downloads from Modrinth. Each has its own search, files and website section. */
	public enum Type {
		RESOURCE_PACKS("resourcepack", "minecraft", "resourcepacks"),
		/** Data packs are files published for the "datapack" loader; many of those projects are also mods. */
		DATA_PACKS("datapack", "datapack", "datapacks");

		final String projectType;
		/** What Modrinth calls the loader of the files to download. */
		final String loader;
		public final URI site;

		Type(final String projectType, final String loader, final String section) {
			this.projectType = projectType;
			this.loader = loader;
			this.site = URI.create("https://modrinth.com/" + section);
		}

		/** The tags a search can be narrowed to, in the order Modrinth's own filter list shows them. */
		public List<String> categories() {
			return this == RESOURCE_PACKS ? RESOURCE_PACK_CATEGORIES : DATA_PACK_CATEGORIES;
		}
	}

	private static final List<String> RESOURCE_PACK_CATEGORIES = List.of(
		"combat", "cursed", "decoration", "realistic", "simplistic", "themed", "tweaks", "utility", "vanilla-like",
		"audio", "blocks", "core-shaders", "entities", "environment", "equipment", "fonts", "gui", "items", "locale", "models",
		"8x-", "16x", "32x", "48x", "64x", "128x", "256x", "512x+"
	);
	private static final List<String> DATA_PACK_CATEGORIES = List.of(
		"adventure", "cursed", "decoration", "economy", "equipment", "food", "game-mechanics", "library", "magic", "management", "minigame", "mobs",
		"optimization", "social", "storage", "technology", "transportation", "utility", "worldgen"
	);
	/** Modrinth's tag for resource packs made to go with mods. */
	public static final String MODDED = "modded";

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
	/** Projects found to have no file of the wanted kind for this Minecraft version, so their cards can say so. */
	private static final Set<String> NO_VERSION = ConcurrentHashMap.newKeySet();
	/** How long a saved page of search results is used before asking Modrinth again. */
	private static final Duration SEARCH_MAX_AGE = Duration.ofMinutes(30);
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
		return search(Type.RESOURCE_PACKS, query, sort, offset, List.of(), false, false);
	}

	/**
	 * One page of projects of that type that list this Minecraft version. Pages are saved on disk for half an hour, so
	 * reopening the screen is instant and the last results still show offline.
	 *
	 * @param categories only projects with all of these tags
	 * @param hideModded leaves out resource packs tagged as made for mods
	 * @param refresh    true asks Modrinth even if a saved page is recent
	 */
	public static Page search(
		final Type type, final String query, final Sort sort, final int offset, final Collection<String> categories, final boolean hideModded,
		final boolean refresh
	) throws IOException, InterruptedException {
		return parseSearch(ListCache.get(searchUrl(type, gameVersion(), query, sort, offset, categories, hideModded), SEARCH_MAX_AGE, refresh));
	}

	/** Whether the last page for this search was an old saved copy because Modrinth couldn't be reached. */
	public static boolean searchIsStale(final Type type, final String query, final Sort sort, final Collection<String> categories, final boolean hideModded) {
		return ListCache.wasStale(searchUrl(type, gameVersion(), query, sort, 0, categories, hideModded));
	}

	static String searchUrl(
		final Type type, final String gameVersion, final String query, final Sort sort, final int offset, final Collection<String> categories,
		final boolean hideModded
	) {
		return API + "/search?limit=" + PAGE_SIZE + "&offset=" + offset + "&index=" + sort.index
			+ "&facets=" + encode(facets(type, gameVersion, categories, hideModded)) + (query.isBlank() ? "" : "&query=" + encode(query.trim()));
	}

	/** Modrinth's search filter: every inner list must match, e.g. [["project_type:datapack"],["versions:26.3"],["categories:worldgen"]]. */
	public static String facets(final Type type, final String gameVersion, final Collection<String> categories, final boolean hideModded) {
		JsonArray facets = new JsonArray();
		List<String> all = new ArrayList<>();
		all.add("project_type:" + type.projectType);
		all.add("versions:" + gameVersion);
		categories.stream().sorted().forEach(category -> all.add("categories:" + category));
		if (hideModded && type == Type.RESOURCE_PACKS) {
			all.add("categories!=" + MODDED);
		}
		for (String facet : all) {
			JsonArray group = new JsonArray();
			group.add(facet);
			facets.add(group);
		}
		return facets.toString();
	}

	/** Reads a page of Modrinth's search reply. */
	public static Page parseSearch(final String body) {
		JsonObject result = JsonParser.parseString(body).getAsJsonObject();
		List<Project> projects = new ArrayList<>();
		for (JsonElement element : result.getAsJsonArray("hits")) {
			JsonObject hit = element.getAsJsonObject();
			List<String> categories = new ArrayList<>();
			if (hit.has("display_categories")) {
				hit.getAsJsonArray("display_categories").forEach(category -> categories.add(category.getAsString()));
			}
			// Loaders are listed with the tags; they say nothing about what a pack does
			categories.removeAll(LOADER_TAGS);
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

	private static final Set<String> LOADER_TAGS = Set.of(
		"minecraft", "datapack", "fabric", "forge", "neoforge", "quilt", "bukkit", "spigot", "paper", "purpur", "folia", "sponge", "bungeecord",
		"velocity", "waterfall", "liteloader", "modloader", "rift", "babric", "legacy-fabric", "nilloader", "ornithe", "bta-babric", "java-agent"
	);

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

	private static String key(final Type type, final String projectId) {
		return type.name() + ":" + projectId;
	}

	/** Requirements if already looked up this session, without starting a lookup. */
	public static @Nullable Requirements knownRequirements(final Type type, final Project project) {
		return REQUIREMENTS.get(key(type, project.id()));
	}

	/** Whether a lookup found the project has no file of this type for this Minecraft version. */
	public static boolean hasNoVersion(final Type type, final Project project) {
		return NO_VERSION.contains(key(type, project.id()));
	}

	/** Requirements if already looked up this session; otherwise starts looking them up and returns null. */
	public static @Nullable Requirements cachedRequirements(final Type type, final Project project) {
		String key = key(type, project.id());
		Requirements requirements = REQUIREMENTS.get(key);
		if (requirements == null && !NO_VERSION.contains(key) && LOADING_REQUIREMENTS.add(key)) {
			LOADER.execute(() -> {
				try {
					requirements(type, project);
				} catch (Exception e) {
					// Shown as unknown; looked up again before downloading
				} finally {
					LOADING_REQUIREMENTS.remove(key);
				}
			});
		}
		return requirements;
	}

	/** Thrown when a project has nothing to download for this Minecraft version. */
	public static final class NoVersionException extends IOException {
		public NoVersionException(final String message) {
			super(message);
		}
	}

	public static Requirements requirements(final Project project) throws IOException, InterruptedException {
		return requirements(Type.RESOURCE_PACKS, project);
	}

	/** Looks up the newest version for this game version and the projects it depends on. Cached for the session. */
	public static Requirements requirements(final Type type, final Project project) throws IOException, InterruptedException {
		String key = key(type, project.id());
		Requirements cached = REQUIREMENTS.get(key);
		if (cached != null) {
			return cached;
		}
		String url = API + "/project/" + project.id() + "/version?game_versions=" + encode("[\"" + gameVersion() + "\"]") + "&loaders=" + encode("[\"" + type.loader + "\"]");
		JsonArray versions = JsonParser.parseString(WebClient.getString(URI.create(url))).getAsJsonArray();
		if (versions.isEmpty()) {
			NO_VERSION.add(key);
			throw new NoVersionException(project.title() + " has no version for Minecraft " + gameVersion());
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
		REQUIREMENTS.put(key, requirements);
		return requirements;
	}

	/** A bare project for a dependency, enough to download it. */
	public static Project projectFor(final LinkedProject linked) {
		return new Project(linked.id(), linked.slug(), linked.title(), "", "", 0, List.of(), null, List.of());
	}

	/** Downloads the newest version of the project made for this Minecraft version. */
	public static Path download(final Project project, final Path folder) throws IOException, InterruptedException {
		return download(Type.RESOURCE_PACKS, project, folder);
	}

	public static Path download(final Type type, final Project project, final Path folder) throws IOException, InterruptedException {
		Path target = folder.resolve(project.fileName());
		Files.createDirectories(folder);
		downloadVersion(requirements(type, project).version(), target);
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

	/** What {@link #installMods} did: mods saved to the mods folder, and ones with no Fabric build for this version. */
	public record ModInstallResult(List<String> installed, List<LinkedProject> failed) {
	}

	public static Path modsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("mods");
	}

	/**
	 * Downloads the newest Fabric build for this Minecraft version of each mod into the mods folder, plus any mods those
	 * require that aren't installed. Release builds are preferred over betas. They load the next time the game starts.
	 *
	 * @param progress told the title of each mod as it starts downloading
	 */
	public static ModInstallResult installMods(final List<LinkedProject> mods, final java.util.function.Consumer<String> progress) throws InterruptedException {
		java.util.ArrayDeque<LinkedProject> queue = new java.util.ArrayDeque<>(mods);
		Set<String> seen = new java.util.HashSet<>();
		mods.forEach(mod -> seen.add(mod.id()));
		List<String> installed = new ArrayList<>();
		List<LinkedProject> failed = new ArrayList<>();
		Path folder = modsDir();
		while (!queue.isEmpty()) {
			LinkedProject mod = queue.poll();
			progress.accept(mod.title());
			try {
				String url = API + "/project/" + mod.id() + "/version?game_versions=" + encode("[\"" + gameVersion() + "\"]") + "&loaders=" + encode("[\"fabric\"]");
				JsonArray versions = JsonParser.parseString(WebClient.getString(URI.create(url))).getAsJsonArray();
				JsonObject chosen = null;
				for (JsonElement element : versions) {
					JsonObject candidate = element.getAsJsonObject();
					if (chosen == null) {
						chosen = candidate;
					}
					if ("release".equals(string(candidate, "version_type"))) {
						chosen = candidate;
						break;
					}
				}
				if (chosen == null) {
					failed.add(mod);
					continue;
				}
				Version version = parseVersion(chosen);
				if (version.fileName().isEmpty() || !version.fileName().endsWith(".jar")) {
					failed.add(mod);
					continue;
				}
				Files.createDirectories(folder);
				// The file name comes from Modrinth: keep only its last part so it can't point outside the mods folder
				Path target = folder.resolve(Path.of(version.fileName()).getFileName().toString());
				if (!Files.exists(target)) {
					downloadVersion(version, target);
				}
				installed.add(mod.title());

				List<String> required = version.dependencies().stream()
					.filter(dependency -> dependency.type().equals("required") && seen.add(dependency.projectId()))
					.map(Dependency::projectId)
					.toList();
				if (!required.isEmpty()) {
					JsonArray ids = new JsonArray();
					required.forEach(ids::add);
					for (JsonElement element : JsonParser.parseString(WebClient.getString(URI.create(API + "/projects?ids=" + encode(ids.toString())))).getAsJsonArray()) {
						JsonObject project = element.getAsJsonObject();
						LinkedProject dependency = new LinkedProject(project.get("id").getAsString(), project.get("slug").getAsString(), project.get("title").getAsString(), project.get("project_type").getAsString());
						if (dependency.projectType().equals("mod") && !dependency.isInstalled()) {
							queue.add(dependency);
						}
					}
				}
			} catch (IOException e) {
				failed.add(mod);
			}
		}
		return new ModInstallResult(List.copyOf(installed), List.copyOf(failed));
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

	public static Version parseVersion(final JsonObject version) {
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
