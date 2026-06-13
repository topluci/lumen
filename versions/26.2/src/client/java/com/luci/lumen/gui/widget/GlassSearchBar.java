package com.luci.lumen.gui.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class GlassSearchBar {
    private final int x, y, width, height;
    private String text = "";
    private boolean focused = false;
    private final Consumer<String> onChange;

    public GlassSearchBar(int x, int y, int width, int height, Consumer<String> onChange) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.onChange = onChange;
    }

    public void render(GuiGraphicsExtractor ctx) {
        int bg = focused ? Theme.glassWithAlpha(0.15f) : Theme.glassWithAlpha(0.08f);
        ctx.fill(x, y, x + width, y + height, bg);
        if (focused) {
            ctx.fill(x, y, x + width, y + 1, Theme.HIGHLIGHT);
        }

        String display = text.isEmpty() && !focused
                ? "Search..."
                : text;
        int color = text.isEmpty() && !focused
                ? Theme.TEXT_DISABLED
                : Theme.TEXT_PRIMARY;
        ctx.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(display), x + 8, y + 5, color);
    }

    public boolean mouseClicked(double mx, double my) {
        focused = mx >= x && mx <= x + width && my >= y && my <= y + height;
        return focused;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;
        if (keyCode == 259 && !text.isEmpty()) {
            text = text.substring(0, text.length() - 1);
            if (onChange != null) onChange.accept(text);
            return true;
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!focused) return false;
        text += chr;
        if (onChange != null) onChange.accept(text);
        return true;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public void setFocused(boolean focused) { this.focused = focused; }
}
