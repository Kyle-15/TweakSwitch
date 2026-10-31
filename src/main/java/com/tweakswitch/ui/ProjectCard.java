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
	public static final int HEIGHT = 32;
	private static final int ICON_SIZE = 24;

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

	/** Two lines: "Title by author" with the download count (or a badge) on the right, then the summary with tags. */
	@Override
	protected void draw(final GuiGraphicsExtractor graphics, final Font font, final boolean hovered) {
		int x = this.getX();
		int y = this.getY();
		int width = this.getWidth();
		boolean isPicked = this.picked.getAsBoolean();
		Draw.roundRectOutlined(graphics, x, y, width, this.getHeight() - 2, 5,
			isPicked ? this.theme.cardSelected() : hovered ? this.theme.cardHover() : this.theme.card(),
			isPicked ? this.theme.accent() : hovered ? this.theme.border() : this.theme.card());

		int iconX = x + 4;
		int iconY = y + 3;
		Draw.roundRect(graphics, iconX, iconY, ICON_SIZE, ICON_SIZE, 4, this.theme.off());
		Identifier texture = this.icon.get();
		if (texture != null) {
			graphics.blit(texture, iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0.0F, 1.0F, 0.0F, 1.0F);
		}

		int textX = iconX + ICON_SIZE + 6;
		int lineOne = y + 4;
		int lineTwo = y + 16;

		// Right of line one: a state badge, or else the download count
		String badge = this.downloaded ? "✔ Installed" : isPicked ? "✚ Picked" : null;
		int rightOne;
		if (badge != null) {
			int badgeWidth = font.width(badge) + 8;
			rightOne = x + width - badgeWidth - 5;
			Draw.roundRect(graphics, rightOne, lineOne - 2, badgeWidth, 11, 5, this.downloaded ? this.theme.off() : this.theme.accent());
			graphics.text(font, badge, rightOne + 4, lineOne, this.downloaded ? this.theme.muted() : this.theme.onAccent(), false);
		} else {
			String count = "⬇ " + this.downloads;
			rightOne = x + width - font.width(count) - 6;
			graphics.text(font, count, rightOne, lineOne, this.theme.muted(), false);
		}
		String by = " by " + this.author;
		String shownTitle = Draw.fit(font, this.title, rightOne - 6 - textX);
		graphics.text(font, shownTitle, textX, lineOne, this.theme.text(), false);
		int byX = textX + font.width(shownTitle);
		if (byX + font.width(by) <= rightOne - 6) {
			graphics.text(font, by, byX, lineOne, this.theme.muted(), false);
		}

		// Line two: tags from the right edge (a gold "Needs mods" first), the summary in what's left
		int pillRight = x + width - 5;
		List<String> pills = new java.util.ArrayList<>();
		if (this.needsMods) {
			pills.add("⚠ Needs mods");
		}
		pills.addAll(this.tags.stream().limit(2).toList());
		for (String pill : pills) {
			int pillWidth = font.width(pill) + 8;
			if (pillRight - pillWidth < textX + 60) {
				break;
			}
			pillRight -= pillWidth;
			boolean warning = pill.startsWith("⚠");
			Draw.roundRect(graphics, pillRight, lineTwo - 2, pillWidth, 11, 5, warning ? 0xFF5A4A1A : this.theme.off());
			graphics.text(font, pill, pillRight + 4, lineTwo, warning ? 0xFFFFC857 : this.theme.muted(), false);
			pillRight -= 3;
		}
		graphics.text(font, Draw.fit(font, this.summary, pillRight - 4 - textX), textX, lineTwo, this.theme.muted(), false);
	}

}
