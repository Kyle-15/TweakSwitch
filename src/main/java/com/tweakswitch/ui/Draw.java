package com.tweakswitch.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small drawing helpers the vanilla GUI doesn't have: rounded rectangles, pills, circles and cut-off text. */
public final class Draw {
	private Draw() {
	}

	/** A filled rectangle with corners rounded to {@code radius} pixels, drawn as one strip per row of the curve. */
	public static void roundRect(final GuiGraphicsExtractor graphics, final int x, final int y, final int width, final int height, final int radius, final int color) {
		int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
			graphics.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
			graphics.fill(x + inset, y + height - i - 1, x + width - inset, y + height - i, color);
		}
		graphics.fill(x, y + r, x + width, y + height - r, color);
	}

	/** A rounded rectangle with a one-pixel border. */
	public static void roundRectOutlined(
		final GuiGraphicsExtractor graphics, final int x, final int y, final int width, final int height, final int radius, final int fill, final int border
	) {
		roundRect(graphics, x, y, width, height, radius, border);
		roundRect(graphics, x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1), fill);
	}

	public static void circle(final GuiGraphicsExtractor graphics, final int x, final int y, final int size, final int color) {
		roundRect(graphics, x, y, size, size, size / 2, color);
	}

	/** {@code text} cut to fit {@code width}, ending in "…" if anything was cut. */
	public static String fit(final Font font, final String text, final int width) {
		if (font.width(text) <= width) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))).stripTrailing() + "…";
	}

	/** Mixes {@code color} toward white by {@code amount} (0 to 1), keeping its alpha. */
	public static int lighten(final int color, final float amount) {
		int a = color >>> 24;
		int r = color >> 16 & 0xFF;
		int g = color >> 8 & 0xFF;
		int b = color & 0xFF;
		r += (int) ((255 - r) * amount);
		g += (int) ((255 - g) * amount);
		b += (int) ((255 - b) * amount);
		return a << 24 | r << 16 | g << 8 | b;
	}
}
