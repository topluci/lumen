package com.luci.lumen.gui.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@Environment(EnvType.CLIENT)
public class GlassScrollbar {
    private final int x, y, height;
    private int contentHeight;
    private int visibleHeight;
    private int scrollOffset = 0;

    private static final int WIDTH = 6;

    public GlassScrollbar(int x, int y, int height) {
        this.x = x;
        this.y = y;
        this.height = height;
    }

    public void setContentSize(int contentHeight, int visibleHeight) {
        this.contentHeight = contentHeight;
        this.visibleHeight = visibleHeight;
    }

    public void render(GuiGraphicsExtractor ctx) {
        if (contentHeight <= visibleHeight) return;

        float thumbHeight = (float) height * visibleHeight / contentHeight;
        float thumbPos = (float) height * scrollOffset / (contentHeight - visibleHeight);

        ctx.fill(x, y, x + WIDTH, y + height, Theme.glassWithAlpha(0.08f));
        ctx.fill(x, y + (int) thumbPos, x + WIDTH, y + (int) (thumbPos + thumbHeight),
                Theme.glassWithAlpha(0.20f));
    }

    public boolean scroll(double deltaY) {
        int maxOffset = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(deltaY) * 20));
        return true;
    }

    public int getScrollOffset() { return scrollOffset; }
    public void setScrollOffset(int offset) { this.scrollOffset = offset; }
}
