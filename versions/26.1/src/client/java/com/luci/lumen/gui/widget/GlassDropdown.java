package com.luci.lumen.gui.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

@Environment(EnvType.CLIENT)
public class GlassDropdown {
    private final int x, y, width;
    private final Component label;
    private final List<String> options;
    private int selectedIndex = 0;
    private boolean expanded = false;

    private static final int ITEM_H = 20;
    private static final int HEADER_H = 20;

    public GlassDropdown(int x, int y, int width, Component label, List<String> options) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.label = label;
        this.options = options;
    }

    public void render(GuiGraphicsExtractor ctx) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        ctx.text(font, label, x, y + 4, Theme.TEXT_PRIMARY);

        int dx = x + 100;
        ctx.fill(dx, y, dx + width, y + HEADER_H, Theme.glassWithAlpha(0.12f));
        ctx.fill(dx, y, dx + width, y + 1, Theme.HIGHLIGHT);

        String selected = selectedIndex >= 0 && selectedIndex < options.size()
                ? options.get(selectedIndex) : "";
        ctx.text(font, Component.literal(selected), dx + 6, y + 5, Theme.TEXT_PRIMARY);
        ctx.text(font, Component.literal(expanded ? "\u25b2" : "\u25bc"),
                dx + width - 14, y + 5, Theme.TEXT_SECONDARY);

        if (expanded) {
            int ey = y + HEADER_H;
            for (int i = 0; i < options.size(); i++) {
                boolean hovered = false;
                int bg = hovered ? Theme.glassWithAlpha(0.18f) : Theme.glassWithAlpha(0.10f);
                ctx.fill(dx, ey, dx + width, ey + ITEM_H, bg);
                ctx.text(font, Component.literal(options.get(i)), dx + 6, ey + 5,
                        i == selectedIndex ? Theme.ACCENT_GREEN : Theme.TEXT_PRIMARY);
                ey += ITEM_H;
            }
        }
    }

    public boolean mouseClicked(double mx, double my) {
        int dx = x + 100;
        if (mx >= dx && mx <= dx + width && my >= y && my <= y + HEADER_H) {
            expanded = !expanded;
            return true;
        }
        if (expanded) {
            int ey = y + HEADER_H;
            for (int i = 0; i < options.size(); i++) {
                if (mx >= dx && mx <= dx + width && my >= ey && my <= ey + ITEM_H) {
                    selectedIndex = i;
                    expanded = false;
                    return true;
                }
                ey += ITEM_H;
            }
        }
        return false;
    }

    public int getSelectedIndex() { return selectedIndex; }
    public String getSelected() { return options.get(selectedIndex); }
}
