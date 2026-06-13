package com.luci.lumen.gui.screen;

import com.luci.lumen.gui.widget.GlassSlider;
import com.luci.lumen.gui.widget.GlassToggle;
import com.luci.lumen.gui.widget.NodeCard;
import com.luci.lumen.gui.widget.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class AdvancedScreen extends LumenScreen {
    private boolean advancedMode = false;
    private final List<ClickableNavBtn> navBtns = new ArrayList<>();
    private NodeCard rtNode, nrdNode;
    private GlassSlider sigmaSlider, maxFramesSlider;
    private GlassToggle jitterToggle;
    private int contentCenterX;
    private int toggleY;

    private static class ClickableNavBtn {
        final String label;
        final int x, y, w, h;
        boolean active, hovered;

        ClickableNavBtn(String label, int x, int y, int w, int h, boolean active) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.active = active;
        }
    }

    public AdvancedScreen(Screen parent) {
        super(parent, "Advanced", "advanced");
    }

    @Override
    protected void init() {
        super.init();
        contentCenterX = CONTENT_LEFT + (width - CONTENT_LEFT - 20) / 2;
        navBtns.clear();

        int bx = CONTENT_LEFT;
        String[][] navItems = {
                {"Back", "false"}, {"Mode: Preset", "false"},
                {"Shader Pack", "true"}, {"Preset: RT-NRD-FSR", "false"},
                {"Reload", "false"}
        };
        for (var item : navItems) {
            int bw = Minecraft.getInstance().font.width(item[0]) + 20;
            navBtns.add(new ClickableNavBtn(item[0], bx, CONTENT_TOP + 10, bw, 22,
                    item[1].equals("true")));
            bx += bw + 6;
        }

        int cy = CONTENT_TOP + 70;
        int nodeW = 220;
        int gap = 60;
        int nodesStartX = contentCenterX - (nodeW * 2 + gap) / 2;

        rtNode = new NodeCard(nodesStartX, cy, nodeW, "Ray Tracing")
                .addOutput("radiance", 0xFFFFB86C)
                .addOutput("diffuse_albedo_metallic", 0xFF50FA7B)
                .addOutput("motion_vector", 0xFFFF79C6);

        nrdNode = new NodeCard(nodesStartX + nodeW + gap, cy, nodeW, "NRD")
                .addInput("diffuse_radiance", 0xFFFFB86C)
                .addInput("specular_radiance", 0xFF50FA7B);

        int sy = cy + Math.max(rtNode.getHeight(), nrdNode.getHeight()) + 40;
        int settingsLeft = contentCenterX - 175;
        int sliderW = 180;

        sigmaSlider = new GlassSlider(settingsLeft, sy, sliderW,
                Component.literal("Antilag Luminance Sigma Scale"), 0, 1, 0.01,
                v -> {}, () -> 0.65);
        sy += 28;

        maxFramesSlider = new GlassSlider(settingsLeft, sy, sliderW,
                Component.literal("Max Accumulated Frames"), 1, 256, 1,
                v -> {}, () -> 128.0);
        sy += 28;

        jitterToggle = new GlassToggle(settingsLeft, sy,
                Component.literal("Use Jitter"), true, () -> {});

        toggleY = sy + 50;
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        var font = minecraft.font;

        for (var btn : navBtns) {
            btn.hovered = mx >= btn.x && mx <= btn.x + btn.w && my >= btn.y && my <= btn.y + btn.h;
            float ba = btn.hovered ? 0.15f : (btn.active ? 0.20f : 0.06f);
            ctx.fill(btn.x, btn.y, btn.x + btn.w, btn.y + btn.h, Theme.glassWithAlpha(ba));
            if (btn.active) {
                ctx.fill(btn.x, btn.y + btn.h - 2, btn.x + btn.w, btn.y + btn.h, 0xFF40F1FF);
            }
            int tc = btn.active ? 0xFF40F1FF : Theme.TEXT_PRIMARY;
            ctx.text(font, Component.literal(btn.label),
                    btn.x + (btn.w - font.width(btn.label)) / 2,
                    btn.y + (btn.h - 9) / 2, tc);
        }

        ctx.text(font, Component.literal("\u00a77Experimental \u00a78Render Pipeline"),
                CONTENT_LEFT, CONTENT_TOP + 42, 0xFFFFFFFF);
        ctx.text(font, Component.literal("\u00a78press ESC or click Back to return"),
                CONTENT_LEFT, CONTENT_TOP + 54, 0xFF666666);

        rtNode.render(ctx);
        nrdNode.render(ctx);

        int x1 = rtNode.getX() + rtNode.getWidth();
        int x2 = nrdNode.getX();
        drawConnection(ctx, x1, rtNode.getOutputPortCenterY(0), x2, nrdNode.getInputPortCenterY(0), 0xFFFFB86C);
        drawConnection(ctx, x1, rtNode.getOutputPortCenterY(1), x2, nrdNode.getInputPortCenterY(1), 0xFF50FA7B);

        sigmaSlider.render(ctx);
        maxFramesSlider.render(ctx);
        jitterToggle.render(ctx);

        boolean toggleHover = mx >= contentCenterX - 60 && mx <= contentCenterX + 60
                && my >= toggleY && my <= toggleY + 20;
        ctx.fill(contentCenterX - 60, toggleY, contentCenterX + 60, toggleY + 20,
                Theme.glassWithAlpha(toggleHover ? 0.18f : 0.08f));
        ctx.fill(contentCenterX - 60, toggleY + 19, contentCenterX + 60, toggleY + 20, 0xFF40F1FF);
        ctx.text(font, Component.literal("\u00a7bADVANCED"),
                contentCenterX - font.width(Component.literal("ADVANCED")) / 2,
                toggleY + 6, 0xFF40F1FF);
    }

    private void drawConnection(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        int midX = (x1 + x2) / 2;
        ctx.fill(x1, y1 - 1, midX, y1 + 1, color);
        ctx.fill(midX, Math.min(y1, y2) - 1, midX + 1, Math.max(y1, y2) + 1, color);
        ctx.fill(midX, y2 - 1, x2, y2 + 1, color);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        double mx = event.x();
        double my = event.y();

        for (var btn : navBtns) {
            if (mx >= btn.x && mx <= btn.x + btn.w && my >= btn.y && my <= btn.y + btn.h) {
                if (btn.label.equals("Back")) {
                    Minecraft.getInstance().setScreen(new HubScreen(null));
                }
                return true;
            }
        }

        if (sigmaSlider.mouseClicked(mx, my)) return true;
        if (maxFramesSlider.mouseClicked(mx, my)) return true;
        if (jitterToggle.mouseClicked(mx, my)) return true;

        if (mx >= contentCenterX - 60 && mx <= contentCenterX + 60
                && my >= toggleY && my <= toggleY + 20) {
            advancedMode = !advancedMode;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double deltaX, double deltaY) {
        if (super.mouseDragged(mx, my, button, deltaX, deltaY)) return true;
        if (button == 0) {
            if (sigmaSlider.isDragging()) {
                sigmaSlider.mouseDragged(mx);
                return true;
            }
            if (maxFramesSlider.isDragging()) {
                maxFramesSlider.mouseDragged(mx);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (super.mouseReleased(mx, my, button)) return true;
        if (button == 0) {
            if (sigmaSlider.isDragging()) {
                sigmaSlider.mouseReleased();
                sigmaSlider.setDragging(false);
                return true;
            }
            if (maxFramesSlider.isDragging()) {
                maxFramesSlider.mouseReleased();
                maxFramesSlider.setDragging(false);
                return true;
            }
        }
        return false;
    }
}
