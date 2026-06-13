package com.luci.lumen.gui.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@Environment(EnvType.CLIENT)
public class GlassPanel {
    private final int x, y, width, height;
    private final float alpha;
    private boolean highlight = true;

    public GlassPanel(int x, int y, int width, int height, float alpha) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.alpha = alpha;
    }

    public GlassPanel setHighlight(boolean highlight) {
        this.highlight = highlight;
        return this;
    }

    public void render(GuiGraphicsExtractor ctx) {
        int bgColor = Theme.glassWithAlpha(alpha);
        ctx.fill(x, y, x + width, y + height, bgColor);

        if (highlight) {
            int hlColor = Theme.glassWithAlpha(Math.min(alpha + 0.12f, 0.35f));
            ctx.fill(x, y, x + width, y + 1, hlColor);
        }

        ctx.fill(x, y, x + 1, y + height, Theme.BORDER);
        ctx.fill(x + width - 1, y, x + width, y + height, Theme.BORDER);
        ctx.fill(x, y, x + width, y + height, Theme.BORDER);
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
