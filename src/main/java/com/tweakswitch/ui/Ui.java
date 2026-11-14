package com.tweakswitch.ui;

import com.tweakswitch.Settings;

/** Sizes that depend on the "UI size" setting: Compact (the default) or Normal, with taller rows and buttons. */
public final class Ui {
	private Ui() {
	}

	public static boolean normal() {
		return "NORMAL".equals(Settings.get().uiSize);
	}

	/** Height of the themed buttons. */
	public static int button() {
		return normal() ? 20 : 16;
	}

	/** Height of one switch card; Normal has room for a second line with the pack's description. */
	public static int switchRow() {
		return normal() ? 30 : 22;
	}

	public static int sectionHeader() {
		return normal() ? 18 : 14;
	}

	/** Height of a Vanilla Tweaks tile. */
	public static int tile() {
		return normal() ? 60 : 50;
	}

	/** Height of a Modrinth result card. */
	public static int card() {
		return normal() ? 38 : 32;
	}

	/** Extra header and footer height in Normal. */
	public static int barExtra() {
		return normal() ? 6 : 0;
	}

	/** The hover preview panel's width in GUI pixels. */
	public static int previewWidth() {
		return switch (String.valueOf(Settings.get().previewSize)) {
			case "SMALL" -> 130;
			case "LARGE" -> 200;
			default -> 160;
		};
	}
}
