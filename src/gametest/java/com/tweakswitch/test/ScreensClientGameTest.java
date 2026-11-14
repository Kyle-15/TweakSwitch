package com.tweakswitch.test;

import com.tweakswitch.compat.Compat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tweakswitch.DownloadScreen;
import com.tweakswitch.Modrinth;
import com.tweakswitch.ModsNeededScreen;
import com.tweakswitch.TweakSwitch;
import com.tweakswitch.UpdatesScreen;
import com.tweakswitch.DataPacksScreen;
import com.tweakswitch.ImageCache;
import com.tweakswitch.ModsInstalledScreen;
import com.tweakswitch.ListCache;
import com.tweakswitch.ModrinthDataPacksScreen;
import com.tweakswitch.PackOrderScreen;
import com.tweakswitch.PackShare;
import com.tweakswitch.Presets;
import com.tweakswitch.PresetsScreen;
import com.tweakswitch.SettingsScreen;
import com.tweakswitch.StartupCheck;
import com.tweakswitch.TweakSwitchPackSource;
import com.tweakswitch.WebErrors;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import com.tweakswitch.VanillaTweaks;
import com.tweakswitch.WebClient;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.mojang.blaze3d.platform.InputConstants;
import com.tweakswitch.ModrinthScreen;
import com.tweakswitch.PackContents;
import com.tweakswitch.Settings;
import com.tweakswitch.PackListScreen;
import com.tweakswitch.TweakSwitchScreen;
import com.tweakswitch.VanillaTweaksScreen;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.server.packs.repository.Pack;

/**
 * Walks through the TweakSwitch screens, downloading two real packs from Vanilla Tweaks, and saves screenshots to
 * build/run/clientGameTest/screenshots. Needs internet.
 */
@SuppressWarnings("unchecked")
public class ScreensClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);

		// Mod Menu's config button for TweakSwitch should open the switch screen
		context.runOnClient(minecraft -> {
			ModMenuApi api = FabricLoader.getInstance().getEntrypoints("modmenu", ModMenuApi.class).stream()
				.filter(entry -> entry.getClass().getName().startsWith("com.tweakswitch"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("TweakSwitch has no Mod Menu entrypoint"));
			if (!(api.getModConfigScreenFactory().create(Compat.screen(minecraft)) instanceof SettingsScreen)) {
				throw new AssertionError("Mod Menu config button doesn't open TweakSwitch's settings");
			}
		});
		context.setScreen(() -> ModMenuApi.createModsScreen(new TitleScreen()));
		context.waitTicks(10);
		context.getInput().typeChars("TweakSwitch");
		context.waitTicks(10);
		context.takeScreenshot("tweakswitch-modmenu");
		context.setScreen(TitleScreen::new);

		context.setScreen(() -> new TweakSwitchScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-switches-empty");

		// A wide window: the header row should sit in the middle, not stuck to the left
		context.getInput().resizeWindow(1600, 600);
		context.runOnClient(minecraft -> {
			minecraft.options.guiScale().set(2);
			Compat.resizeGui(minecraft);
		});
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-wide-header");
		int[] header = context.computeOnClient(minecraft -> {
			EditBox search = (EditBox) field(PackListScreen.class, Compat.screen(minecraft), "search");
			return new int[] {search.getX(), search.getRight(), Compat.screen(minecraft).width};
		});
		if (header[0] < header[2] / 4) {
			throw new AssertionError("Header looks stuck to the left: search starts at " + header[0] + " of " + header[2]);
		}
		context.getInput().resizeWindow(854, 480);
		context.runOnClient(minecraft -> {
			minecraft.options.guiScale().set(0);
			Compat.resizeGui(minecraft);
		});
		context.waitTicks(3);

		// A small window: text wraps, buttons shrink, Vanilla Tweaks drops to one column
		// 640x480 at GUI scale 2 is 320 GUI pixels wide, the narrowest Minecraft allows
		context.getInput().resizeWindow(640, 480);
		context.runOnClient(minecraft -> {
			minecraft.options.guiScale().set(2);
			Compat.resizeGui(minecraft);
		});
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-small-switches");
		context.setScreen(() -> new VanillaTweaksScreen(new TweakSwitchScreen(null)));
		context.waitFor(minecraft -> VanillaTweaksScreen.loadedForTest(VanillaTweaks.Kind.RESOURCE_PACKS), 600);
		context.runOnClient(minecraft -> {
			Set<String> expanded = staticField("EXPANDED");
			expanded.add("Aesthetic");
			refresh(minecraft);
		});
		// Pack icons load from the site in the background
		context.waitTicks(60);
		context.takeScreenshot("tweakswitch-small-picker");
		context.setScreen(() -> new ModrinthScreen(new TweakSwitchScreen(null)));
		context.waitFor(minecraft -> !rows(minecraft).isEmpty(), 600);
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-small-modrinth");
		context.runOnClient(minecraft -> ((Set<String>) staticField("EXPANDED")).clear());
		context.getInput().resizeWindow(854, 480);
		context.runOnClient(minecraft -> {
			minecraft.options.guiScale().set(0);
			Compat.resizeGui(minecraft);
		});
		context.setScreen(() -> new TweakSwitchScreen(null));
		context.waitTicks(5);

		context.clickScreenButton("tweakswitch.getVanillaTweaks");
		context.waitForScreen(VanillaTweaksScreen.class);
		context.waitFor(minecraft -> VanillaTweaksScreen.loadedForTest(VanillaTweaks.Kind.RESOURCE_PACKS), 600);
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-picker-collapsed");

		context.runOnClient(minecraft -> {
			Set<String> expanded = staticField("EXPANDED");
			expanded.add("Aesthetic");
			refresh(minecraft);
		});
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-picker-expanded");

		// Hover a pack until its preview shows up. The test cursor doesn't land exactly where it aims, which is fine here.
		List<AbstractWidget> firstTwo = context.computeOnClient(minecraft -> rows(minecraft).keySet().stream()
			.filter(widget -> widget.getY() > 40 && widget.getY() < 100)
			.sorted(Comparator.comparingInt(AbstractWidget::getY).thenComparingInt(AbstractWidget::getX))
			.limit(2)
			.toList());
		// Previews start on. Clicking the header's "⇧ Preview" button turns them off, which brings up the "Tap Shift" line
		if (!Settings.get().previews) {
			throw new AssertionError("Previews should start on");
		}
		int[] previewButton = context.computeOnClient(minecraft -> ((int[]) field(PackListScreen.class, Compat.screen(minecraft), "previewButtonBounds")).clone());
		if (previewButton[2] <= 0) {
			throw new AssertionError("The Preview button isn't showing in the header");
		}
		clickAt(context, previewButton[0] + previewButton[2] / 2.0, previewButton[1] + previewButton[3] / 2.0);
		context.waitTicks(3);
		if (Settings.get().previews) {
			throw new AssertionError("Clicking the Preview button should turn previews off");
		}
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(3);
		context.takeScreenshot("tweakswitch-shift-hint");
		int[] shiftHint = context.computeOnClient(minecraft -> ((int[]) field(PackListScreen.class, Compat.screen(minecraft), "shiftHintBounds")).clone());
		if (shiftHint[2] <= 0) {
			throw new AssertionError("The \"Tap Shift\" line should show while previews are off");
		}
		// A Shift tap on its own turns them back on
		context.getInput().pressKey(InputConstants.KEY_LSHIFT);
		context.waitTick();
		if (!Settings.get().previews) {
			throw new AssertionError("A Shift tap should turn previews on");
		}
		moveTo(context, firstTwo.get(0));
		context.waitTicks(80);
		context.takeScreenshot("tweakswitch-picker-preview");
		// A moment later the animated preview should be on a different frame
		context.waitTicks(7);
		context.takeScreenshot("tweakswitch-picker-preview-later");
		// Icons and the preview just shown were saved to the disk cache
		try (java.util.stream.Stream<Path> cached = Files.list(ImageCache.dir())) {
			if (cached.count() < 2) {
				throw new AssertionError("Pictures weren't saved to the image cache");
			}
		} catch (IOException e) {
			throw new AssertionError("No image cache folder", e);
		}

		// Pick the first two packs (pressed directly, since the cursor is imprecise)
		for (AbstractWidget row : firstTwo) {
			context.runOnClient(minecraft -> ((AbstractButton) row).onPress(null));
			context.waitTicks(3);
		}
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(3);

		context.takeScreenshot("tweakswitch-picker-picked");

		context.runOnClient(minecraft -> ((Button) field(DownloadScreen.class, Compat.screen(minecraft), "downloadButton")).onPress(null));
		context.waitFor(minecraft -> field(DownloadScreen.class, null, "progress") == null, 1200);
		context.waitTicks(10);
		context.takeScreenshot("tweakswitch-picker-downloaded");

		// A pack you already have still previews when hovered, even though its tile can't be picked again
		AbstractWidget doneSpot = firstTwo.get(0);
		moveTo(context, doneSpot);
		context.waitTicks(60);
		AbstractWidget hoveredDone = context.computeOnClient(minecraft -> {
			try {
				Method method = PackListScreen.class.getDeclaredMethod("hoveredListWidget", double.class, double.class);
				method.setAccessible(true);
				return (AbstractWidget) method.invoke(Compat.screen(minecraft), doneSpot.getX() + doneSpot.getWidth() / 2.0, doneSpot.getY() + doneSpot.getHeight() / 2.0);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		if (hoveredDone == null || hoveredDone.active) {
			throw new AssertionError("Hovering a downloaded pack's tile should find it for the preview");
		}
		context.takeScreenshot("tweakswitch-picker-preview-downloaded");
		context.getInput().setCursorPos(0, 0);
		List<String> downloadedVt = context.computeOnClient(minecraft -> List.copyOf(fileNames(TweakSwitch.packsDir()).stream().filter(name -> name.startsWith("VT - ")).toList()));

		context.clickScreenButton("gui.done");
		context.waitForScreen(TweakSwitchScreen.class);
		// With Switch On (the default) the new packs come back switched on, waiting for Done
		Map<String, Boolean> switchedOn = context.computeOnClient(minecraft -> Map.copyOf((Map<String, Boolean>) field(TweakSwitchScreen.class, Compat.screen(minecraft), "pending")));
		for (String file : downloadedVt) {
			if (!Boolean.TRUE.equals(switchedOn.get("tweakswitch/" + file))) {
				throw new AssertionError(file + " should be switched on after downloading: " + switchedOn);
			}
		}
		if (TweakSwitchScreen.Tab.fromSettingsForTest() != TweakSwitchScreen.Tab.VANILLA_TWEAKS) {
			throw new AssertionError("After a Vanilla Tweaks download the Vanilla Tweaks tab should show");
		}
		context.runOnClient(minecraft -> {
			Settings.get().groupBy = TweakSwitchScreen.GroupBy.SOURCE.name();
			refresh(minecraft);
		});
		context.waitTicks(5);
		context.runOnClient(minecraft -> Compat.toasts(minecraft).clear());
		context.waitTicks(2);
		context.takeScreenshot("tweakswitch-switches-downloaded");

		// Modrinth: search results load, a hovered pack shows its picture, and a picked pack downloads as a switch
		context.clickScreenButton("tweakswitch.getModrinth");
		context.waitForScreen(ModrinthScreen.class);
		context.waitFor(minecraft -> !rows(minecraft).isEmpty(), 600);
		context.waitTicks(5);
		context.runOnClient(minecraft -> Compat.toasts(minecraft).clear());
		context.waitTicks(2);
		context.takeScreenshot("tweakswitch-modrinth-results");
		AbstractWidget firstResult = context.computeOnClient(minecraft -> rows(minecraft).keySet().stream()
			.filter(widget -> widget.getY() > 40 && widget.getY() < 100)
			.min(Comparator.comparingInt(AbstractWidget::getY))
			.orElseThrow());
		moveTo(context, firstResult);
		context.waitTicks(100);
		context.takeScreenshot("tweakswitch-modrinth-preview");
		context.runOnClient(minecraft -> ((AbstractButton) firstResult).onPress(null));

		// Typing in the search box runs a new search once typing stops
		context.getInput().setCursorPos(0, 0);
		context.getInput().typeChars("sky");
		context.waitFor(minecraft -> "sky".equals(ModrinthScreen.queryForTest(Modrinth.Type.RESOURCE_PACKS)) && !ModrinthScreen.loadingForTest(Modrinth.Type.RESOURCE_PACKS), 600);
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-modrinth-search");
		context.runOnClient(minecraft -> ((Button) field(DownloadScreen.class, Compat.screen(minecraft), "downloadButton")).onPress(null));
		context.waitFor(minecraft -> field(DownloadScreen.class, null, "progress") == null, 1200);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(10);
		context.runOnClient(minecraft -> Compat.toasts(minecraft).clear());
		context.waitTicks(2);
		context.takeScreenshot("tweakswitch-modrinth-downloaded");
		// A pack needing a mod brings up the warning with links; a pack needing another pack pulls it in too.
		// Fresh Animations is deleted first so Extensions has to bring it back.
		Path packs = TweakSwitch.packsDir();
		deleteQuietly(packs.resolve("MR - Fresh Animations.zip"));
		// Hyper Punchy (needs a mod) only exists for some game versions, e.g. not 1.21.9 or 1.21.10
		boolean modNeeded = !versions("hyper-punchy").isEmpty();
		if (modNeeded) {
			pickModrinthResult(context, "Hyper Punchy");
		}
		pickModrinthResult(context, "Fresh Animations: Extensions");
		context.runOnClient(minecraft -> ((Button) field(DownloadScreen.class, Compat.screen(minecraft), "downloadButton")).onPress(null));
		if (modNeeded) {
			context.waitForScreen(ModsNeededScreen.class);
			context.waitTicks(5);
			context.runOnClient(minecraft -> Compat.toasts(minecraft).clear());
			context.waitTicks(2);
			context.takeScreenshot("tweakswitch-mods-needed");
			// Download Mods saves the needed mod's Fabric build to the mods folder, then says to restart
			Set<String> modsBefore = fileNames(Modrinth.modsDir());
			context.clickScreenButton("tweakswitch.mods.install");
			context.waitFor(minecraft -> Compat.screen(minecraft) instanceof ModsInstalledScreen, 1200);
			context.waitTicks(5);
			context.runOnClient(minecraft -> Compat.toasts(minecraft).clear());
			context.waitTicks(2);
			context.takeScreenshot("tweakswitch-mods-installed");
			Set<String> newMods = fileNames(Modrinth.modsDir());
			newMods.removeAll(modsBefore);
			if (newMods.stream().noneMatch(name -> name.endsWith(".jar"))) {
				throw new AssertionError("Download Mods didn't save a mod to the mods folder");
			}
			// Removed again so later test runs don't load it
			newMods.forEach(name -> deleteQuietly(Modrinth.modsDir().resolve(name)));
			context.clickScreenButton("tweakswitch.mods.continue");
			context.waitForScreen(ModrinthScreen.class);
		}
		context.waitFor(minecraft -> field(DownloadScreen.class, null, "progress") == null, 1200);
		// Fresh Animations comes along only if this game version's Extensions declares it as required (26.3's does, 26.2's doesn't)
		List<String> expected = new java.util.ArrayList<>(List.of("MR - Fresh Animations_ Extensions.zip"));
		if (modNeeded) {
			expected.add("MR - Hyper Punchy.zip");
		}
		if (requires("fresh-animations-extensions", "50dA9Sha")) {
			expected.add("MR - Fresh Animations.zip");
		}
		for (String file : expected) {
			if (!Files.exists(packs.resolve(file))) {
				throw new AssertionError("Expected " + file + " to be downloaded");
			}
		}

		context.clickScreenButton("gui.done");
		context.waitForScreen(TweakSwitchScreen.class);
		if (TweakSwitchScreen.Tab.fromSettingsForTest() != TweakSwitchScreen.Tab.MODRINTH) {
			throw new AssertionError("After a Modrinth download the Modrinth tab should show");
		}
		context.waitTicks(5);
		context.runOnClient(minecraft -> Compat.toasts(minecraft).clear());
		context.waitTicks(2);
		context.takeScreenshot("tweakswitch-switches-with-modrinth");

		// A pack with no useful name or description: only particle textures inside
		Path flamePack = packs.resolve("untitled-pack.zip");
		writeParticlePack(flamePack);
		context.setScreen(() -> new TweakSwitchScreen(null));
		context.waitFor(minecraft -> PackContents.cached(flamePack) != null, 200);
		if (PackContents.cached(flamePack).primary() != PackContents.Kind.PARTICLES) {
			throw new AssertionError("untitled-pack should be recognised as a particles pack");
		}
		context.runOnClient(minecraft -> {
			Settings.get().tab = TweakSwitchScreen.Tab.IMPORTED.name();
			Settings.get().groupBy = TweakSwitchScreen.GroupBy.CHANGES.name();
			refresh(minecraft);
		});
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-switches-by-changes");

		// Searching a texture's file name finds the pack that contains it
		context.runOnClient(minecraft -> {
			try {
				Method method = PackListScreen.class.getDeclaredMethod("setSearchText", String.class);
				method.setAccessible(true);
				method.invoke(Compat.screen(minecraft), "flame");
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		context.waitTicks(3);
		Map<AbstractWidget, ?> shownRows = context.computeOnClient(minecraft -> (Map<AbstractWidget, ?>) field(TweakSwitchScreen.class, Compat.screen(minecraft), "switchRows"));
		if (shownRows.size() != 1) {
			throw new AssertionError("Searching \"flame\" should show just untitled-pack, showed " + shownRows.size());
		}
		moveTo(context, shownRows.keySet().iterator().next());
		context.waitTicks(30);
		context.takeScreenshot("tweakswitch-switch-preview-textures");
		context.runOnClient(minecraft -> {
			Settings.get().groupBy = TweakSwitchScreen.GroupBy.SOURCE.name();
			try {
				Method method = PackListScreen.class.getDeclaredMethod("setSearchText", String.class);
				method.setAccessible(true);
				method.invoke(Compat.screen(minecraft), "");
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(3);
		// The switch shows the pack's real name, even though ":" can't go in the file name
		boolean realName = context.computeOnClient(minecraft -> minecraft.getResourcePackRepository().getAvailablePacks().stream()
			.anyMatch(pack -> pack.getTitle().getString().equals("Fresh Animations: Extensions")));
		if (!realName) {
			throw new AssertionError("Switch for Fresh Animations: Extensions doesn't show its real name");
		}

		// Packs split from one big pack, like ones made with an AI tool: each holds only a particle definition, they all
		// share one logo, and the textures live in a separate base pack (or are the game's own)
		writeCodexStylePacks(packs);
		context.setScreen(() -> new TweakSwitchScreen(null));
		Path drippingLava = packs.resolve("particles-dripping-lava.zip");
		Path composter = packs.resolve("particles-composter.zip");
		context.waitFor(minecraft -> PackContents.cached(drippingLava) != null && PackContents.cached(composter) != null, 200);
		if (!"Dripping".equals(PackContents.cached(drippingLava).subject())) {
			throw new AssertionError("Expected subject Dripping, got " + PackContents.cached(drippingLava).subject());
		}
		context.runOnClient(minecraft -> {
			Settings.get().tab = TweakSwitchScreen.Tab.IMPORTED.name();
			Settings.get().groupBy = TweakSwitchScreen.GroupBy.CHANGES.name();
			refresh(minecraft);
		});
		// Textures the definitions point to: from the base pack, and the game's own for composter (minecraft:glint)
		context.waitFor(minecraft -> !PackContents.textures(drippingLava).isEmpty() && !PackContents.textures(composter).isEmpty(), 200);
		// Search "particles" so the particle groups come into view
		context.runOnClient(minecraft -> {
			try {
				Method method = PackListScreen.class.getDeclaredMethod("setSearchText", String.class);
				method.setAccessible(true);
				method.invoke(Compat.screen(minecraft), "particles");
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-split-packs-grouped");
		// Narrow to the dripping packs so dripping-lava is on screen to hover
		context.runOnClient(minecraft -> {
			try {
				Method method = PackListScreen.class.getDeclaredMethod("setSearchText", String.class);
				method.setAccessible(true);
				method.invoke(Compat.screen(minecraft), "dripping");
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		context.waitTicks(5);
		AbstractWidget lavaRow = context.computeOnClient(minecraft -> ((Map<AbstractWidget, Pack>) field(TweakSwitchScreen.class, Compat.screen(minecraft), "switchRows"))
			.entrySet().stream().filter(entry -> entry.getValue().getTitle().getString().equals("particles-dripping-lava")).findFirst().orElseThrow().getKey());
		moveTo(context, lavaRow);
		context.waitTicks(30);
		context.takeScreenshot("tweakswitch-split-pack-preview");
		context.getInput().setCursorPos(0, 0);
		context.runOnClient(minecraft -> Settings.get().groupBy = TweakSwitchScreen.GroupBy.SOURCE.name());
		context.waitTicks(3);

		// Updates: swap Fresh Animations for an older version, then update it back
		String newSha1 = freshAnimationsSha1(0);
		String oldSha1 = installOlderVersion(packs.resolve("MR - Fresh Animations.zip"));
		if (!sha1(packs.resolve("MR - Fresh Animations.zip")).equals(oldSha1)) {
			throw new AssertionError("Older Fresh Animations didn't install cleanly");
		}
		// The start-up reminder counts the same update
		try {
			if (StartupCheck.countPackUpdates() < 1) {
				throw new AssertionError("The start-up check should find the Fresh Animations update");
			}
		} catch (AssertionError e) {
			throw e;
		} catch (Exception e) {
			throw new AssertionError("The start-up pack check failed", e);
		}
		context.clickScreenButton("tweakswitch.getUpdates");
		context.waitForScreen(UpdatesScreen.class);
		context.waitFor(minecraft -> field(UpdatesScreen.class, Compat.screen(minecraft), "items") != null, 1200);
		context.waitTicks(5);
		context.runOnClient(minecraft -> Compat.toasts(minecraft).clear());
		context.waitTicks(2);
		context.takeScreenshot("tweakswitch-updates");
		context.runOnClient(minecraft -> ((Button) field(DownloadScreen.class, Compat.screen(minecraft), "downloadButton")).onPress(null));
		context.waitFor(minecraft -> field(DownloadScreen.class, null, "progress") == null
			&& field(UpdatesScreen.class, Compat.screen(minecraft), "items") != null, 1200);
		context.waitTicks(5);
		context.runOnClient(minecraft -> Compat.toasts(minecraft).clear());
		context.waitTicks(2);
		context.takeScreenshot("tweakswitch-updates-done");
		if (!sha1(packs.resolve("MR - Fresh Animations.zip")).equals(newSha1)) {
			throw new AssertionError("Fresh Animations wasn't updated to the newest version");
		}
		context.clickScreenButton("gui.done");
		context.waitForScreen(TweakSwitchScreen.class);

		// Switch one on and apply it: the pack should be selected and resources reloaded
		// Whichever Vanilla Tweaks pack was downloaded: their categories list different packs on different game versions
		String packId;
		try (java.util.stream.Stream<Path> files = Files.list(TweakSwitch.packsDir())) {
			packId = "tweakswitch/" + files.map(file -> file.getFileName().toString()).filter(name -> name.startsWith("VT - ")).sorted().findFirst().orElseThrow();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		context.runOnClient(minecraft -> {
			Map<String, Boolean> pending = (Map<String, Boolean>) field(TweakSwitchScreen.class, Compat.screen(minecraft), "pending");
			pending.put(packId, true);
		});
		context.clickScreenButton("gui.done");
		context.waitFor(minecraft -> !Compat.loading(minecraft) && Compat.screen(minecraft) instanceof TitleScreen, 1200);
		boolean applied = context.computeOnClient(minecraft -> minecraft.getResourcePackRepository().getSelectedIds().contains(packId)
			&& minecraft.options.resourcePacks.contains(packId));
		if (!applied) {
			throw new AssertionError("Switching on " + packId + " didn't select it");
		}

		testDataPacks(context);
		testSwitchFeatures(context);
	}

	/** Sort, pin, the right-click menu, delete, damaged files, clash warnings, order, presets, export/import, settings. */
	private static void testSwitchFeatures(final ClientGameTestContext context) {
		Path packs = TweakSwitch.packsDir();
		context.runOnClient(minecraft -> {
			Settings.get().tab = TweakSwitchScreen.Tab.IMPORTED.name();
			Settings.get().groupBy = TweakSwitchScreen.GroupBy.CHANGES.name();
			Compat.toasts(minecraft).clear();
		});
		context.setScreen(() -> new TweakSwitchScreen(null));
		context.waitTicks(5);

		// Saved lists: the Vanilla Tweaks list and Modrinth searches are on disk from earlier
		if (fileNames(ListCache.dir()).size() < 2) {
			throw new AssertionError("Pack lists weren't saved to the list cache");
		}
		// Vanilla Tweaks downloads keep the site's credit in their description
		boolean credited = context.computeOnClient(minecraft -> minecraft.getResourcePackRepository().getAvailablePacks().stream()
			.filter(pack -> pack.getId().startsWith("tweakswitch/VT - "))
			.allMatch(pack -> pack.getDescription().getString().contains("vanillatweaks.net")));
		if (!credited) {
			throw new AssertionError("A Vanilla Tweaks pack's description lost its credit line");
		}

		// Pin a pack: it moves to a "★ Pinned" group at the top; sort "On First" puts switched-on packs first
		context.runOnClient(minecraft -> {
			TweakSwitchScreen screen = (TweakSwitchScreen) Compat.screen(minecraft);
			Pack pack = ourPack(minecraft, "untitled-pack.zip");
			call(TweakSwitchScreen.class, screen, "togglePin", new Class<?>[] {Pack.class}, pack);
			Settings.get().sortBy = TweakSwitchScreen.SortBy.ON_FIRST.name();
			((Map<String, Boolean>) field(TweakSwitchScreen.class, screen, "pending")).put("tweakswitch/particles-dripping-water.zip", true);
			refresh(minecraft);
		});
		context.waitTicks(5);
		if (!Settings.get().pinned.contains("untitled-pack.zip")) {
			throw new AssertionError("Pinning didn't save");
		}
		context.takeScreenshot("tweakswitch-pinned-sorted");

		// Right-click menu
		context.runOnClient(minecraft -> call(TweakSwitchScreen.class, Compat.screen(minecraft), "openMenu", new Class<?>[] {Pack.class, int.class, int.class},
			ourPack(minecraft, "untitled-pack.zip"), 150, 80));
		context.waitTicks(3);
		context.takeScreenshot("tweakswitch-menu");
		context.runOnClient(minecraft -> {
			try {
				Field menu = TweakSwitchScreen.class.getDeclaredField("menu");
				menu.setAccessible(true);
				menu.set(Compat.screen(minecraft), null);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
			call(TweakSwitchScreen.class, Compat.screen(minecraft), "togglePin", new Class<?>[] {Pack.class}, ourPack(minecraft, "untitled-pack.zip"));
			Settings.get().sortBy = TweakSwitchScreen.SortBy.NAME.name();
			((Map<String, Boolean>) field(TweakSwitchScreen.class, Compat.screen(minecraft), "pending")).clear();
		});

		// Keyboard: Tab reaches the switches (a white ring shows which), and Enter flips the focused one
		context.getInput().setCursorPos(0, 0);
		Pack focusedPack = null;
		for (int presses = 0; presses < 60 && focusedPack == null; presses++) {
			context.getInput().pressKey(InputConstants.KEY_TAB);
			context.waitTick();
			focusedPack = context.computeOnClient(minecraft -> ((Map<AbstractWidget, Pack>) field(TweakSwitchScreen.class, Compat.screen(minecraft), "switchRows"))
				.get((AbstractWidget) call(PackListScreen.class, Compat.screen(minecraft), "focusedListWidget", new Class<?>[0])));
		}
		if (focusedPack == null) {
			throw new AssertionError("Tab never reached a switch");
		}
		context.waitTicks(15);
		context.takeScreenshot("tweakswitch-keyboard-focus");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(3);
		String focusedId = focusedPack.getId();
		Boolean flipped = context.computeOnClient(minecraft -> ((Map<String, Boolean>) field(TweakSwitchScreen.class, Compat.screen(minecraft), "pending")).get(focusedId));
		if (flipped == null) {
			throw new AssertionError("Enter on a focused switch should flip it");
		}
		context.runOnClient(minecraft -> ((Map<String, Boolean>) field(TweakSwitchScreen.class, Compat.screen(minecraft), "pending")).clear());

		// Delete: asks first, then the file is gone
		Path doomed = packs.resolve("delete-me.zip");
		writeParticlePack(doomed);
		context.setScreen(() -> new TweakSwitchScreen(null));
		context.waitTicks(5);
		context.runOnClient(minecraft -> call(TweakSwitchScreen.class, Compat.screen(minecraft), "confirmDelete", new Class<?>[] {String.class, String.class, boolean.class},
			"delete-me.zip", "delete-me", false));
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-delete-confirm");
		context.clickScreenButton("tweakswitch.delete");
		context.waitForScreen(TweakSwitchScreen.class);
		context.waitTicks(3);
		if (Files.exists(doomed)) {
			throw new AssertionError("Delete left the file behind");
		}

		// A damaged download shows up in red instead of vanishing, and one click downloads it again
		String vtFile = fileNames(packs).stream().filter(name -> name.startsWith("VT - ")).sorted(Comparator.reverseOrder()).findFirst().orElseThrow();
		try {
			Files.writeString(packs.resolve(vtFile), "this is not a zip file");
			Files.writeString(packs.resolve("half-download.zip"), "nor is this");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		context.runOnClient(minecraft -> Settings.get().tab = TweakSwitchScreen.Tab.VANILLA_TWEAKS.name());
		context.setScreen(() -> new TweakSwitchScreen(null));
		context.waitTicks(5);
		if (!TweakSwitchPackSource.brokenFiles().contains(vtFile)) {
			throw new AssertionError("The damaged pack wasn't noticed: " + TweakSwitchPackSource.brokenFiles());
		}
		context.takeScreenshot("tweakswitch-damaged");
		context.runOnClient(minecraft -> ((AbstractButton) call(TweakSwitchScreen.class, Compat.screen(minecraft), "createBrokenRow", new Class<?>[] {String.class}, vtFile)).onPress(null));
		context.waitFor(minecraft -> ((Set<String>) field(TweakSwitchScreen.class, null, "FETCHING")).isEmpty() && !TweakSwitchPackSource.brokenFiles().contains(vtFile), 1200);
		boolean repaired = context.computeOnClient(minecraft -> minecraft.getResourcePackRepository().getAvailableIds().contains("tweakswitch/" + vtFile));
		if (!repaired) {
			throw new AssertionError(vtFile + " wasn't downloaded again");
		}
		deleteQuietly(packs.resolve("half-download.zip"));

		// Two Vanilla Tweaks packs that clash: switching the second on asks what to do
		String[] clash = downloadClashingPair();
		context.setScreen(() -> new TweakSwitchScreen(null));
		context.waitTicks(10);
		context.runOnClient(minecraft -> {
			TweakSwitchScreen screen = (TweakSwitchScreen) Compat.screen(minecraft);
			((Map<String, Boolean>) field(TweakSwitchScreen.class, screen, "pending")).put("tweakswitch/" + clash[0], true);
			call(TweakSwitchScreen.class, screen, "toggle", new Class<?>[] {Pack.class}, ourPack(minecraft, clash[1]));
		});
		context.waitFor(minecraft -> Compat.screen(minecraft) instanceof ConfirmScreen, 100);
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-clash-warning");
		context.clickScreenButton("tweakswitch.clash.turnOff");
		context.waitForScreen(TweakSwitchScreen.class);
		context.waitTicks(3);
		Map<String, Boolean> afterClash = context.computeOnClient(minecraft -> Map.copyOf((Map<String, Boolean>) field(TweakSwitchScreen.class, Compat.screen(minecraft), "pending")));
		if (!Boolean.TRUE.equals(afterClash.get("tweakswitch/" + clash[1])) || !Boolean.FALSE.equals(afterClash.get("tweakswitch/" + clash[0]))) {
			throw new AssertionError("\"Turn the Other Off\" should leave only the new pack on: " + afterClash);
		}

		// Order: two packs on, the top one moved down, and the game's selection follows
		context.runOnClient(minecraft -> {
			Map<String, Boolean> pending = (Map<String, Boolean>) field(TweakSwitchScreen.class, Compat.screen(minecraft), "pending");
			pending.put("tweakswitch/particles-dripping-lava.zip", true);
			pending.put("tweakswitch/particles-dripping-honey.zip", true);
		});
		context.clickScreenButton("tweakswitch.order");
		context.waitForScreen(PackOrderScreen.class);
		context.runOnClient(minecraft -> Compat.toasts(minecraft).clear());
		context.waitTicks(5);
		List<String> before = context.computeOnClient(minecraft -> ((PackOrderScreen) Compat.screen(minecraft)).orderForTest());
		if (before.size() < 3) {
			throw new AssertionError("The order screen should list every switched-on pack: " + before);
		}
		context.takeScreenshot("tweakswitch-order");
		context.runOnClient(minecraft -> ((PackOrderScreen) Compat.screen(minecraft)).move(0, 1));
		context.waitTicks(3);
		context.clickScreenButton("gui.done");
		context.waitForScreen(TweakSwitchScreen.class);
		List<String> selectedOurs = context.computeOnClient(minecraft -> minecraft.getResourcePackRepository().getSelectedIds().stream().filter(id -> id.startsWith("tweakswitch/")).toList());
		// The game lists lowest priority first: the pack that was on top is now second from the top
		if (!selectedOurs.get(selectedOurs.size() - 2).equals(before.get(0)) || !selectedOurs.get(selectedOurs.size() - 1).equals(before.get(1))) {
			throw new AssertionError("Moving a pack down didn't change the selection: was " + before + ", now " + selectedOurs);
		}

		// Presets: save what's on, change something, switch back with one click
		context.clickScreenButton("tweakswitch.presets");
		context.waitForScreen(PresetsScreen.class);
		context.getInput().typeChars("Building");
		context.waitTicks(3);
		context.clickScreenButton("tweakswitch.presets.save");
		context.waitTicks(3);
		List<String> saved = Presets.load().get("Building");
		if (saved == null || saved.size() != selectedOurs.size()) {
			throw new AssertionError("The preset should hold the " + selectedOurs.size() + " packs that are on: " + saved);
		}
		context.runOnClient(minecraft -> {
			Presets.put("PvP", List.of("untitled-pack.zip"));
			Compat.toasts(minecraft).clear();
			refresh(minecraft);
		});
		context.waitTicks(3);
		context.takeScreenshot("tweakswitch-presets");
		context.runOnClient(minecraft -> ((PresetsScreen) Compat.screen(minecraft)).apply("PvP", Presets.load().get("PvP")));
		context.waitFor(minecraft -> !Compat.loading(minecraft) && Compat.screen(minecraft) instanceof TweakSwitchScreen, 1200);
		List<String> afterPreset = context.computeOnClient(minecraft -> minecraft.getResourcePackRepository().getSelectedIds().stream().filter(id -> id.startsWith("tweakswitch/")).toList());
		if (!afterPreset.equals(List.of("tweakswitch/untitled-pack.zip"))) {
			throw new AssertionError("The PvP preset should leave only untitled-pack on: " + afterPreset);
		}
		// The "next preset" key's action steps on to the next one
		String next = context.computeOnClient(Presets::applyNext);
		context.waitFor(minecraft -> !Compat.loading(minecraft), 1200);
		if (!"Building".equals(next)) {
			throw new AssertionError("After PvP the next preset should be Building, got " + next);
		}

		// Export, then import a list naming a pack that's missing: it's downloaded and switched on
		Path exported = context.computeOnClient(minecraft -> ((TweakSwitchScreen) Compat.screen(minecraft)).exportSwitches(false));
		PackShare.Export export;
		try {
			export = PackShare.parse(Files.readString(exported));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		if (export == null || export.packs().stream().noneMatch(pack -> pack.file().equals("MR - Fresh Animations.zip") && pack.downloadable())) {
			throw new AssertionError("The export should list Fresh Animations with where it came from");
		}
		context.runOnClient(minecraft -> {
			// As a friend who has none of your Modrinth packs would see it
			TweakSwitchScreen screen = (TweakSwitchScreen) Compat.screen(minecraft);
			call(TweakSwitchScreen.class, screen, "delete", new Class<?>[] {String.class}, "MR - Fresh Animations.zip");
			minecraft.getResourcePackRepository().reload();
			screen.importSwitches(exported);
		});
		context.waitFor(minecraft -> Compat.screen(minecraft) instanceof ConfirmScreen, 100);
		context.waitTicks(5);
		context.runOnClient(minecraft -> Compat.toasts(minecraft).clear());
		context.waitTicks(2);
		context.takeScreenshot("tweakswitch-import");
		context.clickScreenButton("tweakswitch.import.applyAndDownload");
		context.waitForScreen(TweakSwitchScreen.class);
		context.waitFor(minecraft -> ((Set<String>) field(TweakSwitchScreen.class, null, "FETCHING")).isEmpty(), 1200);
		context.waitTicks(5);
		if (!Files.exists(packs.resolve("MR - Fresh Animations.zip"))) {
			throw new AssertionError("Importing didn't download the missing pack");
		}

		// A combined zip from the Vanilla Tweaks website: the menu offers to split it, which picks its tweaks in the picker
		List<String> tweakIds = writeCombinedZip(packs.resolve("VanillaTweaks_combined.zip"));
		context.runOnClient(minecraft -> Settings.get().tab = TweakSwitchScreen.Tab.IMPORTED.name());
		context.setScreen(() -> new TweakSwitchScreen(null));
		context.waitTicks(5);
		context.runOnClient(minecraft -> {
			Compat.toasts(minecraft).clear();
			call(TweakSwitchScreen.class, Compat.screen(minecraft), "openMenu", new Class<?>[] {Pack.class, int.class, int.class},
				ourPack(minecraft, "VanillaTweaks_combined.zip"), 150, 80);
		});
		context.waitTicks(3);
		context.takeScreenshot("tweakswitch-menu-split");
		context.runOnClient(minecraft -> call(TweakSwitchScreen.class, Compat.screen(minecraft), "splitCombined", new Class<?>[] {List.class}, tweakIds));
		context.waitForScreen(VanillaTweaksScreen.class);
		context.waitFor(minecraft -> VanillaTweaksScreen.loadedForTest(VanillaTweaks.Kind.RESOURCE_PACKS), 600);
		context.waitTicks(40);
		int pickedTweaks = context.computeOnClient(minecraft -> ((Map<String, ?>) field(DownloadScreen.class, Compat.screen(minecraft), "selected")).size());
		if (pickedTweaks != tweakIds.size()) {
			throw new AssertionError("Splitting should pick the zip's " + tweakIds.size() + " tweaks, picked " + pickedTweaks);
		}
		context.takeScreenshot("tweakswitch-split-picked");
		context.runOnClient(minecraft -> ((Map<String, ?>) field(DownloadScreen.class, Compat.screen(minecraft), "selected")).clear());

		// A failed load says what's wrong in plain words and offers Try Again, which loads the list
		context.runOnClient(minecraft -> {
			((Map<VanillaTweaks.Kind, ?>) staticField("PACKS")).remove(VanillaTweaks.Kind.RESOURCE_PACKS);
			((Map<VanillaTweaks.Kind, Component>) staticField("LOAD_ERRORS")).put(VanillaTweaks.Kind.RESOURCE_PACKS, WebErrors.describe(new java.net.ConnectException("test")));
			Compat.toasts(minecraft).clear();
			refresh(minecraft);
		});
		context.waitTicks(3);
		context.takeScreenshot("tweakswitch-error-retry");
		context.runOnClient(minecraft -> pressListButton(minecraft, "tweakswitch.retry"));
		context.waitFor(minecraft -> ((Map<VanillaTweaks.Kind, ?>) staticField("PACKS")).containsKey(VanillaTweaks.Kind.RESOURCE_PACKS), 600);

		// Modrinth filters: with "16x" on, every result carries that tag
		context.setScreen(() -> new ModrinthScreen(new TweakSwitchScreen(null)));
		context.waitFor(minecraft -> !ModrinthScreen.loadingForTest(Modrinth.Type.RESOURCE_PACKS), 600);
		context.runOnClient(minecraft -> {
			try {
				Method method = PackListScreen.class.getDeclaredMethod("setSearchText", String.class);
				method.setAccessible(true);
				method.invoke(Compat.screen(minecraft), "");
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		context.waitFor(minecraft -> "".equals(ModrinthScreen.queryForTest(Modrinth.Type.RESOURCE_PACKS)) && !ModrinthScreen.loadingForTest(Modrinth.Type.RESOURCE_PACKS), 600);
		int allHits = ModrinthScreen.hitsForTest(Modrinth.Type.RESOURCE_PACKS);
		context.runOnClient(minecraft -> {
			Object state = field(ModrinthScreen.class, Compat.screen(minecraft), "state");
			try {
				Field open = state.getClass().getDeclaredField("filtersOpen");
				open.setAccessible(true);
				open.set(state, true);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
			((ModrinthScreen) Compat.screen(minecraft)).toggleFilterForTest("16x");
		});
		context.waitTicks(2);
		context.waitFor(minecraft -> !ModrinthScreen.loadingForTest(Modrinth.Type.RESOURCE_PACKS) && !rows(minecraft).isEmpty(), 600);
		context.waitTicks(20);
		int filteredHits = ModrinthScreen.hitsForTest(Modrinth.Type.RESOURCE_PACKS);
		if (filteredHits <= 0 || filteredHits >= allHits) {
			throw new AssertionError("The 16x filter should narrow the search: " + allHits + " packs without it, " + filteredHits + " with");
		}
		context.takeScreenshot("tweakswitch-modrinth-filters");
		context.runOnClient(minecraft -> ((ModrinthScreen) Compat.screen(minecraft)).toggleFilterForTest("16x"));
		context.waitFor(minecraft -> !ModrinthScreen.loadingForTest(Modrinth.Type.RESOURCE_PACKS), 600);

		// Settings, and the Normal size in another color
		context.setScreen(() -> new SettingsScreen(new TweakSwitchScreen(null)));
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-settings");
		context.runOnClient(minecraft -> {
			Settings.get().uiSize = "NORMAL";
			Settings.get().accent = "BLUE";
			Settings.get().tab = TweakSwitchScreen.Tab.MODRINTH.name();
		});
		context.setScreen(() -> new TweakSwitchScreen(null));
		context.waitTicks(10);
		context.takeScreenshot("tweakswitch-normal-size-blue");
		context.runOnClient(minecraft -> {
			Settings.get().uiSize = "COMPACT";
			Settings.get().accent = "GREEN";
		});

		// The start-up check asks GitHub for the newest release; a newer one shows as a note above the switches
		StartupCheck.check(context.computeOnClient(minecraft -> minecraft), true, false);
		context.waitTicks(5);
		if (Settings.get().knownModVersion.isEmpty()) {
			throw new AssertionError("The start-up check didn't learn the newest TweakSwitch version");
		}
		if (StartupCheck.newerVersion() != null) {
			throw new AssertionError("This build shouldn't think " + Settings.get().knownModVersion + " is newer than itself");
		}
		String realLatest = Settings.get().knownModVersion;
		context.runOnClient(minecraft -> {
			Settings.get().knownModVersion = "99.0.0";
			Settings.get().knownPackUpdates = 2;
			Settings.get().tab = TweakSwitchScreen.Tab.IMPORTED.name();
			Compat.toasts(minecraft).clear();
		});
		context.setScreen(() -> new TweakSwitchScreen(null));
		context.waitTicks(10);
		context.takeScreenshot("tweakswitch-new-version-note");
		context.runOnClient(minecraft -> {
			Settings.get().knownModVersion = realLatest;
			Settings.get().knownPackUpdates = 0;
			Settings.get().save();
		});
		context.setScreen(TitleScreen::new);
		context.waitTicks(5);
	}

	/** Presses a button inside the screen's list (the game test's own button click only sees buttons outside it). */
	private static void pressListButton(final Minecraft minecraft, final String translationKey) {
		String label = Component.translatable(translationKey).getString();
		net.minecraft.client.gui.components.events.ContainerEventHandler list =
			(net.minecraft.client.gui.components.events.ContainerEventHandler) field(PackListScreen.class, Compat.screen(minecraft), "list");
		for (Object entry : list.children()) {
			for (Object child : ((net.minecraft.client.gui.components.events.ContainerEventHandler) entry).children()) {
				if (child instanceof AbstractButton button && button.getMessage().getString().equals(label)) {
					button.onPress(null);
					return;
				}
			}
		}
		throw new AssertionError("No list button labelled " + label);
	}

	private static Pack ourPack(final Minecraft minecraft, final String fileName) {
		minecraft.getResourcePackRepository().reload();
		Pack pack = minecraft.getResourcePackRepository().getPack("tweakswitch/" + fileName);
		if (pack == null) {
			throw new AssertionError("No pack for " + fileName);
		}
		return pack;
	}

	private static Object call(final Class<?> owner, final Object instance, final String name, final Class<?>[] types, final Object... args) {
		try {
			Method method = owner.getDeclaredMethod(name, types);
			method.setAccessible(true);
			return method.invoke(instance, args);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	/** Moves the cursor to a GUI position and left-clicks there. */
	private static void clickAt(final ClientGameTestContext context, final double guiX, final double guiY) {
		context.getInput().setCursorPos(100, 100);
		context.waitTick();
		double[] perGuiUnit = context.computeOnClient(minecraft -> new double[] {
			100 / minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
			100 / minecraft.mouseHandler.getScaledYPos(minecraft.getWindow())
		});
		context.getInput().setCursorPos(guiX * perGuiUnit[0], guiY * perGuiUnit[1]);
		context.waitTick();
		context.getInput().pressMouse(0);
		context.waitTick();
	}

	/** Downloads two Vanilla Tweaks packs that the site says clash, and returns their file names. */
	private static String[] downloadClashingPair() {
		try {
			List<VanillaTweaks.VtPack> all = VanillaTweaks.fetchPacks(VanillaTweaks.Kind.RESOURCE_PACKS);
			for (VanillaTweaks.VtPack pack : all) {
				for (String otherId : pack.incompatible()) {
					VanillaTweaks.VtPack other = all.stream().filter(candidate -> candidate.id().equals(otherId)).findFirst().orElse(null);
					if (other != null && !other.fileName().equals(pack.fileName())) {
						return new String[] {
							PackShare.fetch("vanillatweaks", pack.id(), null),
							PackShare.fetch("vanillatweaks", other.id(), null)
						};
					}
				}
			}
			throw new AssertionError("Vanilla Tweaks lists no clashing packs for this version");
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	/** A stand-in for a combined zip from the Vanilla Tweaks website: its "Selected Packs.txt" names two real tweaks. */
	private static List<String> writeCombinedZip(final Path zip) {
		try {
			Set<String> have = fileNames(TweakSwitch.packsDir());
			List<String> ids = VanillaTweaks.fetchPacks(VanillaTweaks.Kind.RESOURCE_PACKS).stream()
				.filter(pack -> !have.contains(pack.fileName()))
				.map(VanillaTweaks.VtPack::id)
				.limit(2)
				.toList();
			try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
				out.putNextEntry(new ZipEntry("pack.mcmeta"));
				out.write("{\"pack\":{\"description\":\"Vanilla Tweaks\",\"min_format\":84,\"max_format\":97}}".getBytes(StandardCharsets.UTF_8));
				out.putNextEntry(new ZipEntry("Selected Packs.txt"));
				out.write(("Vanilla Tweaks Resource Pack\nVersion: " + VanillaTweaks.siteVersion() + "\nPacks:\n\t" + String.join("\n\t", ids)).getBytes(StandardCharsets.UTF_8));
			}
			return ids;
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	/** World creation's More tab opens a data pack window apart from resource packs; picked packs are on in the new world. */
	private static void testDataPacks(final ClientGameTestContext context) {
		context.runOnClient(minecraft -> CreateWorldScreen.openFresh(minecraft, () -> Compat.setScreen(minecraft, new TitleScreen())));
		context.waitFor(minecraft -> Compat.screen(minecraft) instanceof CreateWorldScreen, 1200);
		context.waitTicks(5);
		// Tab headers aren't buttons, so pick the More tab (the third) the way clicking it does. The bar's class differs
		// between versions (MenuTabBar on 26.3), but the field and its selectTab method are named the same
		context.runOnClient(minecraft -> {
			try {
				Field field = CreateWorldScreen.class.getDeclaredField("tabNavigationBar");
				field.setAccessible(true);
				Object bar = field.get(Compat.screen(minecraft));
				bar.getClass().getMethod("selectTab", int.class, boolean.class).invoke(bar, 2, false);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-world-more-tab");
		context.clickScreenButton("tweakswitch.dp.button");
		context.waitForScreen(DataPacksScreen.class);
		context.waitFor(minecraft -> VanillaTweaksScreen.loadedForTest(VanillaTweaks.Kind.DATA_PACKS)
			&& VanillaTweaksScreen.loadedForTest(VanillaTweaks.Kind.CRAFTING_TWEAKS), 600);

		// Open every category on both tabs and pick the first pack on each
		context.runOnClient(minecraft -> {
			Set<String> expanded = staticField("EXPANDED");
			expanded.clear();
		});
		List<String> picked = new java.util.ArrayList<>();
		for (VanillaTweaks.Kind kind : List.of(VanillaTweaks.Kind.CRAFTING_TWEAKS, VanillaTweaks.Kind.DATA_PACKS)) {
			context.runOnClient(minecraft -> {
				Object screen = Compat.screen(minecraft);
				try {
					Field field = VanillaTweaksScreen.class.getDeclaredField("kind");
					field.setAccessible(true);
					field.set(screen, kind);
					Field packsField = VanillaTweaksScreen.class.getDeclaredField("PACKS");
					packsField.setAccessible(true);
					Map<VanillaTweaks.Kind, List<VanillaTweaks.VtPack>> all = (Map<VanillaTweaks.Kind, List<VanillaTweaks.VtPack>>) packsField.get(null);
					Set<String> expanded = staticField("EXPANDED");
					expanded.add(all.get(kind).get(0).category());
				} catch (ReflectiveOperationException e) {
					throw new RuntimeException(e);
				}
				refresh(minecraft);
			});
			context.waitTicks(30);
			String name = context.computeOnClient(minecraft -> {
				Map.Entry<AbstractWidget, ?> first = rows(minecraft).entrySet().stream()
					.min(Comparator.comparingInt((Map.Entry<AbstractWidget, ?> entry) -> entry.getKey().getY()).thenComparingInt(entry -> entry.getKey().getX()))
					.orElseThrow(() -> new AssertionError("No " + kind + " listed"));
				((AbstractButton) first.getKey()).onPress(null);
				return ((VanillaTweaks.VtPack) first.getValue()).fileName();
			});
			picked.add(name);
			context.waitTicks(3);
			if (kind == VanillaTweaks.Kind.CRAFTING_TWEAKS) {
				context.takeScreenshot("tweakswitch-world-crafting-tweaks");
			}
		}
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(3);
		context.takeScreenshot("tweakswitch-world-data-packs");

		Path folder = context.computeOnClient(minecraft -> {
			try {
				Method method = DataPacksScreen.class.getDeclaredMethod("targetFolder");
				method.setAccessible(true);
				return (Path) method.invoke(Compat.screen(minecraft));
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		context.runOnClient(minecraft -> ((Button) field(DownloadScreen.class, Compat.screen(minecraft), "downloadButton")).onPress(null));
		context.waitFor(minecraft -> field(DownloadScreen.class, null, "progress") == null, 1200);
		context.waitTicks(10);
		context.runOnClient(minecraft -> Compat.toasts(minecraft).clear());
		context.waitTicks(2);
		context.takeScreenshot("tweakswitch-world-data-packs-added");
		for (String file : picked) {
			if (!Files.exists(folder.resolve(file))) {
				throw new AssertionError(file + " wasn't saved to the new world's data pack folder");
			}
		}
		// Not listed with resource packs
		if (Files.exists(TweakSwitch.packsDir().resolve(picked.get(0)))) {
			throw new AssertionError("A data pack landed in the resource pack folder");
		}

		// The Modrinth tab: its own search for data packs, and a picked one lands in the same world folder
		context.clickScreenButton("tweakswitch.dp.tab.modrinth");
		context.waitForScreen(ModrinthDataPacksScreen.class);
		context.waitFor(minecraft -> !ModrinthScreen.loadingForTest(Modrinth.Type.DATA_PACKS) && !rows(minecraft).isEmpty(), 600);
		context.waitTicks(40);
		context.takeScreenshot("tweakswitch-world-modrinth");
		// The first result that really has a data pack file for this version and needs nothing else
		List<Modrinth.Project> candidates = context.computeOnClient(minecraft -> rows(minecraft).values().stream().map(project -> (Modrinth.Project) project).toList());
		Modrinth.Project dataPack = null;
		for (Modrinth.Project candidate : candidates) {
			try {
				if (Modrinth.requirements(Modrinth.Type.DATA_PACKS, candidate).required().isEmpty()) {
					dataPack = candidate;
					break;
				}
			} catch (Exception e) {
				// No data pack file for this version: try the next one
			}
		}
		if (dataPack == null) {
			throw new AssertionError("No Modrinth data pack on the first page can be downloaded for this version");
		}
		Modrinth.Project chosen = dataPack;
		context.runOnClient(minecraft -> ((AbstractButton) rows(minecraft).entrySet().stream().filter(entry -> entry.getValue() == chosen).findFirst().orElseThrow().getKey()).onPress(null));
		context.waitTicks(3);
		context.runOnClient(minecraft -> ((Button) field(DownloadScreen.class, Compat.screen(minecraft), "downloadButton")).onPress(null));
		context.waitTicks(5);
		context.waitFor(minecraft -> field(DownloadScreen.class, null, "progress") == null, 1200);
		context.waitTicks(10);
		// Search for it so its card, now marked installed, is on screen for the picture. The search box holds 32
		// characters and trims spaces, so a long title is cut to its first words
		String wholeTitle = chosen.title().strip();
		String searched = wholeTitle.length() > 24 ? wholeTitle.substring(0, 24).strip() : wholeTitle;
		context.runOnClient(minecraft -> {
			Compat.toasts(minecraft).clear();
			call(PackListScreen.class, Compat.screen(minecraft), "setSearchText", new Class<?>[] {String.class}, searched);
		});
		context.waitFor(minecraft -> searched.equals(ModrinthScreen.queryForTest(Modrinth.Type.DATA_PACKS)) && !ModrinthScreen.loadingForTest(Modrinth.Type.DATA_PACKS), 600);
		context.waitTicks(20);
		context.takeScreenshot("tweakswitch-world-modrinth-added");
		if (!Files.exists(folder.resolve(chosen.fileName()))) {
			throw new AssertionError(chosen.fileName() + " wasn't saved to the new world's data pack folder");
		}
		if (Files.exists(TweakSwitch.packsDir().resolve(chosen.fileName()))) {
			throw new AssertionError("A Modrinth data pack landed in the resource pack folder");
		}
		picked.add(chosen.fileName());

		// Done switches them on for the new world, through world creation's own data pack check
		context.clickScreenButton("gui.done");
		context.waitFor(minecraft -> Compat.screen(minecraft) instanceof CreateWorldScreen, 1200);
		context.waitTicks(5);
		List<String> enabled = context.computeOnClient(minecraft -> List.copyOf(
			((CreateWorldScreen) Compat.screen(minecraft)).getUiState().getSettings().dataConfiguration().dataPacks().getEnabled()));
		for (String file : picked) {
			if (!enabled.contains("file/" + file)) {
				throw new AssertionError(file + " isn't switched on for the new world: " + enabled);
			}
		}
		context.takeScreenshot("tweakswitch-world-back");
		context.setScreen(TitleScreen::new);
		context.waitTicks(5);
	}

	private static Set<String> fileNames(final Path folder) {
		Set<String> names = new java.util.HashSet<>();
		try (java.util.stream.Stream<Path> files = Files.list(folder)) {
			files.forEach(file -> names.add(file.getFileName().toString()));
		} catch (IOException e) {
			// No folder yet: nothing in it
		}
		return names;
	}

	/** Searches Modrinth for the title and picks the result with exactly that title. */
	private static void pickModrinthResult(final ClientGameTestContext context, final String title) {
		context.runOnClient(minecraft -> {
			try {
				Method method = PackListScreen.class.getDeclaredMethod("setSearchText", String.class);
				method.setAccessible(true);
				method.invoke(Compat.screen(minecraft), title);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		context.waitFor(minecraft -> title.equals(ModrinthScreen.queryForTest(Modrinth.Type.RESOURCE_PACKS)) && !ModrinthScreen.loadingForTest(Modrinth.Type.RESOURCE_PACKS), 600);
		context.waitTicks(2);
		context.runOnClient(minecraft -> {
			AbstractButton row = (AbstractButton) rows(minecraft).entrySet().stream()
				.filter(entry -> ((Modrinth.Project) entry.getValue()).title().equals(title))
				.findFirst()
				.orElseThrow(() -> new AssertionError("No Modrinth result titled " + title))
				.getKey();
			row.onPress(null);
		});
	}

	/** Whether this game version's newest version of {@code slug} lists {@code projectId} as required. */
	private static boolean requires(final String slug, final String projectId) {
		for (com.google.gson.JsonElement dependency : versions(slug).get(0).getAsJsonObject().getAsJsonArray("dependencies")) {
			JsonObject object = dependency.getAsJsonObject();
			if (projectId.equals(object.has("project_id") && !object.get("project_id").isJsonNull() ? object.get("project_id").getAsString() : null)
				&& "required".equals(object.get("dependency_type").getAsString())) {
				return true;
			}
		}
		return false;
	}

	/** SHA-1 of Fresh Animations' primary file, 0 = newest version for this game version. */
	private static String freshAnimationsSha1(final int index) {
		JsonObject file = versions("fresh-animations").get(index).getAsJsonObject().getAsJsonArray("files").get(0).getAsJsonObject();
		return file.getAsJsonObject("hashes").get("sha1").getAsString();
	}

	private static JsonArray versions(final String slug) {
		try {
			String url = "https://api.modrinth.com/v2/project/" + slug + "/version?game_versions="
				+ URLEncoder.encode("[\"" + Modrinth.gameVersion() + "\"]", StandardCharsets.UTF_8) + "&loaders=" + URLEncoder.encode("[\"minecraft\"]", StandardCharsets.UTF_8);
			return JsonParser.parseString(WebClient.getString(URI.create(url))).getAsJsonArray();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** Replaces the file with the oldest Fresh Animations version for this game version and returns that file's SHA-1. */
	private static String installOlderVersion(final Path target) {
		try {
			String url = "https://api.modrinth.com/v2/project/fresh-animations/version?game_versions="
				+ URLEncoder.encode("[\"" + Modrinth.gameVersion() + "\"]", StandardCharsets.UTF_8) + "&loaders=" + URLEncoder.encode("[\"minecraft\"]", StandardCharsets.UTF_8);
			JsonArray versions = JsonParser.parseString(WebClient.getString(URI.create(url))).getAsJsonArray();
			JsonObject file = versions.get(versions.size() - 1).getAsJsonObject().getAsJsonArray("files").get(0).getAsJsonObject();
			WebClient.download(URI.create(file.get("url").getAsString()), target);
			return file.getAsJsonObject("hashes").get("sha1").getAsString();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static String sha1(final Path file) {
		try {
			return Modrinth.sha1(file);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/** Runs {@code refreshList} on the current screen, for checks that change state from outside it. */
	private static final class PackListScreenTestAccess {
		static void refresh(final Minecraft minecraft) {
			ScreensClientGameTest.refresh(minecraft);
		}
	}

	/**
	 * A base pack with the particle textures, plus one pack per particle holding only its definition and the same shared
	 * logo. particles-composter points at minecraft:glint, which no pack has, so its preview comes from the game itself.
	 */
	private static void writeCodexStylePacks(final Path folder) {
		try {
			byte[] logo = png(16, (x, y) -> (x / 4 + y / 4) % 2 == 0 ? 0xFF7A3DD6 : 0xFF2B1A52);
			String meta = "{\"pack\":{\"description\":\"Split particle pack | %s\",\"min_format\":84,\"max_format\":97}}";
			String[][] particles = {
				{"dripping-lava", "dripping_lava", "drip_hang_lava"},
				{"dripping-water", "dripping_water", "drip_hang_water"},
				{"dripping-honey", "dripping_honey", "drip_hang_honey"},
				{"bubble-pop", "bubble_pop", "bubble_pop_0"},
				{"composter", "composter", "minecraft:glint"},
			};
			int[] colors = {0xFFFF6A00, 0xFF3DA5FF, 0xFFFFC23D, 0xFFB4E6FF};
			try (ZipOutputStream base = new ZipOutputStream(Files.newOutputStream(folder.resolve("crystal-base.zip")))) {
				base.putNextEntry(new ZipEntry("pack.mcmeta"));
				base.write(String.format(meta, "base textures").getBytes(StandardCharsets.UTF_8));
				for (int i = 0; i < 4; i++) {
					int color = colors[i];
					base.putNextEntry(new ZipEntry("assets/minecraft/textures/particle/" + particles[i][2] + ".png"));
					base.write(png(8, (x, y) -> Math.abs(x - 3.5) + Math.abs(y - 3.5) < 4 ? color : 0));
				}
			}
			for (String[] particle : particles) {
				try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(folder.resolve("particles-" + particle[0] + ".zip")))) {
					out.putNextEntry(new ZipEntry("pack.mcmeta"));
					out.write(String.format(meta, "particles " + particle[0].replace('-', ' ')).getBytes(StandardCharsets.UTF_8));
					out.putNextEntry(new ZipEntry("pack.png"));
					out.write(logo);
					out.putNextEntry(new ZipEntry("assets/minecraft/particles/" + particle[1] + ".json"));
					out.write(("{\"textures\":[\"" + particle[2] + "\"]}").getBytes(StandardCharsets.UTF_8));
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private interface Pixels {
		int color(int x, int y);
	}

	private static byte[] png(final int size, final Pixels pixels) throws IOException {
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				image.setRGB(x, y, pixels.color(x, y));
			}
		}
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	/** A resource pack with an empty description and six made-up particle textures, flame.png among them. */
	private static void writeParticlePack(final Path zip) {
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
			out.putNextEntry(new ZipEntry("pack.mcmeta"));
			out.write("{\"pack\":{\"description\":\"\",\"min_format\":84,\"max_format\":97}}".getBytes(StandardCharsets.UTF_8));
			int[] colors = {0xFFFF7A1A, 0xFF3DD6E0, 0xFFE0303C, 0xFFF5E663, 0xFF9B5DE5, 0xFF7CE05A};
			String[] names = {"flame", "soul_fire_flame", "heart", "glint", "spell", "happy_villager"};
			for (int i = 0; i < names.length; i++) {
				BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
				for (int y = 0; y < 16; y++) {
					for (int x = 0; x < 16; x++) {
						// A diamond of color, so the textures look like particles in the preview
						if (Math.abs(x - 7.5) + Math.abs(y - 7.5) < 7) {
							image.setRGB(x, y, (x + y) % 4 == 0 ? 0xFFFFFFFF : colors[i]);
						}
					}
				}
				out.putNextEntry(new ZipEntry("assets/minecraft/textures/particle/" + names[i] + ".png"));
				ImageIO.write(image, "png", out);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void deleteQuietly(final Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static Object field(final Class<?> owner, final Object instance, final String name) {
		try {
			Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(instance);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	/** Moves the test cursor to the widget's center, measuring how cursor units map to GUI units first. */
	private static void moveTo(final ClientGameTestContext context, final AbstractWidget widget) {
		context.getInput().setCursorPos(100, 100);
		context.waitTick();
		double[] perGuiUnit = context.computeOnClient(minecraft -> new double[] {
			100 / minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
			100 / minecraft.mouseHandler.getScaledYPos(minecraft.getWindow())
		});
		context.getInput().setCursorPos((widget.getX() + widget.getWidth() / 2.0) * perGuiUnit[0], (widget.getY() + widget.getHeight() / 2.0) * perGuiUnit[1]);
		context.waitTick();
	}

	private static void refresh(final Minecraft minecraft) {
		try {
			Method method = PackListScreen.class.getDeclaredMethod("refreshList");
			method.setAccessible(true);
			method.invoke(Compat.screen(minecraft));
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T staticField(final String name) {
		try {
			Field field = VanillaTweaksScreen.class.getDeclaredField(name);
			field.setAccessible(true);
			return (T) field.get(null);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static Map<AbstractWidget, ?> rows(final Minecraft minecraft) {
		return Compat.screen(minecraft) instanceof DownloadScreen<?> screen ? (Map<AbstractWidget, ?>) field(DownloadScreen.class, screen, "rows") : Map.of();
	}
}
