package com.tweakswitch.ui;

import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import com.tweakswitch.render.Gfx;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A card with a name, a note under or beside it, and what clicking it does written on a pill at the right ("Apply",
 * "Re-download"). It can carry up to two small extra buttons at the far right, like ▲ ▼ or ✕.
 */
public class ActionRow extends CardButton {
	private static final int SMALL = 14;

	/** A small square button at the row's right end. */
	public record Extra(String symbol, Component tooltip, Runnable action, boolean enabled) {
	}

	private final @Nullable Supplier<@Nullable Identifier> icon;
	private final String title;
	private final String note;
	private final String actionLabel;
	private final boolean warning;
	private final java.util.List<Extra> extras;

	/**
	 * @param note        gray text after the name; empty for none
	 * @param actionLabel what a click does, on a pill; empty for a row that only has its extra buttons
	 * @param warning     shows the name in red, e.g. a damaged pack
	 */
	public ActionRow(
		final Theme theme, final @Nullable Supplier<@Nullable Identifier> icon, final String title, final String note, final String actionLabel,
		final boolean warning, final Runnable action, final java.util.List<Extra> extras
	) {
		super(theme, Ui.switchRow(), Component.literal(title), action);
		this.icon = icon;
		this.title = title;
		this.note = note;
		this.actionLabel = actionLabel;
		this.warning = warning;
		this.extras = extras;
	}

	public ActionRow withTooltip(final Component tooltip) {
		this.setPlainTooltip(Tooltip.create(tooltip));
		return this;
	}

	@Override
	protected @Nullable Component narratedState() {
		return this.note.isEmpty() ? null : Component.literal(this.note);
	}

	private int extraX(final int index) {
		return this.getX() + this.getWidth() - 4 - (this.extras.size() - index) * (SMALL + 2);
	}

	@Override
	protected void draw(final Gfx graphics, final Font font, final boolean hovered) {
		int x = this.getX();
		int y = this.getY();
		Draw.roundRectOutlined(graphics, x, y, this.getWidth(), this.getHeight(), 3, hovered ? this.theme.cardHover() : this.theme.card(),
			this.warning ? 0xFF8A3B3B : this.theme.border());
		int textX = x + 6;
		if (this.icon != null) {
			int iconSize = Ui.normal() ? 24 : 16;
			Identifier texture = this.icon.get();
			if (texture != null) {
				graphics.texture(texture, x + 3, y + (this.getHeight() - iconSize) / 2, iconSize, iconSize);
			}
			textX = x + iconSize + 8;
		}
		int right = x + this.getWidth() - 5;
		int buttonY = y + (this.getHeight() - SMALL) / 2;
		for (int i = 0; i < this.extras.size(); i++) {
			Extra extra = this.extras.get(i);
			int extraX = this.extraX(i);
			Draw.roundRect(graphics, extraX, buttonY, SMALL, SMALL, 3, this.theme.off());
			graphics.text(font, extra.symbol(), extraX + (SMALL - font.width(extra.symbol())) / 2, buttonY + 3, extra.enabled() ? this.theme.text() : this.theme.muted(), false);
		}
		if (!this.extras.isEmpty()) {
			right = this.extraX(0) - 4;
		}
		if (!this.actionLabel.isEmpty()) {
			int pillWidth = font.width(this.actionLabel) + 10;
			int pillX = right - pillWidth;
			Draw.roundRect(graphics, pillX, y + (this.getHeight() - 12) / 2, pillWidth, 12, 6, hovered ? this.theme.accent() : this.theme.off());
			graphics.text(font, this.actionLabel, pillX + 5, y + (this.getHeight() - 12) / 2 + 2, hovered ? this.theme.onAccent() : this.theme.text(), false);
			right = pillX - 5;
		}
		int textY = y + (this.getHeight() - 8) / 2;
		String shownTitle = Draw.fit(font, this.title, right - textX);
		graphics.text(font, shownTitle, textX, textY, this.warning ? 0xFFFF6B6B : this.theme.text(), false);
		int noteX = textX + font.width(shownTitle) + 6;
		if (!this.note.isEmpty() && right - noteX > 30) {
			graphics.text(font, Draw.fit(font, this.note, right - noteX), noteX, textY, this.theme.muted(), false);
		}
	}

	/** A click on one of the small buttons does that button's job; anywhere else does the row's. */
	@Override
	public void onClick(final MouseButtonEvent event, final boolean doubleClick) {
		for (int i = 0; i < this.extras.size(); i++) {
			int extraX = this.extraX(i);
			if (event.x() >= extraX - 1 && event.x() < extraX + SMALL + 1) {
				if (this.extras.get(i).enabled()) {
					this.extras.get(i).action().run();
				}
				return;
			}
		}
		super.onClick(event, doubleClick);
	}
}
