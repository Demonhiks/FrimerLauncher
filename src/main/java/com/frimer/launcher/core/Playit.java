package com.frimer.launcher.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Агент playit.gg: поиск локальной установки и автозагрузка. */
public final class Playit {

    private Playit() {
    }

    public static final String DOWNLOAD_URL =
            "https://github.com/playit-cloud/playit-agent/releases/latest/download/playit-windows-x86-signed.exe";

    public static List<Path> candidates() {
        List<Path> out = new ArrayList<>();
        String localAppData = Procs.env("LOCALAPPDATA", "");
        if (!localAppData.isEmpty()) {
            out.add(Path.of(localAppData).resolve("playit_gg").resolve("bin").resolve("playit.exe"));
        }
        out.add(Path.of("C:/Program Files/playit_gg/bin/playit.exe"));
        out.add(Path.of("C:/Program Files (x86)/playit_gg/bin/playit.exe"));
        out.add(Path.of(Procs.env("ProgramFiles", "C:/Program Files"))
                .resolve("playit_gg").resolve("playit.exe"));
        out.add(Path.of(Procs.env("ProgramFiles(x86)", "C:/Program Files (x86)"))
                .resolve("playit_gg").resolve("playit.exe"));
        out.add(Dirs.GAME_DIR.resolve("playit").resolve("playit.exe"));
        return out;
    }

    public static Path find() {
        for (Path p : candidates()) {
            try {
                if (Files.isRegularFile(p)) {
                    return p;
                }
            } catch (RuntimeException ignored) {
                // недоступный путь - пропускаем
            }
        }
        return null;
    }

    public static Path ensure(Consumer<String> log, ProgressListener progress) {
        Path found = find();
        if (found != null) {
            return found;
        }
        Consumer<String> logger = log == null ? msg -> {
        } : log;
        Path dest = Dirs.GAME_DIR.resolve("playit").resolve("playit.exe");
        logger.accept("playit.gg не найден - скачиваю агент...");
        Downloader.download(DOWNLOAD_URL, dest, null, null, logger, progress);
        logger.accept("playit.gg скачан");
        return dest;
    }
}
