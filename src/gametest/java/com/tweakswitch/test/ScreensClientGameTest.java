package com.tweakswitch.test;

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

		context.clickScreenButton("tweakswitch.getPacks");
		context.waitForScreen(VanillaTweaksScreen.class);
		context.waitFor(minecraft -> !rows(minecraft).isEmpty() || staticField("packs") != null, 600);
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

		context.runOnClient(minecraft -> ((Button) instanceField(minecraft.gui.screen(), "downloadButton")).onPress(null));
		context.waitFor(minecraft -> staticField("progress") == null, 1200);
		context.waitTicks(10);
		context.takeScreenshot("tweakswitch-picker-downloaded");

		context.clickScreenButton("gui.done");
		context.waitForScreen(TweakSwitchScreen.class);
		context.waitTicks(5);
		context.takeScreenshot("tweakswitch-switches-downloaded");

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

	private static Object instanceField(final Object screen, final String name) {
		try {
			Field field = VanillaTweaksScreen.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(screen);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<AbstractWidget, ?> rows(final Minecraft minecraft) {
		if (!(minecraft.gui.screen() instanceof VanillaTweaksScreen screen)) {
			return Map.of();
		}
		try {
			Field field = VanillaTweaksScreen.class.getDeclaredField("rows");
			field.setAccessible(true);
			return (Map<AbstractWidget, ?>) field.get(screen);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}
}
