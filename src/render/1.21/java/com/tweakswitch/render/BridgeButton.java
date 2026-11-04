package com.tweakswitch.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;

/** A clickable widget that draws itself entirely through {@link Gfx}. 1.21 version. */
public abstract class BridgeButton extends AbstractButton {
	protected BridgeButton(final int x, final int y, final int width, final int height, final Component message) {
		super(x, y, width, height, message);
	}

	@Override
	protected final void renderContents(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
		this.drawContents(new Gfx(graphics), mouseX, mouseY, partialTick);
	}

	protected abstract void drawContents(Gfx graphics, int mouseX, int mouseY, float partialTick);
}
