package com.luci.lumen.gui.screen;

import com.luci.lumen.gui.widget.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class LumenScreen extends Screen {
    protected final Screen parent;
    protected final String categoryKey;

    protected static final int SIDEBAR_W = 70;
    protected static final int CONTENT_LEFT = SIDEBAR_W + 24;
    protected static final int CONTENT_TOP = 30;
    protected static final int SIDEBAR_ITEM_H = 44;

    private static final String[][] SIDEBAR_ITEMS = {
        {"H", "hub"},       {"G", "graphics"},
        {"L", "lighting"},  {"S", "shaders"},
        {"P", "perf"},      {"A", "access"},
        {"V", "advanced"},  {"SY", "system"},
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
        renderTopNav(ctx, mx, my);
        renderContent(ctx, mx, my, delta);
        super.extractRenderState(ctx, mx, my, delta);
    }

    private void renderSidebar(GuiGraphicsExtractor ctx, int mx, int my) {
        ctx.fill(0, 0, SIDEBAR_W, height, Theme.glassWithAlpha(0.10f));
        ctx.fill(SIDEBAR_W, 0, SIDEBAR_W + 1, height, Theme.glassWithAlpha(0.15f));
        var font = Minecraft.getInstance().font;
        int y = 30;
        int iconSize = 36;
        int ix = (SIDEBAR_W - iconSize) / 2;
        for (var item : SIDEBAR_ITEMS) {
            String label = item[0];
            String cat = item[1];
            boolean active = cat.equals(categoryKey);
            boolean hovered = mx >= ix && mx <= ix + iconSize && my >= y && my <= y + iconSize;
            if (active) {
                ctx.fill(ix, y, ix + iconSize, y + iconSize, 0xFF40F1FF);
                ctx.fill(ix + 2, y + 2, ix + iconSize - 2, y + iconSize - 2, 0xFF1A1A2E);
            } else if (hovered) {
                ctx.fill(ix, y, ix + iconSize, y + iconSize, Theme.glassWithAlpha(0.18f));
            } else {
                ctx.fill(ix, y, ix + iconSize, y + iconSize, Theme.glassWithAlpha(0.08f));
            }
            int tc = active ? 0xFF40F1FF : (hovered ? 0xFFFFFFFF : Theme.TEXT_SECONDARY);
            ctx.text(font, Component.literal(label),
                    ix + (iconSize - font.width(Component.literal(label))) / 2,
                    y + (iconSize - 9) / 2, tc);
            y += SIDEBAR_ITEM_H;
        }
    }

    protected void renderTopNav(GuiGraphicsExtractor ctx, int mx, int my) {
        var font = Minecraft.getInstance().font;
        String[] items = {"Back", "Mode: Preset"};
        int bx = CONTENT_LEFT;
        for (String label : items) {
            int bw = font.width(label) + 20;
            boolean hovered = mx >= bx && mx <= bx + bw && my >= CONTENT_TOP && my <= CONTENT_TOP + 22;
            ctx.fill(bx, CONTENT_TOP, bx + bw, CONTENT_TOP + 22,
                    Theme.glassWithAlpha(hovered ? 0.15f : 0.06f));
            ctx.text(font, Component.literal(label),
                    bx + (bw - font.width(label)) / 2,
                    CONTENT_TOP + (22 - 9) / 2, 0xFFFFFFFF);
            bx += bw + 6;
        }
    }

    protected abstract void renderContent(GuiGraphicsExtractor ctx, int mx, int my, float delta);

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            if (mx <= SIDEBAR_W) {
                int y = 30;
                int iconSize = 36;
                int ix = (SIDEBAR_W - iconSize) / 2;
                for (var item : SIDEBAR_ITEMS) {
                    String cat = item[1];
                    if (!cat.equals(categoryKey) && mx >= ix && mx <= ix + iconSize
                            && my >= y && my <= y + iconSize) {
                        navigateTo(cat);
                        return true;
                    }
                    y += SIDEBAR_ITEM_H;
                }
            } else if (my >= CONTENT_TOP && my <= CONTENT_TOP + 22) {
                var font = Minecraft.getInstance().font;
                int bx = CONTENT_LEFT;
                String[] items = {"Back", "Mode: Preset"};
                for (String label : items) {
                    int bw = font.width(label) + 20;
                    if (mx >= bx && mx <= bx + bw) {
                        if (label.equals("Back")) navigateTo("hub");
                        return true;
                    }
                    bx += bw + 6;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    protected void navigateTo(String cat) {
        var newScreen = switch (cat) {
            case "hub"       -> new HubScreen(null);
            case "graphics"  -> new GraphicsScreen(null);
            case "lighting"  -> new LightingScreen(null);
            case "shaders"   -> new ShaderScreen(null);
            case "perf"      -> new PerformanceScreen(null);
            case "access"    -> new AccessibilityScreen(null);
            case "advanced"  -> new AdvancedScreen(null);
            case "system"    -> new SystemScreen(null);
            default          -> null;
        };
        if (newScreen != null) Minecraft.getInstance().setScreenAndShow(newScreen);
    }
}
