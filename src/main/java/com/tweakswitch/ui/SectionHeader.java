package com.tweakswitch.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A category heading: "▼ Name" on the left, a count on the right, an accent stripe, and a click to fold it. */
public class SectionHeader extends CardButton {
	private final String name;
	private final String count;
	private final boolean open;

	public SectionHeader(final Theme theme, final String name, final String count, final boolean open, final Runnable toggle) {
		super(theme, 18, Component.literal(name), toggle);
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
		int countWidth = font.width(this.count);
		graphics.text(font, Draw.fit(font, this.name, this.getWidth() - countWidth - 30), x + 18, textY, this.theme.text(), false);
		graphics.text(font, this.count, x + this.getWidth() - countWidth - 4, textY, this.theme.muted(), false);
	}
}
