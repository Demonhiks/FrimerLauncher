package com.frimer.launcher.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Скины: локальная библиотека, загрузка с Mojang, экспорт в CustomSkinLoader. */
public final class Skins {

    private Skins() {
    }

    public static Path indexPath() {
        return Dirs.SKINS_DIR.resolve("index.json");
    }

    public static List<Map<String, Object>> loadIndex() {
        String text;
        try {
            text = Files.readString(indexPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : Json.arr(Json.parse(text))) {
                out.add(Json.obj(o));
            }
            return out;
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    private static void saveIndex(List<Map<String, Object>> items) {
        try {
            Files.createDirectories(Dirs.SKINS_DIR);
            Json.writeFile(indexPath(), items);
        } catch (IOException ignored) {
            // индекс пересоздастся
        }
    }

    public static Map<String, Object> addLocal(byte[] data, String name, String source) {
        try {
            Files.createDirectories(Dirs.SKINS_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Не могу создать папку скинов", e);
        }
        String id = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String fileName = id + ".png";
        try {
            Files.write(Dirs.SKINS_DIR.resolve(fileName), data);
        } catch (IOException e) {
            throw new RuntimeException("Не могу сохранить скин", e);
        }
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty()) {
            cleanName = "Скин";
        } else if (cleanName.length() > 32) {
            cleanName = cleanName.substring(0, 32);
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("name", cleanName);
        entry.put("file", fileName);
        entry.put("source", source == null ? "" : source);
        entry.put("added", System.currentTimeMillis() / 1000);
        List<Map<String, Object>> items = loadIndex();
        items.add(entry);
        saveIndex(items);
        return entry;
    }

    public static void deleteLocal(String skinId) {
        List<Map<String, Object>> items = loadIndex();
        List<Map<String, Object>> keep = new ArrayList<>();
        for (Map<String, Object> entry : items) {
            if (skinId.equals(Json.str(entry.get("id"), ""))) {
                try {
                    Files.deleteIfExists(Dirs.SKINS_DIR.resolve(Json.str(entry.get("file"), "")));
                } catch (IOException ignored) {
                    // файл уже удалён
                }
            } else {
                keep.add(entry);
            }
        }
        saveIndex(keep);
    }

    public static byte[] readSkin(Map<String, Object> entry) {
        try {
            return Files.readAllBytes(Dirs.SKINS_DIR.resolve(Json.str(entry.get("file"), "")));
        } catch (IOException e) {
            return null;
        }
    }

    /** Скачать скин лицензионного игрока по нику. */
    public static byte[] fetchByUsername(String name) {
        String body;
        try {
            body = Http.get(Mojang.PROFILE_API + "/" + Utils.urlEncode(name));
        } catch (Http.ApiException e) {
            if (e.status == 404) {
                throw new RuntimeException("Игрок «" + name + "» не найден — нет лицензионного аккаунта с таким ником.");
            }
            throw new RuntimeException("Mojang API вернул ошибку " + e.status + ".");
        }
        String profileId = Json.str(Json.obj(Json.parseObject(body)).get("id"), "");
        if (profileId.isEmpty()) {
            throw new RuntimeException("Mojang API не вернул UUID игрока.");
        }

        String sessionBody;
        try {
            sessionBody = Http.get(Mojang.SESSION_API + "/" + profileId);
        } catch (Http.ApiException e) {
            throw new RuntimeException("Mojang session API вернул ошибку " + e.status + ".");
        }
        String texturesB64 = null;
        for (Object o : Json.arr(Json.obj(Json.parseObject(sessionBody)).get("properties"))) {
            Map<String, Object> prop = Json.obj(o);
            if ("textures".equals(Json.str(prop.get("name"), ""))) {
                texturesB64 = Json.str(prop.get("value"), null);
                break;
            }
        }
        if (texturesB64 == null || texturesB64.isEmpty()) {
            throw new RuntimeException("У игрока нет данных о текстурах.");
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(texturesB64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Не удалось разобрать данные текстур.", e);
        }
        String skinUrl = Json.str(Json.path(Json.parseObject(decoded), "textures", "SKIN", "url"), "");
        if (skinUrl.isEmpty()) {
            throw new RuntimeException("У игрока «" + name + "» стандартный скин (Steve/Alex по умолчанию).");
        }
        byte[] png = Http.fetchQuiet(skinUrl);
        if (png == null || png.length == 0) {
            throw new RuntimeException("Не удалось скачать файл скина.");
        }
        return png;
    }

    /** Разложить скин в CustomSkinLoader каждой сборки. */
    public static List<Path> exportToCustomSkinLoader(String nickname, byte[] data,
                                                      List<Instance> instances) {
        List<Path> written = new ArrayList<>();
        String safeNick = nickname.replaceAll("[^A-Za-z0-9_]", "_");
        if (safeNick.length() > 16) {
            safeNick = safeNick.substring(0, 16);
        }
        if (safeNick.isEmpty()) {
            safeNick = "Player";
        }
        for (Instance inst : instances) {
            try {
                Path folder = inst.folder.resolve("CustomSkinLoader").resolve("LocalSkin").resolve("skins");
                Files.createDirectories(folder);
                Path path = folder.resolve(safeNick + ".png");
                Files.write(path, data);
                written.add(path);
            } catch (IOException ignored) {
                // пропускаем сборку
            }
        }
        return written;
    }

    /** Отрендеренный скин в виде PNG (для предпросмотра в UI). */
    public static byte[] renderPreviewPng(byte[] skin, int size) {
        java.awt.image.BufferedImage img = SkinRenderer.render(skin, size);
        if (img == null) {
            return null;
        }
        try (var out = new java.io.ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }
}
