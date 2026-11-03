package com.frimer.launcher.core;

import java.io.File;
import java.nio.file.Path;

/** Расположение всех папок лаунчера (аналог констант из Python-версии). */
public final class Dirs {

    private Dirs() {
    }

    public static final Path HOME = Path.of(System.getProperty("user.home", "."));
    public static final Path GAME_DIR = HOME.resolve(".frimerlauncher");

    /** Старое имя папки данных (до v2.3.1) — для автоматического переноса. */
    private static final String LEGACY_DIR_NAME = ".mylauncher";

    public static final Path INSTANCES_DIR = GAME_DIR.resolve("instances");
    public static final Path LOGS_DIR = GAME_DIR.resolve("logs");
    public static final Path RUNTIME_DIR = GAME_DIR.resolve("runtime");
    public static final Path VERSIONS_DIR = GAME_DIR.resolve("versions");
    public static final Path LIBRARIES_DIR = GAME_DIR.resolve("libraries");
    public static final Path ASSETS_DIR = GAME_DIR.resolve("assets");
    public static final Path SERVERS_DIR = GAME_DIR.resolve("servers");
    public static final Path SKINS_DIR = GAME_DIR.resolve("skins");
    public static final Path CACHE_DIR = GAME_DIR.resolve("cache");

    public static final String CLASSPATH_SEP = File.pathSeparator;

    public static String osName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac")) {
            return "osx";
        }
        return "linux";
    }

    public static boolean isWindows() {
        return osName().equals("windows");
    }

    /** Перенос данных со старого имени папки: ~/.mylauncher → ~/.frimerlauncher. */
    private static void migrateLegacyDir() {
        try {
            Path legacy = HOME.resolve(LEGACY_DIR_NAME);
            if (java.nio.file.Files.isDirectory(legacy)
                    && !java.nio.file.Files.exists(GAME_DIR)) {
                java.nio.file.Files.move(legacy, GAME_DIR);
            }
        } catch (java.io.IOException ignored) {
            // не удалось переименовать — создадим новую папку обычным путём
        }
    }

    public static void ensureAll() {
        migrateLegacyDir();
        for (Path p : new Path[]{
                GAME_DIR, INSTANCES_DIR, LOGS_DIR, RUNTIME_DIR,
                VERSIONS_DIR, LIBRARIES_DIR, ASSETS_DIR, SERVERS_DIR, SKINS_DIR, CACHE_DIR}) {
            try {
                java.nio.file.Files.createDirectories(p);
            } catch (java.io.IOException ignored) {
                // папку создадим позже, когда реально понадобится
            }
        }
    }
}
