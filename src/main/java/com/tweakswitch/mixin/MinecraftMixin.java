package com.tweakswitch.mixin;

import com.tweakswitch.TweakSwitchPackSource;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	/** Adds our folder as one more source of the client's resource pack repository. */
	@ModifyArg(
		method = "<init>",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V")
	)
	private RepositorySource[] tweakswitch$addPackSource(final RepositorySource[] sources) {
		RepositorySource[] withOurs = Arrays.copyOf(sources, sources.length + 1);
		withOurs[sources.length] = new TweakSwitchPackSource();
		return withOurs;
	}
}
