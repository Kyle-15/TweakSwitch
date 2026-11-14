package com.tweakswitch.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import com.tweakswitch.render.Gfx;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** A Modrinth project as a card, like on modrinth.com: icon, title and author, summary, download count and tags. */
public class ProjectCard extends CardButton {
	/** 24 pixels in Compact; Normal's taller card has room for 30. */
	private final int iconSize = Ui.normal() ? 30 : 24;

	private final String title;
	private final String author;
	private final String summary;
	private final String downloads;
	private final List<String> tags;
	private final Supplier<@Nullable Identifier> icon;
	private final BooleanSupplier picked;
	private final boolean downloaded;
	private final boolean needsMods;
	/** True once a lookup found the project has no file for this Minecraft version; its card says so. */
	private boolean unavailable;

	public ProjectCard unavailable(final boolean unavailable) {
		this.unavailable = unavailable;
		return this;
	}

	@Override
	protected @Nullable Component narratedState() {
		return this.downloaded ? Component.translatable("tweakswitch.narration.downloaded")
			: this.picked.getAsBoolean() ? Component.translatable("tweakswitch.narration.picked") : null;
	}

	public ProjectCard(
		final Theme theme, final String title, final String author, final String summary, final String downloads, final List<String> tags,
		final Supplier<@Nullable Identifier> icon, final BooleanSupplier picked, final boolean downloaded, final boolean needsMods, final Runnable toggle
	) {
		super(theme, Ui.card(), Component.literal(title), toggle);
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
	protected void draw(final Gfx graphics, final Font font, final boolean hovered) {
		int x = this.getX();
		int y = this.getY();
		int width = this.getWidth();
		boolean isPicked = this.picked.getAsBoolean();
		Draw.roundRectOutlined(graphics, x, y, width, this.getHeight() - 2, 5,
			isPicked ? this.theme.cardSelected() : hovered ? this.theme.cardHover() : this.theme.card(),
			isPicked ? this.theme.accent() : hovered ? this.theme.border() : this.theme.card());

		final int ICON_SIZE = this.iconSize;
		int iconX = x + 4;
		int iconY = y + 3;
		Draw.roundRect(graphics, iconX, iconY, ICON_SIZE, ICON_SIZE, 4, this.theme.off());
		Identifier texture = this.icon.get();
		if (texture != null) {
			graphics.blit(texture, iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0.0F, 1.0F, 0.0F, 1.0F);
		}

		int textX = iconX + ICON_SIZE + 6;
		int lineOne = y + (Ui.normal() ? 6 : 4);
		int lineTwo = y + (Ui.normal() ? 20 : 16);

		// Right of line one: a state badge, or else the download count
		String badge = this.downloaded ? "✔ Installed" : isPicked ? "✚ Picked" : this.unavailable ? "✘ No file" : null;
		int rightOne;
		if (badge != null) {
			int badgeWidth = font.width(badge) + 8;
			rightOne = x + width - badgeWidth - 5;
			boolean quiet = this.downloaded || !isPicked;
			Draw.roundRect(graphics, rightOne, lineOne - 2, badgeWidth, 11, 5, quiet ? this.theme.off() : this.theme.accent());
			graphics.text(font, badge, rightOne + 4, lineOne, quiet ? this.theme.muted() : this.theme.onAccent(), false);
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
