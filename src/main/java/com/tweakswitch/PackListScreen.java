package com.tweakswitch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.mojang.blaze3d.platform.InputConstants;
import com.tweakswitch.ui.Draw;
import net.minecraft.ChatFormatting;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
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
	/** The preview panel's width in GUI pixels; pictures are decoded to fit it sharply. */
	public static final int PREVIEW_WIDTH = 160;
	/** Compact: title, search box and any extra buttons share one row. */
	private static final int HEADER_HEIGHT = 28;
	private static final int FOOTER_HEIGHT = 26;
	private static final int SEARCH_WIDTH = 200;
	private static final int MIN_SEARCH_WIDTH = 60;
	private static final int SEARCH_HEIGHT = 14;
	private static final int HEADER_GAP = 8;
	private static final int FOOTER_GAP = 6;
	private static final String PREVIEW_HINT = "⇧ Preview";
	private static final int PANEL_PADDING = 6;
	private static final int LINE_HEIGHT = 10;
	private static final int MAX_DETAIL_LINES = 6;
	private static final long HOVER_DELAY_MS = 250;
	private static final long SECONDS_PER_IMAGE_MS = 2500;
	private static final int TEXTURE_COLUMNS = 4;

	/**
	 * What the preview panel shows for one hovered item: pictures from a website (stepped through, animations play), or
	 * textures from inside the pack (shown as a grid).
	 *
	 * @param key identifies the item, so the panel knows when the mouse moved to another one
	 */
	protected record Preview(Object key, Component title, Component details, List<String> imageUrls, List<Identifier> textures) {
	}

	protected final @Nullable Screen lastScreen;
	protected final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	protected @Nullable PackList list;
	private @Nullable EditBox search;
	private @Nullable StringWidget titleWidget;
	private final List<AbstractWidget> headerExtras = new ArrayList<>();
	/** Whether the "⇧ Preview" hint fits in the header beside everything else. */
	private boolean hintFits;
	private final List<AbstractWidget> footerWidgets = new ArrayList<>();
	private final List<Integer> footerWidths = new ArrayList<>();
	private boolean refreshQueued;
	/** True from a Shift press until another key or click; a release while still true counts as a Shift tap. */
	private boolean shiftTapped;
	// Preview panel state: which item is hovered, since when, and which of its pictures is showing
	private @Nullable Object previewKey;
	private long hoverStart;
	private int imageIndex;
	private long imageStart;

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
		this.layout.setHeaderHeight(HEADER_HEIGHT);
		this.layout.setFooterHeight(FOOTER_HEIGHT);
		if (this.hasSearch()) {
			// Left-aligned, so the space kept for the "⇧ Preview" hint ends up on the right instead of split across both sides
			LinearLayout header = this.layout.addToHeader(LinearLayout.horizontal().spacing(HEADER_GAP), settings -> settings.alignHorizontallyLeft().paddingLeft(8));
			header.defaultCellSetting().alignVerticallyMiddle();
			this.titleWidget = header.addChild(new StringWidget(this.styledTitle(), this.font));
			String previousSearch = this.search != null ? this.search.getValue() : "";
			this.search = header.addChild(new EditBox(this.font, 0, 0, SEARCH_WIDTH, SEARCH_HEIGHT, Component.translatable("tweakswitch.search")));
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
			this.headerExtras.clear();
			for (AbstractWidget extra : this.createHeaderExtras()) {
				this.headerExtras.add(header.addChild(extra));
			}
		} else {
			this.layout.addTitleHeader(this.styledTitle(), this.font);
		}
		this.list = this.layout.addToContents(new PackList(this.minecraft, this.theme(), this.width, this.layout.getContentHeight(), this.layout.getHeaderHeight()));
		this.addFooter();
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	/** Buttons beside the search box, like the TweakSwitch screen's "Group by". */
	protected List<AbstractWidget> createHeaderExtras() {
		return List.of();
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
			// The search box takes whatever the title, extra buttons and the "⇧ Preview" hint leave
			int taken = (this.titleWidget != null ? this.titleWidget.getWidth() : 0)
				+ this.headerExtras.stream().mapToInt(AbstractWidget::getWidth).sum()
				+ HEADER_GAP * (1 + this.headerExtras.size());
			int hint = this.hasPreviews() ? this.font.width(PREVIEW_HINT) + 12 : 0;
			int room = this.width - 8 - 8 - taken;
			this.hintFits = hint > 0 && room - hint >= MIN_SEARCH_WIDTH;
			this.search.setWidth(Math.max(MIN_SEARCH_WIDTH, Math.min(SEARCH_WIDTH, this.hintFits ? room - hint : room)));
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

	/** What to preview for a hovered list widget, or null for nothing. */
	protected @Nullable Preview previewFor(final AbstractWidget hovered) {
		return null;
	}

	/** Whether the Shift preview panel is on. Widgets hide their plain tooltips while it is. */
	public static boolean previewsOn() {
		return Settings.get().previews;
	}

	private static boolean isShift(final int key) {
		return key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT;
	}

	/** Tapping Shift on its own flips previews; Shift held for capitals while typing doesn't. */
	@Override
	public boolean keyPressed(final KeyEvent event) {
		this.shiftTapped = isShift(event.key());
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(final KeyEvent event) {
		if (isShift(event.key()) && this.shiftTapped) {
			Settings settings = Settings.get();
			settings.previews = !settings.previews;
			settings.save();
		}
		this.shiftTapped = false;
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		this.shiftTapped = false;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		this.extractPreviewHint(graphics);
		AbstractWidget hovered = this.hoveredListWidget(mouseX, mouseY);
		Preview preview = hovered != null && this.hasPreviews() && previewsOn() ? this.previewFor(hovered) : null;
		long now = Util.getMillis();
		Object key = preview != null ? preview.key() : null;
		if (!java.util.Objects.equals(key, this.previewKey)) {
			this.previewKey = key;
			this.hoverStart = now;
			this.imageIndex = 0;
			this.imageStart = now;
		}
		if (preview == null) {
			return;
		}
		RemoteImages.Image image = this.currentImage(preview.imageUrls(), now);
		// Like a tooltip, wait a moment so the panel doesn't flash up while the mouse just passes over the list
		if (now - this.hoverStart >= HOVER_DELAY_MS) {
			this.extractPreviewPanel(graphics, preview, image, now, mouseX, mouseY);
		}
	}

	/**
	 * "⇧ Preview" in the header's right corner, lit in the accent color while previews are on, so the Shift toggle can be
	 * found. Left out when the window is too narrow for it.
	 */
	private void extractPreviewHint(final GuiGraphicsExtractor graphics) {
		if (!this.hasPreviews() || !this.hintFits) {
			return;
		}
		boolean on = previewsOn();
		// Just right of the title/search/buttons row; room for it was kept when sizing the search box
		int rowRight = this.search != null ? this.search.getRight() : 0;
		for (AbstractWidget extra : this.headerExtras) {
			rowRight = Math.max(rowRight, extra.getRight());
		}
		int x = Math.min(rowRight + HEADER_GAP + 4, this.width - this.font.width(PREVIEW_HINT) - 8);
		int y = (this.layout.getHeaderHeight() - 8) / 2;
		if (on) {
			Draw.roundRect(graphics, x - 4, y - 3, this.font.width(PREVIEW_HINT) + 8, 14, 7, this.theme().accent());
		}
		graphics.text(this.font, PREVIEW_HINT, x, y, on ? this.theme().onAccent() : this.theme().muted(), false);
	}

	/** Screens whose items can be previewed show the Shift hint. */
	protected boolean hasPreviews() {
		return true;
	}

	/**
	 * The picture to show now. Several pictures step through: each stays up for a few seconds, or one full loop if it's
	 * animated. Pictures that fail to load are skipped.
	 */
	private RemoteImages.Image currentImage(final List<String> urls, final long now) {
		if (urls.isEmpty()) {
			return RemoteImages.Image.FAILED;
		}
		for (int tries = 0; tries < urls.size(); tries++) {
			RemoteImages.Image image = RemoteImages.get(urls.get(this.imageIndex % urls.size()));
			if (image.failed() && urls.size() > 1) {
				this.imageIndex++;
				this.imageStart = now;
				continue;
			}
			if (!image.loaded()) {
				// Not loaded yet: its time on screen starts once it is
				this.imageStart = now;
			} else if (urls.size() > 1 && now - this.imageStart >= Math.max(SECONDS_PER_IMAGE_MS, image.durationMs())) {
				this.imageIndex++;
				this.imageStart = now;
				continue;
			}
			return image;
		}
		return RemoteImages.Image.FAILED;
	}

	/** A tooltip-like panel on the half of the screen away from the cursor, so the hovered item stays visible. */
	private void extractPreviewPanel(
		final GuiGraphicsExtractor graphics, final Preview preview, final RemoteImages.Image image, final long now, final int mouseX, final int mouseY
	) {
		Theme theme = this.theme();
		int panelWidth = Math.min(PREVIEW_WIDTH, this.width / 2 - 2 * PANEL_PADDING - 8);
		int imageCount = preview.imageUrls().size();

		// Picture area: website picture, or a grid of the pack's own textures
		int imageHeight = 0;
		int imageWidth = panelWidth;
		int cell = (panelWidth - (TEXTURE_COLUMNS - 1) * 2) / TEXTURE_COLUMNS;
		List<Identifier> textures = preview.textures().subList(0, Math.min(preview.textures().size(), TEXTURE_COLUMNS * 4));
		if (!textures.isEmpty()) {
			int rows = (textures.size() + TEXTURE_COLUMNS - 1) / TEXTURE_COLUMNS;
			imageHeight = rows * cell + (rows - 1) * 2;
		} else if (image.loaded()) {
			imageHeight = Math.min(this.height / 3, image.height() * panelWidth / Math.max(1, image.width()));
			imageWidth = Math.min(panelWidth, image.width() * imageHeight / Math.max(1, image.height()));
		} else if (!image.failed()) {
			imageHeight = 9;
		}

		List<FormattedCharSequence> lines = new ArrayList<>(this.font.split(preview.title().copy().withStyle(ChatFormatting.BOLD), panelWidth));
		List<FormattedCharSequence> details = this.font.split(preview.details(), panelWidth);
		if (details.size() > MAX_DETAIL_LINES) {
			details = new ArrayList<>(details.subList(0, MAX_DETAIL_LINES));
			details.add(Component.literal("…").withStyle(ChatFormatting.GRAY).getVisualOrderText());
		}
		lines.addAll(details);
		int boxWidth = panelWidth + 2 * PANEL_PADDING;
		int boxHeight = imageHeight + (imageHeight > 0 ? PANEL_PADDING : 0) + lines.size() * LINE_HEIGHT + 2 * PANEL_PADDING;

		int x = mouseX < this.width / 2 ? this.width - boxWidth - 4 : 4;
		int y = Math.max(4, Math.min(mouseY - boxHeight / 2, this.height - boxHeight - 4));

		graphics.nextStratum();
		Draw.roundRectOutlined(graphics, x, y, boxWidth, boxHeight, 6, theme.bar(), theme.accent());
		int contentY = y + PANEL_PADDING;
		Identifier texture = image.textureAt(now - this.imageStart);
		if (!textures.isEmpty()) {
			for (int i = 0; i < textures.size(); i++) {
				int cellX = x + PANEL_PADDING + (i % TEXTURE_COLUMNS) * (cell + 2);
				int cellY = contentY + (i / TEXTURE_COLUMNS) * (cell + 2);
				graphics.fill(cellX, cellY, cellX + cell, cellY + cell, theme.card());
				// Pack textures are pixel art: drawn crisp, filling the cell
				graphics.blit(RenderPipelines.GUI_TEXTURED, textures.get(i), cellX, cellY, 0.0F, 0.0F, cell, cell, cell, cell);
			}
			contentY += imageHeight + PANEL_PADDING;
		} else if (texture != null) {
			int imageX = x + PANEL_PADDING + (panelWidth - imageWidth) / 2;
			graphics.blit(texture, imageX, contentY, imageX + imageWidth, contentY + imageHeight, 0.0F, 1.0F, 0.0F, 1.0F);
			if (imageCount > 1) {
				// "2/5" in the picture's corner when there are more pictures to come
				String counter = (this.imageIndex % imageCount + 1) + "/" + imageCount;
				int counterX = imageX + imageWidth - this.font.width(counter) - 2;
				int counterY = contentY + imageHeight - 10;
				graphics.fill(counterX - 2, counterY - 1, imageX + imageWidth, contentY + imageHeight, 0xA0000000);
				graphics.text(this.font, counter, counterX, counterY, -1);
			}
			contentY += imageHeight + PANEL_PADDING;
		} else if (!image.failed()) {
			graphics.text(this.font, Component.translatable("tweakswitch.previewLoading").withStyle(ChatFormatting.GRAY), x + PANEL_PADDING, contentY, -1);
			contentY += imageHeight + PANEL_PADDING;
		}
		for (FormattedCharSequence line : lines) {
			graphics.text(this.font, line, x + PANEL_PADDING, contentY, -1);
			contentY += LINE_HEIGHT;
		}
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
		}), SectionHeader.HEIGHT);
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
