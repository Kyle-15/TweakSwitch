package com.tweakswitch.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The drawing calls TweakSwitch makes, for Minecraft 1.21.9 and newer 1.21 versions. Each version family has its own copy of this class with the
 * same methods (see src/render/), so the screens and widgets are written once.
 */
public final class Gfx {
	private final GuiGraphics graphics;

	public Gfx(final GuiGraphics graphics) {
		this.graphics = graphics;
	}

	public void fill(final int x0, final int y0, final int x1, final int y1, final int color) {
		this.graphics.fill(x0, y0, x1, y1, color);
	}

	public void text(final Font font, final @Nullable String text, final int x, final int y, final int color) {
		this.graphics.drawString(font, text, x, y, color);
	}

	public void text(final Font font, final @Nullable String text, final int x, final int y, final int color, final boolean shadow) {
		this.graphics.drawString(font, text, x, y, color, shadow);
	}

	public void text(final Font font, final Component text, final int x, final int y, final int color) {
		this.graphics.drawString(font, text, x, y, color);
	}

	public void text(final Font font, final Component text, final int x, final int y, final int color, final boolean shadow) {
		this.graphics.drawString(font, text, x, y, color, shadow);
	}

	public void text(final Font font, final FormattedCharSequence text, final int x, final int y, final int color) {
		this.graphics.drawString(font, text, x, y, color);
	}

	public void centeredText(final Font font, final String text, final int x, final int y, final int color) {
		this.graphics.drawCenteredString(font, text, x, y, color);
	}

	public void centeredText(final Font font, final Component text, final int x, final int y, final int color) {
		this.graphics.drawCenteredString(font, text, x, y, color);
	}

	public void centeredText(final Font font, final FormattedCharSequence text, final int x, final int y, final int color) {
		this.graphics.drawCenteredString(font, text, x, y, color);
	}

	/** The whole texture stretched over (x0, y0)–(x1, y1), using its own filtering. */
	public void blit(final Identifier texture, final int x0, final int y0, final int x1, final int y1, final float u0, final float u1, final float v0, final float v1) {
		this.graphics.blit(texture, x0, y0, x1, y1, u0, u1, v0, v1);
	}

	/** The whole texture drawn at (x, y) at the given size, like a GUI icon. */
	public void texture(final Identifier texture, final int x, final int y, final int width, final int height) {
		this.graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, width, height, width, height);
	}

	/** Later drawing goes on top of everything drawn so far, tooltips included. */
	public void nextStratum() {
		this.graphics.nextStratum();
	}

	/** Draws a vanilla widget that lives inside one of ours, like a button in a list row. */
	public void widget(final AbstractWidget widget, final int mouseX, final int mouseY, final float partialTick) {
		widget.render(this.graphics, mouseX, mouseY, partialTick);
	}
}
