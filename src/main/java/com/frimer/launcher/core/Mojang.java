package com.frimer.launcher.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API Mojang: манифест версий, JSON версии, ассеты, launcher_profiles.json. */
public final class Mojang {

    private Mojang() {
    }

    public static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    public static final String RESOURCES_URL = "https://resources.download.minecraft.net";
    public static final String PROFILE_API = "https://api.mojang.com/users/profiles/minecraft";
    public static final String SESSION_API = "https://sessionserver.mojang.com/session/minecraft/profile";

    private static Map<String, Object> manifestCache;

    public static synchronized Map<String, Object> manifest() {
        if (manifestCache == null) {
            manifestCache = Json.parseObject(Http.get(MANIFEST_URL));
        }
        return manifestCache;
    }

    public static List<String> releaseVersions() {
        List<String> out = new ArrayList<>();
        for (Object o : Json.arr(Json.path(manifest(), "versions"))) {
            Map<String, Object> v = Json.obj(o);
            if ("release".equals(Json.str(v.get("type"), ""))) {
                out.add(Json.str(v.get("id"), ""));
            }
        }
        return out;
    }

    /** Все версии (release + snapshot) в порядке манифеста. */
    public static List<Map<String, Object>> allVersions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : Json.arr(Json.path(manifest(), "versions"))) {
            out.add(Json.obj(o));
        }
        return out;
    }

    public static Map<String, Object> versionInfo(String versionId) {
        for (Object o : Json.arr(Json.path(manifest(), "versions"))) {
            Map<String, Object> v = Json.obj(o);
            if (versionId.equals(Json.str(v.get("id"), ""))) {
                String url = Json.str(v.get("url"), "");
                if (url.isEmpty()) {
                    break;
                }
                return Json.parseObject(Http.get(url));
            }
        }
        throw new RuntimeException("Версия " + versionId + " не найдена в манифесте Mojang");
    }

    public static String assetUrl(String hash) {
        return hash.length() < 2 ? RESOURCES_URL + "/" + hash
                : RESOURCES_URL + "/" + hash.substring(0, 2) + "/" + hash;
    }

    /** Создать launcher_profiles.json - его требуют установщики Forge/NeoForge. */
    public static void ensureLauncherProfile(Path gameDir) {
        Path profileFile = gameDir.resolve("launcher_profiles.json");
        if (Files.exists(profileFile)) {
            return;
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", "Frimer");
        profile.put("type", "custom");
        profile.put("gameDir", gameDir.toString());
        profile.put("lastVersionId", "latest-release");
        profile.put("created", "2024-01-01T00:00:00.000Z");
        profile.put("lastUsed", "2024-01-01T00:00:00.000Z");
        profile.put("icon", "Furnace_icon");

        Map<String, Object> profiles = new LinkedHashMap<>();
        profiles.put("Frimer", profile);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("enableSnapshots", false);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("profiles", profiles);
        root.put("settings", settings);
        root.put("version", 3L);

        try {
            Files.createDirectories(gameDir);
            Files.writeString(profileFile, Json.write(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Не могу создать launcher_profiles.json: " + e.getMessage(), e);
        }
    }
}
