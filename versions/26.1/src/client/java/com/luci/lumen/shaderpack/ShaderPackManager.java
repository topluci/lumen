package com.luci.lumen.shaderpack;

import com.luci.lumen.LumenInit;
import com.luci.lumen.config.LumenConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class ShaderPackManager {
    private static final Path SHADERPACKS_DIR = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
    private static WatchService watcher;
    private static Thread watchThread;
    private static volatile boolean watching = false;

    public static Path getShaderpacksDir() {
        return SHADERPACKS_DIR;
    }

    public static List<PackEntry> discoverPacks() {
        List<PackEntry> packs = new ArrayList<>();
        packs.add(new PackEntry("builtin", true, true));
        if (!Files.isDirectory(SHADERPACKS_DIR)) return packs;

        try (Stream<Path> stream = Files.list(SHADERPACKS_DIR)) {
            stream.forEach(path -> {
                String name = path.getFileName().toString();
                if (isValidPack(path)) {
                    packs.add(new PackEntry(name, false, isDirectoryPack(path)));
                }
            });
        } catch (IOException e) {
            LumenInit.LOGGER.warn("[Lumen] Failed to scan shaderpacks dir", e);
        }
        return packs;
    }

    public static boolean isValidPack(Path path) {
        if (!Files.exists(path)) return false;
        if (Files.isDirectory(path)) {
            return Files.isDirectory(path.resolve("shaders"));
        }
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".zip");
    }

    private static boolean isDirectoryPack(Path path) {
        return Files.isDirectory(path);
    }

    public static Path importPack(Path source) {
        try {
            Files.createDirectories(SHADERPACKS_DIR);
            String name = source.getFileName().toString();
            Path target = SHADERPACKS_DIR.resolve(name);

            if (Files.isDirectory(source)) {
                copyDirectory(source, target);
            } else {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException e) {
            LumenInit.LOGGER.error("[Lumen] Failed to import shader pack from {}", source, e);
            return null;
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static synchronized void startFileWatcher(Runnable onChange) {
        if (watching) return;
        try {
            Files.createDirectories(SHADERPACKS_DIR);
            watcher = FileSystems.getDefault().newWatchService();
            SHADERPACKS_DIR.register(watcher,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE);
            watching = true;

            watchThread = new Thread(() -> {
                try {
                    while (watching) {
                        WatchKey key = watcher.take();
                        for (WatchEvent<?> event : key.pollEvents()) {
                            if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                            Path changed = SHADERPACKS_DIR.resolve((Path) event.context());
                            if (isValidPack(changed) || event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                                onChange.run();
                                break;
                            }
                        }
                        if (!key.reset()) break;
                    }
                } catch (InterruptedException ignored) {
                }
            }, "lumen-shaderpack-watcher");
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (IOException e) {
            LumenInit.LOGGER.error("[Lumen] Failed to start shaderpack file watcher", e);
        }
    }

    public static synchronized void stopFileWatcher() {
        watching = false;
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
        if (watcher != null) {
            try {
                watcher.close();
            } catch (IOException ignored) {
            }
            watcher = null;
        }
    }

    public static void openShaderpacksFolder() {
        try {
            Files.createDirectories(SHADERPACKS_DIR);
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec("explorer.exe /open," + SHADERPACKS_DIR.toAbsolutePath());
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec("open " + SHADERPACKS_DIR.toAbsolutePath());
            } else {
                Runtime.getRuntime().exec("xdg-open " + SHADERPACKS_DIR.toAbsolutePath());
            }
        } catch (IOException e) {
            LumenInit.LOGGER.error("[Lumen] Failed to open shaderpacks folder", e);
        }
    }

    public static void refreshNativeShader() {
        String packName = LumenConfig.get().shaderPack;
        if ("builtin".equals(packName)) {
            LumenInit.LOGGER.info("[Lumen] Using built-in RT shaders");
            return;
        }
        Path packPath = SHADERPACKS_DIR.resolve(packName);
        if (Files.exists(packPath)) {
            LumenInit.LOGGER.info("[Lumen] Reloading shader pack: {}", packName);
            com.luci.lumen.api.renderer.RendererManager.get().setShaderPack(packPath);
        } else {
            LumenInit.LOGGER.warn("[Lumen] Shader pack not found: {}", packPath);
        }
    }

    public record PackEntry(String name, boolean isBuiltin, boolean isDirectory) {
        public String displayName() {
            return isBuiltin ? "Built-in (Path Tracer)" : name;
        }
    }
}
