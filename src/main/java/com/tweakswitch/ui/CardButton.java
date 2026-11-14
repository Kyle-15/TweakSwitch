package com.tweakswitch.ui;

import com.tweakswitch.PackListScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.tweakswitch.render.BridgeButton;
import com.tweakswitch.render.Gfx;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** A clickable area that draws itself however its subclass likes: a switch card, a tile, a header. */
public abstract class CardButton extends BridgeButton {
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

	/** What a screen reader says: the card's name, then its state ("on", "picked", "already downloaded") if it has one. */
	@Override
	protected void updateWidgetNarration(final NarrationElementOutput output) {
		Component state = this.narratedState();
		output.add(NarratedElementType.TITLE, state == null ? this.getMessage() : Component.translatable("tweakswitch.narration.withState", this.getMessage(), state));
		if (this.active) {
			output.add(NarratedElementType.USAGE, Component.translatable(this.isFocused() ? "narration.button.usage.focused" : "narration.button.usage.hovered"));
		}
	}

	/** The card's state in words for the narrator, or null if it has none. */
	protected @Nullable Component narratedState() {
		return null;
	}

	/** Shown on hover, except while the Shift preview panel is on (it shows more than a tooltip would). */
	private @Nullable Tooltip plainTooltip;

	public void setPlainTooltip(final @Nullable Tooltip tooltip) {
		this.plainTooltip = tooltip;
		this.setTooltip(tooltip);
	}

	@Override
	protected final void drawContents(final Gfx graphics, final int mouseX, final int mouseY, final float a) {
		this.setTooltip(PackListScreen.previewsOn() ? null : this.plainTooltip);
		this.draw(graphics, Minecraft.getInstance().font, this.isHoveredOrFocused() && this.active);
		if (this.isFocused()) {
			// Keyboard focus: a white ring, since hover and "on" already use the accent color
			Draw.ring(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), 3, 0xFFFFFFFF);
		}
	}

	protected abstract void draw(Gfx graphics, Font font, boolean hovered);
}
