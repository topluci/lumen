package com.luci.lumen.compat;

import com.luci.lumen.LumenInit;
import net.fabricmc.loader.api.FabricLoader;

public class CompatibilityGuard {
    private static boolean checked = false;
    private static boolean irisPresent = false;
    private static boolean sodiumPresent = false;
    private static boolean lithiumPresent = false;
    private static boolean potassiumPresent = false;
    private static boolean vulkanModPresent = false;
    private static boolean berylPresent = false;

    public static void check() {
        if (checked) return;
        checked = true;

        var loader = FabricLoader.getInstance();

        irisPresent = loader.isModLoaded("iris");
        sodiumPresent = loader.isModLoaded("sodium") || loader.isModLoaded("indium");
        lithiumPresent = loader.isModLoaded("lithium");
        potassiumPresent = loader.isModLoaded("potassium");
        vulkanModPresent = loader.isModLoaded("vulkanmod");
        berylPresent = loader.isModLoaded("beryl");

        if (irisPresent) {
            LumenInit.LOGGER.info("[Lumen] Iris Shaders detected — Lumen post-process will defer to Iris when shader packs are active");
        }
        if (sodiumPresent) {
            LumenInit.LOGGER.info("[Lumen] Sodium detected — using Sodium-compatible buffer access");
        }
        if (lithiumPresent) {
            LumenInit.LOGGER.info("[Lumen] Lithium detected — no action needed");
        }
        if (potassiumPresent) {
            LumenInit.LOGGER.info("[Lumen] Potassium detected — sharing GPU configuration");
        }
        if (vulkanModPresent) {
            LumenInit.LOGGER.info("[Lumen] VulkanMod detected — using VulkanMod device instead of built-in Vulkan");
        }
        if (berylPresent) {
            LumenInit.LOGGER.info("[Lumen] Beryl detected — shader pipeline integration available");
        }
    }

    public static boolean hasIris() { return irisPresent; }
    public static boolean hasSodium() { return sodiumPresent; }
    public static boolean hasLithium() { return lithiumPresent; }
    public static boolean hasPotassium() { return potassiumPresent; }
    public static boolean hasVulkanMod() { return vulkanModPresent; }
    public static boolean hasBeryl() { return berylPresent; }
    public static boolean shouldDisablePostProcess() { return irisPresent; }

    public static String getSummary() {
        var sb = new StringBuilder();
        if (irisPresent) sb.append("Iris ");
        if (sodiumPresent) sb.append("Sodium ");
        if (lithiumPresent) sb.append("Lithium ");
        if (potassiumPresent) sb.append("Potassium ");
        if (vulkanModPresent) sb.append("VulkanMod ");
        if (berylPresent) sb.append("Beryl ");
        if (sb.isEmpty()) return "none";
        return sb.toString().trim();
    }
}
