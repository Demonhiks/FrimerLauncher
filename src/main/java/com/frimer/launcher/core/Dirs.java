package com.frimer.launcher.core;

import java.io.File;
import java.nio.file.Path;

/** Расположение всех папок лаунчера (аналог констант из Python-версии). */
public final class Dirs {

    private Dirs() {
    }

    public static final Path HOME = Path.of(System.getProperty("user.home", "."));
    public static final Path GAME_DIR = HOME.resolve(".mylauncher");

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

    public static void ensureAll() {
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
