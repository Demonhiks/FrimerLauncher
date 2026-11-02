package com.frimer.launcher.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Импорт сборок Modrinth (.mrpack): зависимости, файлы, overrides. */
public final class ModpackImporter {

    private ModpackImporter() {
    }

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "cdn.modrinth.com", "github.com", "raw.githubusercontent.com",
            "gitlab.com", "objects.githubusercontent.com");

    /** Результат импорта: сборка и список предупреждений. */
    public record Result(Instance instance, List<String> warnings) {
    }

    public static Result importMrpack(Path archive, Consumer<String> status, ProgressListener progress) {
        Consumer<String> log = status == null ? msg -> {
        } : status;
        ProgressListener prog = progress == null ? ProgressListener.NONE : progress;

        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry indexEntry = zip.getEntry("modrinth.index.json");
            if (indexEntry == null) {
                if (zip.getEntry("manifest.json") != null) {
                    throw new RuntimeException("Это сборка CurseForge. Пока поддерживаются только сборки Modrinth.");
                }
                throw new RuntimeException("В архиве нет modrinth.index.json — это не сборка Modrinth (.mrpack).");
            }
            Map<String, Object> index = Json.parseObject(readEntry(zip, indexEntry));
            Map<String, Object> deps = Json.obj(index.get("dependencies"));
            String mc = Json.str(deps.get("minecraft"), "");
            if (mc.isEmpty()) {
                throw new RuntimeException("В сборке не указана версия Minecraft.");
            }
            String[] loaderInfo = pickLoader(deps);
            String loader = loaderInfo[0];
            String loaderVersion = loaderInfo[1];

            String name = Json.str(index.get("name"), "").strip();
            if (name.isEmpty()) {
                name = stripExtension(archive.getFileName().toString());
            }

            Instance inst = InstanceStore.create(name, loader, mc, 4096, loaderVersion);

            List<String> warnings = new ArrayList<>();
            List<Map<String, Object>> files = new ArrayList<>();
            for (Object o : Json.arr(index.get("files"))) {
                Map<String, Object> f = Json.obj(o);
                if ("unsupported".equals(Json.str(Json.path(f, "env", "client"), ""))) {
                    continue;
                }
                files.add(f);
            }
            int total = Math.max(1, files.size());
            log.accept("Скачиваю файлы сборки (" + files.size() + ")...");
            prog.update(0, total);

            AtomicInteger done = new AtomicInteger();
            ExecutorService pool = Executors.newFixedThreadPool(6);
            List<Future<String>> futures = new ArrayList<>();
            try {
                for (Map<String, Object> f : files) {
                    futures.add(pool.submit(() -> fetchFile(inst.folder, f)));
                }
                for (Future<String> future : futures) {
                    int current = done.incrementAndGet();
                    try {
                        String warning = future.get();
                        if (warning != null) {
                            warnings.add(warning);
                        }
                    } catch (Exception e) {
                        warnings.add(e.getMessage() == null ? "ошибка скачивания" : e.getMessage());
                    }
                    prog.update(current, total);
                }
            } finally {
                pool.shutdown();
            }

            log.accept("Распаковываю настройки сборки...");
            extractOverrides(zip, inst.folder, warnings);

            if (!files.isEmpty() && warnings.size() >= files.size()) {
                JavaProvisioner.deleteDir(inst.folder);
                throw new RuntimeException("Не удалось скачать ни одного файла сборки:\n"
                        + String.join("\n", warnings.subList(0, Math.min(5, warnings.size()))));
            }
            inst.prepareFolders();
            inst.save();
            return new Result(inst, warnings);
        } catch (IOException e) {
            throw new RuntimeException("Файл повреждён или это не архив сборки: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw e;
        }
    }

    private static String fetchFile(Path instanceFolder, Map<String, Object> file) {
        String rel = Json.str(file.get("path"), "");
        if (rel.isBlank()) {
            return null;
        }
        Path dest;
        try {
            dest = Utils.safeJoin(instanceFolder, rel);
        } catch (IllegalArgumentException e) {
            return "пропущен небезопасный путь: " + rel;
        }
        List<String> urls = new ArrayList<>();
        for (Object u : Json.arr(file.get("downloads"))) {
            String url = String.valueOf(u);
            if (urlAllowed(url)) {
                urls.add(url);
            }
        }
        if (urls.isEmpty()) {
            return rel + ": нет разрешённой ссылки для скачивания";
        }
        Map<String, Object> hashes = Json.obj(file.get("hashes"));
        String sha1 = Json.str(hashes.get("sha1"), null);
        String sha512 = Json.str(hashes.get("sha512"), null);
        RuntimeException last = null;
        for (String url : urls) {
            try {
                Downloader.download(url, dest, sha1, sha512, null, null);
                return null;
            } catch (RuntimeException e) {
                last = e;
            }
        }
        return rel + ": " + (last == null ? "не скачалось" : last.getMessage());
    }

    private static void extractOverrides(ZipFile zip, Path folder, List<String> warnings) {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            String rel = null;
            if (name.startsWith("overrides/")) {
                rel = name.substring("overrides/".length());
            } else if (name.startsWith("client-overrides/")) {
                rel = name.substring("client-overrides/".length());
            }
            if (rel == null || rel.isEmpty()) {
                continue;
            }
            Path dest;
            try {
                dest = Utils.safeJoin(folder, rel);
            } catch (IllegalArgumentException e) {
                warnings.add("пропущен небезопасный путь: " + name);
                continue;
            }
            try {
                Files.createDirectories(dest.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                warnings.add("не распаковался " + rel + ": " + e.getMessage());
            }
        }
    }

    private static String[] pickLoader(Map<String, Object> deps) {
        if (deps.containsKey("fabric-loader")) {
            return new String[]{"Fabric", Json.str(deps.get("fabric-loader"), "")};
        }
        if (deps.containsKey("quilt-loader")) {
            return new String[]{"Quilt", Json.str(deps.get("quilt-loader"), "")};
        }
        if (deps.containsKey("neoforge")) {
            return new String[]{"NeoForge", Json.str(deps.get("neoforge"), "")};
        }
        if (deps.containsKey("forge")) {
            return new String[]{"Forge", Json.str(deps.get("forge"), "")};
        }
        return new String[]{"Vanilla", null};
    }

    private static boolean urlAllowed(String url) {
        var m = java.util.regex.Pattern.compile("^https://([^/:@]+)(?::\\d+)?/").matcher(url == null ? "" : url);
        return m.find() && ALLOWED_HOSTS.contains(m.group(1).toLowerCase(java.util.Locale.ROOT));
    }

    private static String readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
