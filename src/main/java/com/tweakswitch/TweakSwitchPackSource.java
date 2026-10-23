package com.tweakswitch;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
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
import net.minecraft.util.FileUtil;
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

	public static boolean isOurs(final Pack pack) {
		return pack.getId().startsWith(ID_PREFIX);
	}

	@Override
	public void loadPacks(final Consumer<Pack> result) {
		Path folder = TweakSwitch.packsDir();
		try {
			FileUtil.createDirectoriesSafe(folder);
			FolderRepositorySource.discoverPacks(folder, Minecraft.getInstance().directoryValidator(), (content, resources) -> {
				String fileName = content.getFileName().toString();
				String title = fileName.endsWith(".zip") ? fileName.substring(0, fileName.length() - 4) : fileName;
				PackLocationInfo location = new PackLocationInfo(ID_PREFIX + fileName, Component.literal(title), SOURCE, Optional.empty());
				Pack pack = Pack.readMetaAndCreate(location, resources, PackType.CLIENT_RESOURCES, SELECTION_CONFIG);
				if (pack != null) {
					result.accept(pack);
				}
			});
		} catch (IOException e) {
			LOGGER.warn("Failed to list packs in {}", folder, e);
		}
	}
}
