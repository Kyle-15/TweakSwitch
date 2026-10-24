package com.tweakswitch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.tweakswitch.ui.Draw;
import com.tweakswitch.ui.SectionHeader;
import com.tweakswitch.ui.Theme;
import com.tweakswitch.ui.ThemedButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Shared layout for the TweakSwitch screens: title and search box on top, a {@link PackList} in the middle, buttons at the
 * bottom. Everything shrinks to fit narrow windows.
 */
public abstract class PackListScreen extends Screen {
	private static final int HEADER_HEIGHT = 4 + 9 + 6 + 15 + 6;
	private static final int SEARCH_WIDTH = 200;
	private static final int FOOTER_GAP = 6;

	protected final @Nullable Screen lastScreen;
	protected final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	protected @Nullable PackList list;
	private @Nullable EditBox search;
	private final List<AbstractWidget> footerWidgets = new ArrayList<>();
	private final List<Integer> footerWidths = new ArrayList<>();
	private boolean refreshQueued;

	protected PackListScreen(final @Nullable Screen lastScreen, final Component title) {
		super(title);
		this.lastScreen = lastScreen;
	}

	/** The colors this screen is drawn in. */
	protected Theme theme() {
		return Theme.HOME;
	}

	/** The title as shown in the header; screens can restyle it. */
	protected Component styledTitle() {
		return this.title.copy().withStyle(style -> style.withBold(true));
	}

	/** Screens without a search box (like the mods warning) return false. */
	protected boolean hasSearch() {
		return true;
	}

	@Override
	protected void init() {
		if (this.hasSearch()) {
			this.layout.setHeaderHeight(HEADER_HEIGHT);
			LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(6));
			header.defaultCellSetting().alignHorizontallyCenter();
			header.addChild(new StringWidget(this.styledTitle(), this.font));
			String previousSearch = this.search != null ? this.search.getValue() : "";
			this.search = header.addChild(new EditBox(this.font, 0, 0, SEARCH_WIDTH, 15, Component.translatable("tweakswitch.search")));
			// Drawn on our own rounded box (see extractBackground) instead of vanilla's black field
			this.search.setBordered(false);
			this.search.setTextColor(this.theme().text());
			this.search.setHint(Component.translatable("tweakswitch.search").withColor(this.theme().muted()));
			this.search.setValue(previousSearch);
			this.search.setResponder(value -> {
				this.refreshList();
				if (this.list != null) {
					this.list.setScrollAmount(0.0);
				}
			});
		} else {
			this.layout.addTitleHeader(this.styledTitle(), this.font);
		}
		this.list = this.layout.addToContents(new PackList(this.minecraft, this.theme(), this.width, this.layout.getContentHeight(), this.layout.getHeaderHeight()));
		this.addFooter();
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	/** Adds the bottom buttons, usually with {@link #addFooterButtons}. */
	protected abstract void addFooter();

	/** A row of bottom buttons at their given widths, shrunk evenly if the window is too narrow for them. */
	protected void addFooterButtons(final AbstractWidget... widgets) {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(FOOTER_GAP));
		for (AbstractWidget widget : widgets) {
			footer.addChild(widget);
			this.footerWidgets.add(widget);
			this.footerWidths.add(widget.getWidth());
		}
	}

	@Override
	protected void repositionElements() {
		int preferred = this.footerWidths.stream().mapToInt(Integer::intValue).sum();
		int available = this.width - 12 - FOOTER_GAP * Math.max(0, this.footerWidgets.size() - 1);
		double scale = preferred > available ? (double) available / preferred : 1.0;
		for (int i = 0; i < this.footerWidgets.size(); i++) {
			this.footerWidgets.get(i).setWidth(Math.max(30, (int) (this.footerWidths.get(i) * scale)));
		}
		if (this.search != null) {
			this.search.setWidth(Math.min(SEARCH_WIDTH, this.width - 20));
		}
		this.layout.arrangeElements();
		if (this.list != null) {
			this.list.updateSize(this.width, this.layout);
			// Text wrapping and grid columns depend on the width
			this.refreshList();
		}
	}

	/** The theme's background, darker bars behind the header and footer, and a rounded box behind the search text. */
	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		Theme theme = this.theme();
		graphics.fill(0, 0, this.width, this.height, theme.background());
		graphics.fill(0, 0, this.width, this.layout.getHeaderHeight(), theme.bar());
		graphics.fill(0, this.height - this.layout.getFooterHeight(), this.width, this.height, theme.bar());
		if (this.search != null) {
			boolean focused = this.search.isFocused();
			Draw.roundRectOutlined(graphics, this.search.getX() - 5, this.search.getY() - 4, this.search.getWidth() + 10, this.search.getHeight() + 2, 5,
				theme.card(), focused ? theme.accent() : theme.border());
		}
	}

	/** Makes a bottom-bar button in this screen's theme. */
	protected ThemedButton button(final Component label, final int width, final Button.OnPress onPress) {
		return ThemedButton.secondary(this.theme(), label, width, onPress);
	}

	/** Makes the main bottom-bar button, filled with the theme's accent color. */
	protected ThemedButton primaryButton(final Component label, final int width, final Button.OnPress onPress) {
		return ThemedButton.primary(this.theme(), label, width, onPress);
	}

	@Override
	protected void setInitialFocus() {
		if (this.search != null) {
			this.setInitialFocus(this.search);
		} else {
			super.setInitialFocus();
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.lastScreen);
	}

	/** Rebuilds the rows, keeping the scroll position so expanding a category doesn't jump to the top. */
	protected void refreshList() {
		if (this.list != null) {
			double scroll = this.list.scrollAmount();
			this.list.clear();
			this.fillList(this.list);
			this.list.setScrollAmount(scroll);
		}
	}

	/** Adds the rows that match {@link #matches}. Called again whenever the search or window size changes. */
	protected abstract void fillList(PackList list);

	/**
	 * Adds a clickable "▶ Name (count)" row that shows or hides the rows {@code addRows} adds. {@code toggled} holds the
	 * categories the player flipped away from {@code openByDefault}. Every category is open while searching, so matches are
	 * never hidden.
	 */
	protected void addCategory(
		final PackList list, final Set<String> toggled, final boolean openByDefault, final String name, final String count, final Runnable addRows
	) {
		boolean open = this.isSearching() || openByDefault != toggled.contains(name);
		list.addRow(new SectionHeader(this.theme(), name, count, open, () -> {
			if (this.isSearching()) {
				return;
			}
			if (!toggled.remove(name)) {
				toggled.add(name);
			}
			this.queueRefresh();
		}), 18);
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

	protected void setSearchText(final String text) {
		if (this.search != null) {
			this.search.setValue(text);
		}
	}

	protected String searchText() {
		return this.search != null ? this.search.getValue().trim() : "";
	}

	protected boolean isSearching() {
		return this.search != null && !this.search.getValue().isBlank();
	}

	protected boolean matches(final String... texts) {
		String query = this.searchText().toLowerCase(Locale.ROOT);
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

	/** The button under the mouse in the list, or null. */
	protected @Nullable AbstractWidget hoveredListWidget(final double mouseX, final double mouseY) {
		return this.list != null ? this.list.hoveredWidget(mouseX, mouseY) : null;
	}
}
