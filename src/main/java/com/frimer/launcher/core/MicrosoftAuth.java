package com.frimer.launcher.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Вход через Microsoft (OAuth device code flow → Xbox Live → Minecraft).
 * Нужен свой Azure client ID: он вводится в настройках/окне аккаунтов.
 * Регистрация: portal.azure.com → App registration → «Allow public client flows».
 */
public final class MicrosoftAuth {

    private MicrosoftAuth() {
    }

    public static final String PREF_CLIENT_ID = "ms_client_id";
    private static final String DEVICE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String SCOPE = "XboxLive.signin offline_access";

    /** Код для пользователя из device code flow. */
    public record Device(String userCode, String verifyUri, String message, String deviceCode,
                         int interval, int expiresIn) {
    }

    public static String clientId() {
        return AppSettings.pref(PREF_CLIENT_ID, "").strip();
    }

    public static void setClientId(String id) {
        AppSettings.setPref(PREF_CLIENT_ID, id == null ? "" : id.strip());
    }

    /** Шаг 1: получить код, который пользователь введёт на сайте Microsoft. */
    public static Device startDeviceCode(String clientId) {
        Map<String, Object> json = Json.parseObject(Http.post(DEVICE_URL,
                "client_id=" + Utils.urlEncode(clientId) + "&scope=" + Utils.urlEncode(SCOPE),
                "application/x-www-form-urlencoded", Map.of()));
        return new Device(
                Json.str(json.get("user_code"), ""),
                Json.str(json.get("verification_uri"), "https://microsoft.com/link"),
                Json.str(json.get("message"), ""),
                Json.str(json.get("device_code"), ""),
                Json.integer(json.get("interval"), 5),
                Json.integer(json.get("expires_in"), 900));
    }

    /** Шаг 2: ждать подтверждения и получить аккаунт Minecraft. */
    public static Accounts.Account waitForAccount(Device device, String clientId,
                                                  Consumer<String> status) {
        long deadline = System.currentTimeMillis() + device.expiresIn() * 1000L;
        String accessToken = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(Math.max(2, device.interval()) * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Ожидание входа прервано");
            }
            try {
                Map<String, Object> json = Json.parseObject(Http.post(TOKEN_URL,
                        "grant_type=" + Utils.urlEncode(
                                "urn:ietf:params:oauth:grant-type:device_code")
                                + "&client_id=" + Utils.urlEncode(clientId)
                                + "&device_code=" + Utils.urlEncode(device.deviceCode()),
                        "application/x-www-form-urlencoded", Map.of()));
                accessToken = Json.str(json.get("access_token"), "");
                if (!accessToken.isBlank()) {
                    break;
                }
            } catch (Http.ApiException e) {
                String message = e.getMessage() == null ? "" : e.getMessage();
                if (message.contains("authorization_pending")) {
                    status.accept("Жду подтверждения на сайте Microsoft...");
                    continue;
                }
                if (message.contains("slow_down")) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                if (message.contains("expired_token") || message.contains("code_expired")) {
                    throw new RuntimeException("Код истёк — начните вход заново");
                }
                throw new RuntimeException("Microsoft вернул ошибку: "
                        + Utils.shortText(message, 200));
            }
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("Не дождались входа (код устарел)");
        }
        status.accept("Вход выполнен, получаю профиль Minecraft...");
        return exchangeForMinecraft(accessToken);
    }

    /** Обменять Microsoft-токен на аккаунт Minecraft (Xbox → XSTS → Minecraft). */
    public static Accounts.Account exchangeForMinecraft(String microsoftToken) {
        Map<String, Object> xboxProperties = new LinkedHashMap<>();
        xboxProperties.put("AuthMethod", "RPS");
        xboxProperties.put("SiteName", "user.auth.xboxlive.com");
        xboxProperties.put("RpsTicket", "d=" + microsoftToken);
        Map<String, Object> xboxBody = new LinkedHashMap<>();
        xboxBody.put("Properties", xboxProperties);
        xboxBody.put("RelyingParty", "http://auth.xboxlive.com");
        xboxBody.put("TokenType", "JWT");
        Map<String, Object> xbox = Json.parseObject(Http.post(
                "https://user.auth.xboxlive.com/user/authenticate", Json.write(xboxBody, false),
                "application/json", Map.of("Accept", "application/json")));
        String xboxToken = Json.str(xbox.get("Token"), "");
        String userHash = Json.str(Json.path(xbox, "DisplayClaims", "xui", "0", "uhs"), "");
        if (xboxToken.isBlank()) {
            throw new RuntimeException("Xbox Live не выдал токен");
        }

        Map<String, Object> xstsProperties = new LinkedHashMap<>();
        xstsProperties.put("SandboxId", "RETAIL");
        xstsProperties.put("UserTokens", java.util.List.of(xboxToken));
        Map<String, Object> xstsBody = new LinkedHashMap<>();
        xstsBody.put("Properties", xstsProperties);
        xstsBody.put("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.put("TokenType", "JWT");
        Map<String, Object> xsts = Json.parseObject(Http.post(
                "https://xsts.auth.xboxlive.com/xsts/authorize", Json.write(xstsBody, false),
                "application/json", Map.of("Accept", "application/json")));
        String xstsToken = Json.str(xsts.get("Token"), "");
        if (xstsToken.isBlank()) {
            throw new RuntimeException("XSTS не выдал токен (нет игры на аккаунте?)");
        }

        Map<String, Object> mcBody = new LinkedHashMap<>();
        mcBody.put("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        Map<String, Object> mc = Json.parseObject(Http.post(
                "https://api.minecraftservices.com/authentication/login_with_xbox",
                Json.write(mcBody, false), "application/json",
                Map.of("Accept", "application/json")));
        String mcToken = Json.str(mc.get("access_token"), "");
        if (mcToken.isBlank()) {
            throw new RuntimeException("Minecraft не выдал токен");
        }

        Map<String, Object> profile = Json.parseObject(Http.get(
                "https://api.minecraftservices.com/minecraft/profile",
                Map.of("Authorization", "Bearer " + mcToken)));
        String name = Json.str(profile.get("name"), "");
        String uuid = Json.str(profile.get("id"), "");
        if (name.isBlank()) {
            throw new RuntimeException("У аккаунта нет профиля Minecraft");
        }
        return new Accounts.Account(Accounts.Type.MICROSOFT, name, uuid, mcToken, "",
                System.currentTimeMillis() + 23L * 60 * 60 * 1000);
    }

    /** Ссылка на регистрацию приложения (для подсказки в UI). */
    public static String registrationHint() {
        return "portal.azure.com → App registrations → New → Allow public client flows: Yes";
    }
}
