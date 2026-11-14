package com.tweakswitch;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import org.slf4j.Logger;

/**
 * Adds every pack in {@link TweakSwitch#packsDir()} to the client's pack list. The ids get their own prefix so they
 * never collide with packs in the vanilla resourcepacks folder, and vanilla's options.txt remembers which are on.
 */
public class TweakSwitchPackSource implements RepositorySource {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String ID_PREFIX = TweakSwitch.MOD_ID + "/";
	private static final PackSelectionConfig SELECTION_CONFIG = new PackSelectionConfig(false, Pack.Position.TOP, false);
	private static final PackSource SOURCE = PackSource.create(
		description -> Component.translatable("pack.nameAndSource", description, Component.translatable("tweakswitch.source")).withStyle(ChatFormatting.GRAY),
		false
	);

	/** Zip files in the folder that couldn't be read as a pack the last time the folder was looked at. */
	private static volatile List<String> broken = List.of();

	/** File names of zips in the TweakSwitch folder that are damaged or aren't resource packs. */
	public static List<String> brokenFiles() {
		return broken;
	}

	public static boolean isOurs(final Pack pack) {
		return pack.getId().startsWith(ID_PREFIX);
	}

	/** The pack id for a file in the packs folder. */
	public static String idFor(final String fileName) {
		return ID_PREFIX + fileName;
	}

	/** The pack's file name in the packs folder, e.g. "VT - Steve Zombies.zip". */
	public static String fileName(final Pack pack) {
		return pack.getId().substring(ID_PREFIX.length());
	}

	/** "VT - Steve Zombies.zip" → "Steve Zombies"; downloads are grouped by source already, so the prefix is noise. */
	private static String titleFromFileName(final String fileName) {
		String title = fileName.endsWith(".zip") ? fileName.substring(0, fileName.length() - 4) : fileName;
		for (String prefix : new String[] {VanillaTweaks.FILE_PREFIX, Modrinth.FILE_PREFIX}) {
			if (title.startsWith(prefix)) {
				return title.substring(prefix.length());
			}
		}
		return title;
	}

	@Override
	public void loadPacks(final Consumer<Pack> result) {
		Path folder = TweakSwitch.packsDir();
		try {
			Files.createDirectories(folder);
			Map<String, PackIndex.Entry> index = PackIndex.load();
			Set<String> readable = new HashSet<>();
			FolderRepositorySource.discoverPacks(folder, Minecraft.getInstance().directoryValidator(), (content, resources) -> {
				String fileName = content.getFileName().toString();
				PackIndex.Entry entry = index.get(fileName);
				String title = entry != null && entry.title() != null ? entry.title() : titleFromFileName(fileName);
				PackLocationInfo location = new PackLocationInfo(ID_PREFIX + fileName, Component.literal(title), SOURCE, Optional.empty());
				Pack pack = Pack.readMetaAndCreate(location, resources, PackType.CLIENT_RESOURCES, SELECTION_CONFIG);
				if (pack != null) {
					result.accept(pack);
					readable.add(fileName);
				}
			});
			// A half-downloaded or damaged zip gives no pack; it's remembered so the screen can show it instead of hiding it
			try (Stream<Path> files = Files.list(folder)) {
				broken = files.filter(Files::isRegularFile)
					.map(file -> file.getFileName().toString())
					.filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".zip") && !readable.contains(name))
					.sorted()
					.toList();
			}
		} catch (IOException e) {
			LOGGER.warn("Failed to list packs in {}", folder, e);
		}
	}
}
