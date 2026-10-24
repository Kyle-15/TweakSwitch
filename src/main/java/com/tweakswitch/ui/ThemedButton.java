package com.tweakswitch.ui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** A flat rounded button in a {@link Theme}'s colors instead of vanilla's stone texture. */
public class ThemedButton extends Button {
	public enum Kind {
		/** Filled with the accent color, for the main action (Download, Done). */
		PRIMARY,
		/** Card-colored, for everything else. */
		SECONDARY
	}

	private final Theme theme;
	private final Kind kind;
	private BooleanSupplier highlighted = () -> false;

	public ThemedButton(final Theme theme, final Kind kind, final Component message, final int width, final OnPress onPress) {
		super(0, 0, width, 20, message, onPress, DEFAULT_NARRATION);
		this.theme = theme;
		this.kind = kind;
	}

	public static ThemedButton primary(final Theme theme, final Component message, final int width, final OnPress onPress) {
		return new ThemedButton(theme, Kind.PRIMARY, message, width, onPress);
	}

	public static ThemedButton secondary(final Theme theme, final Component message, final int width, final OnPress onPress) {
		return new ThemedButton(theme, Kind.SECONDARY, message, width, onPress);
	}

	/** Draws the button with an accent border and text while {@code highlighted} is true, e.g. a picked update. */
	public ThemedButton highlightWhen(final BooleanSupplier highlighted) {
		this.highlighted = highlighted;
		return this;
	}

	public ThemedButton withTooltip(final @Nullable Component tooltip) {
		this.setTooltip(tooltip != null ? Tooltip.create(tooltip) : null);
		return this;
	}

	@Override
	protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		boolean primary = this.kind == Kind.PRIMARY;
		int fill = primary ? this.theme.accent() : this.theme.card();
		int textColor = primary ? this.theme.onAccent() : this.theme.text();
		if (!this.active) {
			fill = this.theme.off();
			textColor = this.theme.muted();
		} else if (this.isHoveredOrFocused()) {
			fill = Draw.lighten(fill, 0.12F);
		}
		boolean highlight = this.active && this.highlighted.getAsBoolean();
		if (highlight && !primary) {
			fill = this.theme.cardSelected();
			textColor = this.theme.accent();
		}
		if (primary || !this.active) {
			Draw.roundRect(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), 3, fill);
		} else {
			Draw.roundRectOutlined(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), 3, fill,
				highlight || this.isHoveredOrFocused() ? this.theme.accent() : this.theme.border());
		}
		Font font = Minecraft.getInstance().font;
		String label = Draw.fit(font, this.getMessage().getString(), this.getWidth() - 8);
		// No shadow: it smudges dark text on the bright accent color
		graphics.text(font, label, this.getX() + (this.getWidth() - font.width(label)) / 2, this.getY() + (this.getHeight() - 8) / 2, textColor, false);
	}
}
