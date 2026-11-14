package com.tweakswitch;

import com.tweakswitch.compat.Compat;
import com.tweakswitch.ui.ActionRow;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Named sets of switches. Type a name and click Save Current to keep what's on now; click a preset to switch to it. The
 * same screen exports the whole pack list to a file, and takes an exported file dropped onto it.
 */
public class PresetsScreen extends PackListScreen {
	private final TweakSwitchScreen parent;
	private @Nullable Button saveButton;

	public PresetsScreen(final TweakSwitchScreen parent) {
		super(parent, Component.translatable("tweakswitch.presets.title"));
		this.parent = parent;
	}

	/** The box at the top is where a new preset's name goes; it also narrows the list. */
	@Override
	protected Component searchHint() {
		return Component.translatable("tweakswitch.presets.name");
	}

	@Override
	protected boolean hasPreviews() {
		return false;
	}

	@Override
	protected void addFooter() {
		this.saveButton = this.primaryButton(Component.translatable("tweakswitch.presets.save"), 110, button -> this.saveCurrent())
			.withTooltip(Component.translatable("tweakswitch.presets.save.tooltip"));
		this.addFooterButtons(
			this.saveButton,
			this.button(Component.translatable("tweakswitch.export"), 70, button -> this.parent.exportSwitches())
				.withTooltip(Component.translatable("tweakswitch.export.tooltip")),
			this.button(CommonComponents.GUI_DONE, 70, button -> this.onClose())
		);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.saveButton != null) {
			this.saveButton.active = !this.searchText().isEmpty();
		}
	}

	private List<String> currentFiles() {
		return this.parent.onIds().stream().map(id -> id.substring(Presets.ID_PREFIX.length())).toList();
	}

	/** Saves the switches that are on now under the name in the box, replacing a preset of the same name. */
	public void saveCurrent() {
		String name = this.searchText();
		if (name.isEmpty()) {
			return;
		}
		List<String> files = this.currentFiles();
		Presets.put(name, files);
		this.setSearchText("");
		SystemToast.addOrUpdate(Compat.toasts(this.minecraft), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
			Component.translatable("tweakswitch.presets.saved", name), Component.translatable(files.size() == 1 ? "tweakswitch.presets.countOne" : "tweakswitch.presets.count", files.size()));
		this.refreshList();
	}

	@Override
	protected void fillList(final PackList list) {
		Map<String, List<String>> presets = Presets.load();
		if (presets.isEmpty()) {
			list.addText(Component.translatable("tweakswitch.presets.empty").withStyle(ChatFormatting.GRAY));
		} else {
			list.addText(Component.translatable("tweakswitch.presets.explain").withStyle(ChatFormatting.GRAY));
		}
		Path folder = TweakSwitch.packsDir();
		String typed = this.searchText().toLowerCase(Locale.ROOT);
		presets.forEach((name, files) -> {
			if (!typed.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(typed)) {
				return;
			}
			long missing = files.stream().filter(file -> !Files.exists(folder.resolve(file))).count();
			String note = Component.translatable(missing > 0 ? "tweakswitch.presets.countMissing" : files.size() == 1 ? "tweakswitch.presets.countOne" : "tweakswitch.presets.count",
				files.size(), missing).getString();
			boolean current = name.equals(Settings.get().lastPreset) && files.equals(this.currentFiles());
			list.addRow(new ActionRow(this.theme(), null, (current ? "● " : "") + name, note, Component.translatable("tweakswitch.presets.apply").getString(), false,
				() -> this.apply(name, files),
				List.of(new ActionRow.Extra("✕", Component.translatable("tweakswitch.delete"), () -> this.confirmRemove(name), true)))
				.withTooltip(Component.translatable("tweakswitch.presets.apply.tooltip", name)));
		});
		list.addText(Component.translatable("tweakswitch.presets.tips").withStyle(ChatFormatting.DARK_GRAY));
	}

	/** Switches to the preset straight away (one reload) and goes back to the switches. */
	public void apply(final String name, final List<String> files) {
		this.parent.applyPreset(name, files);
		Compat.setScreen(this.minecraft, this.parent);
	}

	private void confirmRemove(final String name) {
		Compat.setScreen(this.minecraft, new ConfirmScreen(confirmed -> {
			if (confirmed) {
				Presets.remove(name);
			}
			Compat.setScreen(this.minecraft, this);
			this.refreshList();
		}, Component.translatable("tweakswitch.presets.delete.title", name), Component.translatable("tweakswitch.presets.delete.message"),
			Component.translatable("tweakswitch.delete"), CommonComponents.GUI_CANCEL));
	}

	/** An exported switch list dropped here is imported on the TweakSwitch screen. */
	@Override
	public void onFilesDrop(final List<Path> files) {
		for (Path file : files) {
			if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
				Compat.setScreen(this.minecraft, this.parent);
				this.parent.importSwitches(file);
				return;
			}
		}
	}
}
