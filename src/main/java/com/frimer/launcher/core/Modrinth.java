package com.frimer.launcher.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Клиент Modrinth API: поиск проектов, список версий и установка с зависимостями. */
public final class Modrinth {

    private Modrinth() {
    }

    public static final String API = "https://api.modrinth.com/v2";
    public static final String UA = "Frimer-Launcher/2.1 (contact: admin@example.com)";

    private static final Map<String, String> HEADERS = Map.of("User-Agent", UA);

    /** Тип проекта в терминах Modrinth: mod, shader, resourcepack, datapack, plugin. */
    public static final String MOD = "mod";
    public static final String SHADER = "shader";
    public static final String RESOURCEPACK = "resourcepack";
    public static final String DATAPACK = "datapack";
    public static final String PLUGIN = "plugin";

    /** Проект в результатах поиска. */
    public record Hit(String id, String slug, String title, String author, String description,
                      long downloads, String iconUrl, String pageUrl) {
    }

    /** Версия проекта. */
    public record Version(String id, String label, String type, String date) {
    }

    /** Итог установки: что скачалось и что пошло не так. */
    public record Installed(List<String> files, List<String> warnings) {
    }

    public static String folderFor(String projectType) {
        switch (projectType) {
            case SHADER:
                return "shaderpacks";
            case RESOURCEPACK:
                return "resourcepacks";
            case DATAPACK:
                return "datapacks";
            case PLUGIN:
                return "plugins";
            default:
                return "mods";
        }
    }

    public static String labelFor(String projectType) {
        switch (projectType) {
            case SHADER:
                return "Шейдеры";
            case RESOURCEPACK:
                return "Ресурспаки";
            case DATAPACK:
                return "Датапаки";
            case PLUGIN:
                return "Плагины";
            default:
                return "Моды";
        }
    }

    private static boolean needsLoader(String projectType) {
        return MOD.equals(projectType) || PLUGIN.equals(projectType);
    }

    /** Поиск проектов. Возвращает список и общее число найденных. */
    public static SearchResult search(String query, String mcVersion, String loader,
                                      String projectType, int offset, int limit) {
        StringBuilder facets = new StringBuilder("[[\"project_type:").append(projectType).append("\"]");
        if (mcVersion != null && !mcVersion.isBlank()) {
            facets.append(",[\"versions:").append(mcVersion).append("\"]");
        }
        if (needsLoader(projectType) && loader != null && !loader.isBlank()) {
            facets.append(",[\"categories:").append(loader.toLowerCase(java.util.Locale.ROOT)).append("\"]");
        }
        facets.append("]");

        String index = query == null || query.isBlank() ? "downloads" : "relevance";
        String url = API + "/search?query=" + Utils.urlEncode(query == null ? "" : query)
                + "&facets=" + Utils.urlEncode(facets.toString())
                + "&limit=" + limit + "&offset=" + offset + "&index=" + index;

        Map<String, Object> data = Json.parseObject(Http.get(url, HEADERS));
        List<Hit> hits = new ArrayList<>();
        for (Object o : Json.arr(data.get("hits"))) {
            Map<String, Object> hit = Json.obj(o);
            String id = Json.str(hit.get("project_id"), "");
            String slug = Json.str(hit.get("slug"), "");
            if (id.isEmpty()) {
                continue;
            }
            hits.add(new Hit(id, slug, Json.str(hit.get("title"), id),
                    Json.str(hit.get("author"), ""),
                    Json.str(hit.get("description"), ""),
                    Json.lng(hit.get("downloads"), 0),
                    Json.str(hit.get("icon_url"), ""),
                    "https://modrinth.com/" + projectType + "/" + slug));
        }
        return new SearchResult(hits, Json.lng(data.get("total_hits"), hits.size()));
    }

    /** Результат поиска. */
    public record SearchResult(List<Hit> hits, long total) {
    }

    /** Версии проекта под конкретную версию игры и загрузчик (свежие сверху). */
    public static List<Version> versions(String projectId, String mcVersion, String loader,
                                         String projectType) {
        String url = API + "/project/" + projectId + "/version";
        StringBuilder query = new StringBuilder();
        if (mcVersion != null && !mcVersion.isBlank()) {
            query.append("game_versions=").append(Utils.urlEncode("[\"" + mcVersion + "\"]"));
        }
        if (needsLoader(projectType) && loader != null && !loader.isBlank()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append("loaders=")
                    .append(Utils.urlEncode("[\"" + loader.toLowerCase(java.util.Locale.ROOT) + "\"]"));
        }
        if (query.length() > 0) {
            url += "?" + query;
        }
        List<Version> out = new ArrayList<>();
        for (Object o : Json.arr(Json.parse(Http.get(url, HEADERS)))) {
            Map<String, Object> v = Json.obj(o);
            if (Json.arr(v.get("files")).isEmpty()) {
                continue;
            }
            String id = Json.str(v.get("id"), "");
            String label = Json.str(v.get("version_number"), Json.str(v.get("name"), id));
            String date = Json.str(v.get("date_published"), "");
            if (date.length() > 10) {
                date = date.substring(0, 10);
            }
            out.add(new Version(id, label, Json.str(v.get("version_type"), "release"), date));
        }
        return out;
    }

    /**
     * Установить проект в папку.
     *
     * @param versionId конкретная версия или null — тогда последняя release-версия
     */
    public static Installed install(Hit hit, String mcVersion, String loader, String projectType,
                                    Path dest, String versionId, ProgressListener progress) {
        List<String> files = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        installProject(hit.id(), mcVersion, loader, projectType, dest, versionId,
                files, warnings, seen, progress);
        return new Installed(files, warnings);
    }

    private static void installProject(String projectId, String mcVersion, String loader,
                                       String projectType, Path dest, String versionId,
                                       List<String> files, List<String> warnings,
                                       java.util.Set<String> seen, ProgressListener progress) {
        if (!seen.add(projectId)) {
            return;
        }
        Map<String, Object> version = pickVersion(projectId, mcVersion, loader, projectType, versionId);
        List<Object> versionFiles = Json.arr(version.get("files"));
        if (versionFiles.isEmpty()) {
            throw new RuntimeException("У версии нет файлов");
        }
        Map<String, Object> primary = null;
        for (Object o : versionFiles) {
            Map<String, Object> f = Json.obj(o);
            if (Json.bool(f.get("primary"), false)) {
                primary = f;
                break;
            }
        }
        if (primary == null) {
            primary = Json.obj(versionFiles.get(0));
        }
        String fileName = Json.str(primary.get("filename"), "");
        String url = Json.str(primary.get("url"), "");
        if (fileName.isEmpty() || url.isEmpty()) {
            throw new RuntimeException("Modrinth не отдал ссылку на файл");
        }
        Map<String, Object> hashes = Json.obj(primary.get("hashes"));
        Path out = dest.resolve(fileName);
        Downloader.download(url, out, Json.str(hashes.get("sha1"), null),
                Json.str(hashes.get("sha512"), null), null, progress);
        files.add(fileName);

        if (!needsLoader(projectType)) {
            return;
        }
        for (Object o : Json.arr(version.get("dependencies"))) {
            Map<String, Object> dep = Json.obj(o);
            String depId = Json.str(dep.get("project_id"), "");
            if (!"required".equals(Json.str(dep.get("dependency_type"), "")) || depId.isEmpty()) {
                continue;
            }
            try {
                installProject(depId, mcVersion, loader, projectType, dest, null,
                        files, warnings, seen, progress);
            } catch (RuntimeException e) {
                warnings.add("зависимость " + depId + ": " + e.getMessage());
            }
        }
    }

    private static Map<String, Object> pickVersion(String projectId, String mcVersion, String loader,
                                                   String projectType, String versionId) {
        if (versionId != null && !versionId.isBlank()) {
            return Json.parseObject(Http.get(API + "/version/" + versionId, HEADERS));
        }
        String url = API + "/project/" + projectId + "/version";
        StringBuilder query = new StringBuilder();
        if (mcVersion != null && !mcVersion.isBlank()) {
            query.append("game_versions=").append(Utils.urlEncode("[\"" + mcVersion + "\"]"));
        }
        if (needsLoader(projectType) && loader != null && !loader.isBlank()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append("loaders=")
                    .append(Utils.urlEncode("[\"" + loader.toLowerCase(java.util.Locale.ROOT) + "\"]"));
        }
        if (query.length() > 0) {
            url += "?" + query;
        }
        List<Object> versions = Json.arr(Json.parse(Http.get(url, HEADERS)));
        if (versions.isEmpty()) {
            throw new RuntimeException("Нет версии для " + loader + " " + mcVersion);
        }
        for (Object o : versions) {
            if ("release".equals(Json.str(Json.obj(o).get("version_type"), ""))) {
                return Json.obj(o);
            }
        }
        return Json.obj(versions.get(0));
    }

    /** Открыть страницу проекта в браузере. */
    public static void openPage(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // падаем в системную команду
        }
        try {
            if (Dirs.isWindows()) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (Dirs.osName().equals("osx")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (java.io.IOException ignored) {
            // браузер не открылся - не критично
        }
    }
}
