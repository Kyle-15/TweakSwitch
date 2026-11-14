package com.tweakswitch.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import com.tweakswitch.render.Gfx;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** One pack on the TweakSwitch screen: its icon, name and description, and an on/off switch drawn like the mod's icon. */
public class SwitchRow extends CardButton {
	private static final int SWITCH_WIDTH = 22;
	private static final int SWITCH_HEIGHT = 11;

	private final Supplier<Identifier> icon;
	private final Component title;
	private final String summary;
	private final String tag;
	private final BooleanSupplier on;
	private final boolean warning;
	private boolean pinned;
	private boolean clash;

	/**
	 * @param description shown as a tooltip (or in the Shift preview), and as a second line in the Normal UI size
	 * @param tag         what the pack changes most, e.g. "Particles", shown as a small pill; empty for none
	 * @param warning     shows the name in red, for packs made for another Minecraft version
	 */
	public SwitchRow(
		final Theme theme, final Supplier<Identifier> icon, final Component title, final Component description, final String tag, final boolean warning,
		final BooleanSupplier on, final Runnable toggle
	) {
		super(theme, Ui.switchRow(), title, toggle);
		this.icon = icon;
		this.title = title;
		this.summary = description.getString().replace('\n', ' ').strip();
		this.tag = tag;
		this.warning = warning;
		this.on = on;
		this.setPlainTooltip(Tooltip.create(description));
	}

	/** A star before the name, for packs pinned to the top. */
	public SwitchRow pinned(final boolean pinned) {
		this.pinned = pinned;
		return this;
	}

	/** A gold "⚠" before the name, for a pack that's on together with one it clashes with. */
	public SwitchRow clash(final boolean clash) {
		this.clash = clash;
		return this;
	}

	@Override
	protected Component narratedState() {
		return Component.translatable(this.on.getAsBoolean() ? "tweakswitch.narration.on" : "tweakswitch.narration.off");
	}

	@Override
	protected void draw(final Gfx graphics, final Font font, final boolean hovered) {
		int x = this.getX();
		int y = this.getY();
		boolean isOn = this.on.getAsBoolean();
		boolean twoLines = Ui.normal() && !this.summary.isEmpty();
		int iconSize = Ui.normal() ? 24 : 16;
		Draw.roundRectOutlined(graphics, x, y, this.getWidth(), this.getHeight(), 3,
			hovered ? this.theme.cardHover() : this.theme.card(), isOn ? this.theme.accent() : this.theme.border());

		int iconY = y + (this.getHeight() - iconSize) / 2;
		graphics.texture(this.icon.get(), x + 3, iconY, iconSize, iconSize);

		int switchX = x + this.getWidth() - SWITCH_WIDTH - 5;
		int textX = x + iconSize + 8;
		int right = switchX - 6;
		int titleY = twoLines ? y + 5 : y + (this.getHeight() - 8) / 2;
		// The tag pill sits just left of the switch, if there's room for it and a bit of the name
		if (!this.tag.isEmpty()) {
			int pillWidth = font.width(this.tag) + 8;
			if (right - pillWidth - textX >= 40) {
				int pillX = right - pillWidth;
				Draw.roundRect(graphics, pillX, y + (this.getHeight() - 11) / 2, pillWidth, 11, 5, this.theme.off());
				graphics.text(font, this.tag, pillX + 4, y + (this.getHeight() - 11) / 2 + 2, this.theme.muted(), false);
				right = pillX - 4;
			}
		}
		if (this.pinned) {
			graphics.text(font, "★", textX, titleY, this.theme.accent(), false);
			textX += font.width("★") + 3;
		}
		if (this.clash) {
			graphics.text(font, "⚠", textX, titleY, 0xFFFFC857, false);
			textX += font.width("⚠") + 3;
		}
		graphics.text(font, Draw.fit(font, this.title.getString(), right - textX), textX, titleY, this.warning ? 0xFFFF6B6B : this.theme.text(), false);
		if (twoLines) {
			graphics.text(font, Draw.fit(font, this.summary, right - textX), textX, y + 17, this.theme.muted(), false);
		}

		// The switch: a pill, green with the knob right when on, gray with the knob left when off
		int switchY = y + (this.getHeight() - SWITCH_HEIGHT) / 2;
		Draw.roundRect(graphics, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT / 2, isOn ? this.theme.accent() : this.theme.off());
		int knob = SWITCH_HEIGHT - 4;
		Draw.circle(graphics, isOn ? switchX + SWITCH_WIDTH - knob - 2 : switchX + 2, switchY + 2, knob, 0xFFFFFFFF);
	}
}
