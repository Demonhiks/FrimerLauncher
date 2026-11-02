package com.frimer.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Справочники загрузчиков: версии Forge/NeoForge/Fabric/Quilt и поиск серверных launch-файлов. */
public final class Loaders {

    private Loaders() {
    }

    public static final String FABRIC_META = "https://meta.fabricmc.net/v2";
    public static final String QUILT_META = "https://meta.quiltmc.org/v3";
    public static final String FORGE_PROMOS_URL =
            "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    public static final String FORGE_MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge";
    public static final String NEOFORGE_MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge";

    // ------------------------------------------------------------- Forge

    public static Map<String, Map<String, String>> forgeVersionsByMc() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        String body = Http.get(FORGE_PROMOS_URL);
        Map<String, Object> root = Json.parseObject(body);
        for (Map.Entry<String, Object> e : Json.obj(root.get("promos")).entrySet()) {
            String key = e.getKey();
            int dash = key.lastIndexOf('-');
            if (dash <= 0) {
                continue;
            }
            String mc = key.substring(0, dash);
            String kind = key.substring(dash + 1);
            if (!kind.equals("latest") && !kind.equals("recommended")) {
                continue;
            }
            out.computeIfAbsent(mc, k -> new LinkedHashMap<>()).put(kind, Json.str(e.getValue(), ""));
        }
        return out;
    }

    public static String pickForgeVersion(String mc) {
        Map<String, String> entry = forgeVersionsByMc().get(mc);
        if (entry == null || entry.isEmpty()) {
            throw new RuntimeException("Forge для Minecraft " + mc + " не найден");
        }
        String recommended = entry.get("recommended");
        return recommended != null && !recommended.isBlank() ? recommended : entry.get("latest");
    }

    // ------------------------------------------------------------- NeoForge

    /** "21.1.77" -> "1.21.1", "20.4.100" -> "1.20.4", "26.1.0" -> "26.1". */
    public static String neoforgeMc(String ver) {
        Matcher m = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)").matcher(ver);
        if (!m.find()) {
            return null;
        }
        int a = Integer.parseInt(m.group(1));
        int b = Integer.parseInt(m.group(2));
        int c = Integer.parseInt(m.group(3));
        if (a >= 26) {
            return c == 0 ? a + "." + b : a + "." + b + "." + c;
        }
        if (a < 20) {
            return null;
        }
        return b == 0 ? "1." + a : "1." + a + "." + b;
    }

    public static Map<String, List<String>> neoforgeVersionsByMc() {
        String body = Http.get(NEOFORGE_MAVEN + "/maven-metadata.xml");
        Map<String, List<String>> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(body);
        while (m.find()) {
            String v = m.group(1);
            String low = v.toLowerCase(Locale.ROOT);
            if (low.contains("alpha") || low.contains("snapshot")) {
                continue;
            }
            String mc = neoforgeMc(v);
            if (mc != null) {
                out.computeIfAbsent(mc, k -> new ArrayList<>()).add(v);
            }
        }
        for (List<String> list : out.values()) {
            list.sort(Utils.VERSION_DESC.reversed());
        }
        return out;
    }

    public static String pickNeoforgeVersion(String mc) {
        List<String> versions = neoforgeVersionsByMc().get(mc);
        if (versions == null || versions.isEmpty()) {
            throw new RuntimeException("NeoForge для Minecraft " + mc + " не найден");
        }
        List<String> stable = new ArrayList<>();
        for (String v : versions) {
            if (!v.contains("-")) {
                stable.add(v);
            }
        }
        List<String> pool = stable.isEmpty() ? versions : stable;
        return pool.get(pool.size() - 1);
    }

    // ------------------------------------------------------------- списки версий

    /** Версии загрузчика для выбранной версии игры (для диалога создания инстанса). */
    public static List<String> loaderVersions(String loader, String mc) {
        List<String> out = new ArrayList<>();
        switch (loader) {
            case "Fabric", "Quilt" -> {
                String meta = "Fabric".equals(loader) ? FABRIC_META : QUILT_META;
                String body = Http.get(meta + "/versions/loader/" + mc);
                for (Object o : Json.arr(Json.parse(body))) {
                    out.add(Json.str(Json.path(o, "loader", "version"), ""));
                }
            }
            case "Forge" -> {
                try {
                    String body = Http.get(FORGE_MAVEN + "/maven-metadata.xml");
                    String prefix = mc + "-";
                    Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(body);
                    while (m.find()) {
                        String v = m.group(1);
                        if (v.startsWith(prefix)) {
                            out.add(v.substring(prefix.length()));
                        }
                    }
                } catch (RuntimeException ignored) {
                    // fallback ниже
                }
                if (out.isEmpty()) {
                    Map<String, Object> promos = Json.obj(Json.parseObject(
                            Http.get(FORGE_PROMOS_URL)).get("promos"));
                    for (Map.Entry<String, Object> e : promos.entrySet()) {
                        String value = Json.str(e.getValue(), "");
                        if (e.getKey().startsWith(mc + "-") && !out.contains(value)) {
                            out.add(value);
                        }
                    }
                }
            }
            case "NeoForge" -> out.addAll(neoforgeVersionsByMc().getOrDefault(mc, List.of()));
            default -> {
            }
        }
        out.removeIf(String::isBlank);
        out.sort(Utils.VERSION_DESC);
        return out;
    }

    public static String loaderVerFromDirname(String loader, String mc, String name) {
        switch (loader) {
            case "Fabric", "Quilt" -> {
                String pre = loader.toLowerCase(Locale.ROOT) + "-loader-";
                String suf = "-" + mc;
                if (name.startsWith(pre) && name.endsWith(suf) && name.length() > pre.length() + suf.length()) {
                    return name.substring(pre.length(), name.length() - suf.length());
                }
            }
            case "Forge" -> {
                String pre = mc + "-forge-";
                if (name.startsWith(pre)) {
                    return name.substring(pre.length());
                }
            }
            case "NeoForge" -> {
                if (name.startsWith("neoforge-")) {
                    String v = name.substring("neoforge-".length());
                    if (mc.equals(neoforgeMc(v))) {
                        return v;
                    }
                }
            }
            default -> {
            }
        }
        return null;
    }

    public static List<String> installedLoaderVersions(String loader, String mc) {
        List<String> out = new ArrayList<>();
        for (Path dir : LaunchBuilder.listDir(Dirs.VERSIONS_DIR)) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            String name = dir.getFileName().toString();
            String ver = loaderVerFromDirname(loader, mc, name);
            if (ver != null && Files.exists(dir.resolve(name + ".json"))) {
                out.add(ver);
            }
        }
        out.sort(Utils.VERSION_DESC);
        return out;
    }

    // ------------------------------------------------------------- серверные ядра

    public static Set<String> serverSupportedMc(String core) {
        switch (core) {
            case "fabric" -> {
                Set<String> out = new HashSet<>();
                for (Object o : Json.arr(Json.parse(Http.get(FABRIC_META + "/versions/game")))) {
                    out.add(Json.str(Json.obj(o).get("version"), ""));
                }
                return out;
            }
            case "forge" -> {
                Set<String> out = new HashSet<>();
                for (String mc : forgeVersionsByMc().keySet()) {
                    List<Long> key = Utils.numKey(mc);
                    if (compareVersion(key, List.of(1L, 12L, 2L)) >= 0) {
                        out.add(mc);
                    }
                }
                return out;
            }
            case "neoforge" -> {
                return new HashSet<>(neoforgeVersionsByMc().keySet());
            }
            default -> {
                return null;
            }
        }
    }

    private static int compareVersion(List<Long> a, List<Long> b) {
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            long x = i < a.size() ? a.get(i) : 0;
            long y = i < b.size() ? b.get(i) : 0;
            if (x != y) {
                return Long.compare(x, y);
            }
        }
        return 0;
    }

    /**
     * Аргументы запуска сервера Forge/NeoForge (современный @win_args.txt или старый -jar).
     * Возвращает null, если ничего не найдено.
     */
    public static List<String> forgeLikeLaunch(Path folder, String core) {
        String argsName = Dirs.isWindows() ? "win_args.txt" : "unix_args.txt";
        Path base = "forge".equals(core)
                ? folder.resolve("libraries").resolve("net").resolve("minecraftforge").resolve("forge")
                : folder.resolve("libraries").resolve("net").resolve("neoforged").resolve("neoforge");
        if (Files.isDirectory(base)) {
            Path newest = null;
            long newestTime = Long.MIN_VALUE;
            try (var stream = Files.walk(base, 2)) {
                for (Path p : stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().equals(argsName)).toList()) {
                    long t = lastModified(p);
                    if (t > newestTime) {
                        newestTime = t;
                        newest = p;
                    }
                }
            } catch (IOException ignored) {
                // нет папки - проверяем старый вариант
            }
            if (newest != null) {
                List<String> out = new ArrayList<>();
                if (Files.isRegularFile(folder.resolve("user_jvm_args.txt"))) {
                    out.add("@user_jvm_args.txt");
                }
                out.add("@" + folder.relativize(newest).toString().replace('\\', '/'));
                out.add("nogui");
                return out;
            }
        }
        if ("forge".equals(core)) {
            Path newest = null;
            long newestTime = Long.MIN_VALUE;
            for (Path jar : LaunchBuilder.listDir(folder)) {
                String name = jar.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.startsWith("forge-") && name.endsWith(".jar") && !name.contains("installer")) {
                    long t = lastModified(jar);
                    if (t > newestTime) {
                        newestTime = t;
                        newest = jar;
                    }
                }
            }
            if (newest != null) {
                return List.of("-jar", newest.getFileName().toString(), "nogui");
            }
        }
        return null;
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
