package com.frimer.launcher.core;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Настройки лаунчера: ник, тема, память по умолчанию, история ников. */
public final class AppSettings {

    private static final Preferences PREFS = Preferences.userRoot().node("com/frimer/launcher");

    public static final String APP_VERSION = "2.3.0";

    private AppSettings() {
    }

    public static String nickname() {
        return PREFS.get("nick", "Player");
    }

    public static void setNickname(String nick) {
        PREFS.put("nick", nick);
        addNicknameToHistory(nick);
    }

    public static String theme() {
        return PREFS.get("theme", "dark");
    }

    public static void setTheme(String theme) {
        PREFS.put("theme", theme);
    }

    public static int defaultRamMb() {
        return PREFS.getInt("default_ram", 4096);
    }

    public static void setDefaultRamMb(int ramMb) {
        PREFS.putInt("default_ram", ramMb);
    }

    public static boolean closeLauncherOnPlay() {
        return PREFS.getBoolean("close_on_play", false);
    }

    public static void setCloseLauncherOnPlay(boolean value) {
        PREFS.putBoolean("close_on_play", value);
    }

    public static String lastSeenVersion() {
        return PREFS.get("last_seen_version", "");
    }

    public static void setLastSeenVersion(String version) {
        PREFS.put("last_seen_version", version);
    }

    public static List<String> nicknameHistory() {
        String raw = PREFS.get("nick_history", "[]");
        List<String> out = new ArrayList<>();
        try {
            for (Object o : Json.arr(Json.parse(raw))) {
                String s = String.valueOf(o);
                if (!s.isBlank()) {
                    out.add(s);
                }
            }
        } catch (RuntimeException ignored) {
            // повреждённая история не критична
        }
        return out;
    }

    public static void addNicknameToHistory(String nick) {
        String clean = nick == null ? "" : nick.strip();
        if (clean.isEmpty()) {
            return;
        }
        List<String> history = nicknameHistory();
        history.remove(clean);
        history.add(0, clean);
        if (history.size() > 10) {
            history = history.subList(0, 10);
        }
        PREFS.put("nick_history", Json.write(history, false));
    }

    public static void removeNicknameFromHistory(String nick) {
        List<String> history = nicknameHistory();
        history.remove(nick);
        PREFS.put("nick_history", Json.write(history, false));
    }

    /** Любая строковая настройка по ключу. */
    public static String pref(String key, String fallback) {
        return PREFS.get(key, fallback);
    }

    /** Сохранить строковую настройку. */
    public static void setPref(String key, String value) {
        PREFS.put(key, value == null ? "" : value);
    }

    public static void flush() {
        try {
            PREFS.flush();
        } catch (BackingStoreException ignored) {
            // настройки останутся в памяти
        }
    }
}
