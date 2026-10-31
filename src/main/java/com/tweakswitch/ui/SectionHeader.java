package com.tweakswitch.ui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * A category heading: "▼ Name" on the left, a count on the right, an accent stripe, and a click to fold it. It can also
 * carry a small switch at the right end that turns the whole group on or off.
 */
public class SectionHeader extends CardButton {
	public static final int HEIGHT = 14;
	private static final int SWITCH_WIDTH = 18;
	private static final int SWITCH_HEIGHT = 9;

	/** The header's own switch: on when every pack in the group is on; clicking it flips them all. */
	public record BulkSwitch(BooleanSupplier allOn, Runnable toggleAll) {
	}

	private final @Nullable BulkSwitch bulk;

	private final String name;
	private final String count;
	private final boolean open;

	public SectionHeader(final Theme theme, final String name, final String count, final boolean open, final @Nullable BulkSwitch bulk, final Runnable toggle) {
		super(theme, HEIGHT, Component.literal(name), toggle);
		this.bulk = bulk;
		this.name = name;
		this.count = count;
		this.open = open;
	}

	@Override
	protected void draw(final GuiGraphicsExtractor graphics, final Font font, final boolean hovered) {
		int x = this.getX();
		int y = this.getY();
		if (hovered) {
			Draw.roundRect(graphics, x, y, this.getWidth(), this.getHeight(), 3, this.theme.cardHover());
		}
		graphics.fill(x, y + this.getHeight() - 1, x + this.getWidth(), y + this.getHeight(), this.theme.border());
		graphics.fill(x, y + 3, x + 2, y + this.getHeight() - 4, this.theme.accent());
		int textY = y + (this.getHeight() - 9) / 2;
		String arrow = this.open ? "▼" : "▶";
		graphics.text(font, arrow, x + 7, textY, this.theme.muted(), false);
		int right = x + this.getWidth() - 4;
		if (this.bulk != null) {
			int switchX = right - SWITCH_WIDTH;
			int switchY = y + (this.getHeight() - SWITCH_HEIGHT) / 2 - 1;
			boolean on = this.bulk.allOn().getAsBoolean();
			Draw.roundRect(graphics, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT / 2, on ? this.theme.accent() : this.theme.off());
			int knob = SWITCH_HEIGHT - 4;
			Draw.circle(graphics, on ? switchX + SWITCH_WIDTH - knob - 2 : switchX + 2, switchY + 2, knob, 0xFFFFFFFF);
			right = switchX - 6;
		}
		int countWidth = font.width(this.count);
		graphics.text(font, Draw.fit(font, this.name, right - countWidth - 24 - x), x + 18, textY, this.theme.text(), false);
		graphics.text(font, this.count, right - countWidth, textY, this.theme.muted(), false);
	}

	/** A click on the right end flips the group's switch; anywhere else folds the group. */
	@Override
	public void onClick(final MouseButtonEvent event, final boolean doubleClick) {
		if (this.bulk != null && event.x() >= this.getX() + this.getWidth() - SWITCH_WIDTH - 8) {
			this.bulk.toggleAll().run();
			return;
		}
		super.onClick(event, doubleClick);
	}
}
