package com.tweakswitch.ui;

/**
 * Colors for one look of the TweakSwitch screens, as ARGB. Each source gets its own so its screen feels like its website;
 * the mod's own screens use the green from its icon.
 */
public record Theme(
	int background, int bar, int border, int card, int cardHover, int cardSelected, int accent, int onAccent, int text, int muted, int off
) {
	/** TweakSwitch's own screens: dark gray with the icon's green. */
	public static final Theme HOME = new Theme(
		0xFF1F2023, 0xFF2A2B2F, 0xFF3A3C41, 0xFF2E3034, 0xFF383A3F, 0xFF3D4A39, 0xFF5AE83A, 0xFF10200C, 0xFFFFFFFF, 0xFFA3A6AB, 0xFF6E7076
	);

	/** Like vanillatweaks.net: charcoal, dark tiles, orange buttons, light gray picked tiles. */
	public static final Theme VANILLA_TWEAKS = new Theme(
		0xFF353535, 0xFF232323, 0xFF4A4A4A, 0xE01A1A1A, 0xE0303030, 0xC0A3A3A3, 0xFFDF691A, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFBDBDBD, 0xFF5A5A5A
	);

	/** Like modrinth.com: near-black, rounded dark cards, bright green. */
	public static final Theme MODRINTH = new Theme(
		0xFF16181C, 0xFF1D1F24, 0xFF2E3138, 0xFF26292F, 0xFF2E3138, 0xFF203A2A, 0xFF1BD96A, 0xFF0C1A12, 0xFFFFFFFF, 0xFFB0BAC5, 0xFF33363D
	);
}
