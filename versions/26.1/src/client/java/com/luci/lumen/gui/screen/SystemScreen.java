package com.luci.lumen.gui.screen;

import com.luci.lumen.api.renderer.DiagnosticsResult;
import com.luci.lumen.api.renderer.RendererManager;
import com.luci.lumen.gui.widget.GlassButton;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class SystemScreen extends LumenScreen {
    private GlassButton backButton;

    public SystemScreen(Screen parent) {
        super(parent, "System", "system");
    }

    @Override
    protected void init() {
        super.init();
        backButton = new GlassButton(CONTENT_LEFT, height - 35, 100, 20,
                Component.translatable("gui.back"),
                () -> minecraft.setScreenAndShow(parent));
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        var font = minecraft.font;
        ctx.text(font, Component.literal("\u00a77System \u00a78| \u00a7fDiagnostics"),
                CONTENT_LEFT, CONTENT_TOP + 35, 0xFFFFFFFF);

        int y = CONTENT_TOP + 55;
        int col1 = CONTENT_LEFT;
        int col2 = col1 + 130;

        String mcVersion = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        drawInfo(ctx, col1, col2, y, "Minecraft", mcVersion); y += 14;
        drawInfo(ctx, col1, col2, y, "Lumen", "0.1.0"); y += 14;
        drawInfo(ctx, col1, col2, y, "Renderer", RendererManager.get().getStatusMessage()); y += 14;

        var info = RendererManager.get().getActive().getInfo();
        drawInfo(ctx, col1, col2, y, "GPU", info.gpuName()); y += 14;
        drawInfo(ctx, col1, col2, y, "Backend", info.name() + " " + info.version()); y += 14;
        drawInfo(ctx, col1, col2, y, "Java", System.getProperty("java.version")); y += 14;
        drawInfo(ctx, col1, col2, y, "Memory", Runtime.getRuntime().totalMemory() / 1024 / 1024 + "MB"); y += 14;
        y += 10;

        ctx.text(font, Component.literal("\u00a7eRay Tracing Diagnostics"), col1, y, 0xFFFFFFFF);
        y += 16;

        DiagnosticsResult diag = RendererManager.get().getActive().getDiagnostics();
        for (Map.Entry<String, DiagnosticsResult.StepStatus> entry : diag.steps().entrySet()) {
            DiagnosticsResult.StepStatus step = entry.getValue();
            String icon = switch (step.status()) {
                case OK -> "\u00a7a\u2713";
                case FAILED -> "\u00a7c\u2717";
                case SKIPPED -> "\u00a78\u2014";
                case NOT_APPLICABLE -> "\u00a78\u00b7";
            };
            String color = switch (step.status()) {
                case OK -> "\u00a7a";
                case FAILED -> "\u00a7c";
                case SKIPPED, NOT_APPLICABLE -> "\u00a78";
            };
            ctx.text(font, Component.literal(icon + " " + color + step.label() + " \u00a77" + step.message()),
                    col1 + 10, y, 0xFFFFFFFF);
            y += 12;
        }

        backButton.render(ctx, mx, my);
    }

    private void drawInfo(GuiGraphicsExtractor ctx, int col1, int col2, int y, String label, String value) {
        ctx.text(minecraft.font, Component.literal("\u00a78" + label + ":"), col1, y, 0xFFFFFFFF);
        ctx.text(minecraft.font, Component.literal("\u00a7f" + value), col2, y, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == 0 && backButton.mouseClicked(event.x(), event.y())) return true;
        return false;
    }
}
