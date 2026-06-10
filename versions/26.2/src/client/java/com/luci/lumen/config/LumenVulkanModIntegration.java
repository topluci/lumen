package com.luci.lumen.config;

import com.luci.lumen.LumenInit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class LumenVulkanModIntegration {
    private static Class<?> optionClass, blockClass, impactClass;
    private static Constructor<?> switchCtor, rangeCtor, blockCtor, pageCtor;
    private static Method setImpact;
    private static Object impactLow, impactMed, impactHigh;
    private static final Component LUMEN_NAME = Component.literal("Lumen")
            .withStyle(ChatFormatting.GOLD);

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded("vulkanmod")) {
            LumenInit.LOGGER.info("[Lumen] VulkanMod not loaded, skipping settings integration");
            return;
        }
        try {
            initReflection();
            registerEntry();
        } catch (Exception e) {
            LumenInit.LOGGER.warn("[Lumen] Failed to register with VulkanMod settings", e);
        }
    }

    private static void initReflection() throws Exception {
        optionClass = Class.forName("net.vulkanmod.config.option.Option");
        blockClass = Class.forName("net.vulkanmod.config.gui.OptionBlock");
        impactClass = Class.forName("net.vulkanmod.config.option.PerformanceImpact");

        var switchOptClass = Class.forName("net.vulkanmod.config.option.SwitchOption");
        var rangeOptClass = Class.forName("net.vulkanmod.config.option.RangeOption");

        switchCtor = switchOptClass.getConstructor(FormattedText.class, Consumer.class, Supplier.class);
        rangeCtor = rangeOptClass.getConstructor(FormattedText.class, double.class, double.class, double.class, double.class, Consumer.class, Supplier.class);

        impactLow = impactClass.getField("LOW").get(null);
        impactMed = impactClass.getField("MEDIUM").get(null);
        impactHigh = impactClass.getField("HIGH").get(null);

        setImpact = optionClass.getMethod("setImpact", impactClass);

        blockCtor = Class.forName("net.vulkanmod.config.gui.OptionBlock")
                .getConstructor(String.class, Class.forName("[Lnet.vulkanmod.config.option.Option;"));
        pageCtor = Class.forName("net.vulkanmod.config.option.OptionPage")
                .getConstructor(String.class, Class.forName("[Lnet.vulkanmod.config.gui.OptionBlock;"));

        LumenInit.LOGGER.info("[Lumen] VulkanMod reflection initialized");
    }

    private static void registerEntry() throws Exception {
        var registryClass = Class.forName("net.vulkanmod.config.gui.ModSettingsRegistry");
        Object registry = registryClass.getField("INSTANCE").get(null);

        var entryClass = Class.forName("net.vulkanmod.config.gui.ModSettingsEntry");
        var entryCtor = entryClass.getConstructor(
                FormattedText.class, Supplier.class, Supplier.class, Runnable.class);

        Object entry = entryCtor.newInstance(
                LUMEN_NAME,
                (Supplier<Identifier>) () -> Identifier.fromNamespaceAndPath("lumen", "icon.png"),
                (Supplier<List<?>>) LumenVulkanModIntegration::buildPages,
                (Runnable) LumenConfig::save);

        registryClass.getMethod("addModEntry", entryClass).invoke(registry, entry);
        LumenInit.LOGGER.info("[Lumen] Registered in VulkanMod settings");
    }

    private static Object[] newOptArray(List<Object> items) {
        Object arr = Array.newInstance(optionClass, items.size());
        for (int i = 0; i < items.size(); i++) Array.set(arr, i, items.get(i));
        return (Object[]) arr;
    }

    private static Object[] newBlockArray(List<Object> items) {
        Object arr = Array.newInstance(blockClass, items.size());
        for (int i = 0; i < items.size(); i++) Array.set(arr, i, items.get(i));
        return (Object[]) arr;
    }

    private static List<Object> buildPages() {
        List<Object> pages = new ArrayList<>();
        try {
            var cfg = LumenConfig.get();

            pages.add(pageCtor.newInstance("General",
                    newBlockArray(List.of(
                            blockCtor.newInstance("",
                                    newOptArray(List.of(
                                            makeSwitch("Enable Lumen", b -> cfg.enabled = b, () -> cfg.enabled, impactLow),
                                            makeSwitch("HDR", b -> cfg.hdrEnabled = b, () -> cfg.hdrEnabled, impactMed),
                                            makeSwitch("Frame Gen", b -> cfg.frameGenEnabled = b, () -> cfg.frameGenEnabled, impactHigh)
                                    )))
                    ))));

            pages.add(pageCtor.newInstance("Post-Process",
                    newBlockArray(List.of(
                            blockCtor.newInstance("",
                                    newOptArray(List.of(
                                            makeRange("Brightness", (double) cfg.brightness, -1.0, 1.0, 0.01, v -> cfg.brightness = v.floatValue(), () -> (double) cfg.brightness, impactLow),
                                            makeRange("Contrast", (double) cfg.contrast, -1.0, 1.0, 0.01, v -> cfg.contrast = v.floatValue(), () -> (double) cfg.contrast, impactLow),
                                            makeRange("Saturation", (double) cfg.saturation, 0.0, 2.0, 0.01, v -> cfg.saturation = v.floatValue(), () -> (double) cfg.saturation, impactLow),
                                            makeRange("Exposure", (double) cfg.exposure, -2.0, 2.0, 0.01, v -> cfg.exposure = v.floatValue(), () -> (double) cfg.exposure, impactLow)
                                    )))
                    ))));

            pages.add(pageCtor.newInstance("Performance",
                    newBlockArray(List.of(
                            blockCtor.newInstance("",
                                    newOptArray(List.of(
                                            makeRange("RT Quality", (double) cfg.rtQuality, 0.0, 2.0, 1.0, v -> cfg.rtQuality = v.intValue(), () -> (double) cfg.rtQuality, impactHigh)
                                    )))
                    ))));

            LumenInit.LOGGER.info("[Lumen] Built {} option pages", pages.size());
        } catch (Exception e) {
            LumenInit.LOGGER.warn("[Lumen] Failed to build option pages", e);
        }
        return pages;
    }

    private static Object makeSwitch(String name, Consumer<Boolean> setter, Supplier<Boolean> getter, Object impact) throws Exception {
        Object opt = switchCtor.newInstance(Component.literal(name), setter, getter);
        setImpact.invoke(opt, impact);
        return opt;
    }

    private static Object makeRange(String name, double value, double min, double max, double step, Consumer<Double> setter, Supplier<Double> getter, Object impact) throws Exception {
        Object opt = rangeCtor.newInstance(Component.literal(name), value, min, max, step, setter, getter);
        setImpact.invoke(opt, impact);
        return opt;
    }
}
