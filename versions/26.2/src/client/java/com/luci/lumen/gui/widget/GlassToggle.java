package com.luci.lumen.gui.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class GlassToggle {
    private final int x, y;
    private final Component label;
    private boolean state;
    private Runnable onChange;

    private static final int WIDTH = 40;
    private static final int HEIGHT = 18;

    public GlassToggle(int x, int y, Component label, boolean initialState, Runnable onChange) {
        this.x = x;
        this.y = y;
        this.label = label;
        this.state = initialState;
        this.onChange = onChange;
    }

    public void render(GuiGraphicsExtractor ctx) {
        ctx.text(net.minecraft.client.Minecraft.getInstance().font, label, x, y + 4, Theme.TEXT_PRIMARY);

        int tx = x + 160;
        int ty = y;
        int bgColor = state ? 0xFF4CAF50 : Theme.glassWithAlpha(0.15f);
        ctx.fill(tx, ty, tx + WIDTH, ty + HEIGHT, bgColor);

        int thumbX = state ? tx + WIDTH - HEIGHT : tx;
        ctx.fill(thumbX + 2, ty + 2, thumbX + HEIGHT - 2, ty + HEIGHT - 2, 0xFFFFFFFF);
    }

    public boolean mouseClicked(double mx, double my) {
        int tx = x + 160;
        if (mx >= tx && mx <= tx + WIDTH && my >= y && my <= y + HEIGHT) {
            state = !state;
            if (onChange != null) onChange.run();
            return true;
        }
        return false;
    }

    public boolean getState() { return state; }
    public void setState(boolean state) { this.state = state; }
}
