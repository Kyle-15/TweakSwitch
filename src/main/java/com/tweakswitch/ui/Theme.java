package com.tweakswitch.ui;

import com.tweakswitch.Settings;

/**
 * Colors for one look of the TweakSwitch screens, as ARGB. Each source gets its own so its screen feels like its website;
 * the mod's own screens use the green from its icon, or another accent picked in Settings.
 */
public record Theme(
	int background, int bar, int border, int card, int cardHover, int cardSelected, int accent, int onAccent, int text, int muted, int off
) {
	/** The accent colors TweakSwitch's own screens can use. */
	public enum Accent {
		GREEN(0xFF5AE83A, 0xFF10200C, 0xFF3D4A39),
		BLUE(0xFF4AA8FF, 0xFF06182B, 0xFF2F3F52),
		PURPLE(0xFFB48CFF, 0xFF1B0F33, 0xFF403A55),
		ORANGE(0xFFFF9D3A, 0xFF2B1600, 0xFF4F4233),
		PINK(0xFFFF7EB6, 0xFF2E0A1B, 0xFF523A46);

		private final Theme theme;

		Accent(final int accent, final int onAccent, final int selected) {
			this.theme = new Theme(0xFF1F2023, 0xFF2A2B2F, 0xFF3A3C41, 0xFF2E3034, 0xFF383A3F, selected, accent, onAccent, 0xFFFFFFFF, 0xFFA3A6AB, 0xFF6E7076);
		}

		public static Accent fromSettings() {
			try {
				return valueOf(Settings.get().accent);
			} catch (IllegalArgumentException | NullPointerException e) {
				return GREEN;
			}
		}
	}

	/** TweakSwitch's own screens: dark gray with the icon's green. */
	public static final Theme HOME = Accent.GREEN.theme;

	/** TweakSwitch's own screens in the accent color picked in Settings. */
	public static Theme home() {
		return Accent.fromSettings().theme;
	}

	/** Like vanillatweaks.net: charcoal, dark tiles, orange buttons, light gray picked tiles. */
	public static final Theme VANILLA_TWEAKS = new Theme(
		0xFF353535, 0xFF232323, 0xFF4A4A4A, 0xE01A1A1A, 0xE0303030, 0xC0A3A3A3, 0xFFDF691A, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFBDBDBD, 0xFF5A5A5A
	);

	/** Like modrinth.com: near-black, rounded dark cards, bright green. */
	public static final Theme MODRINTH = new Theme(
		0xFF16181C, 0xFF1D1F24, 0xFF2E3138, 0xFF26292F, 0xFF2E3138, 0xFF203A2A, 0xFF1BD96A, 0xFF0C1A12, 0xFFFFFFFF, 0xFFB0BAC5, 0xFF33363D
	);
}
