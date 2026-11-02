package com.frimer.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Подбор и автоматическая установка Java (Eclipse Temurin через Adoptium API). */
public final class JavaProvisioner {

    private JavaProvisioner() {
    }

    public static final String ADOPTIUM_API = "https://api.adoptium.net/v3";
    private static final int[] JAVA_LTS = {8, 11, 17, 21, 25};

    public static int ltsAtLeast(int major) {
        for (int v : JAVA_LTS) {
            if (v >= major) {
                return v;
            }
        }
        return major;
    }

    /** Какая Java нужна для версии Minecraft. */
    public static int javaMajorForMc(String mc) {
        Path versionJson = Dirs.VERSIONS_DIR.resolve(mc).resolve(mc + ".json");
        if (Files.isRegularFile(versionJson)) {
            Map<String, Object> data = Json.readFileQuiet(versionJson);
            if (data != null) {
                int major = Json.integer(Json.path(data, "javaVersion", "majorVersion"), 0);
                if (major > 0) {
                    return ltsAtLeast(major);
                }
            }
        }
        var lead = java.util.regex.Pattern.compile("^(\\d+)").matcher(mc == null ? "" : mc);
        if (lead.find() && Integer.parseInt(lead.group(1)) >= 26) {
            return 25;
        }
        int minor = 0;
        int patch = 0;
        try {
            String[] parts = mc.split("\\.");
            minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        } catch (RuntimeException e) {
            return 17;
        }
        if (minor >= 21 || (minor == 20 && patch >= 5)) {
            return 21;
        }
        if (minor >= 17) {
            return 17;
        }
        return 8;
    }

    /** Гарантировать рабочую Java нужной мажорной версии; вернуть путь к java(.exe). */
    public static Path ensureJava(String mcVersion, Consumer<String> log, ProgressListener progress) {
        Consumer<String> logger = log == null ? msg -> {
        } : log;
        try {
            Files.createDirectories(Dirs.RUNTIME_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Не могу создать папку runtime", e);
        }
        int needed = javaMajorForMc(mcVersion);
        Path javaDir = Dirs.RUNTIME_DIR.resolve("jdk-" + needed);

        Path existing = Procs.findJavaExe(javaDir);
        if (existing != null) {
            if (Procs.detectJavaMajor(existing) != null) {
                logger.accept("Java " + needed + " уже есть: " + existing);
                return existing;
            }
            logger.accept("Скачанная Java повреждена, качаю заново...");
            deleteDir(javaDir);
        }

        try {
            return downloadJava(needed, javaDir, logger, progress);
        } catch (RuntimeException e) {
            Path systemJava = findSystemJava(needed);
            if (systemJava != null) {
                logger.accept("Не удалось скачать Java (" + e.getMessage()
                        + "). Использую системную " + needed + ": " + systemJava);
                return systemJava;
            }
            throw new RuntimeException("Не удалось получить Java " + needed + ": " + e.getMessage(), e);
        }
    }

    private static Path findSystemJava(int needed) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        String name = Dirs.isWindows() ? "java.exe" : "java";
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir).resolve(name);
            if (Files.isRegularFile(candidate) && Integer.valueOf(needed).equals(Procs.detectJavaMajor(candidate))) {
                return candidate;
            }
        }
        Path javaHome = Path.of(System.getProperty("java.home", ""));
        Path own = javaHome.resolve("bin").resolve(name);
        if (Files.isRegularFile(own) && Integer.valueOf(needed).equals(Procs.detectJavaMajor(own))) {
            return own;
        }
        return null;
    }

    private static Path downloadJava(int needed, Path javaDir, Consumer<String> log, ProgressListener progress) {
        String os = Dirs.osName();
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String archName = (arch.contains("aarch64") || arch.contains("arm64")) ? "aarch64" : "x64";

        Map<String, Object> asset = null;
        for (String imageType : List.of("jre", "jdk")) {
            String api = ADOPTIUM_API + "/assets/latest/" + needed + "/hotspot?architecture="
                    + archName + "&image_type=" + imageType + "&os=" + os + "&vendor=eclipse";
            String body = Http.getQuiet(api);
            if (body != null) {
                List<Object> list = Json.arr(Json.parse(body));
                if (!list.isEmpty()) {
                    asset = Json.obj(list.get(0));
                    break;
                }
            }
        }
        if (asset == null) {
            throw new RuntimeException("Adoptium не вернул Java " + needed + " для " + os + "/" + archName);
        }

        Map<String, Object> pkg = Json.obj(Json.path(asset, "binary", "package"));
        String url = Json.str(pkg.get("link"), "");
        String name = Json.str(pkg.get("name"), "java-" + needed + ".zip");
        String checksum = Json.str(pkg.get("checksum"), "");

        log.accept("Скачиваю Java " + needed + " (Eclipse Temurin, " + name + ")...");
        Path archive = Dirs.RUNTIME_DIR.resolve(name);
        Downloader.download(url, archive, null, null, log, progress);
        if (!checksum.isEmpty() && !checksum.equalsIgnoreCase(Utils.sha256(archive))) {
            try {
                Files.deleteIfExists(archive);
            } catch (IOException ignored) {
                // ничего
            }
            throw new RuntimeException("Контрольная сумма Java не совпала");
        }

        Path tmp = Dirs.RUNTIME_DIR.resolve("jdk-" + needed + ".tmp");
        deleteDir(tmp);
        try {
            Files.createDirectories(tmp);
        } catch (IOException e) {
            throw new RuntimeException("Не могу создать временную папку Java", e);
        }
        log.accept("Распаковываю Java...");
        try {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".zip")) {
                Archives.extractZip(archive, tmp);
            } else if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
                Archives.extractTarGz(archive, tmp);
            } else {
                throw new RuntimeException("Неизвестный формат архива Java: " + name);
            }
        } catch (IOException | RuntimeException e) {
            deleteDir(tmp);
            throw new RuntimeException("Не удалось распаковать Java: " + e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(archive);
            } catch (IOException ignored) {
                // ничего
            }
        }

        deleteDir(javaDir);
        try {
            Files.move(tmp, javaDir, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Не могу переместить Java в " + javaDir, e);
        }
        Path exe = Procs.findJavaExe(javaDir);
        if (exe == null || Procs.detectJavaMajor(exe) == null) {
            deleteDir(javaDir);
            throw new RuntimeException("Java не запускается после распаковки");
        }
        log.accept("Java " + needed + " установлена: " + exe);
        return exe;
    }

    public static void deleteDir(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // файл занят - пропускаем
                }
            });
        } catch (IOException ignored) {
            // уже удалено
        }
    }
}
