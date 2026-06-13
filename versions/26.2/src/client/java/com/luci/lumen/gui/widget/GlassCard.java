package com.luci.lumen.gui.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@Environment(EnvType.CLIENT)
public class GlassCard {
    private final int x, y, width, height;
    private float alpha = 0.12f;
    private boolean hovered = false;
    private boolean active = false;

    public GlassCard(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public GlassCard setHovered(boolean hovered) {
        this.hovered = hovered;
        return this;
    }

    public GlassCard setActive(boolean active) {
        this.active = active;
        return this;
    }

    public void render(GuiGraphicsExtractor ctx) {
        float a = active ? 0.22f : (hovered ? 0.18f : 0.12f);
        int bgColor = Theme.glassWithAlpha(a);
        ctx.fill(x, y, x + width, y + height, bgColor);

        if (active) {
            ctx.fill(x, y, x + width, y + 1, Theme.HIGHLIGHT);
        } else if (hovered) {
            ctx.fill(x, y, x + width, y + 1, Theme.glassWithAlpha(0.25f));
        }

        ctx.fill(x, y, x + 1, y + height, Theme.BORDER);
        ctx.fill(x + width - 1, y, x + width, y + height, Theme.BORDER);
    }

    public boolean isMouseOver(int mx, int my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }
}
