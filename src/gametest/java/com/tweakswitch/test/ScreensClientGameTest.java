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

		// A wide window: the header row should sit in the middle, not stuck to the left
		context.getInput().resizeWindow(1600, 600);
		context.runOnClient(minecraft -> {
			minecraft.options.guiScale().set(2);
			minecraft.resizeGui();
		});
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-wide-header");
		int[] header = context.computeOnClient(minecraft -> {
			EditBox search = (EditBox) field(PackListScreen.class, minecraft.gui.screen(), "search");
			return new int[] {search.getX(), search.getRight(), minecraft.gui.screen().width};
		});
		if (header[0] < header[2] / 4) {
			throw new AssertionError("Header looks stuck to the left: search starts at " + header[0] + " of " + header[2]);
		}
		context.getInput().resizeWindow(854, 480);
		context.runOnClient(minecraft -> {
			minecraft.options.guiScale().set(0);
			minecraft.resizeGui();
		});
		context.waitTicks(3);

		// A small window: text wraps, buttons shrink, Vanilla Tweaks drops to one column
		// 640x480 at GUI scale 2 is 320 GUI pixels wide, the narrowest Minecraft allows
		context.getInput().resizeWindow(640, 480);
		context.runOnClient(minecraft -> {
			minecraft.options.guiScale().set(2);
			minecraft.resizeGui();
		});
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-small-switches");
		context.setScreen(() -> new VanillaTweaksScreen(new TweakSwitchScreen(null)));
		context.waitFor(minecraft -> staticField("packs") != null, 600);
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
			minecraft.resizeGui();
		});
		context.setScreen(() -> new TweakSwitchScreen(null));
		context.waitTicks(5);

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
		// Previews start off; a Shift tap on its own turns them on
		if (Settings.get().previews) {
			throw new AssertionError("Previews should start off");
		}
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

		// Pick the first two packs (pressed directly, since the cursor is imprecise)
		for (AbstractWidget row : firstTwo) {
			context.runOnClient(minecraft -> ((AbstractButton) row).onPress(null));
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
		context.runOnClient(minecraft -> minecraft.gui.toastManager().clear());
		context.waitTicks(2);
		context.takeScreenshot("tweakswitch-switches-downloaded");

		// Modrinth: search results load, a hovered pack shows its picture, and a picked pack downloads as a switch
		context.clickScreenButton("tweakswitch.getModrinth");
		context.waitForScreen(ModrinthScreen.class);
		context.waitFor(minecraft -> !rows(minecraft).isEmpty(), 600);
		context.waitTicks(5);
		context.runOnClient(minecraft -> minecraft.gui.toastManager().clear());
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
		context.waitFor(minecraft -> "sky".equals(field(ModrinthScreen.class, null, "query")) && !(boolean) field(ModrinthScreen.class, null, "loading"), 600);
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-modrinth-search");
		context.runOnClient(minecraft -> ((Button) field(DownloadScreen.class, minecraft.gui.screen(), "downloadButton")).onPress(null));
		context.waitFor(minecraft -> field(DownloadScreen.class, null, "progress") == null, 1200);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(10);
		context.runOnClient(minecraft -> minecraft.gui.toastManager().clear());
		context.waitTicks(2);
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
		context.runOnClient(minecraft -> minecraft.gui.toastManager().clear());
		context.waitTicks(2);
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
		context.runOnClient(minecraft -> minecraft.gui.toastManager().clear());
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
				method.invoke(minecraft.gui.screen(), "flame");
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		context.waitTicks(3);
		Map<AbstractWidget, ?> shownRows = context.computeOnClient(minecraft -> (Map<AbstractWidget, ?>) field(TweakSwitchScreen.class, minecraft.gui.screen(), "switchRows"));
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
				method.invoke(minecraft.gui.screen(), "");
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
				method.invoke(minecraft.gui.screen(), "particles");
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
				method.invoke(minecraft.gui.screen(), "dripping");
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		context.waitTicks(5);
		AbstractWidget lavaRow = context.computeOnClient(minecraft -> ((Map<AbstractWidget, Pack>) field(TweakSwitchScreen.class, minecraft.gui.screen(), "switchRows"))
			.entrySet().stream().filter(entry -> entry.getValue().getTitle().getString().equals("particles-dripping-lava")).findFirst().orElseThrow().getKey());
		moveTo(context, lavaRow);
		context.waitTicks(30);
		context.takeScreenshot("tweakswitch-split-pack-preview");
		context.getInput().setCursorPos(0, 0);
		context.runOnClient(minecraft -> Settings.get().groupBy = TweakSwitchScreen.GroupBy.SOURCE.name());
		context.waitTicks(3);

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
		context.runOnClient(minecraft -> minecraft.gui.toastManager().clear());
		context.waitTicks(2);
		context.takeScreenshot("tweakswitch-updates");
		context.runOnClient(minecraft -> ((Button) field(DownloadScreen.class, minecraft.gui.screen(), "downloadButton")).onPress(null));
		context.waitFor(minecraft -> field(DownloadScreen.class, null, "progress") == null
			&& field(UpdatesScreen.class, minecraft.gui.screen(), "items") != null, 1200);
		context.waitTicks(5);
		context.runOnClient(minecraft -> minecraft.gui.toastManager().clear());
		context.waitTicks(2);
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
			AbstractButton row = (AbstractButton) rows(minecraft).entrySet().stream()
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
			String meta = "{\"pack\":{\"description\":\"Crystal Vanilla Tweaks | %s\",\"min_format\":84,\"max_format\":97}}";
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
