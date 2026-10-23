package com.tweakswitch.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tweakswitch.DownloadScreen;
import com.tweakswitch.Modrinth;
import com.tweakswitch.ModsNeededScreen;
import com.tweakswitch.TweakSwitch;
import com.tweakswitch.UpdatesScreen;
import com.tweakswitch.WebClient;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.tweakswitch.ModrinthScreen;
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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;

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
			if (!(api.getModConfigScreenFactory().create(minecraft.gui.screen()) instanceof TweakSwitchScreen)) {
				throw new AssertionError("Mod Menu config button doesn't open TweakSwitch");
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

		context.clickScreenButton("tweakswitch.getVanillaTweaks");
		context.waitForScreen(VanillaTweaksScreen.class);
		context.waitFor(minecraft -> staticField("packs") != null, 600);
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
		moveTo(context, firstTwo.get(0));
		context.waitTicks(80);
		context.takeScreenshot("tweakswitch-picker-preview");

		// Pick the first two packs (pressed directly, since the cursor is imprecise)
		for (AbstractWidget row : firstTwo) {
			context.runOnClient(minecraft -> ((Button) row).onPress(null));
			context.waitTicks(3);
		}
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(3);
		context.takeScreenshot("tweakswitch-picker-picked");

		context.runOnClient(minecraft -> ((Button) field(DownloadScreen.class, minecraft.gui.screen(), "downloadButton")).onPress(null));
		context.waitFor(minecraft -> field(DownloadScreen.class, null, "progress") == null, 1200);
		context.waitTicks(10);
		context.takeScreenshot("tweakswitch-picker-downloaded");

		context.clickScreenButton("gui.done");
		context.waitForScreen(TweakSwitchScreen.class);
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-switches-downloaded");

		// Modrinth: search results load, a hovered pack shows its picture, and a picked pack downloads as a switch
		context.clickScreenButton("tweakswitch.getModrinth");
		context.waitForScreen(ModrinthScreen.class);
		context.waitFor(minecraft -> !rows(minecraft).isEmpty(), 600);
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-modrinth-results");
		AbstractWidget firstResult = context.computeOnClient(minecraft -> rows(minecraft).keySet().stream()
			.filter(widget -> widget.getY() > 40 && widget.getY() < 100)
			.min(Comparator.comparingInt(AbstractWidget::getY))
			.orElseThrow());
		moveTo(context, firstResult);
		context.waitTicks(100);
		context.takeScreenshot("tweakswitch-modrinth-preview");
		context.runOnClient(minecraft -> ((Button) firstResult).onPress(null));

		// Typing in the search box runs a new search once typing stops
		context.getInput().setCursorPos(0, 0);
		context.getInput().typeChars("sky");
		context.waitFor(minecraft -> "sky".equals(field(ModrinthScreen.class, null, "query")) && !(boolean) field(ModrinthScreen.class, null, "loading"), 600);
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-modrinth-search");
		context.runOnClient(minecraft -> ((Button) field(DownloadScreen.class, minecraft.gui.screen(), "downloadButton")).onPress(null));
		context.waitFor(minecraft -> field(DownloadScreen.class, null, "progress") == null, 1200);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(10);
		context.takeScreenshot("tweakswitch-modrinth-downloaded");
		// A pack needing a mod brings up the warning with links; a pack needing another pack pulls it in too.
		// Fresh Animations is deleted first so Extensions has to bring it back.
		Path packs = TweakSwitch.packsDir();
		deleteQuietly(packs.resolve("MR - Fresh Animations.zip"));
		pickModrinthResult(context, "Hyper Punchy");
		pickModrinthResult(context, "Fresh Animations: Extensions");
		context.runOnClient(minecraft -> ((Button) field(DownloadScreen.class, minecraft.gui.screen(), "downloadButton")).onPress(null));
		context.waitForScreen(ModsNeededScreen.class);
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-mods-needed");
		context.clickScreenButton("tweakswitch.mods.downloadAnyway");
		context.waitForScreen(ModrinthScreen.class);
		context.waitFor(minecraft -> field(DownloadScreen.class, null, "progress") == null, 1200);
		for (String file : List.of("MR - Hyper Punchy.zip", "MR - Fresh Animations_ Extensions.zip", "MR - Fresh Animations.zip")) {
			if (!Files.exists(packs.resolve(file))) {
				throw new AssertionError("Expected " + file + " to be downloaded");
			}
		}

		context.clickScreenButton("gui.done");
		context.waitForScreen(TweakSwitchScreen.class);
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-switches-with-modrinth");

		// Updates: swap Fresh Animations for an older version, then update it back
		String newSha1 = sha1(packs.resolve("MR - Fresh Animations.zip"));
		String oldSha1 = installOlderVersion(packs.resolve("MR - Fresh Animations.zip"));
		if (!sha1(packs.resolve("MR - Fresh Animations.zip")).equals(oldSha1)) {
			throw new AssertionError("Older Fresh Animations didn't install cleanly");
		}
		context.clickScreenButton("tweakswitch.getUpdates");
		context.waitForScreen(UpdatesScreen.class);
		context.waitFor(minecraft -> field(UpdatesScreen.class, minecraft.gui.screen(), "items") != null, 1200);
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-updates");
		context.runOnClient(minecraft -> ((Button) field(DownloadScreen.class, minecraft.gui.screen(), "downloadButton")).onPress(null));
		context.waitFor(minecraft -> field(DownloadScreen.class, null, "progress") == null
			&& field(UpdatesScreen.class, minecraft.gui.screen(), "items") != null, 1200);
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-updates-done");
		if (!sha1(packs.resolve("MR - Fresh Animations.zip")).equals(newSha1)) {
			throw new AssertionError("Fresh Animations wasn't updated to the newest version");
		}
		context.clickScreenButton("gui.done");
		context.waitForScreen(TweakSwitchScreen.class);

		// Switch one on and apply it: the pack should be selected and resources reloaded
		String packId = "tweakswitch/VT - Cherry Picking.zip";
		context.runOnClient(minecraft -> {
			Map<String, Boolean> pending = (Map<String, Boolean>) field(TweakSwitchScreen.class, minecraft.gui.screen(), "pending");
			pending.put(packId, true);
		});
		context.clickScreenButton("gui.done");
		context.waitFor(minecraft -> minecraft.gui.overlay() == null && minecraft.gui.screen() instanceof TitleScreen, 1200);
		boolean applied = context.computeOnClient(minecraft -> minecraft.getResourcePackRepository().getSelectedIds().contains(packId)
			&& minecraft.options.resourcePacks.contains(packId));
		if (!applied) {
			throw new AssertionError("Switching on " + packId + " didn't select it");
		}
	}

	/** Searches Modrinth for the title and picks the result with exactly that title. */
	private static void pickModrinthResult(final ClientGameTestContext context, final String title) {
		context.runOnClient(minecraft -> {
			try {
				Method method = PackListScreen.class.getDeclaredMethod("setSearchText", String.class);
				method.setAccessible(true);
				method.invoke(minecraft.gui.screen(), title);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		context.waitFor(minecraft -> title.equals(field(ModrinthScreen.class, null, "query")) && !(boolean) field(ModrinthScreen.class, null, "loading"), 600);
		context.waitTicks(2);
		context.runOnClient(minecraft -> {
			Button row = (Button) rows(minecraft).entrySet().stream()
				.filter(entry -> ((Modrinth.Project) entry.getValue()).title().equals(title))
				.findFirst()
				.orElseThrow(() -> new AssertionError("No Modrinth result titled " + title))
				.getKey();
			row.onPress(null);
		});
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
			method.invoke(minecraft.gui.screen());
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
		return minecraft.gui.screen() instanceof DownloadScreen<?> screen ? (Map<AbstractWidget, ?>) field(DownloadScreen.class, screen, "rows") : Map.of();
	}
}
