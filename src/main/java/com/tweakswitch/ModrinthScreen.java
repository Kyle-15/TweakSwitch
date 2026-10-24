package com.tweakswitch;

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

/** Searches Modrinth for resource packs made for this Minecraft version and downloads the picked ones as switches. */
public class ModrinthScreen extends DownloadScreen<Modrinth.Project> {
	private static final Logger LOGGER = LogUtils.getLogger();
	/** Wait this many ticks after the last keystroke before searching. */
	private static final int SEARCH_DELAY_TICKS = 8;

	// Kept for the session so reopening the screen shows the last results
	private static final List<Modrinth.Project> results = new ArrayList<>();
	private static int totalHits;
	private static String query = "";
	private static Modrinth.Sort sort = Modrinth.Sort.DOWNLOADS;
	private static boolean loading;
	private static @Nullable String loadError;
	private static boolean searched;
	/** Bumped for every new search so answers to older ones are thrown away. */
	private static int generation;

	private int ticksSinceTyping;

	public ModrinthScreen(final @Nullable Screen lastScreen) {
		super(lastScreen, Component.translatable("tweakswitch.mr.title", Modrinth.gameVersion()));
	}

	@Override
	protected void init() {
		super.init();
		// The search box starts with the last search instead of empty
		if (this.searchText().isEmpty() && !query.isEmpty()) {
			this.setSearchText(query);
		}
		// First visit, or the last search failed: search now
		if (!searched || (loadError != null && !loading)) {
			this.search(false);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.searchText().equals(query) && ++this.ticksSinceTyping >= SEARCH_DELAY_TICKS) {
			this.search(false);
		}
	}

	/** Also runs on every keystroke in the search box, which restarts the wait before searching. */
	@Override
	protected void refreshList() {
		this.ticksSinceTyping = 0;
		super.refreshList();
	}

	/** Starts a search for the text in the search box, or loads the next page of the current one. */
	private void search(final boolean nextPage) {
		searched = true;
		if (!nextPage) {
			query = this.searchText();
			results.clear();
			totalHits = 0;
		}
		loading = true;
		loadError = null;
		int thisSearch = ++generation;
		String text = query;
		Modrinth.Sort order = sort;
		int offset = results.size();
		this.refreshList();

		Thread thread = new Thread(() -> {
			Modrinth.Page page = null;
			String error = null;
			try {
				page = Modrinth.search(text, order, offset);
			} catch (Exception e) {
				LOGGER.warn("Modrinth search failed", e);
				error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			}
			Modrinth.Page found = page;
			String failure = error;
			this.minecraft.execute(() -> {
				if (thisSearch != generation) {
					return;
				}
				loading = false;
				loadError = failure;
				if (found != null) {
					results.addAll(found.projects());
					totalHits = found.totalHits();
				}
				this.refreshIfShown();
			});
		}, "TweakSwitch Modrinth search");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	protected void addFooter() {
		this.addFooterButtons(
			// Cycles through the sort orders, like Modrinth's "Sort by" dropdown
			new ThemedButton(this.theme(), ThemedButton.Kind.SECONDARY, Component.empty(), 120, button -> {
				sort = Modrinth.Sort.values()[(sort.ordinal() + 1) % Modrinth.Sort.values().length];
				this.search(false);
			}) {
				@Override
				public Component getMessage() {
					return Component.translatable("tweakswitch.mr.sortBy", Component.translatable("tweakswitch.mr.sort." + sort.name().toLowerCase(Locale.ROOT)));
				}
			},
			this.createDownloadButton(),
			this.button(Component.translatable("tweakswitch.website"), 70, ConfirmLinkScreen.confirmLink(this, Modrinth.SITE)),
			this.button(CommonComponents.GUI_DONE, 70, button -> this.onClose())
		);
	}

	@Override
	protected void fillPackList(final PackList list) {
		if (results.isEmpty()) {
			Component message;
			if (loadError != null) {
				message = Component.translatable("tweakswitch.loadFailed", loadError).withStyle(ChatFormatting.RED);
			} else if (loading || !searched) {
				message = Component.translatable("tweakswitch.loading").withStyle(ChatFormatting.GRAY);
			} else {
				message = Component.translatable("tweakswitch.noMatches").withStyle(ChatFormatting.GRAY);
			}
			list.addText(message);
			return;
		}

		list.addText(Component.translatable("tweakswitch.mr.results", String.format(Locale.ROOT, "%,d", totalHits)).withStyle(ChatFormatting.GRAY));
		Set<String> downloaded = this.downloadedFiles();
		for (Modrinth.Project project : results) {
			list.addRow(this.createRow(project, downloaded), ProjectCard.HEIGHT);
		}

		if (loading) {
			list.addText(Component.translatable("tweakswitch.loading").withStyle(ChatFormatting.GRAY));
		} else if (loadError != null) {
			list.addText(Component.translatable("tweakswitch.loadFailed", loadError).withStyle(ChatFormatting.RED));
		} else if (results.size() < totalHits) {
			list.addRow(this.button(Component.translatable("tweakswitch.mr.more"), 150, button -> this.search(true)));
		}
	}

	@Override
	protected Theme theme() {
		return Theme.MODRINTH;
	}

	/** Their green on "Modrinth", then the page name, like the site's header. */
	@Override
	protected Component styledTitle() {
		return Component.literal("Modrinth").withStyle(style -> style.withBold(true).withColor(Theme.MODRINTH.accent()))
			.append(Component.literal("  Resource Packs for " + Modrinth.gameVersion()).withColor(Theme.MODRINTH.text()));
	}

	/** A card like Modrinth's search results: icon, title and author, summary, tags and download count. */
	@Override
	protected AbstractWidget createPackWidget(final Modrinth.Project project, final boolean done, final BooleanSupplier picked, final Runnable toggle) {
		// Only known once the pack has been hovered; looking up every result would cost two API calls each
		Modrinth.Requirements requirements = Modrinth.knownRequirements(project);
		boolean needsMods = requirements != null && !requirements.missingMods().isEmpty();
		return new ProjectCard(this.theme(), project.title(), project.author(), project.description(), compact(project.downloads()),
			project.categories().stream().filter(tag -> !tag.equals("minecraft")).toList(),
			() -> project.iconUrl() != null ? RemoteImages.getIcon(project.iconUrl()).textureAt(0) : null,
			picked, done, needsMods, toggle);
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
		return project.images();
	}

	@Override
	protected Component details(final Modrinth.Project project) {
		MutableComponent text = Component.translatable("tweakswitch.mr.byline", project.author(), compact(project.downloads())).withStyle(ChatFormatting.GRAY);
		text.append(Component.literal("\n" + project.description()).withStyle(ChatFormatting.WHITE));
		if (!project.categories().isEmpty()) {
			text.append(Component.literal("\n" + String.join(", ", project.categories())).withStyle(ChatFormatting.DARK_AQUA));
		}

		// Looked up in the background the first time the pack is hovered
		Modrinth.Requirements requirements = Modrinth.cachedRequirements(project);
		if (requirements != null) {
			List<Modrinth.LinkedProject> mods = requirements.required().stream().filter(linked -> !linked.isResourcePack()).toList();
			if (!mods.isEmpty()) {
				String names = String.join(", ", mods.stream().map(mod -> mod.title() + (mod.isInstalled() ? " ✔" : "")).toList());
				boolean allInstalled = requirements.missingMods().isEmpty();
				text.append(Component.literal("\n").append(Component.translatable(allInstalled ? "tweakswitch.mr.needsModsInstalled" : "tweakswitch.mr.needsMods", names)
					.withStyle(allInstalled ? ChatFormatting.GREEN : ChatFormatting.GOLD)));
			}
			if (!requirements.requiredPacks().isEmpty()) {
				String names = String.join(", ", requirements.requiredPacks().stream().map(Modrinth.LinkedProject::title).toList());
				text.append(Component.literal("\n").append(Component.translatable("tweakswitch.mr.needsPacks", names).withStyle(ChatFormatting.YELLOW)));
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
	 * Looks up what each pack needs: required resource packs are added to the batch, and if any mods are missing the
	 * player sees {@link ModsNeededScreen} with links before anything downloads.
	 */
	@Override
	protected void beforeDownload(final List<Modrinth.Project> queue, final Consumer<List<Modrinth.Project>> proceed) {
		this.setProgress(Component.translatable("tweakswitch.mr.checking").getString());
		Set<String> files = this.downloadedFiles();
		Thread thread = new Thread(() -> {
			List<Modrinth.Project> batch = new ArrayList<>(queue);
			Map<String, List<Modrinth.LinkedProject>> missing = new LinkedHashMap<>();
			Set<String> queued = new HashSet<>();
			queue.forEach(project -> queued.add(project.id()));
			// Index loop: required packs added here are checked too
			for (int i = 0; i < batch.size(); i++) {
				Modrinth.Project project = batch.get(i);
				try {
					Modrinth.Requirements requirements = Modrinth.requirements(project);
					if (!requirements.missingMods().isEmpty()) {
						missing.put(project.title(), requirements.missingMods());
					}
					for (Modrinth.LinkedProject pack : requirements.requiredPacks()) {
						Modrinth.Project dependency = Modrinth.projectFor(pack);
						if (queued.add(pack.id()) && !files.contains(dependency.fileName())) {
							batch.add(dependency);
						}
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
					this.minecraft.gui.setScreen(new ModsNeededScreen(this, missing, () -> proceed.accept(batch)));
				}
			});
		}, "TweakSwitch requirement check");
		thread.setDaemon(true);
		thread.start();
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
		Modrinth.download(project, folder);
	}
}
