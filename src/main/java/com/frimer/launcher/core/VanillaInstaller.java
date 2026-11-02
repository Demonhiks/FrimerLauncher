package com.frimer.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Установка ванильного Minecraft: клиент, библиотеки, ассеты. */
public final class VanillaInstaller {

    private final String versionId;
    private final Consumer<String> log;
    private final ProgressListener progress;

    public VanillaInstaller(String versionId, Consumer<String> log, ProgressListener progress) {
        this.versionId = versionId;
        this.log = log == null ? msg -> {
        } : log;
        this.progress = progress == null ? ProgressListener.NONE : progress;
    }

    public Path install() {
        try {
            Files.createDirectories(Dirs.VERSIONS_DIR);
            Files.createDirectories(Dirs.LIBRARIES_DIR);
            Files.createDirectories(Dirs.ASSETS_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Не могу создать папки игры: " + e.getMessage(), e);
        }

        Path versionDir = Dirs.VERSIONS_DIR.resolve(versionId);
        Path versionJson = versionDir.resolve(versionId + ".json");
        Path versionJar = versionDir.resolve(versionId + ".jar");

        if (Files.exists(versionJson) && Files.exists(versionJar)) {
            log.accept("Vanilla " + versionId + " уже установлена");
            return versionJson;
        }

        Map<String, Object> info = Mojang.versionInfo(versionId);
        try {
            Files.createDirectories(versionDir);
            Json.writeFile(versionJson, info);
        } catch (IOException e) {
            throw new RuntimeException("Не могу записать JSON версии", e);
        }

        Map<String, Object> client = Json.obj(Json.path(info, "downloads", "client"));
        String clientUrl = Json.str(client.get("url"), "");
        if (!clientUrl.isEmpty() && !Files.exists(versionJar)) {
            log.accept("Скачиваю клиент " + versionId + "...");
            Downloader.download(clientUrl, versionJar, Json.str(client.get("sha1"), null), null, log, progress);
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
            Path dest = Dirs.LIBRARIES_DIR.resolve(path);
            try {
                Downloader.download(url, dest, Json.str(artifact.get("sha1"), null), null, null, null);
            } catch (RuntimeException e) {
                log.accept("Не скачалось " + dest.getFileName() + ": " + e.getMessage());
            }
            progress.update(index, total);
        }

        Map<String, Object> assetIndex = Json.obj(info.get("assetIndex"));
        String aiUrl = Json.str(assetIndex.get("url"), "");
        String aiId = Json.str(assetIndex.get("id"), "");
        if (!aiUrl.isEmpty() && !aiId.isEmpty()) {
            Path indexPath = Dirs.ASSETS_DIR.resolve("indexes").resolve(aiId + ".json");
            Downloader.download(aiUrl, indexPath, Json.str(assetIndex.get("sha1"), null), null, log, null);
            installAssets(indexPath);
        }

        log.accept("Vanilla " + versionId + " готова");
        return versionJson;
    }

    private void installAssets(Path indexPath) {
        Map<String, Object> index = Json.readFileQuiet(indexPath);
        if (index == null) {
            return;
        }
        Map<String, Object> objects = Json.obj(index.get("objects"));
        log.accept("Объектов ассетов: " + objects.size());

        List<Runnable> tasks = new ArrayList<>();
        for (Map.Entry<String, Object> e : objects.entrySet()) {
            String hash = Json.str(Json.obj(e.getValue()).get("hash"), "");
            if (hash.isEmpty()) {
                continue;
            }
            Path dest = Dirs.ASSETS_DIR.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
            if (Files.exists(dest)) {
                continue;
            }
            tasks.add(() -> {
                try {
                    Downloader.download(Mojang.assetUrl(hash), dest, hash, null, null, null);
                } catch (RuntimeException ignored) {
                    // ассет мог быть удалён на стороне Mojang
                }
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable task : tasks) {
                futures.add(pool.submit(task));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {
                    // пропускаем проблемный ассет
                }
            }
        } finally {
            pool.shutdown();
        }
    }
}
