package com.frimer.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Запуск и общение с дочерними процессами (java, server.jar, установщики). */
public final class Procs {

    private Procs() {
    }

    /** Процесс с выводом, объединённым со stderr. */
    public static ProcessBuilder builder(List<String> cmd, Path cwd) {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (cwd != null) {
            pb.directory(cwd.toFile());
        }
        pb.redirectErrorStream(true);
        return pb;
    }

    /** Запустить и построчно отдать вывод в log. Возвращает код выхода. */
    public static int runStreaming(List<String> cmd, Path cwd, Consumer<String> log) {
        try {
            Process p = builder(cmd, cwd).start();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (log != null && !line.isBlank()) {
                        log.accept(Utils.stripAnsi(line));
                    }
                }
            }
            return p.waitFor();
        } catch (IOException e) {
            throw new RuntimeException("Не удалось запустить процесс: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Процесс прерван", e);
        }
    }

    /** Версия Java из вывода {@code java -version} (например 21). */
    public static Integer detectJavaMajor(Path javaExe) {
        try {
            Process p = builder(List.of(javaExe.toString(), "-version"), null).start();
            String text;
            try (var in = p.getInputStream()) {
                text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            var m = java.util.regex.Pattern.compile("version \"?(\\d+)(?:\\.(\\d+))?").matcher(text);
            if (m.find()) {
                int first = Integer.parseInt(m.group(1));
                int second = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
                return first == 1 ? second : first;
            }
        } catch (Exception ignored) {
            // не Java или не запускается
        }
        return null;
    }

    /** Первый java/java.exe внутри папки jdk (ищем только в bin). */
    public static Path findJavaExe(Path javaDir) {
        if (javaDir == null || !Files.isDirectory(javaDir)) {
            return null;
        }
        String name = Dirs.isWindows() ? "java.exe" : "java";
        try (Stream<Path> stream = Files.walk(javaDir, 4)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(name))
                    .filter(p -> p.getParent() != null && "bin".equals(p.getParent().getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    public static void killTree(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        try {
            if (Dirs.isWindows()) {
                List<String> cmd = new ArrayList<>(List.of("taskkill", "/F", "/T", "/PID",
                        Long.toString(process.pid())));
                new ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start().waitFor(10, TimeUnit.SECONDS);
            } else {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (Exception ignored) {
            process.destroyForcibly();
        }
    }

    /** Переменная окружения с запасным значением. */
    public static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    public static Map<String, String> noWindowFlags() {
        return Map.of();
    }
}
