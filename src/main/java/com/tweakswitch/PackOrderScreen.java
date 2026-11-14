package com.tweakswitch;

import com.tweakswitch.ui.ActionRow;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.jspecify.annotations.Nullable;

/**
 * The switched-on TweakSwitch packs in priority order, highest first: when two packs change the same thing, the one
 * higher up wins. ▲ and ▼ move a pack; the new order is applied when the TweakSwitch screen's Done is clicked.
 */
public class PackOrderScreen extends PackListScreen {
	private final PackRepository repository;
	private final Runnable onChanged;
	/** Pack ids, highest priority first. */
	private final List<String> order = new ArrayList<>();
	private final Map<String, Pack> packs = new HashMap<>();
	private boolean changed;

	/** @param onChanged run when the order was changed, so the opener knows resources need reloading */
	public PackOrderScreen(final @Nullable Screen lastScreen, final PackRepository repository, final Runnable onChanged) {
		super(lastScreen, Component.translatable("tweakswitch.order.title"));
		this.repository = repository;
		this.onChanged = onChanged;
		// The game lists the selection lowest priority first
		for (Pack pack : repository.getSelectedPacks()) {
			if (TweakSwitchPackSource.isOurs(pack)) {
				this.order.add(0, pack.getId());
				this.packs.put(pack.getId(), pack);
			}
		}
	}

	/** The order as it will be applied, highest priority first, for the screenshot test. */
	public List<String> orderForTest() {
		return List.copyOf(this.order);
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
		this.addFooterButtons(this.primaryButton(CommonComponents.GUI_DONE, 80, button -> this.onClose()));
	}

	@Override
	protected void fillList(final PackList list) {
		list.addText(Component.translatable(this.order.isEmpty() ? "tweakswitch.order.empty" : "tweakswitch.order.explain").withStyle(ChatFormatting.GRAY));
		for (int i = 0; i < this.order.size(); i++) {
			int position = i;
			Pack pack = this.packs.get(this.order.get(i));
			Path file = TweakSwitch.packsDir().resolve(TweakSwitchPackSource.fileName(pack));
			PackContents.Info contents = PackContents.cached(file);
			Identifier icon = TweakSwitchScreen.packIcon(pack);
			// What it changes, so packs that might overlap are easy to spot
			String note = contents != null ? contents.primary().label : "";
			list.addRow(new ActionRow(this.theme(), () -> icon, (i + 1) + ". " + pack.getTitle().getString(), note, "", false, () -> { }, List.of(
				new ActionRow.Extra("▲", Component.translatable("tweakswitch.order.up"), () -> this.move(position, -1), i > 0),
				new ActionRow.Extra("▼", Component.translatable("tweakswitch.order.down"), () -> this.move(position, 1), i < this.order.size() - 1)
			)).withTooltip(Component.translatable("tweakswitch.order.rowTooltip")));
		}
	}

	/** Moves the pack at {@code position} up (-1) or down (+1) one place. */
	public void move(final int position, final int by) {
		int target = position + by;
		if (position < 0 || position >= this.order.size() || target < 0 || target >= this.order.size()) {
			return;
		}
		Collections.swap(this.order, position, target);
		this.changed = true;
		this.queueRefresh();
	}

	@Override
	public void onClose() {
		if (this.changed) {
			List<String> lowestFirst = new ArrayList<>(this.order);
			Collections.reverse(lowestFirst);
			this.repository.setSelected(Presets.reorder(List.copyOf(this.repository.getSelectedIds()), lowestFirst));
			this.onChanged.run();
		}
		super.onClose();
	}
}
