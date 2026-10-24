package com.tweakswitch.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A clickable area that draws itself however its subclass likes: a switch card, a tile, a header. */
public abstract class CardButton extends AbstractButton {
	protected final Theme theme;
	private final Runnable action;

	protected CardButton(final Theme theme, final int height, final Component narration, final Runnable action) {
		super(0, 0, 150, height, narration);
		this.theme = theme;
		this.action = action;
	}

	@Override
	public void onPress(final InputWithModifiers input) {
		this.action.run();
	}

	@Override
	protected void updateWidgetNarration(final NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}

	@Override
	protected final void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		this.draw(graphics, Minecraft.getInstance().font, this.isHoveredOrFocused() && this.active);
	}

	protected abstract void draw(GuiGraphicsExtractor graphics, Font font, boolean hovered);
}
