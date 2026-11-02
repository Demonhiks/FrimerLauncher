package com.frimer.launcher.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Запуск Java-сервера с консолью, остановкой через "stop" и агентом playit.gg. */
public final class ServerRunner {

    /** Слушатель событий сервера; вызывается из фонового потока. */
    public interface Listener {
        default void onStatus(String status) {
        }

        default void onLog(String line) {
        }

        default void onStarted() {
        }

        default void onStopped(int code) {
        }

        default void onFailed(String error) {
        }
    }

    private final ServerInstance server;
    private final Path javaExe;
    private final boolean withPlayit;
    private final Listener listener;

    private volatile Process process;
    private volatile Process playitProcess;
    private volatile boolean running;
    private volatile boolean killRequested;

    public ServerRunner(ServerInstance server, Path javaExe, boolean withPlayit, Listener listener) {
        this.server = server;
        this.javaExe = javaExe;
        this.withPlayit = withPlayit;
        this.listener = listener == null ? new Listener() {
        } : listener;
    }

    public boolean isRunning() {
        return running;
    }

    /** Запустить сервер в отдельном потоке (метод не блокирует). */
    public void start() {
        Thread thread = new Thread(this::run, "frimer-server-runner");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        try {
            List<String> launch = server.launchArgs();
            if (launch == null) {
                listener.onFailed("Нет ядра сервера (" + server.coreLabel() + ") в " + server.folder
                        + "\nСоздай сервер заново.");
                return;
            }
            List<String> cmd = new ArrayList<>();
            cmd.add(javaExe.toString());
            cmd.add("-Xmx" + server.ramMb + "M");
            cmd.add("-Xms" + (server.ramMb / 2) + "M");
            cmd.addAll(launch);

            listener.onStatus("Запускаю Java-сервер...");
            ServerStore.ensureEulaAndProperties(server, listener::onLog);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(server.folder.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            process = p;
            running = true;
            listener.onStarted();
            listener.onStatus("Сервер запущен");

            startPlayitIfNeeded();
            pumpOutput(p);

            int code = p.waitFor();
            listener.onLog("[server] Процесс завершился с кодом " + code);
            stopPlayit();
            running = false;
            process = null;
            listener.onStopped(code);
        } catch (IOException e) {
            running = false;
            listener.onFailed("Не удалось запустить сервер: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
            listener.onFailed("Запуск сервера прерван");
        }
    }

    private void pumpOutput(Process p) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                listener.onLog(Utils.stripAnsi(line));
            }
        } catch (IOException ignored) {
            // поток закрылся вместе с процессом
        }
    }

    private void startPlayitIfNeeded() {
        if (!withPlayit || !server.autoPlayit) {
            return;
        }
        Path playit = Playit.find();
        if (playit == null) {
            listener.onLog("[playit] агент не найден - пропускаю (можно скачать в настройках)");
            return;
        }
        try {
            listener.onLog("[playit] Запускаю " + playit);
            ProcessBuilder pb = new ProcessBuilder(List.of(playit.toString()));
            pb.directory(playit.getParent().toFile());
            pb.redirectErrorStream(true);
            Process pp = pb.start();
            playitProcess = pp;
            Thread pump = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(pp.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            listener.onLog("[playit] " + Utils.stripAnsi(line));
                        }
                    }
                } catch (IOException ignored) {
                    // процесс завершился
                }
            }, "frimer-playit");
            pump.setDaemon(true);
            pump.start();
        } catch (IOException e) {
            listener.onLog("[playit] Не удалось запустить: " + e.getMessage());
        }
    }

    private void stopPlayit() {
        Process pp = playitProcess;
        playitProcess = null;
        if (pp != null && pp.isAlive()) {
            Procs.killTree(pp);
        }
    }

    /** Мягкая остановка: команда stop в консоль сервера. */
    public void requestStop() {
        Process p = process;
        if (p == null || !p.isAlive()) {
            return;
        }
        try (OutputStream in = p.getOutputStream()) {
            in.write("stop\n".getBytes(StandardCharsets.UTF_8));
            in.flush();
        } catch (IOException ignored) {
            // сервер уже не слушает stdin
        }
    }

    /** Жёсткое завершение процесса сервера и агента. */
    public void kill() {
        killRequested = true;
        Procs.killTree(process);
        stopPlayit();
    }

    public boolean killRequested() {
        return killRequested;
    }

    public boolean playitRunning() {
        Process pp = playitProcess;
        return pp != null && pp.isAlive();
    }

    public Path folder() {
        return server.folder;
    }

    public static void openFolder(Path folder) {
        try {
            if (Dirs.isWindows()) {
                new ProcessBuilder("explorer", folder.toString()).start();
            } else if (Dirs.osName().equals("osx")) {
                new ProcessBuilder("open", folder.toString()).start();
            } else {
                new ProcessBuilder("xdg-open", folder.toString()).start();
            }
        } catch (IOException ignored) {
            // не критично
        }
    }

    public static void ensureServerFolders(ServerInstance srv) {
        try {
            Files.createDirectories(srv.folder.resolve("mods"));
            Files.createDirectories(srv.folder.resolve("plugins"));
            Files.createDirectories(srv.folder.resolve("world"));
        } catch (IOException ignored) {
            // папки создаст сам сервер
        }
    }

    public static Consumer<String> nullLog() {
        return msg -> {
        };
    }
}
