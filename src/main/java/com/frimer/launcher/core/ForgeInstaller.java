package com.frimer.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Установка Forge и NeoForge: официальный installer.jar с выводом в лог. */
public final class ForgeInstaller {

    private ForgeInstaller() {
    }

    /**
     * @param kind "forge" или "neoforge"
     */
    public static Path install(String kind, String mcVersion, String loaderVersion,
                               Consumer<String> log, ProgressListener progress) {
        Consumer<String> logger = log == null ? msg -> {
        } : log;
        ProgressListener prog = progress == null ? ProgressListener.NONE : progress;
        boolean forge = "forge".equalsIgnoreCase(kind);

        try {
            Files.createDirectories(Dirs.RUNTIME_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Не могу создать папку runtime", e);
        }
        Mojang.ensureLauncherProfile(Dirs.GAME_DIR);

        Path java = JavaProvisioner.ensureJava(mcVersion, logger, prog);

        String installerUrl;
        String installerName;
        String versionId;
        if (forge) {
            if (loaderVersion == null || loaderVersion.isBlank()) {
                loaderVersion = Loaders.pickForgeVersion(mcVersion);
            }
            installerUrl = Loaders.FORGE_MAVEN + "/" + mcVersion + "-" + loaderVersion + "/"
                    + "forge-" + mcVersion + "-" + loaderVersion + "-installer.jar";
            installerName = "forge-" + mcVersion + "-" + loaderVersion + "-installer.jar";
            versionId = mcVersion + "-forge-" + loaderVersion;
        } else {
            if (loaderVersion == null || loaderVersion.isBlank()) {
                loaderVersion = Loaders.pickNeoforgeVersion(mcVersion);
            }
            installerUrl = Loaders.NEOFORGE_MAVEN + "/" + loaderVersion + "/"
                    + "neoforge-" + loaderVersion + "-installer.jar";
            installerName = "neoforge-" + loaderVersion + "-installer.jar";
            versionId = "neoforge-" + loaderVersion;
        }

        Path installer = Dirs.RUNTIME_DIR.resolve(installerName);
        logger.accept("Скачиваю " + installerName + "...");
        Downloader.download(installerUrl, installer, null, null, logger, null);

        logger.accept("Запускаю установщик " + label(kind) + " " + loaderVersion + "...");
        logger.accept("Java: " + java);
        int code = Procs.runStreaming(
                List.of(java.toString(), "-jar", installer.toString(), "--installClient",
                        Dirs.GAME_DIR.toString()),
                Dirs.GAME_DIR, logger);
        if (code != 0) {
            throw new RuntimeException("Инсталлятор " + label(kind) + " вернул код " + code + ".\n"
                    + "Проверь Java " + JavaProvisioner.javaMajorForMc(mcVersion) + "+ и повтори попытку.");
        }

        Path json = Dirs.VERSIONS_DIR.resolve(versionId).resolve(versionId + ".json");
        if (!Files.exists(json)) {
            json = findInstalledVersion(kind, mcVersion);
        }
        if (json == null || !Files.exists(json)) {
            throw new RuntimeException("Инсталлятор отработал, но не нашёл " + label(kind) + " в versions/");
        }
        logger.accept(label(kind) + " " + loaderVersion + " установлен");
        return json;
    }

    private static Path findInstalledVersion(String kind, String mcVersion) {
        List<Path> candidates = new ArrayList<>();
        for (Path dir : LaunchBuilder.listDir(Dirs.VERSIONS_DIR)) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            String name = dir.getFileName().toString().toLowerCase();
            if (name.contains(kind) && name.contains(mcVersion)) {
                Path json = dir.resolve(dir.getFileName() + ".json");
                if (Files.exists(json)) {
                    candidates.add(json);
                }
            }
        }
        return candidates.isEmpty() ? null : candidates.get(candidates.size() - 1);
    }

    public static String label(String kind) {
        if ("neoforge".equalsIgnoreCase(kind)) {
            return "NeoForge";
        }
        return "Forge";
    }

    public static String versionIdOf(String kind, String mcVersion, String loaderVersion) {
        return "neoforge".equalsIgnoreCase(kind)
                ? "neoforge-" + loaderVersion
                : mcVersion + "-forge-" + loaderVersion;
    }

    public static Map<String, Object> probe() {
        return Map.of();
    }
}
