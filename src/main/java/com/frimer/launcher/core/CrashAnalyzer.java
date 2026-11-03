package com.frimer.launcher.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Разбор крашей Minecraft: причина, подсказка и текст отчёта. */
public final class CrashAnalyzer {

    private CrashAnalyzer() {
    }

    /** Итог анализа краша. */
    public record Report(String summary, String body) {
    }

    private record Hint(String pattern, String message) {
    }

    private static final List<Hint> HINTS = List.of(
            new Hint("OutOfMemoryError|Could not reserve enough space|GC overhead limit",
                    "Не хватает оперативной памяти. Увеличь RAM у сборки (правый клик - Изменить)."),
            new Hint("UnsupportedClassVersionError|compiled by a more recent version of the Java Runtime",
                    "Неподходящая версия Java для этой версии Minecraft или загрузчика."),
            new Hint("Module [\\w.\\-]+ not found|java\\.lang\\.module\\.FindException|ResolutionException",
                    "Загрузчик не нашёл нужную библиотеку. Удали папку этой версии в .frimerlauncher\\versions."),
            new Hint("Mixin apply failed|MixinApplyError|InvalidMixinException|Mixin transformation",
                    "Конфликт модов (Mixin). Отключи недавно добавленные моды."),
            new Hint("Incompatible mods found|Missing or unsupported mandatory dependencies|Unmet dependency|"
                    + "requires any version|requires version|Could not find required mod",
                    "У какого-то мода не хватает обязательной зависимости."),
            new Hint("NoSuchMethodError|NoSuchFieldError|NoClassDefFoundError|ClassNotFoundException",
                    "Какой-то мод собран под другую версию игры или загрузчика."),
            new Hint("EXCEPTION_ACCESS_VIOLATION|A fatal error has been detected|SIGSEGV",
                    "Сбой на уровне Java или видеодрайвера."),
            new Hint("GLFW error|Pixel format not accelerated|failed to create window|OpenGL error",
                    "Проблема с графикой. Обнови драйвер видеокарты."));

    public static Report analyze(int code, List<String> tailLines, Path instDir, long startedAt,
                                 String loader, String mcVersion) {
        Path crashFile = findCrashReport(instDir, startedAt);
        String report = "";
        if (crashFile != null) {
            try {
                report = Files.readString(crashFile, StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                report = "";
            }
        }
        String tail = String.join("\n", tailLines);
        String scan = report + "\n" + tail;

        List<String> lines = new ArrayList<>();
        lines.add("Игра (" + loader + " " + mcVersion + ") завершилась с ошибкой, код " + code + ".");

        String cause = cause(scan);
        if (!cause.isEmpty()) {
            lines.add("Причина: " + cause);
        }
        List<String> hints = new ArrayList<>();
        for (Hint hint : HINTS) {
            if (Pattern.compile(hint.pattern, Pattern.MULTILINE).matcher(scan).find()) {
                hints.add(hint.message);
            }
        }
        for (int i = 0; i < Math.min(2, hints.size()); i++) {
            lines.add("Подсказка: " + hints.get(i));
        }
        if (hints.isEmpty()) {
            lines.add("Точная причина не определена, подробности в тексте ниже.");
        }
        if (crashFile != null) {
            lines.add("Краш-репорт: " + crashFile.getFileName());
        }

        String body = report.isBlank() ? tail : report;
        if (body.length() > 80_000) {
            body = body.substring(body.length() - 80_000);
        }
        return new Report(String.join("\n", lines), body);
    }

    public static Path findCrashReport(Path instDir, long startedAt) {
        List<Path> candidates = new ArrayList<>();
        Path crashDir = instDir.resolve("crash-reports");
        for (Path p : LaunchBuilder.listDir(crashDir)) {
            String name = p.getFileName().toString();
            if (name.startsWith("crash-") && name.endsWith(".txt")) {
                candidates.add(p);
            }
        }
        for (Path p : LaunchBuilder.listDir(instDir)) {
            String name = p.getFileName().toString();
            if (name.startsWith("hs_err_pid") && name.endsWith(".log")) {
                candidates.add(p);
            }
        }
        Path newest = null;
        long newestTime = Long.MIN_VALUE;
        for (Path p : candidates) {
            long modified;
            try {
                modified = Files.getLastModifiedTime(p).toMillis();
            } catch (IOException e) {
                continue;
            }
            if (startedAt > 0 && modified < startedAt - 5000) {
                continue;
            }
            if (modified > newestTime) {
                newestTime = modified;
                newest = p;
            }
        }
        return newest;
    }

    private static String cause(String text) {
        List<String> parts = new ArrayList<>();
        Matcher desc = Pattern.compile("^Description:\\s*(.+)$", Pattern.MULTILINE).matcher(text);
        if (desc.find()) {
            parts.add(desc.group(1).strip());
        }
        List<String> caused = new ArrayList<>();
        Matcher causedMatcher = Pattern.compile("^Caused by:\\s*(.+)$", Pattern.MULTILINE).matcher(text);
        while (causedMatcher.find()) {
            caused.add(causedMatcher.group(1).strip());
        }
        if (!caused.isEmpty()) {
            parts.add(caused.get(caused.size() - 1));
        } else {
            Matcher exc = Pattern.compile("^((?:[\\w$]+\\.)+[\\w$]*(?:Exception|Error)\\b.*)$",
                    Pattern.MULTILINE).matcher(text);
            if (exc.find()) {
                parts.add(exc.group(1).strip());
            }
        }
        return Utils.shortText(String.join(" - ", parts), 300);
    }

    /** Простое заключение по коду выхода, когда отчёта нет. */
    public static String shortVerdict(int code) {
        if (code == 0) {
            return "Игра закрылась нормально.";
        }
        if (code == 1) {
            return "Игра завершилась с ошибкой (код 1). Смотри лог выше.";
        }
        return "Игра завершилась с кодом " + code + ".";
    }
}
