package com.luci.lumen.gui.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public class GlassSlider {
    private final int x, y, width;
    private final Component label;
    private final double min, max, step;
    private final Consumer<Double> setter;
    private final Supplier<Double> getter;

    private static final int HEIGHT = 20;
    private static final int TRACK_H = 4;
    private static final int THUMB_R = 6;

    private boolean dragging = false;

    public GlassSlider(int x, int y, int width, Component label,
                       double min, double max, double step,
                       Consumer<Double> setter, Supplier<Double> getter) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.setter = setter;
        this.getter = getter;
    }

    public void render(GuiGraphicsExtractor ctx) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        ctx.text(font, label, x, y + 4, Theme.TEXT_PRIMARY);

        int tx = x + 120;
        int ty = y + HEIGHT / 2 - TRACK_H / 2;
        ctx.fill(tx, ty, tx + width, ty + TRACK_H, Theme.glassWithAlpha(0.15f));

        double value = getter.get();
        float frac = (float) ((value - min) / (max - min));
        int fillEnd = tx + (int) (width * frac);
        ctx.fill(tx, ty, fillEnd, ty + TRACK_H, 0xFF4CAF50);

        int thumbX = fillEnd;
        int thumbY = ty + TRACK_H / 2;
        ctx.fill(thumbX - THUMB_R, thumbY - THUMB_R, thumbX + THUMB_R, thumbY + THUMB_R, 0xFFFFFFFF);

        String valueStr = String.format("%.2f", value);
        ctx.text(font, Component.literal(valueStr), tx + width + 8, y + 4, Theme.TEXT_SECONDARY);
    }

    public boolean mouseClicked(double mx, double my) {
        int tx = x + 120;
        int ty = y + HEIGHT / 2 - THUMB_R;
        if (mx >= tx - THUMB_R && mx <= tx + width + THUMB_R && my >= ty && my <= ty + THUMB_R * 2) {
            dragging = true;
            updateFromMouse(mx, tx);
            return true;
        }
        return false;
    }

    public boolean mouseReleased() {
        if (dragging) { dragging = false; return true; }
        return false;
    }

    public void mouseDragged(double mx) {
        if (dragging) {
            int tx = x + 120;
            updateFromMouse(mx, tx);
        }
    }

    private void updateFromMouse(double mx, int tx) {
        float frac = (float) ((mx - tx) / width);
        frac = Math.max(0, Math.min(1, frac));
        double value = min + frac * (max - min);
        if (step > 0) value = Math.round(value / step) * step;
        setter.accept(value);
    }
}
