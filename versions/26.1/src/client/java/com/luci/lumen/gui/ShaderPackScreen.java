package com.luci.lumen.gui;

import com.luci.lumen.LumenInit;
import com.luci.lumen.config.LumenConfig;
import com.luci.lumen.shaderpack.ShaderPackManager;
import com.luci.lumen.vk.LumenNativeBridge;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ShaderPackScreen extends Screen {
    private final Screen parent;
    private List<ShaderPackManager.PackEntry> packs;
    private List<String> packList;
    private int scrollOffset;
    private int hoveredIndex = -1;
    private String notificationMessage;
    private long notificationTimer;
    private boolean showFiles = false;
    private List<String> filePackList;

    private static final int PANEL_X = 60;
    private static final int PANEL_W = 280;
    private static final int ENTRY_H = 42;
    private static final int ENTRY_GAP = 6;
    private static final int LIST_TOP = 75;
    private static final int LIST_BOTTOM_OFFSET = 60;

    public ShaderPackScreen(Screen parent) {
        super(Component.literal("Shader Packs"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        ShaderPackManager.startFileWatcher(this::onPacksChanged);
        refreshPacks();

        int left = PANEL_X;
        int right = PANEL_X + PANEL_W;

        addRenderableWidget(Button.builder(
                Component.literal("\u2b07 Browse Files"),
                b -> browseFiles()
        ).bounds(left, height - 35, 90, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("\uD83D\uDCC2 Open Folder"),
                b -> ShaderPackManager.openShaderpacksFolder()
        ).bounds(left + 95, height - 35, 90, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                b -> {
                    LumenConfig.save();
                    Minecraft.getInstance().setScreenAndShow(parent);
                }
        ).bounds(left + 190, height - 35, 90, 20).build());
    }

    @Override
    public void removed() {
        super.removed();
        ShaderPackManager.stopFileWatcher();
    }

    private void onPacksChanged() {
        refreshPacks();
        clearWidgets();
        rebuildWidgets();
    }

    private void refreshPacks() {
        packs = ShaderPackManager.discoverPacks();
        packList = new ArrayList<>();
        for (var p : packs) packList.add(p.name());
        filePackList = null;
        showFiles = false;
        scrollOffset = 0;
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
            showNotification("\u00a7aImported " + imported + " pack" + (imported > 1 ? "s" : ""));
        } else {
            showNotification("\u00a7eNo valid packs found (need \u00a77shaders/\u00a7e or .zip)");
        }
    }

    private void browseFiles() {
        Frame frame = new Frame();
        FileDialog dialog = new FileDialog(frame, "Select a shader pack", FileDialog.LOAD);
        dialog.setFilenameFilter((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".zip") || new File(dir, name).isDirectory();
        });
        dialog.setMultipleMode(true);
        dialog.setVisible(true);

        String dir = dialog.getDirectory();
        String[] files = dialog.getFiles() != null
                ? java.util.Arrays.stream(dialog.getFiles()).map(File::getAbsolutePath).toArray(String[]::new)
                : dialog.getFile() != null ? new String[]{dir + dialog.getFile()} : new String[0];
        frame.dispose();

        if (files.length > 0) {
            int imported = 0;
            for (String file : files) {
                Path path = Path.of(file);
                if (ShaderPackManager.isValidPack(path)) {
                    if (ShaderPackManager.importPack(path) != null) imported++;
                }
            }
            if (imported > 0) {
                refreshPacks();
                showNotification("\u00a7aImported " + imported + " pack" + (imported > 1 ? "s" : ""));
            } else {
                showNotification("\u00a7eNo valid packs found");
            }
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
        int visCount = (height - LIST_TOP - LIST_BOTTOM_OFFSET) / (ENTRY_H + ENTRY_GAP);
        int maxScroll = Math.max(0, packList.size() - visCount);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(deltaY)));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        extractBackground(ctx, mx, my, delta);

        int prevIndex = hoveredIndex;
        hoveredIndex = -1;

        int listBottom = height - LIST_BOTTOM_OFFSET;
        int y = LIST_TOP;
        int entryW = PANEL_W;
        int entryX = PANEL_X;
        float[] entryAlpha = {0.0f};

        for (int i = scrollOffset; i < packList.size(); i++) {
            if (y + ENTRY_H > listBottom) break;

            var pack = packs.get(i);
            boolean active = pack.name().equals(LumenConfig.get().shaderPack);
            boolean hovered = mx >= entryX && mx <= entryX + entryW && my >= y && my <= y + ENTRY_H;

            if (hovered) {
                hoveredIndex = i;
                entryAlpha[0] = 0.28f;
            } else {
                entryAlpha[0] = active ? 0.18f : 0.10f;
            }

            int alpha = (int) (entryAlpha[0] * 255);
            int glassColor = (alpha << 24) | 0xFFFFFF;
            int highlightAlpha = (int) ((active ? 0.35f : 0.20f) * 255);
            int highlightColor = (highlightAlpha << 24) | 0xFFFFFF;
            int borderColor = (int) (0.25f * 255) << 24 | 0xFFFFFF;

            ctx.fill(entryX, y, entryX + entryW, y + ENTRY_H, glassColor);

            ctx.fill(entryX, y, entryX + entryW, y + 1, highlightColor);

            ctx.fill(entryX, y, entryX + 1, y + ENTRY_H, borderColor);

            String name = pack.displayName();
            if (!pack.isDirectory() && !pack.isBuiltin()) {
                name += " \u00a77.zip";
            }
            int textColor = active ? 0xFFFFFFFF : (hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
            ctx.text(getFont(), Component.literal(name), entryX + 14, y + 6, textColor);

            if (active) {
                ctx.text(getFont(), Component.literal("\u25b6"), entryX + 4, y + 12, 0xFF4CAF50);
                ctx.text(getFont(), Component.literal("\u00a7aActive"), entryX + 14, y + 22, 0xFF888888);
            }

            if (hovered && !active) {
                ctx.text(getFont(), Component.literal("\u00a77Click to select"), entryX + 14, y + 22, 0xFF888888);
            }

            y += ENTRY_H + ENTRY_GAP;
        }

        ctx.fill(PANEL_X - 2, LIST_TOP - 2, PANEL_X + PANEL_W + 2, listBottom + 2, (int) (0.08f * 255) << 24 | 0x000000);

        ctx.centeredText(getFont(), getTitle(), width / 2, 20, 0xFFFFFFFF);

        int activeCount = (int) packs.stream().filter(p -> p.name().equals(LumenConfig.get().shaderPack)).count();
        String status = "\u00a77" + packs.size() + " packs  \u00a78|  \u00a7a" + activeCount + " active";
        ctx.centeredText(getFont(), Component.literal(status), PANEL_X + PANEL_W / 2, 48, 0xFFFFFFFF);

        if (hoveredIndex >= 0 && hoveredIndex < packs.size()) {
            var pack = packs.get(hoveredIndex);
            String tooltip = "\u00a77" + (pack.isBuiltin() ? "Built-in path tracer" :
                    pack.isDirectory() ? "Folder pack" : "ZIP pack");
            ctx.text(getFont(), Component.literal(tooltip), PANEL_X + PANEL_W + 12, LIST_TOP, 0xFF888888);
        }

        if (notificationMessage != null) {
            int notifW = getFont().width(notificationMessage) + 24;
            int notifX = (width - notifW) / 2;
            int notifAlpha = (int) (0.85f * 255) << 24;
            ctx.fill(notifX - 2, 6, notifX + notifW + 2, 26, notifAlpha | 0x222222);
            ctx.fill(notifX - 1, 7, notifX + notifW + 1, 25, notifAlpha | 0x333333);
            ctx.centeredText(getFont(), Component.literal(notificationMessage), width / 2, 12, 0xFFFFFFFF);
        }

        super.extractRenderState(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            int y = LIST_TOP;
            int listBottom = height - LIST_BOTTOM_OFFSET;

            for (int i = scrollOffset; i < packList.size(); i++) {
                if (y + ENTRY_H > listBottom) break;

                boolean hit = mx >= PANEL_X && mx <= PANEL_X + PANEL_W && my >= y && my <= y + ENTRY_H;
                if (hit) {
                    var pack = packs.get(i);
                    LumenConfig.get().shaderPack = pack.name();
                    if (!pack.isBuiltin()) {
                        LumenNativeBridge.nativeLoadShaderPack(
                                ShaderPackManager.getShaderpacksDir()
                                        .resolve(pack.name()).toString());
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
}
