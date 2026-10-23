package com.tweakswitch;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Scrolling list for the TweakSwitch screens. Unlike vanilla's options list, rows shrink to fit narrow windows, text wraps
 * instead of running off the sides, and grids drop to one column when there's no room for two.
 */
public class PackList extends ContainerObjectSelectionList<PackList.Entry> {
	private static final int MAX_ROW_WIDTH = 310;
	private static final int ROW_HEIGHT = 25;
	private static final int COLUMN_GAP = 10;
	private static final int LINE_HEIGHT = 10;
	/** Below this row width, grids use one column so names stay readable. */
	private static final int MIN_TWO_COLUMN_WIDTH = 260;

	public PackList(final Minecraft minecraft, final int width, final int height, final int y) {
		super(minecraft, width, height, y, ROW_HEIGHT);
		this.centerListVertically = false;
	}

	@Override
	public int getRowWidth() {
		return Math.max(80, Math.min(MAX_ROW_WIDTH, this.width - 24));
	}

	/** Centered text, wrapped to the row width. */
	public void addText(final Component text) {
		List<FormattedCharSequence> lines = this.minecraft.font.split(text, this.getRowWidth());
		this.addEntry(new TextEntry(lines), lines.size() * LINE_HEIGHT + 8);
	}

	/** One widget across the whole row. */
	public void addRow(final AbstractWidget widget) {
		this.addEntry(new WidgetRow(List.of(widget), 1), ROW_HEIGHT);
	}

	/** Widgets in rows of {@code columns}, or one per row if the list is too narrow. */
	public void addGrid(final List<? extends AbstractWidget> widgets, final int columns) {
		int perRow = this.getRowWidth() >= MIN_TWO_COLUMN_WIDTH ? columns : 1;
		for (int i = 0; i < widgets.size(); i += perRow) {
			this.addEntry(new WidgetRow(List.copyOf(widgets.subList(i, Math.min(widgets.size(), i + perRow))), perRow), ROW_HEIGHT);
		}
	}

	public void clear() {
		this.clearEntries();
	}

	/** The widget under the mouse, or null. Only counts rows drawn this frame. */
	public @Nullable AbstractWidget hoveredWidget(final double mouseX, final double mouseY) {
		Entry entry = this.getHovered();
		if (entry instanceof WidgetRow row) {
			for (AbstractWidget widget : row.widgets) {
				if (widget.isMouseOver(mouseX, mouseY)) {
					return widget;
				}
			}
		}
		return null;
	}

	public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
	}

	private static class WidgetRow extends Entry {
		private final List<AbstractWidget> widgets;
		private final int columns;

		WidgetRow(final List<AbstractWidget> widgets, final int columns) {
			this.widgets = widgets;
			this.columns = columns;
		}

		@Override
		public void extractContent(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final boolean hovered, final float a) {
			int width = (this.getContentWidth() - COLUMN_GAP * (this.columns - 1)) / this.columns;
			for (int i = 0; i < this.widgets.size(); i++) {
				AbstractWidget widget = this.widgets.get(i);
				widget.setWidth(width);
				widget.setX(this.getContentX() + i * (width + COLUMN_GAP));
				widget.setY(this.getContentY());
				widget.extractRenderState(graphics, mouseX, mouseY, a);
			}
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return this.widgets;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return this.widgets;
		}
	}

	private static class TextEntry extends Entry {
		private final List<FormattedCharSequence> lines;

		TextEntry(final List<FormattedCharSequence> lines) {
			this.lines = lines;
		}

		@Override
		public void extractContent(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final boolean hovered, final float a) {
			int y = this.getContentY() + 4;
			for (FormattedCharSequence line : this.lines) {
				graphics.centeredText(Minecraft.getInstance().font, line, this.getContentXMiddle(), y, -1);
				y += LINE_HEIGHT;
			}
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of();
		}
	}
}
