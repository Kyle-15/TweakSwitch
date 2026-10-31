package com.tweakswitch.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import java.util.function.Supplier;

/** A Vanilla Tweaks pack as a tile, like on their website: the pack's icon on top, its name below, gray when picked. */
public class PackTile extends CardButton {
	public static final int HEIGHT = 50;
	public static final int MIN_WIDTH = 60;
	private static final int ICON_SIZE = 20;

	private final Component name;
	private final Supplier<@Nullable Identifier> icon;
	private final BooleanSupplier picked;
	private final boolean downloaded;

	public PackTile(
		final Theme theme, final Component name, final Supplier<@Nullable Identifier> icon, final BooleanSupplier picked, final boolean downloaded,
		final Runnable toggle
	) {
		super(theme, HEIGHT, name, toggle);
		this.name = name;
		this.icon = icon;
		this.picked = picked;
		this.downloaded = downloaded;
		this.active = !downloaded;
	}

	@Override
	protected void draw(final GuiGraphicsExtractor graphics, final Font font, final boolean hovered) {
		int x = this.getX();
		int y = this.getY();
		boolean isPicked = this.picked.getAsBoolean();
		int fill = isPicked ? this.theme.cardSelected() : hovered ? this.theme.cardHover() : this.theme.card();
		Draw.roundRect(graphics, x, y, this.getWidth(), this.getHeight(), 4, fill);

		int iconX = x + (this.getWidth() - ICON_SIZE) / 2;
		int iconY = y + 4;
		// A dark frame behind the icon, like the site's pack thumbnails
		graphics.fill(iconX - 1, iconY - 1, iconX + ICON_SIZE + 1, iconY + ICON_SIZE + 1, 0xFF101010);
		Identifier texture = this.icon.get();
		if (texture != null) {
			graphics.blit(texture, iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0.0F, 1.0F, 0.0F, 1.0F);
		}

		// Up to two lines, the second ending in "…" if the name needs more; the full name is in the Shift preview
		List<FormattedCharSequence> lines = font.split(this.name, this.getWidth() - 4);
		int color = this.downloaded ? this.theme.muted() : this.theme.text();
		int textY = iconY + ICON_SIZE + 3;
		if (!lines.isEmpty()) {
			graphics.centeredText(font, lines.get(0), x + this.getWidth() / 2, textY, color);
		}
		if (lines.size() == 2) {
			graphics.centeredText(font, lines.get(1), x + this.getWidth() / 2, textY + 10, color);
		} else if (lines.size() > 2) {
			String rest = this.name.getString().substring(Math.min(this.name.getString().length(), lengthOf(lines.get(0)))).strip();
			graphics.centeredText(font, Draw.fit(font, rest, this.getWidth() - 4), x + this.getWidth() / 2, textY + 10, color);
		}

		if (this.downloaded) {
			// Already in the TweakSwitch folder: a green check in the corner
			Draw.circle(graphics, x + this.getWidth() - 11, y + 2, 9, 0xFF3FA34D);
			graphics.text(font, "✔", x + this.getWidth() - 9, y + 3, 0xFFFFFFFF, false);
		} else if (isPicked) {
			Draw.circle(graphics, x + this.getWidth() - 11, y + 2, 9, this.theme.accent());
			graphics.text(font, "✚", x + this.getWidth() - 9, y + 3, this.theme.onAccent(), false);
		}
	}

	private static int lengthOf(final FormattedCharSequence line) {
		int[] count = {0};
		line.accept((position, style, codePoint) -> {
			count[0] += Character.charCount(codePoint);
			return true;
		});
		return count[0];
	}
}
