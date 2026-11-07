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
            "#0a0e14", "#0d1219", "#151c26", "#332811", "#252e3c",
            "#e9eef7", "#8d99ad", "#ffb02e", "#c47a12", "#ffd07a",
            "#0e141b", "#ff6b6b", "#ffcc66", "#7ad47a", "#1c2431", "#5b657a");

    public static final Theme LIGHT = new Theme("light",
            "#f2f4f8", "#ffffff", "#ffffff", "#fff1d6", "#e1e6ee",
            "#1b2130", "#6b7688", "#e8890c", "#b36608", "#ffb04d",
            "#f7f9fc", "#c0392b", "#a56b00", "#2e7d32", "#eef2f8", "#9aa3b2");

    /** Радиусы скруглений: карточки, кнопки, поля ввода. */
    public static final int RADIUS_CARD = 12;
    public static final int RADIUS_TILE = 12;
    public static final int RADIUS_BUTTON = 8;
    public static final int RADIUS_INPUT = 8;

    /** Мягкая тень под карточками (альфа). */
    public int shadowAlpha() {
        return "light".equals(name) ? 26 : 90;
    }

    /** Верхний и нижний цвет карточки-градиента. */
    public Color cardTop() {
        return "light".equals(name) ? Theme.rgb("#ffffff") : Theme.rgb("#1e242e");
    }

    public Color cardBottom() {
        return "light".equals(name) ? Theme.rgb("#f7f9fc") : Theme.rgb("#171c24");
    }

    /** Градиент акцентной кнопки. */
    public Color accentTop() {
        return "light".equals(name) ? Theme.rgb("#f59b1c") : Theme.rgb("#ffb644");
    }

    public Color accentBottom() {
        return "light".equals(name) ? Theme.rgb("#e07f08") : Theme.rgb("#f29a16");
    }

    /** Тема из готовых цветов (для пользовательских JSON-тем). */
    public static Theme custom(String name, Color bg, Color sidebar, Color card, Color cardSel,
                               Color border, Color text, Color muted, Color accent, Color accentDark,
                               Color accentLight, Color input, Color err, Color warn, Color ok,
                               Color hover, Color disabledText) {
        return new Theme(name, hex(bg), hex(sidebar), hex(card), hex(cardSel), hex(border),
                hex(text), hex(muted), hex(accent), hex(accentDark), hex(accentLight), hex(input),
                hex(err), hex(warn), hex(ok), hex(hover), hex(disabledText));
    }

    private static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

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
