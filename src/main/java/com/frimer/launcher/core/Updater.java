package com.frimer.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Автообновление лаунчера через GitHub Releases: последний тег, описание, jar-ассет.
 * Работает без токена — релизы публичные.
 */
public final class Updater {

    private Updater() {
    }

    public static final String REPO = "Demonhiks/FrimerLaunchers";
    public static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    public static final String PAGE = "https://github.com/" + REPO + "/releases/latest";

    /** Найденное обновление. */
    public record Info(String version, String downloadUrl, String notes, String pageUrl) {
    }

    /** Последний релиз, если он новее текущей версии (иначе null). */
    public static Info latest() {
        String body;
        try {
            body = Http.get(API, Map.of("Accept", "application/vnd.github+json"));
        } catch (RuntimeException e) {
            return null;
        }
        Map<String, Object> json;
        try {
            json = Json.parseObject(body);
        } catch (RuntimeException e) {
            return null;
        }
        String tag = Json.str(json.get("tag_name"), "");
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        if (version.isBlank() || !isNewer(version, AppSettings.APP_VERSION)) {
            return null;
        }
        String jarUrl = "";
        for (Object asset : Json.arr(json.get("assets"))) {
            Map<String, Object> map = Json.obj(asset);
            String name = Json.str(map.get("name"), "").toLowerCase(Locale.ROOT);
            if (name.endsWith(".jar")) {
                jarUrl = Json.str(map.get("browser_download_url"), "");
                break;
            }
        }
        return new Info(version, jarUrl, Json.str(json.get("body"), ""),
                Json.str(json.get("html_url"), PAGE));
    }

    /** Скачать новую версию рядом с текущим jar. */
    public static Path download(Info info, Path targetDir, ProgressListener progress) {
        if (info.downloadUrl().isBlank()) {
            throw new RuntimeException("В релизе нет jar-файла");
        }
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new RuntimeException("Не могу создать папку для обновления", e);
        }
        Path dest = targetDir.resolve("FrimerLauncher-" + info.version() + ".jar");
        Downloader.download(info.downloadUrl(), dest, null, null, null, progress);
        return dest;
    }

    /** Запустить скачанный jar и закрыть текущий процесс. */
    public static void relaunch(Path jar) {
        try {
            String javaExe = Path.of(System.getProperty("java.home"), "bin",
                    Dirs.isWindows() ? "java.exe" : "java").toString();
            new ProcessBuilder(javaExe, "-jar", jar.toAbsolutePath().toString())
                    .directory(jar.toAbsolutePath().getParent().toFile())
                    .start();
        } catch (IOException e) {
            throw new RuntimeException("Не удалось перезапустить лаунчер: " + e.getMessage(), e);
        }
    }

    /** Скопировать обновление на место текущего jar (если он не занят). */
    public static boolean replaceCurrent(Path downloaded) {
        try {
            Path current = Path.of(Updater.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI());
            if (!Files.isRegularFile(current) || current.toString().endsWith(".class")) {
                return false;
            }
            Path backup = current.resolveSibling(current.getFileName() + ".old");
            Files.deleteIfExists(backup);
            Files.move(current, backup, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(downloaded, current, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Сравнение версий: 2.10 > 2.9, суффиксы игнорируются. */
    public static boolean isNewer(String candidate, String current) {
        List<Long> a = Utils.numKey(candidate);
        List<Long> b = Utils.numKey(current);
        int size = Math.max(a.size(), b.size());
        for (int i = 0; i < size; i++) {
            long x = i < a.size() ? a.get(i) : 0;
            long y = i < b.size() ? b.get(i) : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    /** Короткая строка «2.2.0 → 2.3.0» для подписей. */
    public static String describe(Info info) {
        return AppSettings.APP_VERSION + " → " + info.version();
    }

    /** Ссылки на страницы релизов (для окна «нет обновлений»). */
    public static List<String> links() {
        return new ArrayList<>(List.of(PAGE));
    }
}
