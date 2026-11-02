package com.frimer.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Установка ядер сервера: Paper, Vanilla, Fabric, Forge, NeoForge. */
public final class ServerInstallers {

    private ServerInstallers() {
    }

    public static final String PAPER_API = "https://fill.papermc.io/v3/projects/paper";
    public static final String PAPER_UA = "Frimer-Launcher/2.1 (contact: admin@example.com)";

    // ------------------------------------------------------------------ Paper

    public static String installPaper(String mcVersion, Path dest, Consumer<String> status,
                                      ProgressListener progress) {
        Consumer<String> log = status == null ? msg -> {
        } : status;
        log.accept("Смотрю версии Paper для " + mcVersion + "...");
        String body = Http.get(PAPER_API, Map.of("User-Agent", PAPER_UA));
        Map<String, Object> data = Json.parseObject(body);

        List<String> ids = new ArrayList<>();
        Object raw = data.get("versions");
        if (raw instanceof Map<?, ?> groups) {
            for (Object group : groups.values()) {
                for (Object v : Json.arr(group)) {
                    if (v instanceof Map<?, ?>) {
                        String id = Json.str(Json.obj(v).get("id"),
                                Json.str(Json.obj(v).get("version"), ""));
                        if (!id.isEmpty()) {
                            ids.add(id);
                        }
                    } else if (v instanceof String s) {
                        ids.add(s);
                    }
                }
            }
        } else if (raw instanceof List<?>) {
            for (Object v : Json.arr(raw)) {
                String id = v instanceof String s ? s : Json.str(Json.path(v, "id"), "");
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        }

        List<String> candidates = new ArrayList<>();
        candidates.add(mcVersion);
        String[] parts = mcVersion.split("\\.");
        if (parts.length >= 2) {
            try {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);
                for (int delta = 1; delta <= 3; delta++) {
                    if (minor - delta > 0) {
                        candidates.add(major + "." + (minor - delta));
                    }
                }
            } catch (NumberFormatException ignored) {
                // версия не из цифр - остаётся только точное совпадение
            }
        }
        String chosen = null;
        for (String c : candidates) {
            if (ids.contains(c)) {
                chosen = c;
                break;
            }
        }
        if (chosen == null) {
            List<String> sorted = new ArrayList<>(ids);
            sorted.sort(Utils.VERSION_DESC);
            List<String> tail = sorted.size() > 10 ? sorted.subList(0, 10) : sorted;
            throw new RuntimeException("Paper пока нет для " + mcVersion + ".\nДоступные: "
                    + String.join(", ", tail));
        }
        if (!chosen.equals(mcVersion)) {
            log.accept("Paper для " + mcVersion + " ещё нет — ставлю " + chosen + ".");
        }

        log.accept("Ищу последний билд Paper " + chosen + "...");
        String buildsBody = Http.get(PAPER_API + "/versions/" + chosen + "/builds",
                Map.of("User-Agent", PAPER_UA));
        List<Object> builds = Json.arr(Json.parse(buildsBody));
        if (builds.isEmpty()) {
            Map<String, Object> obj = Json.parseObject(buildsBody);
            builds = Json.arr(obj.get("builds"));
        }
        if (builds.isEmpty()) {
            throw new RuntimeException("Нет билдов Paper " + chosen);
        }

        Map<String, Object> pick = null;
        for (Object b : builds) {
            Map<String, Object> build = Json.obj(b);
            String channel = Json.str(build.get("channel"), "").toLowerCase(java.util.Locale.ROOT);
            if (channel.isEmpty() || channel.equals("default") || channel.equals("stable")
                    || channel.equals("recommended")) {
                pick = build;
                break;
            }
        }
        if (pick == null) {
            pick = Json.obj(builds.get(builds.size() - 1));
        }

        Map<String, Object> downloads = Json.obj(pick.get("downloads"));
        String url = "";
        Object dl = downloads.get("server:default");
        if (dl instanceof Map<?, ?>) {
            url = Json.str(Json.obj(dl).get("url"), "");
        } else if (dl instanceof String s) {
            url = s;
        }
        if (url.isEmpty()) {
            url = Json.str(Json.path(pick, "downloads", "application", "url"),
                    Json.str(pick.get("url"), ""));
        }
        if (url.isEmpty()) {
            throw new RuntimeException("Paper API не отдал ссылку для скачивания.");
        }

        String buildNum = Json.str(pick.get("id"), Json.str(pick.get("build"), "?"));
        log.accept("Скачиваю Paper " + chosen + " build " + buildNum + "...");
        Downloader.download(url, dest, null, null, log, progress);
        log.accept("Paper " + chosen + " готов");
        return chosen;
    }

    // ------------------------------------------------------------------ Vanilla

    public static String installVanilla(String mcVersion, Path dest, Consumer<String> status,
                                        ProgressListener progress) {
        Consumer<String> log = status == null ? msg -> {
        } : status;
        log.accept("Ищу серверную версию " + mcVersion + " у Mojang...");
        Map<String, Object> server = Json.obj(Json.path(Mojang.versionInfo(mcVersion), "downloads", "server"));
        String url = Json.str(server.get("url"), "");
        if (url.isEmpty()) {
            throw new RuntimeException("У Mojang нет server.jar для " + mcVersion);
        }
        log.accept("Скачиваю server.jar (" + mcVersion + ")...");
        Downloader.download(url, dest, Json.str(server.get("sha1"), null), null, log, progress);
        log.accept("server.jar " + mcVersion + " готов");
        return mcVersion;
    }

    // ------------------------------------------------------------------ Fabric

    public static String installFabric(String mcVersion, Path dest, Consumer<String> status,
                                       ProgressListener progress) {
        Consumer<String> log = status == null ? msg -> {
        } : status;
        log.accept("Ищу Fabric для Minecraft " + mcVersion + "...");
        List<Object> loaders = Json.arr(Json.parse(Http.get(Loaders.FABRIC_META + "/versions/loader/" + mcVersion)));
        if (loaders.isEmpty()) {
            throw new RuntimeException("Fabric пока не поддерживает Minecraft " + mcVersion);
        }
        Map<String, Object> pick = Json.obj(loaders.get(0));
        for (Object o : loaders) {
            if (Json.bool(Json.path(o, "loader", "stable"), false)) {
                pick = Json.obj(o);
                break;
            }
        }
        String loaderVersion = Json.str(Json.path(pick, "loader", "version"), "");

        List<Object> installers = Json.arr(Json.parse(Http.get(Loaders.FABRIC_META + "/versions/installer")));
        if (installers.isEmpty()) {
            throw new RuntimeException("Fabric Meta не отдал версию установщика");
        }
        String installerVersion = Json.str(Json.path(installers.get(0), "version"), "");
        for (Object o : installers) {
            if (Json.bool(Json.path(o, "stable"), false)) {
                installerVersion = Json.str(Json.path(o, "version"), installerVersion);
                break;
            }
        }

        String url = Loaders.FABRIC_META + "/versions/loader/" + mcVersion + "/" + loaderVersion
                + "/" + installerVersion + "/server/jar";
        log.accept("Скачиваю Fabric " + loaderVersion + " для " + mcVersion + "...");
        Downloader.download(url, dest, null, null, log, progress);
        log.accept("Fabric " + loaderVersion + " готов");
        return loaderVersion;
    }

    // ------------------------------------------------------------------ Forge / NeoForge

    public static String installForgeLike(String kind, String mcVersion, Path folder,
                                          Consumer<String> status, ProgressListener progress) {
        Consumer<String> log = status == null ? msg -> {
        } : status;
        String label = ServerStore.coreLabel(kind);
        log.accept("Ищу " + label + " для Minecraft " + mcVersion + "...");

        String version;
        String installerName;
        String url;
        if ("forge".equals(kind)) {
            version = Loaders.pickForgeVersion(mcVersion);
            installerName = "forge-" + mcVersion + "-" + version + "-installer.jar";
            url = Loaders.FORGE_MAVEN + "/" + mcVersion + "-" + version + "/" + installerName;
        } else {
            version = Loaders.pickNeoforgeVersion(mcVersion);
            installerName = "neoforge-" + version + "-installer.jar";
            url = Loaders.NEOFORGE_MAVEN + "/" + version + "/" + installerName;
        }

        Path java = JavaProvisioner.ensureJava(mcVersion, log, progress);
        try {
            Files.createDirectories(Dirs.RUNTIME_DIR);
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new RuntimeException("Не могу создать папки для сервера", e);
        }
        Path installer = Dirs.RUNTIME_DIR.resolve(installerName);
        log.accept("Скачиваю " + installerName + "...");
        Downloader.download(url, installer, null, null, log, null);

        log.accept("Ставлю " + label + " " + version + " (это несколько минут)...");
        List<String> tail = new ArrayList<>();
        int code = Procs.runStreaming(
                List.of(java.toString(), "-jar", installer.toString(), "--installServer", folder.toString()),
                folder, line -> {
                    tail.add(line);
                    if (tail.size() > 25) {
                        tail.remove(0);
                    }
                    log.accept(Utils.shortText(line, 100));
                });
        if (code != 0) {
            throw new RuntimeException("Установщик " + label + " вернул код " + code + ".\n\n"
                    + String.join("\n", tail));
        }
        if (Loaders.forgeLikeLaunch(folder, kind) == null) {
            throw new RuntimeException("Установщик " + label + " завершился, но запускаемый файл не найден.\n\n"
                    + String.join("\n", tail));
        }
        log.accept(label + " " + version + " готов");
        return version;
    }

    /** Установить ядро нужного типа в папку сервера. */
    public static void install(ServerInstance srv, Consumer<String> status, ProgressListener progress) {
        try {
            Files.createDirectories(srv.folder);
        } catch (IOException e) {
            throw new RuntimeException("Не могу создать папку сервера", e);
        }
        switch (srv.core) {
            case "paper" -> srv.loaderVersion = installPaper(srv.mcVersion, srv.jarPath(), status, progress);
            case "vanilla" -> srv.loaderVersion = installVanilla(srv.mcVersion, srv.jarPath(), status, progress);
            case "fabric" -> srv.loaderVersion = installFabric(srv.mcVersion, srv.jarPath(), status, progress);
            case "forge", "neoforge" ->
                    srv.loaderVersion = installForgeLike(srv.core, srv.mcVersion, srv.folder, status, progress);
            default -> throw new RuntimeException("Неизвестное ядро: " + srv.core);
        }
        srv.save();
    }

    public static void copyIfNeeded(Path from, Path to) throws IOException {
        if (!Files.exists(to)) {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
