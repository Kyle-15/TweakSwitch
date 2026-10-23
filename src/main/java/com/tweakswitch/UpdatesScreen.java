package com.tweakswitch;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Checks every pack in the TweakSwitch folder for a newer version and updates the picked ones. Modrinth packs are found by
 * file hash, so this works for zips dropped in by hand too. Vanilla Tweaks has no versions, so those can only be fetched again.
 */
public class UpdatesScreen extends DownloadScreen<UpdatesScreen.Item> {
	private static final Logger LOGGER = LogUtils.getLogger();
	/** Categories the player flipped from their default; "Updates available" and Vanilla Tweaks start open. */
	private static final Set<String> TOGGLED = new HashSet<>();

	public enum Status {
		UPDATE, VANILLA_TWEAKS, UP_TO_DATE, NO_VERSION, UNKNOWN
	}

	/**
	 * @param latest   the newest Modrinth version for this game version, if any
	 * @param vanillaTweaks the matching Vanilla Tweaks pack, for fetching it again
	 */
	public record Item(
		String fileName, String title, Status status, @Nullable String installedVersion, Modrinth.@Nullable Version latest,
		VanillaTweaks.@Nullable VtPack vanillaTweaks, PackIndex.@Nullable Entry entry
	) {
	}

	private @Nullable List<Item> items;
	private @Nullable String checkError;
	private boolean checking;

	public UpdatesScreen(final @Nullable Screen lastScreen) {
		super(lastScreen, Component.translatable("tweakswitch.updates.title"));
	}

	@Override
	protected void init() {
		super.init();
		if (this.items == null && !this.checking) {
			this.check();
		}
	}

	private void check() {
		this.checking = true;
		this.checkError = null;
		this.items = null;
		this.selected.clear();
		this.refreshList();
		Thread thread = new Thread(() -> {
			List<Item> found = null;
			String error = null;
			try {
				found = findUpdates();
			} catch (Exception e) {
				LOGGER.warn("Update check failed", e);
				error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			}
			List<Item> result = found;
			String failure = error;
			this.minecraft.execute(() -> {
				this.checking = false;
				this.items = result;
				this.checkError = failure;
				if (result != null) {
					// Updates start picked; Vanilla Tweaks re-downloads are up to the player
					result.stream().filter(item -> item.status() == Status.UPDATE).forEach(item -> this.selected.put(item.fileName(), item));
				}
				this.refreshIfShown();
			});
		}, "TweakSwitch update check");
		thread.setDaemon(true);
		thread.start();
	}

	/** Hashes every pack zip, asks Modrinth about all of them at once, and matches the rest to Vanilla Tweaks. */
	private static List<Item> findUpdates() throws Exception {
		Map<String, PackIndex.Entry> index = PackIndex.load();
		Map<String, Path> byHash = new HashMap<>();
		List<Path> zips;
		try (Stream<Path> files = Files.list(TweakSwitch.packsDir())) {
			zips = files.filter(file -> Files.isRegularFile(file) && file.getFileName().toString().endsWith(".zip")).sorted().toList();
		} catch (java.nio.file.NoSuchFileException e) {
			return List.of();
		}
		for (Path zip : zips) {
			byHash.put(Modrinth.sha1(zip), zip);
		}

		Modrinth.UpdateCheck modrinth = byHash.isEmpty() ? new Modrinth.UpdateCheck(Map.of(), Map.of()) : Modrinth.checkUpdates(byHash.keySet());
		List<VanillaTweaks.VtPack> vanillaTweaks = null;

		List<Item> items = new ArrayList<>();
		for (Map.Entry<String, Path> file : byHash.entrySet()) {
			String hash = file.getKey();
			String fileName = file.getValue().getFileName().toString();
			String title = displayTitle(fileName);
			PackIndex.Entry entry = index.get(fileName);
			Modrinth.Version installed = modrinth.current().get(hash);
			Modrinth.Version latest = modrinth.latest().get(hash);

			if (installed != null) {
				Status status = latest == null ? Status.NO_VERSION : latest.sha1().equalsIgnoreCase(hash) ? Status.UP_TO_DATE : Status.UPDATE;
				items.add(new Item(fileName, title, status, installed.number(), latest, null, entry));
				continue;
			}

			boolean fromVanillaTweaks = entry != null ? PackIndex.VANILLA_TWEAKS.equals(entry.source()) : fileName.startsWith(VanillaTweaks.FILE_PREFIX);
			if (fromVanillaTweaks) {
				if (vanillaTweaks == null) {
					vanillaTweaks = VanillaTweaks.fetchPacks();
				}
				VanillaTweaks.VtPack match = findVanillaTweaksPack(vanillaTweaks, entry, fileName);
				items.add(new Item(fileName, title, match != null ? Status.VANILLA_TWEAKS : Status.UNKNOWN, null, null, match, entry));
				continue;
			}

			items.add(new Item(fileName, title, Status.UNKNOWN, null, null, null, entry));
		}
		items.sort((a, b) -> a.title().compareToIgnoreCase(b.title()));
		return items;
	}

	/** By the id saved when it was downloaded, or else by the name in its file name. */
	private static VanillaTweaks.@Nullable VtPack findVanillaTweaksPack(final Collection<VanillaTweaks.VtPack> packs, final PackIndex.@Nullable Entry entry, final String fileName) {
		for (VanillaTweaks.VtPack pack : packs) {
			if (entry != null && pack.id().equals(entry.id()) || pack.fileName().equals(fileName)) {
				return pack;
			}
		}
		return null;
	}

	private static String displayTitle(final String fileName) {
		String title = fileName.endsWith(".zip") ? fileName.substring(0, fileName.length() - 4) : fileName;
		for (String prefix : new String[] {VanillaTweaks.FILE_PREFIX, Modrinth.FILE_PREFIX}) {
			if (title.startsWith(prefix)) {
				return title.substring(prefix.length());
			}
		}
		return title;
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(6));
		footer.addChild(Button.builder(Component.translatable("tweakswitch.updates.checkAgain"), button -> this.check()).width(100).build());
		footer.addChild(this.createDownloadButton());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(80).build());
	}

	@Override
	protected String downloadLabelKey() {
		return "tweakswitch.updates.update";
	}

	@Override
	protected void fillPackList(final OptionsList list) {
		if (this.items == null) {
			list.addHeader(this.checkError != null
				? Component.translatable("tweakswitch.loadFailed", this.checkError).withStyle(ChatFormatting.RED)
				: Component.translatable("tweakswitch.updates.checking").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (this.items.isEmpty()) {
			list.addHeader(Component.translatable("tweakswitch.updates.none").withStyle(ChatFormatting.GRAY));
			return;
		}

		Set<String> files = Set.of();
		this.addGroup(list, files, Status.UPDATE, true);
		this.addGroup(list, files, Status.VANILLA_TWEAKS, true);
		this.addGroup(list, files, Status.UP_TO_DATE, false);
		this.addGroup(list, files, Status.NO_VERSION, false);
		this.addGroup(list, files, Status.UNKNOWN, false);
	}

	private void addGroup(final OptionsList list, final Set<String> files, final Status status, final boolean openByDefault) {
		List<Item> group = this.items == null ? List.of() : this.items.stream()
			.filter(item -> item.status() == status && this.matches(item.title()))
			.toList();
		if (group.isEmpty()) {
			return;
		}
		String name = Component.translatable("tweakswitch.updates.group." + status.name().toLowerCase(Locale.ROOT)).getString();
		this.addCategory(list, TOGGLED, openByDefault, name, Integer.toString(group.size()), () -> group.forEach(item -> list.addBig(this.createRow(item, files))));
	}

	@Override
	protected boolean isDone(final Item item, final Set<String> files) {
		return item.status() != Status.UPDATE && item.status() != Status.VANILLA_TWEAKS;
	}

	@Override
	protected String id(final Item item) {
		return item.fileName();
	}

	@Override
	protected String displayName(final Item item) {
		if (item.status() == Status.UPDATE && item.latest() != null) {
			return item.title() + "  " + item.installedVersion() + " → " + item.latest().number();
		}
		return item.installedVersion() != null ? item.title() + "  " + item.installedVersion() : item.title();
	}

	@Override
	protected String fileName(final Item item) {
		return item.fileName();
	}

	@Override
	protected PackIndex.Entry indexEntry(final Item item) {
		if (item.vanillaTweaks() != null) {
			return new PackIndex.Entry(item.vanillaTweaks().category(), PackIndex.VANILLA_TWEAKS, item.vanillaTweaks().id());
		}
		String category = item.entry() != null ? item.entry().category() : "Modrinth";
		return new PackIndex.Entry(category, PackIndex.MODRINTH, item.latest() != null ? item.latest().projectId() : null);
	}

	@Override
	protected @Nullable String imageUrl(final Item item) {
		return null;
	}

	@Override
	protected Component details(final Item item) {
		return switch (item.status()) {
			case UPDATE -> Component.translatable("tweakswitch.updates.detail.update", item.installedVersion(), item.latest() != null ? item.latest().number() : "?");
			case VANILLA_TWEAKS -> Component.translatable("tweakswitch.updates.detail.vanillaTweaks");
			case UP_TO_DATE -> Component.translatable("tweakswitch.updates.detail.upToDate", item.installedVersion());
			case NO_VERSION -> Component.translatable("tweakswitch.updates.detail.noVersion", item.installedVersion(), Modrinth.gameVersion());
			case UNKNOWN -> Component.translatable("tweakswitch.updates.detail.unknown");
		};
	}

	@Override
	protected void download(final Item item, final Path folder) throws Exception {
		if (item.status() == Status.UPDATE && item.latest() != null) {
			Modrinth.downloadVersion(item.latest(), folder.resolve(item.fileName()));
		} else if (item.vanillaTweaks() != null) {
			VanillaTweaks.download(item.vanillaTweaks(), folder);
		}
	}

	/** Packs that are switched on need a resource reload to use their new files; then check again to refresh the list. */
	@Override
	protected void afterDownload(final List<Item> succeeded) {
		Collection<String> on = this.minecraft.getResourcePackRepository().getSelectedIds();
		if (succeeded.stream().anyMatch(item -> on.contains(TweakSwitchPackSource.idFor(item.fileName())))) {
			this.minecraft.reloadResourcePacks();
		}
		this.check();
	}
}
