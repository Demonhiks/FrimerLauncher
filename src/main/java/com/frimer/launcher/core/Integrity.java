package com.frimer.launcher.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Проверка целостности сборки и починка: сверяет клиент, библиотеки и индекс ассетов
 * по SHA-1 из JSON версии, битые/отсутствующие файлы докачивает.
 */
public final class Integrity {

    private Integrity() {
    }

    /** Проблемный файл: что это, куда качать и какой хеш ждём. */
    public record Issue(String what, Path path, String url, String sha1) {
    }

    /** Результат проверки. */
    public record Report(List<Issue> issues, int checked) {

        public boolean ok() {
            return issues.isEmpty();
        }
    }

    /** Проверить клиент и библиотеки сборки. */
    public static Report check(Instance instance, java.util.function.Consumer<String> log,
                               ProgressListener progress) {
        java.util.function.Consumer<String> logger = log == null ? msg -> {
        } : log;
        List<Issue> issues = new ArrayList<>();
        int checked = 0;

        Path versionJson = Dirs.VERSIONS_DIR.resolve(instance.mcVersion)
                .resolve(instance.mcVersion + ".json");
        if (!Files.exists(versionJson)) {
            issues.add(new Issue("version " + instance.mcVersion, versionJson, null, null));
            logger.accept("нет JSON версии " + instance.mcVersion);
        } else {
            Map<String, Object> info = Json.readFileQuiet(versionJson);
            if (info != null) {
                checked += checkVersionFiles(info, instance.mcVersion, issues, logger, progress);
            }
        }
        return new Report(issues, checked);
    }

    private static int checkVersionFiles(Map<String, Object> info, String versionId,
                                         List<Issue> issues, java.util.function.Consumer<String> log,
                                         ProgressListener progress) {
        int checked = 0;
        Map<String, Object> client = Json.obj(Json.path(info, "downloads", "client"));
        String clientUrl = Json.str(client.get("url"), "");
        if (!clientUrl.isEmpty()) {
            Path jar = Dirs.VERSIONS_DIR.resolve(versionId).resolve(versionId + ".jar");
            checked++;
            if (!matches(jar, Json.str(client.get("sha1"), null))) {
                issues.add(new Issue("клиент " + versionId, jar, clientUrl,
                        Json.str(client.get("sha1"), null)));
                log.accept("битый клиент: " + jar.getFileName());
            }
        }

        List<Object> libs = Json.arr(info.get("libraries"));
        int total = Math.max(1, libs.size());
        int index = 0;
        for (Object o : libs) {
            index++;
            Map<String, Object> lib = Json.obj(o);
            if (!Rules.allowed(lib)) {
                continue;
            }
            Map<String, Object> artifact = Json.obj(Json.path(lib, "downloads", "artifact"));
            String path = Json.str(artifact.get("path"), "");
            String url = Json.str(artifact.get("url"), "");
            if (path.isEmpty() || url.isEmpty()) {
                continue;
            }
            Path file = Dirs.LIBRARIES_DIR.resolve(path);
            checked++;
            String sha1 = Json.str(artifact.get("sha1"), null);
            if (!matches(file, sha1)) {
                issues.add(new Issue(file.getFileName().toString(), file, url, sha1));
                log.accept("библиотека не на месте: " + file.getFileName());
            }
            progress.update(index, total);
        }

        Map<String, Object> assetIndex = Json.obj(info.get("assetIndex"));
        String aiId = Json.str(assetIndex.get("id"), "");
        if (!aiId.isEmpty()) {
            Path indexFile = Dirs.ASSETS_DIR.resolve("indexes").resolve(aiId + ".json");
            checked++;
            if (!Files.exists(indexFile)) {
                issues.add(new Issue("индекс ассетов " + aiId, indexFile,
                        Json.str(assetIndex.get("url"), ""),
                        Json.str(assetIndex.get("sha1"), null)));
                log.accept("нет индекса ассетов " + aiId);
            }
        }
        return checked;
    }

    private static boolean matches(Path file, String sha1) {
        if (!Files.exists(file)) {
            return false;
        }
        if (sha1 == null || sha1.isBlank()) {
            return true;
        }
        try {
            return sha1.equalsIgnoreCase(Utils.sha1(file));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Докачать всё, что сломано. Возвращает число исправленных файлов. */
    public static int repair(Report report, java.util.function.Consumer<String> log,
                             ProgressListener progress) {
        java.util.function.Consumer<String> logger = log == null ? msg -> {
        } : log;
        int fixed = 0;
        int total = Math.max(1, report.issues().size());
        int index = 0;
        for (Issue issue : report.issues()) {
            index++;
            try {
                if (issue.url() == null || issue.url().isBlank()) {
                    logger.accept("пропускаю " + issue.what() + " — нет ссылки на скачивание");
                } else {
                    Downloader.download(issue.url(), issue.path(), issue.sha1(), null, null, null);
                    fixed++;
                    logger.accept("восстановлено: " + issue.what());
                }
            } catch (RuntimeException e) {
                logger.accept("не удалось: " + issue.what() + " (" + e.getMessage() + ")");
            }
            progress.update(index, total);
        }
        return fixed;
    }
}
