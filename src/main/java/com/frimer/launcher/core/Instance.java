package com.frimer.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Одна сборка (инстанс) Minecraft: папка, загрузчик, версия, память, статистика. */
public final class Instance {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public final Path folder;
    public String name;
    public String loader;
    public String mcVersion;
    public int ramMb;
    public String loaderVersion;

    public Instance(Path folder, String name, String loader, String mcVersion, int ramMb,
                    String loaderVersion) {
        this.folder = folder;
        this.name = name;
        this.loader = loader;
        this.mcVersion = mcVersion;
        this.ramMb = ramMb;
        this.loaderVersion = loaderVersion;
    }

    public void save() {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new RuntimeException("Не могу создать папку сборки " + folder, e);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("loader", loader);
        data.put("mc_version", mcVersion);
        data.put("ram_mb", ramMb);
        if (loaderVersion != null && !loaderVersion.isBlank()) {
            data.put("loader_version", loaderVersion);
        }
        Json.writeFileQuiet(folder.resolve("instance.json"), data);
    }

    public Path statsFile() {
        return folder.resolve("stats.json");
    }

    public Map<String, Object> stats() {
        Map<String, Object> data = Json.readFileQuiet(statsFile());
        if (data == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("seconds", 0L);
            empty.put("sessions", 0L);
            empty.put("last", null);
            return empty;
        }
        return data;
    }

    public void addSession(long seconds) {
        Map<String, Object> s = stats();
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("seconds", Json.lng(s.get("seconds"), 0) + seconds);
        next.put("sessions", Json.lng(s.get("sessions"), 0) + 1);
        next.put("last", LocalDateTime.now().format(STAMP));
        Json.writeFileQuiet(statsFile(), next);
    }

    public long playedSeconds() {
        return Json.lng(stats().get("seconds"), 0);
    }

    public String lastPlayed() {
        return Json.str(stats().get("last"), "");
    }

    public Path iconFile() {
        return folder.resolve("icon.png");
    }

    public static Instance load(Path folder) {
        Path file = folder.resolve("instance.json");
        if (!Files.exists(file)) {
            return null;
        }
        Map<String, Object> data = Json.readFileQuiet(file);
        if (data == null) {
            return null;
        }
        return new Instance(folder,
                Json.str(data.get("name"), folder.getFileName().toString()),
                Json.str(data.get("loader"), "Vanilla"),
                Json.str(data.get("mc_version"), ""),
                Json.integer(data.get("ram_mb"), 4096),
                Json.str(data.get("loader_version"), null));
    }

    /** Создать структуру папок сборки. */
    public void prepareFolders() {
        try {
            Files.createDirectories(folder);
            for (String sub : List.of("mods", "resourcepacks", "shaderpacks", "saves",
                    "screenshots", "crash-reports", "logs")) {
                Files.createDirectories(folder.resolve(sub));
            }
        } catch (IOException e) {
            throw new RuntimeException("Не могу создать папки сборки " + folder, e);
        }
    }

    public Instance duplicate() {
        String base = Utils.slugify(name + "-copy");
        Path target = InstanceStore.uniqueFolder(base);
        try {
            copyTree(folder, target, List.of("logs", "crash-reports", "natives"));
        } catch (IOException e) {
            return null;
        }
        Instance copy = load(target);
        if (copy != null) {
            copy.name = name + " (copy)";
            copy.save();
        }
        return copy;
    }

    private static void copyTree(Path from, Path to, List<String> skip) throws IOException {
        List<Path> entries;
        try (var stream = Files.walk(from)) {
            entries = stream.toList();
        }
        for (Path src : entries) {
            Path rel = from.relativize(src);
            if (rel.toString().isEmpty()) {
                continue;
            }
            String first = rel.getName(0).toString();
            if (skip.contains(first)) {
                continue;
            }
            Path dest = to.resolve(rel);
            if (Files.isDirectory(src)) {
                Files.createDirectories(dest);
            } else {
                Files.createDirectories(dest.getParent());
                Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** Список "Модов" с человеческим названием из метаданных jar. */
    public List<ModFiles.ModEntry> mods() {
        return ModFiles.list(folder.resolve("mods"));
    }

    public Path serverFolder() {
        return folder.resolve("server");
    }

    public List<String> supportedLoaders() {
        return new ArrayList<>(List.of("Vanilla", "Fabric", "Quilt", "Forge", "NeoForge"));
    }
}
