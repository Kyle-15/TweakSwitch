package com.tweakswitch;

import com.mojang.logging.LogUtils;
import com.tweakswitch.compat.Compat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Shown before downloading packs that need mods you don't have: TweakSwitch can download the mods into the mods folder
 * (they load after a restart), or you can open each mod's page, skip the mods, or go back.
 */
public class ModsNeededScreen extends PackListScreen {
	private static final Logger LOGGER = LogUtils.getLogger();

	private final Map<String, List<Modrinth.LinkedProject>> missing;
	private final Runnable onDownload;
	/** "Downloading Sodium..." while mods download; null otherwise. */
	private @Nullable String installing;

	/** @param missing pack name → the mods it needs that aren't installed */
	public ModsNeededScreen(final Screen lastScreen, final Map<String, List<Modrinth.LinkedProject>> missing, final Runnable onDownload) {
		super(lastScreen, Component.translatable("tweakswitch.mods.title"));
		this.missing = missing;
		this.onDownload = onDownload;
	}

	/** The title in amber, as a warning. */
	@Override
	protected Component styledTitle() {
		return this.title.copy().withStyle(style -> style.withBold(true).withColor(0xFFFFC857));
	}

	@Override
	protected boolean hasPreviews() {
		return false;
	}

	@Override
	protected boolean hasSearch() {
		return false;
	}

	@Override
	protected void fillList(final PackList list) {
		if (this.installing != null) {
			list.addText(Component.literal(this.installing).withColor(this.theme().text()));
			return;
		}
		list.addText(Component.translatable("tweakswitch.mods.explain").withStyle(ChatFormatting.GRAY));
		this.missing.forEach((pack, mods) -> {
			list.addText(Component.translatable("tweakswitch.mods.needs", pack).withColor(this.theme().text()));
			list.addGrid(linkButtons(this, mods), 2);
		});
	}

	/** A button per mod that opens its Fabric versions page on Modrinth. */
	static List<AbstractWidget> linkButtons(final PackListScreen screen, final List<Modrinth.LinkedProject> mods) {
		return mods.stream()
			.<AbstractWidget>map(mod -> screen.button(Component.literal(mod.title() + " ↗"), 150, button ->
					ConfirmLinkScreen.confirmLinkNow(screen, mod.downloadPage(), true))
				.withTooltip(Component.literal(mod.downloadPage().toString())))
			.toList();
	}

	@Override
	protected void addFooter() {
		this.addFooterButtons(
			this.primaryButton(Component.translatable("tweakswitch.mods.install"), 110, button -> this.installMods())
				.withTooltip(Component.translatable("tweakswitch.mods.install.tooltip")),
			this.button(Component.translatable("tweakswitch.mods.downloadAnyway"), 90, button -> {
				Compat.setScreen(this.minecraft, this.lastScreen);
				this.onDownload.run();
			}).withTooltip(Component.translatable("tweakswitch.mods.downloadAnyway.tooltip")),
			this.button(CommonComponents.GUI_BACK, 70, button -> this.onClose())
		);
	}

	/** Downloads every missing mod (each only once, even if several packs need it), then shows what happened. */
	private void installMods() {
		if (this.installing != null) {
			return;
		}
		Map<String, Modrinth.LinkedProject> unique = new LinkedHashMap<>();
		this.missing.values().forEach(mods -> mods.forEach(mod -> unique.putIfAbsent(mod.id(), mod)));
		List<Modrinth.LinkedProject> mods = new ArrayList<>(unique.values());
		this.installing = Component.translatable("tweakswitch.mods.installing", "").getString();
		this.refreshList();

		Thread thread = new Thread(() -> {
			Modrinth.ModInstallResult result;
			try {
				result = Modrinth.installMods(mods, title -> {
					String text = Component.translatable("tweakswitch.mods.installing", title).getString();
					this.minecraft.execute(() -> {
						this.installing = text;
						this.refreshIfShown();
					});
				});
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (RuntimeException e) {
				LOGGER.warn("Failed to download mods", e);
				result = new Modrinth.ModInstallResult(List.of(), mods);
			}
			Modrinth.ModInstallResult done = result;
			this.minecraft.execute(() -> {
				this.installing = null;
				Compat.setScreen(this.minecraft, new ModsInstalledScreen(this.lastScreen, done, this.onDownload));
			});
		}, "TweakSwitch mod downloads");
		thread.setDaemon(true);
		thread.start();
	}

	private void refreshIfShown() {
		if (Compat.screen(this.minecraft) == this) {
			this.refreshList();
		}
	}
}
