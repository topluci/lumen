package com.luci.lumen.gui.screen;

import com.luci.lumen.LumenInit;
import com.luci.lumen.api.renderer.RendererManager;
import com.luci.lumen.config.LumenConfig;
import com.luci.lumen.gui.widget.GlassButton;
import com.luci.lumen.gui.widget.GlassSearchBar;
import com.luci.lumen.gui.widget.Theme;
import com.luci.lumen.shaderpack.ShaderPackManager;
import com.luci.lumen.vk.LumenNativeBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ShaderScreen extends LumenScreen {
    private List<ShaderPackManager.PackEntry> packs;
    private int scrollOffset;
    private String notificationMessage;
    private long notificationTimer;
    private GlassSearchBar searchBar;
    private GlassButton browseButton;
    private GlassButton openFolderButton;
    private GlassButton doneButton;

    private static final int PANEL_W = 300;
    private static final int ENTRY_H = 42;
    private static final int ENTRY_GAP = 6;
    private static final int LIST_TOP = 90;

    public ShaderScreen(Screen parent) {
        super(parent, "Shader Packs", "shaders");
    }

    @Override
    protected void init() {
        super.init();
        refreshPacks();
        int x = CONTENT_LEFT;
        searchBar = new GlassSearchBar(x, 50, PANEL_W, 22, q -> {});
        browseButton = new GlassButton(x, height - 35, 90, 20,
                Component.literal("Browse"), this::browseFiles);
        openFolderButton = new GlassButton(x + 95, height - 35, 90, 20,
                Component.literal("Open Folder"), ShaderPackManager::openShaderpacksFolder);
        doneButton = new GlassButton(x + 190, height - 35, 90, 20,
                Component.literal("Done"), () -> {
            LumenConfig.save();
            Minecraft.getInstance().setScreenAndShow(parent);
        });
    }

    private void refreshPacks() {
        packs = ShaderPackManager.discoverPacks();
        scrollOffset = 0;
    }

    private void browseFiles() {
        Frame frame = new Frame();
        java.awt.FileDialog dialog = new java.awt.FileDialog(frame, "Select a shader pack", java.awt.FileDialog.LOAD);
        dialog.setFilenameFilter((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".zip") || new File(dir, name).isDirectory();
        });
        dialog.setMultipleMode(true);
        dialog.setVisible(true);
        String[] files = dialog.getFiles() != null
                ? java.util.Arrays.stream(dialog.getFiles()).map(File::getAbsolutePath).toArray(String[]::new)
                : dialog.getFile() != null ? new String[]{dialog.getDirectory() + dialog.getFile()} : new String[0];
        frame.dispose();
        if (files.length > 0) {
            int imported = 0;
            for (String f : files) {
                Path path = Path.of(f);
                if (ShaderPackManager.isValidPack(path) && ShaderPackManager.importPack(path) != null) imported++;
            }
            if (imported > 0) { refreshPacks(); showNotification("\u00a7aImported " + imported + " pack(s)"); }
            else { showNotification("\u00a7eNo valid packs found"); }
        }
    }

    private void showNotification(String msg) {
        notificationMessage = msg;
        notificationTimer = System.currentTimeMillis() + 4000;
    }

    @Override
    public void tick() {
        super.tick();
        if (notificationMessage != null && System.currentTimeMillis() > notificationTimer) {
            notificationMessage = null;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double deltaX, double deltaY) {
        int visCount = (height - LIST_TOP - 70) / (ENTRY_H + ENTRY_GAP);
        int maxScroll = Math.max(0, packs.size() - visCount);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(deltaY)));
        return true;
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        var font = minecraft.font;
        ctx.text(font, Component.literal("\u00a77Shaders \u00a78| \u00a7fPack Selection"),
                CONTENT_LEFT, 20, 0xFFFFFFFF);

        searchBar.render(ctx);

        ctx.text(font, Component.literal("\u00a77" + packs.size() + " packs  \u00a78|  " + RendererManager.get().getStatusMessage()),
                CONTENT_LEFT, 72, 0xFFFFFFFF);

        int y = LIST_TOP;
        int entryX = CONTENT_LEFT;
        int listBottom = height - 70;

        for (int i = scrollOffset; i < packs.size(); i++) {
            if (y + ENTRY_H > listBottom) break;
            var pack = packs.get(i);
            boolean active = pack.name().equals(LumenConfig.get().shaderPack);
            boolean hovered = mx >= entryX && mx <= entryX + PANEL_W && my >= y && my <= y + ENTRY_H;

            float alpha = hovered ? 0.28f : (active ? 0.18f : 0.10f);
            ctx.fill(entryX, y, entryX + PANEL_W, y + ENTRY_H, Theme.glassWithAlpha(alpha));
            if (active) {
                ctx.fill(entryX, y, 3, y + ENTRY_H, Theme.ACCENT_GREEN);
            }

            String name = pack.displayName();
            if (!pack.isDirectory() && !pack.isBuiltin()) name += " \u00a77.zip";

            boolean compatible = RendererManager.get().canHandle(
                    ShaderPackManager.getShaderpacksDir().resolve(pack.name()));
            int textColor = compatible ? (active ? 0xFFFFFFFF : (hovered ? 0xFFFFFFFF : 0xFFCCCCCC)) : 0xFF666666;

            ctx.text(font, Component.literal(name), entryX + 14, y + 6, textColor);

            if (active) {
                ctx.text(font, Component.literal("\u25b6"), entryX + 4, y + 12, 0xFF4CAF50);
            }
            if (!compatible && hovered) {
                ctx.text(font, Component.literal("Incompatible with current renderer"), entryX + 14, y + 22, 0xFF888888);
            }

            y += ENTRY_H + ENTRY_GAP;
        }

        browseButton.render(ctx, mx, my);
        openFolderButton.render(ctx, mx, my);
        doneButton.render(ctx, mx, my);

        if (notificationMessage != null) {
            ctx.text(font, Component.literal(notificationMessage), CONTENT_LEFT, 10, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();

            if (searchBar.mouseClicked(mx, my)) return true;
            if (browseButton.mouseClicked(mx, my)) return true;
            if (openFolderButton.mouseClicked(mx, my)) return true;
            if (doneButton.mouseClicked(mx, my)) return true;

            int y = LIST_TOP;
            int listBottom = height - 70;
            for (int i = scrollOffset; i < packs.size(); i++) {
                if (y + ENTRY_H > listBottom) break;
                boolean hit = mx >= CONTENT_LEFT && mx <= CONTENT_LEFT + PANEL_W && my >= y && my <= y + ENTRY_H;
                if (hit) {
                    var pack = packs.get(i);
                    LumenConfig.get().shaderPack = pack.name();
                    if (!pack.isBuiltin()) {
                        RendererManager.get().setShaderPack(
                                ShaderPackManager.getShaderpacksDir().resolve(pack.name()));
                    }
                    ShaderPackManager.refreshNativeShader();
                    refreshPacks();
                    return true;
                }
                y += ENTRY_H + ENTRY_GAP;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        int imported = 0;
        for (Path path : paths) {
            if (ShaderPackManager.isValidPack(path)) {
                if (ShaderPackManager.importPack(path) != null) imported++;
            }
        }
        if (imported > 0) {
            refreshPacks();
            showNotification("\u00a7aImported " + imported + " pack(s)");
        }
    }
}
