package com.frimer.launcher.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Сборка команды запуска Minecraft: правила ОС, подстановки, natives,
 * наследование версий загрузчиков (inheritsFrom), classpath.
 */
public final class LaunchBuilder {

    private LaunchBuilder() {
    }

    public static List<String> build(Path versionJson, Path instDir, Path java,
                                     String nickname, int ramMb) {
        return build(versionJson, instDir, java, nickname, ramMb,
                Utils.offlineUuid(nickname), "0");
    }

    /** Вариант с данными аккаунта: uuid и токен (для лицензионного входа). */
    public static List<String> build(Path versionJson, Path instDir, Path java, String nickname,
                                     int ramMb, String uuid, String accessToken) {
        Map<String, Object> info = Json.readFileQuiet(versionJson);
        if (info == null) {
            throw new RuntimeException("Не читается JSON версии: " + versionJson);
        }
        String versionId = Json.str(info.get("id"), stripJson(versionJson));
        Path versionDir = versionJson.getParent();

        String parentIdForJar = null;
        if (info.containsKey("inheritsFrom")) {
            parentIdForJar = Json.str(info.get("inheritsFrom"), null);
            info = mergeInherited(info);
        }

        List<String> classpath = new ArrayList<>();
        for (Object o : Json.arr(info.get("libraries"))) {
            Map<String, Object> lib = Json.obj(o);
            if (!Rules.allowed(lib)) {
                continue;
            }
            Map<String, Object> downloads = Json.obj(lib.get("downloads"));
            Map<String, Object> artifact = Json.obj(downloads.get("artifact"));
            String artifactPath = Json.str(artifact.get("path"), "");
            if (!artifactPath.isEmpty()) {
                classpath.add(Dirs.LIBRARIES_DIR.resolve(artifactPath).toString());
                continue;
            }
            String name = Json.str(lib.get("name"), "");
            String url = Json.str(lib.get("url"), "");
            if (name.isEmpty()) {
                continue;
            }
            String[] parts = name.split(":");
            if (parts.length >= 3) {
                String group = parts[0];
                String artifactId = parts[1];
                String ver = parts[2];
                String classifier = parts.length > 3 ? parts[3] : null;
                String rel = group.replace('.', '/') + "/" + artifactId + "/" + ver + "/"
                        + artifactId + "-" + ver + (classifier != null ? "-" + classifier : "") + ".jar";
                Path local = Dirs.LIBRARIES_DIR.resolve(rel);
                if (Files.exists(local)) {
                    classpath.add(local.toString());
                } else if (!url.isEmpty()) {
                    try {
                        Downloader.download(url.replaceAll("/+$", "") + "/" + rel, local);
                        classpath.add(local.toString());
                    } catch (RuntimeException ignored) {
                        // библиотека недоступна - гейм запустится как сможет
                    }
                }
            }
        }

        Path clientJar = versionDir.resolve(versionId + ".jar");
        if (!Files.exists(clientJar) && parentIdForJar != null) {
            clientJar = Dirs.VERSIONS_DIR.resolve(parentIdForJar).resolve(parentIdForJar + ".jar");
        }
        classpath.add(clientJar.toString());
        String cp = String.join(Dirs.CLASSPATH_SEP, classpath);

        String assetIndexId = Json.str(info.get("assets"), "legacy");
        Path assetIndexPath = Dirs.ASSETS_DIR.resolve("indexes").resolve(assetIndexId + ".json");
        if (!Files.exists(assetIndexPath)) {
            Map<String, Object> ai = Json.obj(info.get("assetIndex"));
            String url = Json.str(ai.get("url"), "");
            if (!url.isEmpty()) {
                Downloader.download(url, assetIndexPath, Json.str(ai.get("sha1"), null), null, null, null);
            }
        }

        String mainClass = Json.str(info.get("mainClass"), "");
        if (mainClass.isEmpty()) {
            throw new RuntimeException("В JSON версии нет mainClass");
        }

        Path nativesDir = instDir.resolve("natives");
        try {
            Files.createDirectories(nativesDir);
        } catch (IOException e) {
            throw new RuntimeException("Не могу создать папку natives", e);
        }
        extractNatives(info, nativesDir);

        Sub sub = new Sub(info, instDir, nativesDir, nickname, uuid, accessToken);
        List<String> jvmArgs = new ArrayList<>();
        List<String> gameArgs = new ArrayList<>();
        Object args = info.get("arguments");
        if (args instanceof Map<?, ?>) {
            jvmArgs.addAll(resolveArgs(Json.arr(Json.path(args, "jvm")), sub));
            gameArgs.addAll(resolveArgs(Json.arr(Json.path(args, "game")), sub));
        } else {
            String legacy = Json.str(info.get("minecraftArguments"), "");
            if (!legacy.isBlank()) {
                for (String a : legacy.trim().split("\\s+")) {
                    gameArgs.add(sub.apply(a));
                }
            }
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(java.toString());
        cmd.add("-XX:+IgnoreUnrecognizedVMOptions");
        cmd.add("-Xmx" + ramMb + "M");
        cmd.add("-Xms" + (ramMb / 2) + "M");
        cmd.add("-Djava.library.path=" + nativesDir);
        cmd.add("-Dminecraft.launcher.brand=Frimer");
        cmd.add("-Dminecraft.launcher.version=2.1");
        for (String a : jvmArgs) {
            if (a.startsWith("-Djava.library.path") || a.equals("-cp") || a.equals(cp) || a.isEmpty()) {
                continue;
            }
            cmd.add(a);
        }
        cmd.add("-cp");
        cmd.add(cp);
        cmd.add(mainClass);

        if (gameArgs.isEmpty()) {
            gameArgs.addAll(defaultGameArgs(nickname, versionId, instDir, assetIndexId,
                    uuid, accessToken));
        }
        cmd.addAll(gameArgs);
        return cmd;
    }

    public static List<String> defaultGameArgs(String nickname, String versionId, Path instDir,
                                               String assetIndexId, String uuid,
                                               String accessToken) {
        return List.of(
                "--username", nickname,
                "--version", versionId,
                "--gameDir", instDir.toString(),
                "--assetsDir", Dirs.ASSETS_DIR.toString(),
                "--assetIndex", assetIndexId,
                "--uuid", uuid,
                "--accessToken", accessToken,
                "--userType", "legacy");
    }

    private static String stripJson(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    private static final class Sub {
        private final Map<String, Object> info;
        private final Path gameDir;
        private final Path nativesDir;
        private final String nickname;
        private final String uuid;
        private final String accessToken;

        Sub(Map<String, Object> info, Path gameDir, Path nativesDir, String nickname, String uuid,
            String accessToken) {
            this.info = info;
            this.gameDir = gameDir;
            this.nativesDir = nativesDir;
            this.nickname = nickname;
            this.uuid = uuid;
            this.accessToken = accessToken;
        }

        String apply(String s) {
            Map<String, String> repl = new LinkedHashMap<>();
            repl.put("${auth_player_name}", nickname);
            repl.put("${version_name}", Json.str(info.get("id"), ""));
            repl.put("${game_directory}", gameDir.toString());
            repl.put("${assets_root}", Dirs.ASSETS_DIR.toString());
            repl.put("${assets_index_name}", Json.str(info.get("assets"), "legacy"));
            repl.put("${auth_uuid}", uuid);
            repl.put("${auth_access_token}", accessToken);
            repl.put("${auth_session}", accessToken);
            repl.put("${user_type}", "legacy");
            repl.put("${version_type}", Json.str(info.get("type"), "release"));
            repl.put("${natives_directory}", nativesDir.toString());
            repl.put("${launcher_name}", "Frimer");
            repl.put("${launcher_version}", "2.1");
            repl.put("${classpath}", "");
            repl.put("${classpath_separator}", Dirs.CLASSPATH_SEP);
            repl.put("${library_directory}", Dirs.LIBRARIES_DIR.toString());
            String out = s;
            for (Map.Entry<String, String> e : repl.entrySet()) {
                out = out.replace(e.getKey(), e.getValue());
            }
            return out;
        }
    }

    private static List<String> resolveArgs(List<Object> args, Sub sub) {
        List<String> out = new ArrayList<>();
        for (Object o : args) {
            if (o instanceof String s) {
                out.add(sub.apply(s));
                continue;
            }
            if (!(o instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> a = Json.obj(o);
            if (!Rules.allowed(a)) {
                continue;
            }
            Object value = a.get("value");
            if (value instanceof String s) {
                out.add(sub.apply(s));
            } else if (value instanceof List<?>) {
                for (Object v : Json.arr(value)) {
                    out.add(sub.apply(String.valueOf(v)));
                }
            }
        }
        return out;
    }

    private static void extractNatives(Map<String, Object> info, Path nativesDir) {
        String key = "natives-" + Dirs.osName();
        for (Object o : Json.arr(info.get("libraries"))) {
            Map<String, Object> lib = Json.obj(o);
            if (!Rules.allowed(lib)) {
                continue;
            }
            Map<String, Object> classifiers = Json.obj(Json.path(lib, "downloads", "classifiers"));
            Map<String, Object> nativeArt = Json.obj(classifiers.get(key));
            if (nativeArt.isEmpty()) {
                continue;
            }
            String path = Json.str(nativeArt.get("path"), "");
            if (path.isEmpty()) {
                continue;
            }
            Path jarPath = Dirs.LIBRARIES_DIR.resolve(path);
            if (!Files.exists(jarPath)) {
                try {
                    Downloader.download(Json.str(nativeArt.get("url"), ""), jarPath,
                            Json.str(nativeArt.get("sha1"), null), null, null, null);
                } catch (RuntimeException e) {
                    continue;
                }
            }
            unzipFlat(jarPath, nativesDir);
        }
    }

    /** Распаковать jar «плоско» (только имена файлов) - так требуют natives. */
    public static void unzipFlat(Path jar, Path targetDir) {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || name.startsWith("META-INF/")) {
                    continue;
                }
                Path dest = targetDir.resolve(Path.of(name).getFileName().toString());
                try (var out = Files.newOutputStream(dest)) {
                    in.transferTo(out);
                } catch (IOException ignored) {
                    // занятый dll - пропускаем
                }
            }
        } catch (IOException ignored) {
            // повреждённый natives-jar - не повод падать
        }
    }

    private static String libKey(Map<String, Object> lib) {
        String[] parts = Json.str(lib.get("name"), "").split(":");
        if (parts.length >= 4) {
            return parts[0] + ":" + parts[1] + ":" + parts[3];
        }
        return parts.length >= 2 ? parts[0] + ":" + parts[1] : "";
    }

    /** Слить дочернюю версию загрузчика с родительской ванильной. */
    public static Map<String, Object> mergeInherited(Map<String, Object> info) {
        String parentId = Json.str(info.get("inheritsFrom"), "");
        if (parentId.isEmpty()) {
            return info;
        }
        Path parentJson = Dirs.VERSIONS_DIR.resolve(parentId).resolve(parentId + ".json");
        if (!Files.exists(parentJson)) {
            return info;
        }
        Map<String, Object> parent = mergeInherited(Json.readFileQuiet(parentJson));

        Map<String, Object> merged = new LinkedHashMap<>(parent == null ? Map.of() : parent);
        for (Map.Entry<String, Object> e : info.entrySet()) {
            String k = e.getKey();
            if (k.equals("inheritsFrom") || k.equals("libraries") || k.equals("arguments")) {
                continue;
            }
            merged.put(k, e.getValue());
        }

        List<Object> libs = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<Object> combined = new ArrayList<>(Json.arr(info.get("libraries")));
        combined.addAll(Json.arr(parent == null ? null : parent.get("libraries")));
        for (Object o : combined) {
            Map<String, Object> lib = Json.obj(o);
            String key = libKey(lib);
            if (!key.isEmpty() && !seen.add(key)) {
                continue;
            }
            libs.add(lib);
        }
        merged.put("libraries", libs);

        List<Object> parentJvm = Json.arr(Json.path(parent == null ? null : parent.get("arguments"), "jvm"));
        List<Object> parentGame = Json.arr(Json.path(parent == null ? null : parent.get("arguments"), "game"));
        List<Object> childJvm = Json.arr(Json.path(info.get("arguments"), "jvm"));
        List<Object> childGame = Json.arr(Json.path(info.get("arguments"), "game"));
        if (!parentJvm.isEmpty() || !parentGame.isEmpty() || !childJvm.isEmpty() || !childGame.isEmpty()) {
            List<Object> jvm = new ArrayList<>(parentJvm);
            jvm.addAll(childJvm);
            List<Object> game = new ArrayList<>(parentGame);
            game.addAll(childGame);
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("jvm", jvm);
            args.put("game", game);
            merged.put("arguments", args);
        }
        return merged;
    }

    public static String osKey() {
        return Dirs.osName().toLowerCase(Locale.ROOT);
    }

    /** Быстрое чтение одного каталога без исключения (изменяемый список). */
    public static List<Path> listDir(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return new ArrayList<>();
        }
        try (var stream = Files.list(dir)) {
            List<Path> out = new ArrayList<>();
            stream.forEach(out::add);
            return out;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public static void copyJar(Path from, Path to) throws IOException {
        Files.createDirectories(to.getParent());
        try (InputStream in = Files.newInputStream(from)) {
            Files.copy(in, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static Consumer<String> quieter() {
        return msg -> {
        };
    }
}
