package com.luci.lumen.gui.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class NodeCard {
    private final int x, y, width;
    private final String title;
    private final List<Port> inputPorts = new ArrayList<>();
    private final List<Port> outputPorts = new ArrayList<>();
    private static final int HEADER_H = 28;
    private static final int PORT_H = 22;
    private static final int PORT_DOT = 8;

    public static class Port {
        public final String label;
        public final int color;
        public Port(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    public NodeCard(int x, int y, int width, String title) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.title = title;
    }

    public NodeCard addInput(String label, int color) {
        inputPorts.add(new Port(label, color));
        return this;
    }

    public NodeCard addOutput(String label, int color) {
        outputPorts.add(new Port(label, color));
        return this;
    }

    public int getHeight() {
        int ports = Math.max(inputPorts.size(), outputPorts.size());
        return HEADER_H + ports * PORT_H + 8;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }

    public int getInputPortCenterY(int index) {
        return y + HEADER_H + index * PORT_H + PORT_H / 2;
    }

    public int getOutputPortCenterY(int index) {
        return y + HEADER_H + index * PORT_H + PORT_H / 2;
    }

    public void render(GuiGraphicsExtractor ctx) {
        int h = getHeight();
        ctx.fill(x, y, x + width, y + h, 0xE61E1E28);
        ctx.fill(x, y, x + width, y + 1, Theme.glassWithAlpha(0.15f));
        ctx.fill(x, y + h - 1, x + width, y + h, Theme.glassWithAlpha(0.10f));
        ctx.fill(x, y, x + 1, y + h, Theme.glassWithAlpha(0.15f));
        ctx.fill(x + width - 1, y, x + width, y + h, Theme.glassWithAlpha(0.15f));

        ctx.fill(x, y, x + width, y + HEADER_H, 0x28FFFFFF);
        ctx.fill(x, y + HEADER_H, x + width, y + HEADER_H + 1, Theme.glassWithAlpha(0.10f));

        var font = Minecraft.getInstance().font;
        ctx.text(font, Component.literal("\u00a77" + title + " \u00a78\u2699"),
                x + 12, y + (HEADER_H - 9) / 2, 0xFFFFFFFF);

        int maxPorts = Math.max(inputPorts.size(), outputPorts.size());
        for (int i = 0; i < maxPorts; i++) {
            int py = y + HEADER_H + i * PORT_H;
            ctx.fill(x, py, x + width, py + 1, Theme.glassWithAlpha(0.05f));
        }

        for (int i = 0; i < inputPorts.size(); i++) {
            Port p = inputPorts.get(i);
            int py = y + HEADER_H + i * PORT_H;
            int dotY = py + (PORT_H - PORT_DOT) / 2;
            ctx.fill(x + 10, dotY, x + 10 + PORT_DOT, dotY + PORT_DOT, p.color);
            ctx.text(font, Component.literal(p.label), x + 24, py + (PORT_H - 9) / 2, Theme.TEXT_SECONDARY);
        }

        for (int i = 0; i < outputPorts.size(); i++) {
            Port p = outputPorts.get(i);
            int py = y + HEADER_H + i * PORT_H;
            int dotY = py + (PORT_H - PORT_DOT) / 2;
            int labelW = font.width(p.label);
            ctx.text(font, Component.literal(p.label), x + width - 24 - labelW, py + (PORT_H - 9) / 2, Theme.TEXT_SECONDARY);
            ctx.fill(x + width - 10 - PORT_DOT, dotY, x + width - 10, dotY + PORT_DOT, p.color);
        }
    }
}
