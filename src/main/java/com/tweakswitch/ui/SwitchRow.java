package com.tweakswitch.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

/** One pack on the TweakSwitch screen: its icon, name and description, and an on/off switch drawn like the mod's icon. */
public class SwitchRow extends CardButton {
	public static final int HEIGHT = 22;
	private static final int ICON_SIZE = 16;
	private static final int SWITCH_WIDTH = 22;
	private static final int SWITCH_HEIGHT = 11;

	private final Supplier<Identifier> icon;
	private final Component title;
	private final String tag;
	private final BooleanSupplier on;
	private final boolean warning;

	/**
	 * @param description shown as a tooltip (or in the Shift preview)
	 * @param tag         what the pack changes most, e.g. "Particles", shown as a small pill; empty for none
	 * @param warning     shows the name in red, for packs made for another Minecraft version
	 */
	public SwitchRow(
		final Theme theme, final Supplier<Identifier> icon, final Component title, final Component description, final String tag, final boolean warning,
		final BooleanSupplier on, final Runnable toggle
	) {
		super(theme, HEIGHT, title, toggle);
		this.icon = icon;
		this.title = title;
		this.tag = tag;
		this.warning = warning;
		this.on = on;
		this.setTooltip(Tooltip.create(description));
	}

	@Override
	protected void draw(final GuiGraphicsExtractor graphics, final Font font, final boolean hovered) {
		int x = this.getX();
		int y = this.getY();
		boolean isOn = this.on.getAsBoolean();
		Draw.roundRectOutlined(graphics, x, y, this.getWidth(), this.getHeight(), 3,
			hovered ? this.theme.cardHover() : this.theme.card(), isOn ? this.theme.accent() : this.theme.border());

		int iconY = y + (this.getHeight() - ICON_SIZE) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, this.icon.get(), x + 3, iconY, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

		int switchX = x + this.getWidth() - SWITCH_WIDTH - 5;
		int textX = x + ICON_SIZE + 8;
		int right = switchX - 6;
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
		graphics.text(font, Draw.fit(font, this.title.getString(), right - textX), textX, y + (this.getHeight() - 8) / 2, this.warning ? 0xFFFF6B6B : this.theme.text(), false);

		// The switch: a pill, green with the knob right when on, gray with the knob left when off
		int switchY = y + (this.getHeight() - SWITCH_HEIGHT) / 2;
		Draw.roundRect(graphics, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT / 2, isOn ? this.theme.accent() : this.theme.off());
		int knob = SWITCH_HEIGHT - 4;
		Draw.circle(graphics, isOn ? switchX + SWITCH_WIDTH - knob - 2 : switchX + 2, switchY + 2, knob, 0xFFFFFFFF);
	}
}
