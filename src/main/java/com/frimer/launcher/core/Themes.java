package com.frimer.launcher.core;

import com.frimer.launcher.ui.Theme;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Свои темы: встроенные dark/light плюс JSON-файлы из ~/.frimerlauncher/themes. */
public final class Themes {

    private Themes() {
    }

    public static final String DARK = "dark";
    public static final String LIGHT = "light";

    public static Path dir() {
        return Dirs.GAME_DIR.resolve("themes");
    }

    /** Имена доступных тем: сначала встроенные, потом пользовательские. */
    public static List<String> names() {
        List<String> out = new ArrayList<>(List.of(DARK, LIGHT));
        Path dir = dir();
        if (Files.isDirectory(dir)) {
            for (Path file : LaunchBuilder.listDir(dir)) {
                String name = file.getFileName().toString();
                if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
                    String theme = name.substring(0, name.length() - 5);
                    if (!out.contains(theme)) {
                        out.add(theme);
                    }
                }
            }
        }
        return out;
    }

    /** Загрузить тему по имени; при ошибке — тёмная. */
    public static Theme load(String name) {
        if (name == null || name.isBlank()) {
            return Theme.DARK;
        }
        if (DARK.equalsIgnoreCase(name)) {
            return Theme.DARK;
        }
        if (LIGHT.equalsIgnoreCase(name)) {
            return Theme.LIGHT;
        }
        Map<String, Object> json = Json.readFileQuiet(dir().resolve(name + ".json"));
        if (json == null) {
            return Theme.DARK;
        }
        return fromJson(name, json);
    }

    /** Сохранить тему как JSON-шаблон, который можно править руками. */
    public static Path export(Theme theme, String name) {
        try {
            Files.createDirectories(dir());
            Path file = dir().resolve(name + ".json");
            Json.writeFile(file, toJson(theme));
            return file;
        } catch (IOException e) {
            throw new RuntimeException("Не могу сохранить тему: " + e.getMessage(), e);
        }
    }

    /** Тема в виде JSON (все поддерживаемые ключи). */
    public static Map<String, Object> toJson(Theme theme) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("bg", hex(theme.bg));
        json.put("sidebar", hex(theme.sidebar));
        json.put("card", hex(theme.card));
        json.put("cardSel", hex(theme.cardSel));
        json.put("border", hex(theme.border));
        json.put("text", hex(theme.text));
        json.put("muted", hex(theme.muted));
        json.put("accent", hex(theme.accent));
        json.put("accentDark", hex(theme.accentDark));
        json.put("accentLight", hex(theme.accentLight));
        json.put("input", hex(theme.input));
        json.put("err", hex(theme.err));
        json.put("warn", hex(theme.warn));
        json.put("ok", hex(theme.ok));
        json.put("hover", hex(theme.hover));
        json.put("disabledText", hex(theme.disabledText));
        return json;
    }

    /** Создать тему из JSON: отсутствующие ключи берутся из тёмной темы. */
    public static Theme fromJson(String name, Map<String, Object> json) {
        Theme base = Theme.DARK;
        try {
            return Theme.custom(name,
                    color(json, "bg", base.bg),
                    color(json, "sidebar", base.sidebar),
                    color(json, "card", base.card),
                    color(json, "cardSel", base.cardSel),
                    color(json, "border", base.border),
                    color(json, "text", base.text),
                    color(json, "muted", base.muted),
                    color(json, "accent", base.accent),
                    color(json, "accentDark", base.accentDark),
                    color(json, "accentLight", base.accentLight),
                    color(json, "input", base.input),
                    color(json, "err", base.err),
                    color(json, "warn", base.warn),
                    color(json, "ok", base.ok),
                    color(json, "hover", base.hover),
                    color(json, "disabledText", base.disabledText));
        } catch (RuntimeException e) {
            return base;
        }
    }

    private static java.awt.Color color(Map<String, Object> json, String key,
                                       java.awt.Color fallback) {
        Object value = json.get(key);
        if (value instanceof String text && text.matches("#?[0-9a-fA-F]{6}")) {
            return Theme.rgb(text.startsWith("#") ? text : "#" + text);
        }
        return fallback;
    }

    private static String hex(java.awt.Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
