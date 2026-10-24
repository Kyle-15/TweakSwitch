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
	public static final int HEIGHT = 30;
	private static final int ICON_SIZE = 22;
	private static final int SWITCH_WIDTH = 24;
	private static final int SWITCH_HEIGHT = 12;

	private final Supplier<Identifier> icon;
	private final Component title;
	private final Component description;
	private final BooleanSupplier on;
	private final boolean warning;

	/** @param warning shows the name in red, for packs made for another Minecraft version */
	public SwitchRow(
		final Theme theme, final Supplier<Identifier> icon, final Component title, final Component description, final boolean warning, final BooleanSupplier on,
		final Runnable toggle
	) {
		super(theme, HEIGHT, title, toggle);
		this.icon = icon;
		this.title = title;
		this.description = description;
		this.warning = warning;
		this.on = on;
		this.setTooltip(Tooltip.create(description));
	}

	@Override
	protected void draw(final GuiGraphicsExtractor graphics, final Font font, final boolean hovered) {
		int x = this.getX();
		int y = this.getY();
		boolean isOn = this.on.getAsBoolean();
		Draw.roundRectOutlined(graphics, x, y, this.getWidth(), this.getHeight(), 4,
			hovered ? this.theme.cardHover() : this.theme.card(), isOn ? this.theme.accent() : this.theme.border());

		int iconY = y + (this.getHeight() - ICON_SIZE) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, this.icon.get(), x + 4, iconY, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

		int textX = x + ICON_SIZE + 10;
		int textWidth = this.getWidth() - ICON_SIZE - 14 - SWITCH_WIDTH - 8;
		graphics.text(font, Draw.fit(font, this.title.getString(), textWidth), textX, y + 5, this.warning ? 0xFFFF6B6B : this.theme.text(), false);
		graphics.text(font, Draw.fit(font, this.description.getString().replace('\n', ' '), textWidth), textX, y + 17, this.theme.muted(), false);

		// The switch: a pill, green with the knob right when on, gray with the knob left when off
		int switchX = x + this.getWidth() - SWITCH_WIDTH - 6;
		int switchY = y + (this.getHeight() - SWITCH_HEIGHT) / 2;
		Draw.roundRect(graphics, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT / 2, isOn ? this.theme.accent() : this.theme.off());
		int knob = SWITCH_HEIGHT - 4;
		Draw.circle(graphics, isOn ? switchX + SWITCH_WIDTH - knob - 2 : switchX + 2, switchY + 2, knob, 0xFFFFFFFF);
	}
}
