package com.luci.lumen.framegen;

import com.luci.lumen.LumenInit;
import com.luci.lumen.config.LumenConfig;

import java.util.ServiceLoader;

public abstract class FrameGenProvider {
    public enum Vendor { NONE, NVIDIA, AMD, INTEL }

    private static FrameGenProvider instance = null;

    public abstract Vendor getVendor();
    public abstract boolean isAvailable();
    public abstract boolean initialize(long vkDevicePtr);
    public abstract boolean generateFrame(long vkCommandBufferPtr, long inputImagePtr, long outputImagePtr);
    public abstract void shutdown();

    public static FrameGenProvider get() {
        if (instance == null) {
            int mode = LumenConfig.get().frameGenMode;
            instance = switch (mode) {
                case 1 -> new NvidiaFrameGen();
                case 2 -> new AmdFrameGen();
                case 3 -> new IntelFrameGen();
                default -> detectBest();
            };
            if (!instance.isAvailable()) {
                LumenInit.LOGGER.warn("[Lumen] {} frame gen not available, falling back", instance.getVendor());
                instance = new NopFrameGen();
            }
        }
        return instance;
    }

    private static FrameGenProvider detectBest() {
        String gpu = System.getProperty("lumen.gpu.vendor", "").toLowerCase();
        if (gpu.contains("nvidia")) return new NvidiaFrameGen();
        if (gpu.contains("amd") || gpu.contains("advanced micro")) return new AmdFrameGen();
        if (gpu.contains("intel")) return new IntelFrameGen();
        return new NopFrameGen();
    }

    public static void reset() { instance = null; }

    private static class NopFrameGen extends FrameGenProvider {
        @Override public Vendor getVendor() { return Vendor.NONE; }
        @Override public boolean isAvailable() { return false; }
        @Override public boolean initialize(long vkDevicePtr) { return false; }
        @Override public boolean generateFrame(long vkCommandBufferPtr, long inputImagePtr, long outputImagePtr) { return false; }
        @Override public void shutdown() {}
    }
}
