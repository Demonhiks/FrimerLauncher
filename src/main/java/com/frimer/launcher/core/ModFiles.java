package com.frimer.launcher.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Работа с папкой mods: список, названия/иконки из метаданных, включение и удаление. */
public final class ModFiles {

    private ModFiles() {
    }

    public static final String DISABLED_SUFFIX = ".disabled";

    /** Один мод в папке. */
    public record ModEntry(Path file, String title, byte[] icon, boolean enabled, long size) {

        public String fileName() {
            return file.getFileName().toString();
        }
    }

    public static List<ModEntry> list(Path modsDir) {
        List<ModEntry> out = new ArrayList<>();
        if (modsDir == null || !Files.isDirectory(modsDir)) {
            return out;
        }
        List<Path> files = LaunchBuilder.listDir(modsDir);
        files.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean jar = name.endsWith(".jar");
            boolean disabled = name.endsWith(".jar" + DISABLED_SUFFIX);
            if (!jar && !disabled) {
                continue;
            }
            long size = 0;
            try {
                size = Files.size(file);
            } catch (IOException ignored) {
                // размер не критичен
            }
            Meta meta = disabled ? new Meta(null, null) : readMeta(file);
            String title = meta.title;
            if (title == null || title.isBlank()) {
                String base = file.getFileName().toString();
                base = base.replace(".jar" + DISABLED_SUFFIX, "").replace(".jar", "");
                title = base;
            }
            out.add(new ModEntry(file, title, meta.icon, !disabled, size));
        }
        return out;
    }

    /** Метаданные мода: название и иконка (если есть). */
    public record Meta(String title, byte[] icon) {
    }

    public static Meta readMeta(Path jarPath) {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            String title = null;
            byte[] icon = null;

            ZipEntry fabric = zip.getEntry("fabric.mod.json");
            ZipEntry quilt = zip.getEntry("quilt.mod.json");
            ZipEntry neoToml = zip.getEntry("META-INF/neoforge.mods.toml");
            ZipEntry forgeToml = zip.getEntry("META-INF/mods.toml");

            if (fabric != null) {
                Map<String, Object> d = Json.parseObject(readEntry(zip, fabric));
                title = Json.str(d.get("name"), null);
                icon = resolveIcon(zip, d.get("icon"));
            } else if (quilt != null) {
                Map<String, Object> d = Json.parseObject(readEntry(zip, quilt));
                Map<String, Object> md = Json.obj(Json.path(d, "quilt_loader", "metadata"));
                title = Json.str(md.get("name"), null);
                icon = resolveIcon(zip, md.get("icon"));
            } else if (neoToml != null || forgeToml != null) {
                String text = readEntry(zip, neoToml != null ? neoToml : forgeToml);
                title = tomlString(text, "displayName");
                String logo = tomlString(text, "logoFile");
                if (logo != null && !logo.isBlank()) {
                    ZipEntry e = zip.getEntry(logo.replaceFirst("^/+", ""));
                    if (e != null) {
                        icon = readBytes(zip, e);
                    }
                }
            }

            if (icon == null) {
                ZipEntry pack = zip.getEntry("pack.png");
                if (pack != null) {
                    icon = readBytes(zip, pack);
                }
            }
            return new Meta(title, icon);
        } catch (IOException | RuntimeException e) {
            return new Meta(null, null);
        }
    }

    /** Переключить мод: .jar <-> .jar.disabled. Возвращает новый путь. */
    public static Path toggle(ModEntry entry) {
        String name = entry.file.getFileName().toString();
        Path target;
        if (name.toLowerCase(Locale.ROOT).endsWith(DISABLED_SUFFIX)) {
            target = entry.file.resolveSibling(name.substring(0, name.length() - DISABLED_SUFFIX.length()));
        } else {
            target = entry.file.resolveSibling(name + DISABLED_SUFFIX);
        }
        try {
            Files.move(entry.file, target);
            return target;
        } catch (IOException e) {
            throw new RuntimeException("Не могу переименовать " + name + ": " + e.getMessage(), e);
        }
    }

    public static void delete(ModEntry entry) {
        try {
            Files.deleteIfExists(entry.file);
        } catch (IOException e) {
            throw new RuntimeException("Не могу удалить " + entry.fileName() + ": " + e.getMessage(), e);
        }
    }

    /** Снимок папки модов (имя -> sha1) для анализа изменений. */
    public static Map<String, String> snapshot(Path modsDir) {
        Map<String, String> out = new LinkedHashMap<>();
        if (modsDir == null || !Files.isDirectory(modsDir)) {
            return out;
        }
        for (Path p : LaunchBuilder.listDir(modsDir)) {
            if (!Files.isRegularFile(p)) {
                continue;
            }
            String name = p.getFileName().toString();
            if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                continue;
            }
            try {
                out.put(name, Utils.sha1(p));
            } catch (RuntimeException ignored) {
                // файл занят - пропускаем
            }
        }
        return out;
    }

    private static byte[] resolveIcon(ZipFile zip, Object ref) {
        Object value = ref;
        if (value instanceof Map<?, ?> map && !map.isEmpty()) {
            // {"64": "icon.png"} - берём самый крупный ключ
            String bestKey = null;
            long best = Long.MIN_VALUE;
            for (Object key : map.keySet()) {
                try {
                    long k = Long.parseLong(String.valueOf(key));
                    if (k > best) {
                        best = k;
                        bestKey = String.valueOf(key);
                    }
                } catch (NumberFormatException ignored) {
                    // нечисловой ключ пропускаем
                }
            }
            value = bestKey != null ? map.get(bestKey) : map.values().iterator().next();
        }
        if (!(value instanceof String path) || path.isBlank()) {
            return null;
        }
        ZipEntry entry = zip.getEntry(path.replaceFirst("^/+", ""));
        return entry == null ? null : readBytes(zip, entry);
    }

    private static String readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readBytes(ZipFile zip, ZipEntry entry) {
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /** Грубое извлечение строкового значения из TOML (без внешних парсеров). */
    private static String tomlString(String toml, String key) {
        var m = java.util.regex.Pattern.compile(
                "(?m)^\\s*" + java.util.regex.Pattern.quote(key) + "\\s*=\\s*[\"']([^\"']*)[\"']")
                .matcher(toml == null ? "" : toml);
        return m.find() ? m.group(1) : null;
    }
}
