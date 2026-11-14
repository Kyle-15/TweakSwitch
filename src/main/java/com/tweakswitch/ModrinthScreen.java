package com.tweakswitch;

import com.tweakswitch.compat.Compat;
import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import com.tweakswitch.ui.ProjectCard;
import com.tweakswitch.ui.SectionHeader;
import com.tweakswitch.ui.Ui;
import com.tweakswitch.ui.Theme;
import com.tweakswitch.ui.ThemedButton;
import java.util.function.BooleanSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Searches Modrinth for packs made for this Minecraft version and downloads the picked ones. This screen does resource
 * packs, which become switches; {@link ModrinthDataPacksScreen} reuses it for data packs that go into a new world.
 */
public class ModrinthScreen extends DownloadScreen<Modrinth.Project> {
	private static final Logger LOGGER = LogUtils.getLogger();
	/** Wait this many ticks after the last keystroke before searching. */
	private static final int SEARCH_DELAY_TICKS = 8;

	/** One search and its results. Kept per type for the session, so reopening the screen shows the last results. */
	private static final class State {
		final List<Modrinth.Project> results = new ArrayList<>();
		int totalHits;
		String query = "";
		Modrinth.Sort sort = Modrinth.Sort.DOWNLOADS;
		boolean loading;
		@Nullable Component loadError;
		boolean searched;
		/** Bumped for every new search so answers to older ones are thrown away. */
		int generation;
		/** Tags every result must have. */
		final Set<String> categories = new java.util.TreeSet<>();
		/** Leaves out resource packs tagged as made for mods. */
		boolean hideModded;
		boolean filtersOpen;
	}

	private static final Map<Modrinth.Type, State> STATES = new java.util.EnumMap<>(Modrinth.Type.class);

	protected final Modrinth.Type type;
	private final State state;
	private int ticksSinceTyping;

	public ModrinthScreen(final @Nullable Screen lastScreen) {
		this(lastScreen, Modrinth.Type.RESOURCE_PACKS, Component.translatable("tweakswitch.mr.title", Modrinth.gameVersion()));
	}

	protected ModrinthScreen(final @Nullable Screen lastScreen, final Modrinth.Type type, final Component title) {
		super(lastScreen, title);
		this.type = type;
		this.state = STATES.computeIfAbsent(type, key -> new State());
	}

	/** The search that last ran for a type, for the screenshot test. */
	public static String queryForTest(final Modrinth.Type type) {
		State state = STATES.get(type);
		return state == null ? "" : state.query;
	}

	/** Whether a search for that type is still running (or hasn't started), for the screenshot test. */
	public static boolean loadingForTest(final Modrinth.Type type) {
		State state = STATES.get(type);
		return state == null || state.loading || !state.searched;
	}

	/** How many projects the last search for a type found in all, for the screenshot test. */
	public static int hitsForTest(final Modrinth.Type type) {
		State state = STATES.get(type);
		return state == null ? 0 : state.totalHits;
	}

	/** Switches a filter on or off and searches again, as clicking its button does; for the screenshot test. */
	public void toggleFilterForTest(final String category) {
		this.toggleCategory(category);
	}

	@Override
	protected void init() {
		super.init();
		// The search box starts with the last search instead of empty
		if (this.searchText().isEmpty() && !this.state.query.isEmpty()) {
			this.setSearchText(this.state.query);
		}
		// First visit, or the last search failed: search now
		if (!this.state.searched || (this.state.loadError != null && !this.state.loading)) {
			this.search(false, false);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.searchText().equals(this.state.query) && ++this.ticksSinceTyping >= SEARCH_DELAY_TICKS) {
			this.search(false, false);
		}
	}

	/** Also runs on every keystroke in the search box, which restarts the wait before searching. */
	@Override
	protected void refreshList() {
		this.ticksSinceTyping = 0;
		super.refreshList();
	}

	/**
	 * Starts a search for the text in the search box, or loads the next page of the current one.
	 *
	 * @param refresh true asks Modrinth even if a saved page is recent, for Retry
	 */
	private void search(final boolean nextPage, final boolean refresh) {
		State state = this.state;
		state.searched = true;
		if (!nextPage) {
			state.query = this.searchText();
			state.results.clear();
			state.totalHits = 0;
		}
		state.loading = true;
		state.loadError = null;
		int thisSearch = ++state.generation;
		String text = state.query;
		Modrinth.Sort order = state.sort;
		int offset = state.results.size();
		List<String> categories = List.copyOf(state.categories);
		boolean hideModded = state.hideModded;
		Modrinth.Type searchType = this.type;
		this.refreshList();

		Thread thread = new Thread(() -> {
			Modrinth.Page page = null;
			Component error = null;
			try {
				page = Modrinth.search(searchType, text, order, offset, categories, hideModded, refresh);
			} catch (Exception e) {
				LOGGER.warn("Modrinth search failed", e);
				error = WebErrors.describe(e);
			}
			Modrinth.Page found = page;
			Component failure = error;
			this.minecraft.execute(() -> {
				if (thisSearch != state.generation) {
					return;
				}
				state.loading = false;
				state.loadError = failure;
				if (found != null) {
					state.results.addAll(found.projects());
					state.totalHits = found.totalHits();
				}
				this.refreshIfShown();
			});
		}, "TweakSwitch Modrinth search");
		thread.setDaemon(true);
		thread.start();
	}

	private void toggleCategory(final String category) {
		if (!this.state.categories.remove(category)) {
			this.state.categories.add(category);
		}
		this.search(false, false);
	}

	@Override
	protected void addFooter() {
		List<AbstractWidget> buttons = new ArrayList<>();
		// Cycles through the sort orders, like Modrinth's "Sort by" dropdown
		buttons.add(new ThemedButton(this.theme(), ThemedButton.Kind.SECONDARY, Component.empty(), 120, button -> {
			this.state.sort = Modrinth.Sort.values()[(this.state.sort.ordinal() + 1) % Modrinth.Sort.values().length];
			this.search(false, false);
		}) {
			@Override
			public Component getMessage() {
				return Component.translatable("tweakswitch.mr.sortBy", Component.translatable("tweakswitch.mr.sort." + ModrinthScreen.this.state.sort.name().toLowerCase(Locale.ROOT)));
			}
		});
		buttons.add(this.createDownloadButton());
		if (this.switchesOnDownloads()) {
			buttons.add(this.createSwitchOnToggle());
		}
		buttons.add(this.button(Component.translatable("tweakswitch.website"), 70, ConfirmLinkScreen.confirmLink(this, this.type.site)));
		buttons.add(this.button(CommonComponents.GUI_DONE, 70, button -> this.onClose()));
		this.addFooterButtons(buttons.toArray(AbstractWidget[]::new));
	}

	/** "▶ Filters (2)": opens to a grid of tag buttons, like the filter list down the side of Modrinth's search page. */
	private void addFilters(final PackList list) {
		State state = this.state;
		int active = state.categories.size() + (state.hideModded ? 1 : 0);
		String count = active > 0 ? Component.translatable("tweakswitch.mr.filters.active", active).getString() : "";
		list.addRow(new SectionHeader(this.theme(), Component.translatable("tweakswitch.mr.filters").getString(), count, state.filtersOpen, null, () -> {
			state.filtersOpen = !state.filtersOpen;
			this.queueRefresh();
		}));
		if (!state.filtersOpen) {
			return;
		}
		List<AbstractWidget> buttons = new ArrayList<>();
		if (this.type == Modrinth.Type.RESOURCE_PACKS) {
			buttons.add(new ThemedButton(this.theme(), ThemedButton.Kind.SECONDARY, Component.translatable("tweakswitch.mr.filters.noMods"), 80, button -> {
				state.hideModded = !state.hideModded;
				this.search(false, false);
			}).highlightWhen(() -> state.hideModded).withTooltip(Component.translatable("tweakswitch.mr.filters.noMods.tooltip")));
		}
		for (String category : this.type.categories()) {
			buttons.add(new ThemedButton(this.theme(), ThemedButton.Kind.SECONDARY, Component.literal(tagName(category)), 80, button -> this.toggleCategory(category))
				.highlightWhen(() -> state.categories.contains(category)));
		}
		if (active > 0) {
			buttons.add(new ThemedButton(this.theme(), ThemedButton.Kind.SECONDARY, Component.translatable("tweakswitch.mr.filters.clear"), 80, button -> {
				state.categories.clear();
				state.hideModded = false;
				this.search(false, false);
			}));
		}
		// Three to a row, so the longest tag names fit
		list.addTiles(buttons, 96, Ui.button());
	}

	/** "vanilla-like" → "Vanilla-like", "game-mechanics" → "Game mechanics"; resolutions like "16x" stay as they are. */
	static String tagName(final String category) {
		if (Character.isDigit(category.charAt(0))) {
			return category;
		}
		if (category.equals("gui")) {
			return "GUI";
		}
		String words = category.equals("vanilla-like") ? category : category.replace('-', ' ');
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	@Override
	protected void fillPackList(final PackList list) {
		State state = this.state;
		this.addFilters(list);
		if (state.results.isEmpty()) {
			if (state.loadError != null && !state.loading) {
				this.addError(list, state.loadError, () -> this.search(false, true));
			} else if (state.loading || !state.searched) {
				list.addText(Component.translatable("tweakswitch.loading").withStyle(ChatFormatting.GRAY));
			} else {
				list.addText(Component.translatable("tweakswitch.noMatches").withStyle(ChatFormatting.GRAY));
			}
			return;
		}

		if (Modrinth.searchIsStale(this.type, state.query, state.sort, state.categories, state.hideModded)) {
			this.addStaleNote(list, () -> this.search(false, true));
		}
		list.addText(Component.translatable("tweakswitch.mr.results", String.format(Locale.ROOT, "%,d", state.totalHits)).withStyle(ChatFormatting.GRAY));
		Set<String> downloaded = this.downloadedFiles();
		for (Modrinth.Project project : state.results) {
			list.addRow(this.createRow(project, downloaded));
		}

		if (state.loading) {
			list.addText(Component.translatable("tweakswitch.loading").withStyle(ChatFormatting.GRAY));
		} else if (state.loadError != null) {
			this.addError(list, state.loadError, () -> this.search(true, true));
		} else if (state.results.size() < state.totalHits) {
			list.addCentered(this.button(Component.translatable("tweakswitch.mr.more"), 150, button -> this.search(true, false)));
		}
	}

	@Override
	protected Theme theme() {
		return Theme.MODRINTH;
	}

	/** Their green on "Modrinth", then the page name, like the site's header. */
	@Override
	protected Component styledTitle() {
		return Component.literal("Modrinth ").withStyle(style -> style.withBold(true).withColor(Theme.MODRINTH.accent()))
			.append(Component.literal(Modrinth.gameVersion()).withColor(Theme.MODRINTH.text()));
	}

	/** A card like Modrinth's search results: icon, title and author, summary, tags and download count. */
	@Override
	protected AbstractWidget createPackWidget(final Modrinth.Project project, final boolean done, final BooleanSupplier picked, final Runnable toggle) {
		// Only known once the pack has been hovered; looking up every result would cost two API calls each
		Modrinth.Requirements requirements = Modrinth.knownRequirements(this.type, project);
		boolean needsMods = requirements != null && !this.missingMods(requirements).isEmpty();
		return new ProjectCard(this.theme(), project.title(), project.author(), project.description(), compact(project.downloads()),
			project.categories().stream().map(ModrinthScreen::tagName).toList(),
			() -> project.iconUrl() != null ? RemoteImages.getIcon(project.iconUrl()).textureAt(0) : null,
			picked, done, needsMods, toggle).unavailable(Modrinth.hasNoVersion(this.type, project));
	}

	/** Back on the TweakSwitch screen, show the tab the new packs landed on. */
	@Override
	protected void afterDownload(final List<Modrinth.Project> succeeded) {
		if (!succeeded.isEmpty()) {
			Settings settings = Settings.get();
			settings.tab = TweakSwitchScreen.Tab.MODRINTH.name();
			settings.save();
		}
	}

	@Override
	protected String id(final Modrinth.Project project) {
		return project.id();
	}

	@Override
	protected String displayName(final Modrinth.Project project) {
		return project.title();
	}

	@Override
	protected String fileName(final Modrinth.Project project) {
		return project.fileName();
	}

	@Override
	protected PackIndex.Entry indexEntry(final Modrinth.Project project) {
		return new PackIndex.Entry("Modrinth", PackIndex.MODRINTH, project.id());
	}

	@Override
	protected List<String> imageUrls(final Modrinth.Project project) {
		return Modrinth.images(project);
	}

	@Override
	protected Component details(final Modrinth.Project project) {
		MutableComponent text = Component.translatable("tweakswitch.mr.byline", project.author(), compact(project.downloads())).withStyle(ChatFormatting.GRAY);
		text.append(Component.literal("\n" + project.description()).withStyle(ChatFormatting.WHITE));
		if (!project.categories().isEmpty()) {
			text.append(Component.literal("\n" + String.join(", ", project.categories())).withStyle(ChatFormatting.DARK_AQUA));
		}

		// Looked up in the background the first time the pack is hovered
		Modrinth.Requirements requirements = Modrinth.cachedRequirements(this.type, project);
		if (Modrinth.hasNoVersion(this.type, project)) {
			text.append(Component.literal("\n").append(Component.translatable("tweakswitch.mr.noVersion", Modrinth.gameVersion()).withStyle(ChatFormatting.RED)));
		}
		if (requirements != null) {
			List<Modrinth.LinkedProject> mods = requirements.required().stream().filter(this::isMod).toList();
			if (!mods.isEmpty()) {
				String names = String.join(", ", mods.stream().map(mod -> mod.title() + (mod.isInstalled() ? " ✔" : "")).toList());
				boolean allInstalled = this.missingMods(requirements).isEmpty();
				text.append(Component.literal("\n").append(Component.translatable(allInstalled ? "tweakswitch.mr.needsModsInstalled" : "tweakswitch.mr.needsMods", names)
					.withStyle(allInstalled ? ChatFormatting.GREEN : ChatFormatting.GOLD)));
			}
			List<Modrinth.LinkedProject> packs = requirements.required().stream().filter(linked -> !this.isMod(linked)).toList();
			if (!packs.isEmpty()) {
				String names = String.join(", ", packs.stream().map(Modrinth.LinkedProject::title).toList());
				text.append(Component.literal("\n").append(Component.translatable(this.type == Modrinth.Type.DATA_PACKS ? "tweakswitch.mr.needsOther" : "tweakswitch.mr.needsPacks", names)
					.withStyle(ChatFormatting.YELLOW)));
			}
			if (!requirements.optional().isEmpty()) {
				String names = String.join(", ", requirements.optional().stream().limit(4).map(Modrinth.LinkedProject::title).toList());
				String more = requirements.optional().size() > 4 ? ", …" : "";
				text.append(Component.literal("\n").append(Component.translatable("tweakswitch.mr.worksWith", names + more).withStyle(ChatFormatting.GRAY)));
			}
		}
		return text;
	}

	/**
	 * Whether a pack's requirement is a mod. For resource packs, anything that isn't a resource pack is. Data packs can
	 * also need other data packs, which Modrinth files under "mod"; those are told apart by having a data pack file.
	 */
	private boolean isMod(final Modrinth.LinkedProject linked) {
		if (linked.isResourcePack()) {
			return false;
		}
		// For data packs it's only known once the download check has asked Modrinth
		return this.type == Modrinth.Type.RESOURCE_PACKS || this.modDependencies.contains(linked.id());
	}

	private List<Modrinth.LinkedProject> missingMods(final Modrinth.Requirements requirements) {
		return requirements.required().stream().filter(linked -> this.isMod(linked) && !linked.isInstalled()).toList();
	}

	/** Projects a data pack requires that turned out to have no data pack file for this version: they're mods. */
	private final Set<String> modDependencies = java.util.concurrent.ConcurrentHashMap.newKeySet();
	/** Resource packs a data pack needs: these go to the TweakSwitch folder as switches, not into the world. */
	protected final Set<String> resourcePackDependencies = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * Looks up what each pack needs: required packs are added to the batch, and if any mods are missing the player sees
	 * {@link ModsNeededScreen} with links before anything downloads.
	 */
	@Override
	protected void beforeDownload(final List<Modrinth.Project> queue, final Consumer<List<Modrinth.Project>> proceed) {
		this.setProgress(Component.translatable("tweakswitch.mr.checking").getString());
		Set<String> files = this.downloadedFiles();
		Set<String> resourcePackFiles = fileNames(TweakSwitch.packsDir());
		Thread thread = new Thread(() -> {
			List<Modrinth.Project> batch = new ArrayList<>(queue);
			Map<String, List<Modrinth.LinkedProject>> missing = new LinkedHashMap<>();
			Set<String> queued = new HashSet<>();
			queue.forEach(project -> queued.add(project.id()));
			// Index loop: required packs added here are checked too
			for (int i = 0; i < batch.size(); i++) {
				Modrinth.Project project = batch.get(i);
				if (this.resourcePackDependencies.contains(project.id())) {
					continue;
				}
				try {
					Modrinth.Requirements requirements = Modrinth.requirements(this.type, project);
					for (Modrinth.LinkedProject linked : requirements.required()) {
						Modrinth.Project dependency = Modrinth.projectFor(linked);
						if (linked.isResourcePack()) {
							boolean have = (this.type == Modrinth.Type.RESOURCE_PACKS ? files : resourcePackFiles).contains(dependency.fileName());
							if (queued.add(linked.id()) && !have) {
								if (this.type == Modrinth.Type.DATA_PACKS) {
									this.resourcePackDependencies.add(linked.id());
								}
								batch.add(dependency);
							}
						} else if (this.type == Modrinth.Type.DATA_PACKS && !linked.isInstalled()) {
							// Another data pack comes along too; anything else is a mod, shown on the warning screen
							if (!this.hasDataPack(dependency)) {
								this.modDependencies.add(linked.id());
							} else if (queued.add(linked.id()) && !files.contains(dependency.fileName())) {
								batch.add(dependency);
							}
						}
					}
					if (!this.missingMods(requirements).isEmpty()) {
						missing.put(project.title(), this.missingMods(requirements));
					}
				} catch (Exception e) {
					LOGGER.warn("Couldn't check what {} needs", project.title(), e);
				}
			}

			this.minecraft.execute(() -> {
				this.setProgress(null);
				if (missing.isEmpty()) {
					proceed.accept(batch);
				} else {
					Compat.setScreen(this.minecraft, new ModsNeededScreen(this, missing, () -> proceed.accept(batch)));
				}
			});
		}, "TweakSwitch requirement check");
		thread.setDaemon(true);
		thread.start();
	}

	/** Whether a required project has a data pack file for this Minecraft version. Asks Modrinth; blocks. */
	private boolean hasDataPack(final Modrinth.Project project) {
		try {
			Modrinth.requirements(Modrinth.Type.DATA_PACKS, project);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static Set<String> fileNames(final Path folder) {
		Set<String> names = new HashSet<>();
		try (java.util.stream.Stream<Path> files = java.nio.file.Files.list(folder)) {
			files.forEach(file -> names.add(file.getFileName().toString()));
		} catch (Exception e) {
			// No folder yet: nothing in it
		}
		return names;
	}

	/** 51568648 → "51.6M". */
	private static String compact(final long count) {
		if (count >= 1_000_000) {
			return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
		}
		if (count >= 1_000) {
			return String.format(Locale.ROOT, "%.1fK", count / 1_000.0);
		}
		return Long.toString(count);
	}

	@Override
	protected void download(final Modrinth.Project project, final Path folder) throws Exception {
		if (this.type == Modrinth.Type.DATA_PACKS && this.resourcePackDependencies.contains(project.id())) {
			// A resource pack a data pack needs: saved with your other switches, and switched on next time they show
			Modrinth.download(Modrinth.Type.RESOURCE_PACKS, project, TweakSwitch.packsDir());
			PackIndex.put(project.fileName(), new PackIndex.Entry("Modrinth", PackIndex.MODRINTH, project.id()).withTitle(project.title()));
			TweakSwitchScreen.switchOnWhenShown(List.of(project.fileName()));
			return;
		}
		Modrinth.download(this.type, project, folder);
	}
}
