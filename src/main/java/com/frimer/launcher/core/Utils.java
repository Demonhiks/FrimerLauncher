package com.frimer.launcher.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Утилиты: строки, пути, форматирование, хеши. */
public final class Utils {

    private Utils() {
    }

    private static final Map<Character, String> TRANSLIT = new TreeMap<>();

    static {
        String[] pairs = {
                "а", "a", "б", "b", "в", "v", "г", "g", "д", "d", "е", "e", "ё", "e",
                "ж", "zh", "з", "z", "и", "i", "й", "y", "к", "k", "л", "l", "м", "m",
                "н", "n", "о", "o", "п", "p", "р", "r", "с", "s", "т", "t", "у", "u",
                "ф", "f", "х", "kh", "ц", "ts", "ч", "ch", "ш", "sh", "щ", "shch",
                "ъ", "", "ы", "y", "ь", "", "э", "e", "ю", "yu", "я", "ya",
                "і", "i", "ї", "yi", "є", "ye", "ґ", "g",
        };
        for (int i = 0; i < pairs.length; i += 2) {
            TRANSLIT.put(pairs[i].charAt(0), pairs[i + 1]);
        }
    }

    /** UUID офлайн-игрока (MD5 от "OfflinePlayer:<ник>", как в ванильном сервере). */
    public static String offlineUuid(String name) {
        MessageDigest md5 = digest("MD5");
        byte[] h = md5.digest(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        h[6] = (byte) ((h[6] & 0x0F) | 0x30);
        h[8] = (byte) ((h[8] & 0x3F) | 0x80);
        HexFormat hex = HexFormat.of();
        String s = hex.formatHex(h);
        return s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16)
                + "-" + s.substring(16, 20) + "-" + s.substring(20);
    }

    /** Безопасное имя папки: транслитерация, только a-z0-9_- . */
    public static String slugify(String name) {
        String low = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (char c : low.toCharArray()) {
            String t = TRANSLIT.get(c);
            sb.append(t != null ? t : c);
        }
        String s = sb.toString().replaceAll("[^a-z0-9_-]+", "-");
        s = s.replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "instance" : s;
    }

    public static String fmtDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m";
        }
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    public static String fmtSize(long bytes) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double value = bytes;
        int i = 0;
        while (value >= 1024 && i < units.length - 1) {
            value /= 1024;
            i++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[i]);
    }

    public static String fmtNum(long n) {
        if (n >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        }
        if (n >= 1_000) {
            return (n / 1_000) + "K";
        }
        return Long.toString(n);
    }

    public static String shortText(String text, int limit) {
        String t = (text == null ? "" : text).replaceAll("\\s+", " ").trim();
        if (t.length() <= limit) {
            return t;
        }
        String cut = t.substring(0, Math.max(0, limit - 1));
        return cut.replaceAll("\\s+$", "") + "...";
    }

    public static String fmtGb(int ramMb) {
        double gb = ramMb / 1024.0;
        if (gb == Math.rint(gb)) {
            return ((long) gb) + " GB";
        }
        return String.format(Locale.ROOT, "%.1f GB", gb);
    }

    public static long dirSize(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return 0;
        }
        try (var stream = Files.walk(path)) {
            return stream.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    public static int countMods(Path folder) {
        Path mods = folder.resolve("mods");
        if (!Files.isDirectory(mods)) {
            return 0;
        }
        try (var stream = Files.list(mods)) {
            return (int) stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".jar") && !n.endsWith(".jar.disabled");
                    })
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }

    public static String sha1(Path path) {
        return hashFile(path, "SHA-1");
    }

    public static String sha512(Path path) {
        return hashFile(path, "SHA-512");
    }

    public static String sha256(Path path) {
        return hashFile(path, "SHA-256");
    }

    public static String hashFile(Path path, String algorithm) {
        MessageDigest md = digest(algorithm);
        byte[] buf = new byte[1 << 20];
        try (InputStream in = Files.newInputStream(path)) {
            int read;
            while ((read = in.read(buf)) > 0) {
                md.update(buf, 0, read);
            }
        } catch (IOException e) {
            throw new RuntimeException("Не могу прочитать " + path + ": " + e.getMessage(), e);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Нет алгоритма " + algorithm, e);
        }
    }

    /** Склеить пути с защитой от выхода за пределы базовой папки. */
    public static Path safeJoin(Path base, String relative) {
        Path root = base.toAbsolutePath().normalize();
        Path dest = root.resolve(relative.replace('\\', '/')).normalize();
        if (!dest.startsWith(root)) {
            throw new IllegalArgumentException("Небезопасный путь: " + dest);
        }
        return dest;
    }

    /** "1.20.1" -> [1,20,1]; используется для сортировки версий. */
    public static List<Long> numKey(String text) {
        List<Long> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        String head = text.split("-")[0];
        Matcher m = Pattern.compile("\\d+").matcher(head);
        while (m.find()) {
            try {
                out.add(Long.parseLong(m.group()));
            } catch (NumberFormatException ignored) {
                // пропускаем слишком большие числа
            }
        }
        return out;
    }

    /** Компаратор "по-человечески" убывающий: 1.10 > 1.9 . */
    public static final Comparator<String> VERSION_DESC = (a, b) -> {
        List<Long> ka = numKey(a);
        List<Long> kb = numKey(b);
        int n = Math.max(ka.size(), kb.size());
        for (int i = 0; i < n; i++) {
            long x = i < ka.size() ? ka.get(i) : 0L;
            long y = i < kb.size() ? kb.get(i) : 0L;
            if (x != y) {
                return Long.compare(y, x);
            }
        }
        return b.compareTo(a);
    };

    public static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Убрать управляющие ANSI-коды из строк лога. */
    public static String stripAnsi(String line) {
        if (line == null) {
            return "";
        }
        return line.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
