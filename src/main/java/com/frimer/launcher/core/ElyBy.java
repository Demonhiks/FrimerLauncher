package com.frimer.launcher.core;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Ely.by — русская система скинов и аккаунтов, совместимая с Mojang API.
 *
 * <p>Скины: {@code https://skinsystem.ely.by/skins/<ник>.png}. Если у игрока нет
 * собственного скина Ely.by, сервис прозрачно отдаёт скин из Mojang.
 */
public final class ElyBy {

    /** Документация: https://docs.ely.by/ru/skins-system.html */
    public static final String SKIN_URL = "https://skinsystem.ely.by/skins/";
    public static final String PROFILE_URL = "https://authserver.ely.by/api/users/profiles/minecraft/";

    private ElyBy() {
    }

    /** Профиль на Ely.by: UUID и ник в нормальном регистре (или null, если ника нет). */
    public static Profile profile(String nickname) {
        String nick = clean(nickname);
        if (nick.isEmpty()) {
            return null;
        }
        try {
            String body = Http.get(PROFILE_URL + encode(nick),
                    Map.of("Accept", "application/json"));
            if (body == null || body.isBlank()) {
                return null;
            }
            Map<String, Object> json = Json.parseObject(body);
            String id = Json.str(json.get("id"), "");
            String name = Json.str(json.get("name"), nick);
            return id.isEmpty() ? null : new Profile(id, name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Текстура скина по нику: PNG 64×64 (или null, если текстуры нет). */
    public static byte[] skin(String nickname) {
        String nick = clean(nickname);
        if (nick.isEmpty()) {
            return null;
        }
        byte[] data = fetch(SKIN_URL + encode(nick) + ".png?version=2");
        return data != null && data.length > 100 ? data : null;
    }

    private static byte[] fetch(String url) {
        try {
            return Http.getBytesFollowing(url);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String clean(String nickname) {
        return nickname == null ? "" : nickname.strip().replaceAll("[\\s\\p{Cntrl}]", "");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Профиль Ely.by. */
    public record Profile(String uuid, String name) {
    }
}
