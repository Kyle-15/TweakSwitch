package com.tweakswitch.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** A Modrinth project as a card, like on modrinth.com: icon, title and author, summary, download count and tags. */
public class ProjectCard extends CardButton {
	public static final int HEIGHT = 46;
	private static final int ICON_SIZE = 34;

	private final String title;
	private final String author;
	private final String summary;
	private final String downloads;
	private final List<String> tags;
	private final Supplier<@Nullable Identifier> icon;
	private final BooleanSupplier picked;
	private final boolean downloaded;
	private final boolean needsMods;

	public ProjectCard(
		final Theme theme, final String title, final String author, final String summary, final String downloads, final List<String> tags,
		final Supplier<@Nullable Identifier> icon, final BooleanSupplier picked, final boolean downloaded, final boolean needsMods, final Runnable toggle
	) {
		super(theme, HEIGHT, Component.literal(title), toggle);
		this.title = title;
		this.author = author;
		this.summary = summary;
		this.downloads = downloads;
		this.tags = tags;
		this.icon = icon;
		this.picked = picked;
		this.downloaded = downloaded;
		this.needsMods = needsMods;
		this.active = !downloaded;
	}

	@Override
	protected void draw(final GuiGraphicsExtractor graphics, final Font font, final boolean hovered) {
		int x = this.getX();
		int y = this.getY();
		int width = this.getWidth();
		boolean isPicked = this.picked.getAsBoolean();
		Draw.roundRectOutlined(graphics, x, y, width, this.getHeight() - 2, 6,
			isPicked ? this.theme.cardSelected() : hovered ? this.theme.cardHover() : this.theme.card(),
			isPicked ? this.theme.accent() : hovered ? this.theme.border() : this.theme.card());

		int iconX = x + 6;
		int iconY = y + 5;
		Draw.roundRect(graphics, iconX, iconY, ICON_SIZE, ICON_SIZE, 5, this.theme.off());
		Identifier texture = this.icon.get();
		if (texture != null) {
			graphics.blit(texture, iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0.0F, 1.0F, 0.0F, 1.0F);
		}

		// Right side: state badge on top, download count below
		String badge = this.downloaded ? "✔ Installed" : isPicked ? "✚ Picked" : null;
		int badgeWidth = badge != null ? font.width(badge) + 8 : 0;
		if (badge != null) {
			int badgeX = x + width - badgeWidth - 6;
			Draw.roundRect(graphics, badgeX, y + 4, badgeWidth, 12, 6, this.downloaded ? this.theme.off() : this.theme.accent());
			graphics.text(font, badge, badgeX + 4, y + 6, this.downloaded ? this.theme.muted() : this.theme.onAccent(), false);
		}
		String count = "⬇ " + this.downloads;
		int countWidth = font.width(count);
		graphics.text(font, count, x + width - countWidth - 7, y + 30, this.theme.muted(), false);

		int textX = iconX + ICON_SIZE + 7;
		int textRight = x + width - Math.max(badgeWidth, countWidth) - 12;
		String by = " by " + this.author;
		String shownTitle = Draw.fit(font, this.title, textRight - textX);
		graphics.text(font, shownTitle, textX, y + 5, this.theme.text(), false);
		int byX = textX + font.width(shownTitle);
		if (byX + font.width(by) <= textRight) {
			graphics.text(font, by, byX, y + 5, this.theme.muted(), false);
		}
		graphics.text(font, Draw.fit(font, this.summary, textRight - textX), textX, y + 17, this.theme.muted(), false);

		// Tag pills, as many as fit; a gold one first if the pack needs mods
		int pillX = textX;
		int pillY = y + 29;
		if (this.needsMods) {
			pillX = this.pill(graphics, font, "⚠ Needs mods", pillX, pillY, textRight, 0xFF5A4A1A, 0xFFFFC857);
		}
		for (String tag : this.tags) {
			int next = this.pill(graphics, font, tag, pillX, pillY, textRight, this.theme.off(), this.theme.muted());
			if (next == pillX) {
				break;
			}
			pillX = next;
		}
	}

	/** Draws a small rounded tag if it fits before {@code right}; returns where the next one goes. */
	private int pill(final GuiGraphicsExtractor graphics, final Font font, final String text, final int x, final int y, final int right, final int fill, final int color) {
		int width = font.width(text) + 8;
		if (x + width > right) {
			return x;
		}
		Draw.roundRect(graphics, x, y, width, 11, 5, fill);
		graphics.text(font, text, x + 4, y + 2, color, false);
		return x + width + 3;
	}
}
