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
	public static final int HEIGHT = 62;
	public static final int MIN_WIDTH = 66;
	private static final int ICON_SIZE = 28;

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
		Draw.roundRect(graphics, x, y, this.getWidth(), this.getHeight(), 5, fill);

		int iconX = x + (this.getWidth() - ICON_SIZE) / 2;
		int iconY = y + 5;
		// A dark frame behind the icon, like the site's pack thumbnails
		graphics.fill(iconX - 1, iconY - 1, iconX + ICON_SIZE + 1, iconY + ICON_SIZE + 1, 0xFF101010);
		Identifier texture = this.icon.get();
		if (texture != null) {
			graphics.blit(texture, iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0.0F, 1.0F, 0.0F, 1.0F);
		}

		List<FormattedCharSequence> lines = font.split(this.name, this.getWidth() - 6);
		int textY = iconY + ICON_SIZE + 4;
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			FormattedCharSequence line = i == 1 && lines.size() > 2
				? Component.literal(Draw.fit(font, this.name.getString().substring(Math.min(this.name.getString().length(), approximateStart(font, lines, 1))), this.getWidth() - 6)).getVisualOrderText()
				: lines.get(i);
			graphics.centeredText(font, line, x + this.getWidth() / 2, textY + i * 10, this.downloaded ? this.theme.muted() : this.theme.text());
		}

		if (this.downloaded) {
			// Already in the TweakSwitch folder: a green check in the corner
			Draw.circle(graphics, x + this.getWidth() - 12, y + 3, 9, 0xFF3FA34D);
			graphics.text(font, "✔", x + this.getWidth() - 10, y + 4, 0xFFFFFFFF, false);
		} else if (isPicked) {
			Draw.circle(graphics, x + this.getWidth() - 12, y + 3, 9, this.theme.accent());
			graphics.text(font, "✚", x + this.getWidth() - 10, y + 4, this.theme.onAccent(), false);
		}
	}

	/** Roughly where line {@code index} starts in the name, so the second line can end in "…" when the name needs three. */
	private static int approximateStart(final Font font, final List<FormattedCharSequence> lines, final int index) {
		StringBuilder before = new StringBuilder();
		for (int i = 0; i < index; i++) {
			lines.get(i).accept((position, style, codePoint) -> {
				before.appendCodePoint(codePoint);
				return true;
			});
		}
		return before.length();
	}
}
