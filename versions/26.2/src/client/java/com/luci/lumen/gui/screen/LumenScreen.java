package com.luci.lumen.gui.screen;

import com.luci.lumen.gui.widget.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class LumenScreen extends Screen {
    protected final Screen parent;
    protected final String categoryKey;

    protected static final int SIDEBAR_W = 150;
    protected static final int CONTENT_LEFT = SIDEBAR_W + 24;
    protected static final int CONTENT_TOP = 30;
    protected static final int SIDEBAR_ITEM_H = 32;

    private static final String[][] SIDEBAR_ITEMS = {
        {"Lumen", "hub"},       {"Graphics", "graphics"},
        {"Lighting", "lighting"}, {"Shaders", "shaders"},
        {"Performance", "perf"}, {"Accessibility", "access"},
        {"Advanced", "advanced"}, {"System", "system"},
    };

    protected LumenScreen(Screen parent, String title, String categoryKey) {
        super(Component.literal(title));
        this.parent = parent;
        this.categoryKey = categoryKey;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, ((int) (0.06f * 255) << 24) | 0x000000);
        renderSidebar(ctx, mx, my);
        renderContent(ctx, mx, my, delta);
        super.extractRenderState(ctx, mx, my, delta);
    }

    protected abstract void renderContent(GuiGraphicsExtractor ctx, int mx, int my, float delta);

    private void renderSidebar(GuiGraphicsExtractor ctx, int mx, int my) {
        ctx.fill(0, 0, SIDEBAR_W, height, Theme.glassWithAlpha(0.10f));
        ctx.fill(SIDEBAR_W, 0, SIDEBAR_W + 1, height, Theme.BORDER);

        var font = Minecraft.getInstance().font;
        int y = 20;
        for (var item : SIDEBAR_ITEMS) {
            String label = item[0];
            String cat = item[1];
            boolean active = cat.equals(categoryKey);
            boolean hovered = mx >= 0 && mx <= SIDEBAR_W && my >= y && my <= y + SIDEBAR_ITEM_H;

            if (active) {
                ctx.fill(0, y, SIDEBAR_W, y + SIDEBAR_ITEM_H, Theme.glassWithAlpha(0.18f));
                ctx.fill(0, y, 3, y + SIDEBAR_ITEM_H, Theme.ACCENT_GREEN);
            } else if (hovered) {
                ctx.fill(0, y, SIDEBAR_W, y + SIDEBAR_ITEM_H, Theme.glassWithAlpha(0.10f));
            }

            ctx.text(font, Component.literal(label), 16, y + 9,
                    active ? 0xFFFFFFFF : Theme.TEXT_SECONDARY);
            y += SIDEBAR_ITEM_H;
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && event.x() <= SIDEBAR_W) {
            double my = event.y();
            int y = 20;
            for (var item : SIDEBAR_ITEMS) {
                String cat = item[1];
                if (!cat.equals(categoryKey) && my >= y && my <= y + SIDEBAR_ITEM_H) {
                    navigateTo(cat);
                    return true;
                }
                y += SIDEBAR_ITEM_H;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    protected void navigateTo(String cat) {
        switch (cat) {
            case "hub"       -> Minecraft.getInstance().setScreenAndShow(new HubScreen(null));
            case "graphics"  -> Minecraft.getInstance().setScreenAndShow(new GraphicsScreen(null));
            case "lighting"  -> Minecraft.getInstance().setScreenAndShow(new LightingScreen(null));
            case "shaders"   -> Minecraft.getInstance().setScreenAndShow(new ShaderScreen(null));
            case "perf"      -> Minecraft.getInstance().setScreenAndShow(new PerformanceScreen(null));
            case "access"    -> Minecraft.getInstance().setScreenAndShow(new AccessibilityScreen(null));
            case "advanced"  -> Minecraft.getInstance().setScreenAndShow(new AdvancedScreen(null));
            case "system"    -> Minecraft.getInstance().setScreenAndShow(new SystemScreen(null));
        }
    }
}
