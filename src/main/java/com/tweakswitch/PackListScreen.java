package com.tweakswitch;

import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Shared layout for the TweakSwitch screens: title and search box on top, a scrolling list of buttons below. */
public abstract class PackListScreen extends OptionsSubScreen {
	private static final int HEADER_HEIGHT = 4 + 9 + 6 + 15 + 6;

	private @Nullable EditBox search;
	private boolean refreshQueued;

	protected PackListScreen(final @Nullable Screen lastScreen, final Component title) {
		super(lastScreen, Minecraft.getInstance().options, title);
	}

	@Override
	protected void addTitle() {
		this.layout.setHeaderHeight(HEADER_HEIGHT);
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(6));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font));
		String previousSearch = this.search != null ? this.search.getValue() : "";
		this.search = header.addChild(new EditBox(this.font, 0, 0, 200, 15, Component.translatable("tweakswitch.search")));
		this.search.setHint(Component.translatable("tweakswitch.search").withStyle(EditBox.SEARCH_HINT_STYLE));
		this.search.setValue(previousSearch);
		this.search.setResponder(value -> {
			this.refreshList();
			if (this.list != null) {
				this.list.setScrollAmount(0.0);
			}
		});
	}

	@Override
	protected void addContents() {
		this.list = this.layout.addToContents(new PackList(this.minecraft, this.width, this));
		this.addOptions();
	}

	@Override
	protected void addOptions() {
		this.refreshList();
	}

	@Override
	protected void setInitialFocus() {
		if (this.search != null) {
			this.setInitialFocus(this.search);
		} else {
			super.setInitialFocus();
		}
	}

	/** Rebuilds the rows, keeping the scroll position so expanding a category doesn't jump to the top. */
	protected void refreshList() {
		if (this.list instanceof PackList packList) {
			double scroll = packList.scrollAmount();
			packList.clear();
			this.fillList(packList);
			packList.setScrollAmount(scroll);
		}
	}

	/**
	 * Adds a clickable "▶ Name (count)" row that shows or hides the rows {@code addRows} adds. {@code toggled} holds the
	 * categories the player flipped away from {@code openByDefault}. Every category is open while searching, so matches are
	 * never hidden.
	 */
	protected void addCategory(
		final OptionsList list, final Set<String> toggled, final boolean openByDefault, final String name, final String count, final Runnable addRows
	) {
		boolean open = this.isSearching() || openByDefault != toggled.contains(name);
		Component label = Component.literal((open ? "▼ " : "▶ ") + name + " ").append(Component.literal("(" + count + ")").withStyle(ChatFormatting.GRAY));
		list.addBig(Button.builder(label, button -> {
			if (this.isSearching()) {
				return;
			}
			if (!toggled.remove(name)) {
				toggled.add(name);
			}
			this.queueRefresh();
		}).build());
		if (open) {
			addRows.run();
		}
	}

	/** Refreshes on the next tick, for buttons in the list that would otherwise remove themselves while being clicked. */
	protected void queueRefresh() {
		this.refreshQueued = true;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.refreshQueued) {
			this.refreshQueued = false;
			this.refreshList();
		}
	}

	protected boolean isSearching() {
		return this.search != null && !this.search.getValue().isBlank();
	}

	/** Adds the rows that match {@link #matches}. Called again whenever the search changes. */
	protected abstract void fillList(OptionsList list);

	protected boolean matches(final String... texts) {
		String query = this.search != null ? this.search.getValue().toLowerCase(Locale.ROOT).trim() : "";
		if (query.isEmpty()) {
			return true;
		}
		for (String text : texts) {
			if (text.toLowerCase(Locale.ROOT).contains(query)) {
				return true;
			}
		}
		return false;
	}

	/** The button under the mouse in the list, or null. Only counts rows actually drawn this frame. */
	protected @Nullable AbstractWidget hoveredListWidget(final double mouseX, final double mouseY) {
		return this.list instanceof PackList packList ? packList.hoveredWidget(mouseX, mouseY) : null;
	}

	private static class PackList extends OptionsList {
		PackList(final Minecraft minecraft, final int width, final OptionsSubScreen screen) {
			super(minecraft, width, screen);
		}

		void clear() {
			this.clearEntries();
		}

		@Nullable AbstractWidget hoveredWidget(final double mouseX, final double mouseY) {
			OptionsList.AbstractEntry entry = this.getHovered();
			if (entry != null) {
				for (var child : entry.children()) {
					if (child instanceof AbstractWidget widget && widget.isMouseOver(mouseX, mouseY)) {
						return widget;
					}
				}
			}
			return null;
		}
	}
}
