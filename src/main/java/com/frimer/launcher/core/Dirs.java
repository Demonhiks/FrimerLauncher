package com.frimer.launcher.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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

    private static volatile String migrationWarning;

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

    /** Сообщение о проблемах при переносе данных (null — всё прошло гладко). */
    public static String migrationWarning() {
        return migrationWarning;
    }

    /**
     * Перенос данных со старого имени папки: ~/.mylauncher → ~/.frimerlauncher.
     *
     * <p>Перенос срабатывает и тогда, когда новая папка уже создана, но пуста, —
     * например, если прошлый запуск не смог переименовать старую (файлы были заняты).
     * Если переименовать не выходит, данные копируются, а в {@link #migrationWarning()}
     * остаётся понятное объяснение.
     */
    private static void migrateLegacyDir() {
        Path legacy = HOME.resolve(LEGACY_DIR_NAME);
        try {
            if (!Files.isDirectory(legacy) || !hasFiles(legacy)) {
                return;
            }
            if (!Files.exists(GAME_DIR)) {
                Files.move(legacy, GAME_DIR);
                return;
            }
            if (!hasFiles(GAME_DIR)) {
                // Новая папка есть, но данных в ней нет — освобождаем место и переносим.
                clearEmptyDirs(GAME_DIR);
                Files.move(legacy, GAME_DIR);
                return;
            }
            // Данные есть в обеих папках — считаем, что перенос уже состоялся.
        } catch (IOException e) {
            copyLegacyInstead(legacy);
        }
    }

    /** Запасной путь: скопировать данные, если переименовать папку не получилось. */
    private static void copyLegacyInstead(Path legacy) {
        try {
            copyInto(legacy, GAME_DIR);
            migrationWarning = "Папку " + LEGACY_DIR_NAME + " не удалось переименовать — данные"
                    + " скопированы в " + GAME_DIR.getFileName() + ". Старая папка осталась на месте.";
        } catch (IOException copyError) {
            migrationWarning = "Не удалось перенести данные из " + LEGACY_DIR_NAME + " в "
                    + GAME_DIR.getFileName() + " (" + Utils.shortText(copyError.getMessage(), 60)
                    + "). Старая папка не тронута.";
        }
    }

    /** Есть ли в папке хоть один файл (пустые подкаталоги не в счёт). */
    private static boolean hasFiles(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.anyMatch(Files::isRegularFile);
        }
    }

    /** Удалить пустые подкаталоги, а затем и сам корень, если он опустел. */
    private static void clearEmptyDirs(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> dirs = walk.filter(Files::isDirectory)
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path dir : dirs) {
                if (dir.equals(root)) {
                    continue;
                }
                try (Stream<Path> children = Files.list(dir)) {
                    if (children.findAny().isEmpty()) {
                        Files.delete(dir);
                    }
                }
            }
        }
        try (Stream<Path> children = Files.list(root)) {
            if (children.findAny().isEmpty()) {
                Files.delete(root);
            }
        }
    }

    /** Скопировать содержимое папки поверх целевой (файлы перезаписываются). */
    private static void copyInto(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path source : walk.toList()) {
                Path target = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else if (Files.isRegularFile(source)) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public static void ensureAll() {
        migrateLegacyDir();
        for (Path p : new Path[]{
                GAME_DIR, INSTANCES_DIR, LOGS_DIR, RUNTIME_DIR,
                VERSIONS_DIR, LIBRARIES_DIR, ASSETS_DIR, SERVERS_DIR, SKINS_DIR, CACHE_DIR}) {
            try {
                Files.createDirectories(p);
            } catch (IOException ignored) {
                // папку создадим позже, когда реально понадобится
            }
        }
    }
}
