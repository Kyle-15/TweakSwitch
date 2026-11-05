package com.tweakswitch.mixin;

import com.mojang.datafixers.util.Pair;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.WorldDataConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The parts of world creation its own Data Packs button uses, so downloaded data packs go through the same steps. */
@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenAccessor {
	@Accessor("uiState")
	WorldCreationUiState tweakswitch$uiState();

	/** The new world's temporary data pack folder and its pack repository; null if the folder couldn't be made. */
	@Invoker("getDataPackSelectionSettings")
	Pair<Path, PackRepository> tweakswitch$dataPackSettings(WorldDataConfiguration dataConfiguration);

	/** Applies the repository's selected packs to the new world, as closing the Data Packs screen does. */
	@Invoker("tryApplyNewDataPacks")
	void tweakswitch$applyDataPacks(PackRepository packRepository, boolean isDataPackScreen, Consumer<WorldDataConfiguration> onAbort);
}
