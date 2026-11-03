package com.frimer.launcher.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Аккаунты лаунчера: офлайн-ники и вход через Microsoft. */
public final class Accounts {

    private Accounts() {
    }

    public static final String PREF_LIST = "accounts";
    public static final String PREF_ACTIVE = "active_account";

    public enum Type {
        OFFLINE, MICROSOFT
    }

    /** Один аккаунт. */
    public static final class Account {
        public Type type = Type.OFFLINE;
        public String name = "";
        public String uuid = "";
        public String accessToken = "";
        public String refreshToken = "";
        public long expiresAt;

        public Account() {
        }

        public Account(Type type, String name, String uuid, String accessToken,
                       String refreshToken, long expiresAt) {
            this.type = type;
            this.name = name;
            this.uuid = uuid;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAt = expiresAt;
        }

        public boolean microsoft() {
            return type == Type.MICROSOFT;
        }

        /** UUID для запуска: у офлайна считается из ника. */
        public String launchUuid() {
            return uuid == null || uuid.isBlank() ? Utils.offlineUuid(name) : uuid;
        }

        public String launchToken() {
            return microsoft() && accessToken != null && !accessToken.isBlank() ? accessToken : "0";
        }

        public String label() {
            return name + (microsoft() ? "  ·  Microsoft" : "  ·  офлайн");
        }

        public boolean expired() {
            return microsoft() && expiresAt > 0 && System.currentTimeMillis() > expiresAt;
        }

        Map<String, Object> toJson() {
            Map<String, Object> json = new java.util.LinkedHashMap<>();
            json.put("type", type.name());
            json.put("name", name);
            json.put("uuid", uuid);
            json.put("access_token", accessToken);
            json.put("refresh_token", refreshToken);
            json.put("expires_at", expiresAt);
            return json;
        }

        static Account fromJson(Map<String, Object> json) {
            Account account = new Account();
            account.type = "MICROSOFT".equals(Json.str(json.get("type"), ""))
                    ? Type.MICROSOFT : Type.OFFLINE;
            account.name = Json.str(json.get("name"), "");
            account.uuid = Json.str(json.get("uuid"), "");
            account.accessToken = Json.str(json.get("access_token"), "");
            account.refreshToken = Json.str(json.get("refresh_token"), "");
            account.expiresAt = Json.lng(json.get("expires_at"), 0);
            return account;
        }
    }

    public static List<Account> list() {
        List<Account> out = new ArrayList<>();
        String raw = com.frimer.launcher.core.AppSettings.pref(PREF_LIST, "[]");
        try {
            for (Object o : Json.arr(Json.parse(raw))) {
                Account account = Account.fromJson(Json.obj(o));
                if (!account.name.isBlank()) {
                    out.add(account);
                }
            }
        } catch (RuntimeException ignored) {
            // повреждённый список считаем пустым
        }
        return out;
    }

    public static void save(List<Account> accounts) {
        List<Object> json = new ArrayList<>();
        for (Account account : accounts) {
            json.add(account.toJson());
        }
        com.frimer.launcher.core.AppSettings.setPref(PREF_LIST, Json.write(json, false));
    }

    /** Активный аккаунт; если ничего не выбрано — офлайн с ником из настроек. */
    public static Account active() {
        String activeName = com.frimer.launcher.core.AppSettings.pref(PREF_ACTIVE, "");
        for (Account account : list()) {
            if (account.name.equalsIgnoreCase(activeName)) {
                return account;
            }
        }
        Account offline = new Account(Type.OFFLINE, com.frimer.launcher.core.AppSettings.nickname(),
                "", "", "", 0);
        return offline;
    }

    public static void setActive(Account account) {
        com.frimer.launcher.core.AppSettings.setPref(PREF_ACTIVE, account == null ? "" : account.name);
        if (account != null) {
            com.frimer.launcher.core.AppSettings.setNickname(account.name);
        }
    }

    public static Account addOffline(String nick) {
        Account account = new Account(Type.OFFLINE, nick, "", "", "", 0);
        List<Account> accounts = list();
        accounts.removeIf(a -> a.name.equalsIgnoreCase(nick) && !a.microsoft());
        accounts.add(account);
        save(accounts);
        return account;
    }

    public static void add(Account account) {
        List<Account> accounts = list();
        accounts.removeIf(a -> a.name.equalsIgnoreCase(account.name));
        accounts.add(account);
        save(accounts);
    }

    public static void remove(Account account) {
        List<Account> accounts = list();
        accounts.removeIf(a -> a.name.equalsIgnoreCase(account.name));
        save(accounts);
        if (com.frimer.launcher.core.AppSettings.pref(PREF_ACTIVE, "")
                .equalsIgnoreCase(account.name)) {
            com.frimer.launcher.core.AppSettings.setPref(PREF_ACTIVE, "");
        }
    }
}
