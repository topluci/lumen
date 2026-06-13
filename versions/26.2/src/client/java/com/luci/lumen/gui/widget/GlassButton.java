package com.luci.lumen.gui.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class GlassButton {
    private final int x, y, width, height;
    private final Component label;
    private boolean hovered = false;
    private boolean pressed = false;
    private Runnable onClick;

    public GlassButton(int x, int y, int width, int height, Component label, Runnable onClick) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.onClick = onClick;
    }

    public void render(GuiGraphicsExtractor ctx, int mx, int my) {
        hovered = mx >= x && mx <= x + width && my >= y && my <= y + height;
        float alpha = pressed ? 0.20f : (hovered ? 0.15f : 0.08f);
        int bgColor = Theme.glassWithAlpha(alpha);
        ctx.fill(x, y, x + width, y + height, bgColor);
        if (hovered) {
            ctx.fill(x, y, x + width, y + 1, Theme.HIGHLIGHT);
        }
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int textX = x + (width - font.width(label)) / 2;
        int textY = y + (height - 9) / 2;
        ctx.text(font, label, textX, textY, Theme.TEXT_PRIMARY);
    }

    public boolean mouseClicked(double mx, double my) {
        if (mx >= x && mx <= x + width && my >= y && my <= y + height) {
            pressed = true;
            if (onClick != null) onClick.run();
            return true;
        }
        return false;
    }

    public void setOnClick(Runnable onClick) { this.onClick = onClick; }
}
