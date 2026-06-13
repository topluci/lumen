package com.luci.lumen.gui.screen;

import com.luci.lumen.api.renderer.RendererManager;
import com.luci.lumen.gui.widget.GlassCard;
import com.luci.lumen.gui.widget.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HubScreen extends LumenScreen {
    private static final String[] CATEGORIES = {
            "Graphics", "Lighting", "Shaders",
            "Performance", "Accessibility", "Advanced",
            "System"
    };
    private static final String[] CAT_KEYS = {
            "graphics", "lighting", "shaders",
            "perf", "access", "advanced",
            "system"
    };

    public HubScreen(Screen parent) {
        super(parent, "Lumen Hub", "hub");
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        var font = minecraft.font;
        ctx.text(font, Component.literal("\u00a77Lumen \u00a78| \u00a7fGraphics Platform"),
                CONTENT_LEFT, CONTENT_TOP + 35, 0xFFFFFFFF);
        ctx.text(font, Component.literal("\u00a77" + RendererManager.get().getStatusMessage()),
                CONTENT_LEFT, CONTENT_TOP + 51, 0xFFFFFFFF);

        int cols = 3;
        int gap = 12;
        int cardW = 170;
        int cardH = 70;
        int totalW = cols * cardW + (cols - 1) * gap;
        int startX = CONTENT_LEFT + (width - CONTENT_LEFT - totalW) / 2;
        int startY = height / 2 - 60;

        for (int i = 0; i < CATEGORIES.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int x = startX + col * (cardW + gap);
            int y = startY + row * (cardH + gap);
            String cat = CATEGORIES[i];

            boolean hovered = mx >= x && mx <= x + cardW && my >= y && my <= y + cardH;
            ctx.fill(x, y, x + cardW, y + cardH,
                    hovered ? Theme.glassWithAlpha(0.18f) : Theme.glassWithAlpha(0.10f));
            if (hovered) {
                ctx.fill(x, y, x + cardW, y + 1, Theme.HIGHLIGHT);
            }
            ctx.fill(x, y, x + 1, y + cardH, Theme.BORDER);
            ctx.fill(x + cardW - 1, y, x + cardW, y + cardH, Theme.BORDER);

            ctx.text(font, Component.literal(cat), x + 12, y + cardH / 2 - 4, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == 0) {
            int cols = 3;
            int gap = 12;
            int cardW = 170;
            int cardH = 70;
            int totalW = cols * cardW + (cols - 1) * gap;
            int startX = CONTENT_LEFT + (width - CONTENT_LEFT - totalW) / 2;
            int startY = height / 2 - 60;

            double mx = event.x();
            double my = event.y();

            for (int i = 0; i < CATEGORIES.length; i++) {
                int col = i % cols;
                int row = i / cols;
                int x = startX + col * (cardW + gap);
                int y = startY + row * (cardH + gap);
                String catKey = CAT_KEYS[i];

                if (mx >= x && mx <= x + cardW && my >= y && my <= y + cardH) {
                    navigateTo(catKey);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
