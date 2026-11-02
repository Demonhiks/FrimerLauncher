package com.frimer.launcher.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Цветовые схемы лаунчера (тёмная и светлая) и цвета бейджей загрузчиков. */
public final class Theme {

    public final String name;
    public final Color bg;
    public final Color sidebar;
    public final Color card;
    public final Color cardSel;
    public final Color border;
    public final Color text;
    public final Color muted;
    public final Color accent;
    public final Color accentDark;
    public final Color accentLight;
    public final Color input;
    public final Color err;
    public final Color warn;
    public final Color ok;
    public final Color hover;
    public final Color disabledText;

    private Theme(String name, String bg, String sidebar, String card, String cardSel, String border,
                  String text, String muted, String accent, String accentDark, String accentLight,
                  String input, String err, String warn, String ok, String hover, String disabledText) {
        this.name = name;
        this.bg = rgb(bg);
        this.sidebar = rgb(sidebar);
        this.card = rgb(card);
        this.cardSel = rgb(cardSel);
        this.border = rgb(border);
        this.text = rgb(text);
        this.muted = rgb(muted);
        this.accent = rgb(accent);
        this.accentDark = rgb(accentDark);
        this.accentLight = rgb(accentLight);
        this.input = rgb(input);
        this.err = rgb(err);
        this.warn = rgb(warn);
        this.ok = rgb(ok);
        this.hover = rgb(hover);
        this.disabledText = rgb(disabledText);
    }

    public static final Theme DARK = new Theme("dark",
            "#15181e", "#1d2129", "#232833", "#3a3222", "#2c323d",
            "#dfe3ea", "#8a93a3", "#f2a93b", "#b8791a", "#ffbd52",
            "#15181e", "#ff6b6b", "#ffcc66", "#7ad47a", "#2a3040", "#5a6170");

    public static final Theme LIGHT = new Theme("light",
            "#f4f5f7", "#ffffff", "#ffffff", "#fff3d9", "#d8dce4",
            "#232833", "#6d7686", "#e08a1c", "#a56410", "#ffb84d",
            "#ffffff", "#c0392b", "#a56b00", "#2e7d32", "#f0f2f6", "#9aa1ad");

    public static Theme of(String name) {
        return "light".equalsIgnoreCase(name) ? LIGHT : DARK;
    }

    private static final Map<String, Color[]> LOADER_COLORS = new LinkedHashMap<>();

    static {
        LOADER_COLORS.put("Vanilla", new Color[]{rgb("#3a4150"), rgb("#dfe3ea")});
        LOADER_COLORS.put("Paper", new Color[]{rgb("#4db6ac"), rgb("#08302c")});
        LOADER_COLORS.put("Fabric", new Color[]{rgb("#dbb27e"), rgb("#3a2a10")});
        LOADER_COLORS.put("Quilt", new Color[]{rgb("#c17fd6"), rgb("#2e1038")});
        LOADER_COLORS.put("Forge", new Color[]{rgb("#e08a3c"), rgb("#3a1f05")});
        LOADER_COLORS.put("NeoForge", new Color[]{rgb("#4fa8e0"), rgb("#0a2233")});
    }

    public Color loaderBg(String loader) {
        Color[] colors = LOADER_COLORS.get(loader);
        return colors == null ? LOADER_COLORS.get("Vanilla")[0] : colors[0];
    }

    public Color loaderFg(String loader) {
        Color[] colors = LOADER_COLORS.get(loader);
        return colors == null ? LOADER_COLORS.get("Vanilla")[1] : colors[1];
    }

    public String loaderLetter(String loader) {
        switch (loader) {
            case "Vanilla":
                return "V";
            case "Paper":
                return "P";
            case "Fabric":
                return "F";
            case "Quilt":
                return "Q";
            case "Forge":
                return "Fo";
            case "NeoForge":
                return "NF";
            default:
                return "?";
        }
    }

    public Color rgba(String hex, int alpha) {
        Color c = rgb(hex);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    public static Color rgb(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        return new Color(Integer.parseInt(h.substring(0, 2), 16),
                Integer.parseInt(h.substring(2, 4), 16),
                Integer.parseInt(h.substring(4, 6), 16));
    }

    // ------------------------------------------------------------------ шрифты

    private static final List<String> UI_FONTS = List.of("Segoe UI", "Inter", "Noto Sans", "DejaVu Sans");
    private static final List<String> TITLE_FONTS = List.of("Bahnschrift", "Segoe UI Semibold", "Segoe UI");

    public static String pickFont(List<String> candidates) {
        var env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        var available = new java.util.HashSet<>(List.of(env.getAvailableFontFamilyNames()));
        for (String candidate : candidates) {
            if (available.contains(candidate)) {
                return candidate;
            }
        }
        return Font.SANS_SERIF;
    }

    public static Font uiFont(int style, float size) {
        return new Font(pickFont(UI_FONTS), style, Math.round(size));
    }

    public static Font titleFont(int style, float size) {
        return new Font(pickFont(TITLE_FONTS), style, Math.round(size));
    }

    public static Font monoFont(int style, float size) {
        return new Font(Font.MONOSPACED, style, Math.round(size));
    }
}
