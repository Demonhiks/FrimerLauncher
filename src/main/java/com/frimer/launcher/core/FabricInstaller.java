package com.frimer.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Установка Fabric и Quilt: JSON профиля загрузчика + библиотеки из maven. */
public final class FabricInstaller {

    private FabricInstaller() {
    }

    /**
     * @param kind "fabric" или "quilt"
     */
    public static Path install(String kind, String mcVersion, String loaderVersion,
                               Consumer<String> log, ProgressListener progress) {
        Consumer<String> logger = log == null ? msg -> {
        } : log;
        ProgressListener prog = progress == null ? ProgressListener.NONE : progress;
        boolean fabric = "fabric".equalsIgnoreCase(kind);
        String meta = fabric ? Loaders.FABRIC_META : Loaders.QUILT_META;

        if (loaderVersion == null || loaderVersion.isBlank()) {
            String body = Http.get(meta + "/versions/loader/" + mcVersion);
            List<Object> list = Json.arr(Json.parse(body));
            if (list.isEmpty()) {
                throw new RuntimeException(kindLabel(kind) + " пока не поддерживает Minecraft " + mcVersion);
            }
            loaderVersion = Json.str(Json.path(list.get(0), "loader", "version"), "");
        }

        String versionId = fabric
                ? "fabric-loader-" + loaderVersion + "-" + mcVersion
                : "quilt-loader-" + loaderVersion + "-" + mcVersion;
        String maven = fabric ? "https://maven.fabricmc.net/" : "https://maven.quiltmc.org/repository/release/";

        logger.accept(kindLabel(kind) + " loader " + loaderVersion + " для " + mcVersion);
        String profileUrl = meta + "/versions/loader/" + mcVersion + "/" + loaderVersion + "/profile/json";
        Map<String, Object> profile = Json.parseObject(Http.get(profileUrl));

        Path versionDir = Dirs.VERSIONS_DIR.resolve(versionId);
        try {
            Files.createDirectories(versionDir);
            Json.writeFile(versionDir.resolve(versionId + ".json"), profile);
        } catch (IOException e) {
            throw new RuntimeException("Не могу записать профиль " + versionId, e);
        }

        List<Object> libs = Json.arr(profile.get("libraries"));
        int total = Math.max(1, libs.size());
        int index = 0;
        for (Object o : libs) {
            index++;
            Map<String, Object> lib = Json.obj(o);
            String name = Json.str(lib.get("name"), "");
            String url = Json.str(lib.get("url"), "");
            if (url.isEmpty()) {
                url = maven;
            }
            if (name.isEmpty()) {
                continue;
            }
            try {
                String[] coord = name.split(":");
                String group = coord[0];
                String artifact = coord[1];
                String ver = coord[2];
                String rel = group.replace('.', '/') + "/" + artifact + "/" + ver + "/"
                        + artifact + "-" + ver + ".jar";
                Path dest = Dirs.LIBRARIES_DIR.resolve(rel);
                if (!Files.exists(dest)) {
                    Downloader.download(url.replaceAll("/+$", "") + "/" + rel, dest, null, null, null, null);
                }
            } catch (RuntimeException e) {
                logger.accept("Не скачалось " + name + ": " + e.getMessage());
            }
            prog.update(index, total);
        }

        logger.accept(kindLabel(kind) + " " + loaderVersion + " готов");
        return versionDir.resolve(versionId + ".json");
    }

    public static String kindLabel(String kind) {
        return "quilt".equalsIgnoreCase(kind) ? "Quilt" : "Fabric";
    }
}
