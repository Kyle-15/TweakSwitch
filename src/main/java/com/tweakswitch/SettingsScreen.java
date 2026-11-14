package com.tweakswitch;

import com.tweakswitch.compat.Compat;
import com.tweakswitch.ui.Theme;
import com.tweakswitch.ui.ThemedButton;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Every TweakSwitch setting in one place. Opened from the ⚙ button on the TweakSwitch screen and from Mod Menu. */
public class SettingsScreen extends PackListScreen {
	/** True once the size or color changed, so the screen underneath is rebuilt when this one closes. */
	private final boolean lookChanged;

	public SettingsScreen(final @Nullable Screen lastScreen) {
		this(lastScreen, false);
	}

	private SettingsScreen(final @Nullable Screen lastScreen, final boolean lookChanged) {
		super(lastScreen, Component.translatable("tweakswitch.settings.title"));
		this.lookChanged = lookChanged;
	}

	@Override
	protected boolean hasSearch() {
		return false;
	}

	@Override
	protected boolean hasPreviews() {
		return false;
	}

	@Override
	protected void addFooter() {
		List<AbstractWidget> buttons = new ArrayList<>();
		if (!(this.lastScreen instanceof TweakSwitchScreen)) {
			// From Mod Menu: a way on to the switches themselves
			buttons.add(this.button(Component.translatable("tweakswitch.settings.open"), 120, button -> Compat.setScreen(this.minecraft, new TweakSwitchScreen(this.lastScreen))));
		}
		buttons.add(this.primaryButton(CommonComponents.GUI_DONE, 80, button -> this.onClose()));
		this.addFooterButtons(buttons.toArray(AbstractWidget[]::new));
	}

	/** A button reading "Name: Value" that runs {@code change} and shows the new value. */
	private ThemedButton option(final String key, final Supplier<Component> value, final Runnable change) {
		ThemedButton button = new ThemedButton(this.theme(), ThemedButton.Kind.SECONDARY, Component.empty(), 150, pressed -> {
			change.run();
			Settings.get().save();
		}) {
			@Override
			public Component getMessage() {
				return Component.translatable("tweakswitch.settings.option", Component.translatable(key), value.get());
			}
		};
		return button.withTooltip(Component.translatable(key + ".tooltip"));
	}

	private static Component onOff(final boolean on) {
		return Component.translatable(on ? "options.on" : "options.off");
	}

	private static String next(final String current, final String... values) {
		for (int i = 0; i < values.length; i++) {
			if (values[i].equals(current)) {
				return values[(i + 1) % values.length];
			}
		}
		return values[0];
	}

	/** Size and color are read when a screen is built, so this one is replaced to show the change. */
	private void reopen() {
		Settings.get().save();
		Compat.setScreen(this.minecraft, new SettingsScreen(this.lastScreen, true));
	}

	@Override
	protected void fillList(final PackList list) {
		Settings settings = Settings.get();
		list.addText(Component.translatable("tweakswitch.settings.section.look").withStyle(ChatFormatting.GRAY));
		list.addGrid(List.of(
			this.option("tweakswitch.settings.previews", () -> onOff(settings.previews), () -> settings.previews = !settings.previews),
			this.option("tweakswitch.settings.previewSize", () -> Component.translatable("tweakswitch.settings.size." + settings.previewSize.toLowerCase(java.util.Locale.ROOT)),
				() -> settings.previewSize = next(settings.previewSize, "SMALL", "NORMAL", "LARGE")),
			this.option("tweakswitch.settings.uiSize", () -> Component.translatable("tweakswitch.settings.ui." + settings.uiSize.toLowerCase(java.util.Locale.ROOT)), () -> {
				settings.uiSize = next(settings.uiSize, "COMPACT", "NORMAL");
				this.reopen();
			}),
			this.option("tweakswitch.settings.accent", () -> Component.translatable("tweakswitch.settings.accent." + Theme.Accent.fromSettings().name().toLowerCase(java.util.Locale.ROOT)), () -> {
				settings.accent = Theme.Accent.values()[(Theme.Accent.fromSettings().ordinal() + 1) % Theme.Accent.values().length].name();
				this.reopen();
			})
		), 2);
		list.addText(Component.translatable("tweakswitch.settings.section.packs").withStyle(ChatFormatting.GRAY));
		list.addGrid(List.of(
			this.option("tweakswitch.settings.switchOn", () -> onOff(settings.switchOnDownloads), () -> settings.switchOnDownloads = !settings.switchOnDownloads),
			this.option("tweakswitch.settings.clash", () -> onOff(settings.conflictWarnings), () -> settings.conflictWarnings = !settings.conflictWarnings)
		), 2);
		list.addText(Component.translatable("tweakswitch.settings.section.startup").withStyle(ChatFormatting.GRAY));
		list.addGrid(List.of(
			this.option("tweakswitch.settings.updateReminder", () -> onOff(settings.updateReminder), () -> settings.updateReminder = !settings.updateReminder),
			this.option("tweakswitch.settings.modUpdate", () -> onOff(settings.modUpdateCheck), () -> settings.modUpdateCheck = !settings.modUpdateCheck)
		), 2);
		list.addText(Component.translatable("tweakswitch.settings.section.more").withStyle(ChatFormatting.GRAY));
		list.addGrid(List.of(
			this.button(Component.translatable("tweakswitch.settings.keys"), 150, button -> Compat.setScreen(this.minecraft, new KeyBindsScreen(this, this.minecraft.options)))
				.withTooltip(Component.translatable("tweakswitch.settings.keys.tooltip")),
			this.button(Component.translatable("tweakswitch.openFolder"), 150, button -> {
				try {
					Files.createDirectories(TweakSwitch.packsDir());
				} catch (java.io.IOException e) {
					// Opening it will show what's wrong
				}
				Compat.openFolder(TweakSwitch.packsDir());
			}).withTooltip(Component.translatable("tweakswitch.settings.folder.tooltip"))
		), 2);
	}

	@Override
	public void onClose() {
		if (this.lookChanged && this.lastScreen instanceof TweakSwitchScreen switches) {
			Compat.setScreen(this.minecraft, switches.rebuilt());
		} else {
			super.onClose();
		}
	}
}
