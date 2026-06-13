package com.luci.lumen.gui.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Theme {
    public static final int BACKGROUND = 0xFF0B0B0F;
    public static final int GLASS = 0x14FFFFFF;
    public static final int CARD = 0x1FFFFFFF;
    public static final int HIGHLIGHT = 0x33FFFFFF;
    public static final int BORDER = 0x26FFFFFF;
    public static final int TEXT_PRIMARY = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFFCCCCCC;
    public static final int TEXT_DISABLED = 0xFF666666;
    public static final int ACCENT_GREEN = 0xFF4CAF50;

    public static final int RADIUS_WINDOW = 20;
    public static final int RADIUS_CARD = 16;
    public static final int RADIUS_BUTTON = 12;
    public static final int RADIUS_TOGGLE = 14;

    public static final long HOVER_MS = 150;
    public static final float HOVER_SCALE = 1.02f;
    public static final float PRESS_SCALE = 0.98f;

    public static int glassWithAlpha(float alpha) {
        int a = (int) (alpha * 255);
        return (a << 24) | 0xFFFFFF;
    }

    private Theme() {}
}
