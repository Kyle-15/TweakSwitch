package com.tweakswitch.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A screen that draws its own background and an overlay above its widgets, through {@link Gfx}. 1.21 version. */
public abstract class BridgeScreen extends Screen {
	protected BridgeScreen(final Component title) {
		super(title);
	}

	@Override
	public final void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
		this.drawBackground(new Gfx(graphics), mouseX, mouseY, partialTick);
	}

	@Override
	public final void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		this.drawOverlay(new Gfx(graphics), mouseX, mouseY, partialTick);
	}

	/** Everything behind the widgets. */
	protected abstract void drawBackground(Gfx graphics, int mouseX, int mouseY, float partialTick);

	/** Anything on top of the widgets, like the preview panel. */
	protected void drawOverlay(final Gfx graphics, final int mouseX, final int mouseY, final float partialTick) {
	}
}
