package com.tweakswitch.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;

/** A scrolling list whose background, separators, scrollbar and rows draw through {@link Gfx}. 1.21 version. */
public abstract class BridgeList<E extends BridgeList.Entry<E>> extends ContainerObjectSelectionList<E> {
	protected BridgeList(final Minecraft minecraft, final int width, final int height, final int y, final int itemHeight) {
		super(minecraft, width, height, y, itemHeight);
	}

	@Override
	protected final void renderListBackground(final GuiGraphics graphics) {
		this.drawListBackground(new Gfx(graphics));
	}

	@Override
	protected final void renderListSeparators(final GuiGraphics graphics) {
		this.drawListSeparators(new Gfx(graphics));
	}

	@Override
	protected final void renderScrollbar(final GuiGraphics graphics, final int mouseX, final int mouseY) {
		this.drawScrollbar(new Gfx(graphics), mouseX, mouseY);
	}

	protected abstract void drawListBackground(Gfx graphics);

	protected abstract void drawListSeparators(Gfx graphics);

	protected abstract void drawScrollbar(Gfx graphics, int mouseX, int mouseY);

	/** Whether there's more content than fits, so the scrollbar matters. */
	protected boolean canScroll() {
		return this.maxScrollAmount() > 0;
	}

	public abstract static class Entry<E extends Entry<E>> extends ContainerObjectSelectionList.Entry<E> {
		@Override
		public final void renderContent(final GuiGraphics graphics, final int mouseX, final int mouseY, final boolean hovered, final float partialTick) {
			this.drawContent(new Gfx(graphics), mouseX, mouseY, hovered, partialTick);
		}

		public abstract void drawContent(Gfx graphics, int mouseX, int mouseY, boolean hovered, float partialTick);
	}
}
