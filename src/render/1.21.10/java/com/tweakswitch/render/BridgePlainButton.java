package com.tweakswitch.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A vanilla {@link Button} (click handler, narration) that draws itself through {@link Gfx}. 1.21.9–1.21.10 version. */
public abstract class BridgePlainButton extends Button {
	protected BridgePlainButton(final int x, final int y, final int width, final int height, final Component message, final OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}

	@Override
	protected final void renderWidget(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
		this.drawContents(new Gfx(graphics), mouseX, mouseY, partialTick);
	}

	protected abstract void drawContents(Gfx graphics, int mouseX, int mouseY, float partialTick);
}
