package com.frimer.launcher.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Полный цикл запуска игры: подготовка версии и загрузчика, Java, команда,
 * процесс, вывод в консоль, статистика и анализ краша.
 */
public final class GameLauncher {

    /** События запуска; вызываются из фонового потока. */
    public interface Listener {
        default void onStatus(String status) {
        }

        default void onProgress(long done, long total) {
        }

        default void onLog(String line) {
        }

        default void onGameStarted(long timestamp) {
        }

        default void onFinished(int code) {
        }

        default void onFailed(String error) {
        }

        default void onCrash(String summary, String body) {
        }
    }

    private final Instance instance;
    private final String nickname;
    private final Listener listener;

    private volatile Process process;
    private volatile boolean killRequested;
    private volatile long startedAt;

    public GameLauncher(Instance instance, String nickname, Listener listener) {
        this.instance = instance;
        this.nickname = nickname;
        this.listener = listener == null ? new Listener() {
        } : listener;
    }

    public void start() {
        Thread thread = new Thread(this::run, "frimer-game-launcher");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        try {
            Dirs.ensureAll();
            Mojang.ensureLauncherProfile(Dirs.GAME_DIR);

            Consumer<String> log = listener::onLog;
            ProgressListener prog = listener::onProgress;

            listener.onStatus("Готовлю " + instance.loader + " " + instance.mcVersion + "...");
            Path vanillaJson = new VanillaInstaller(instance.mcVersion, log, prog).install();

            Path versionJson;
            switch (instance.loader) {
                case "Fabric" -> {
                    listener.onStatus("Ставлю Fabric...");
                    versionJson = FabricInstaller.install("fabric", instance.mcVersion,
                            instance.loaderVersion, log, prog);
                }
                case "Quilt" -> {
                    listener.onStatus("Ставлю Quilt...");
                    versionJson = FabricInstaller.install("quilt", instance.mcVersion,
                            instance.loaderVersion, log, prog);
                }
                case "Forge" -> {
                    listener.onStatus("Ставлю Forge...");
                    versionJson = ForgeInstaller.install("forge", instance.mcVersion,
                            instance.loaderVersion, log, prog);
                }
                case "NeoForge" -> {
                    listener.onStatus("Ставлю NeoForge...");
                    versionJson = ForgeInstaller.install("neoforge", instance.mcVersion,
                            instance.loaderVersion, log, prog);
                }
                default -> versionJson = vanillaJson;
            }

            listener.onStatus("Готовлю Java...");
            Path java = JavaProvisioner.ensureJava(instance.mcVersion, log,
                    (done, total) -> prog.update(done / 1024, total / 1024));

            instance.prepareFolders();
            List<String> cmd = LaunchBuilder.build(versionJson, instance.folder, java,
                    nickname, instance.ramMb);

            listener.onLog("Запускаю игру...");
            log.accept(String.join(" ", cmd).length() > 2000 ? "команда запуска собрана" : "cmd: " + Utils.shortText(String.join(" ", cmd), 300));
            listener.onStatus("Игра запущена");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(instance.folder.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            process = p;
            startedAt = System.currentTimeMillis();
            listener.onGameStarted(startedAt);

            List<String> tail = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String clean = Utils.stripAnsi(line);
                    tail.add(clean);
                    if (tail.size() > 400) {
                        tail.remove(0);
                    }
                    listener.onLog(clean);
                }
            }

            int code = p.waitFor();
            long seconds = Math.max(0, (System.currentTimeMillis() - startedAt) / 1000);
            if (!killRequested && code != 0) {
                CrashAnalyzer.Report report = CrashAnalyzer.analyze(code, tail, instance.folder,
                        startedAt, instance.loader, instance.mcVersion);
                listener.onCrash(report.summary(), report.body());
            }
            if (seconds > 5) {
                instance.addSession(seconds);
            }
            listener.onFinished(code);
        } catch (IOException e) {
            listener.onFailed("Не удалось запустить игру: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            listener.onFailed("Запуск прерван");
        } catch (RuntimeException e) {
            listener.onFailed(e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            process = null;
        }
    }

    /** Принудительно закрыть Minecraft вместе с дочерними процессами. */
    public boolean killGame() {
        Process p = process;
        if (p == null || !p.isAlive()) {
            return false;
        }
        killRequested = true;
        Procs.killTree(p);
        return true;
    }

    public boolean isRunning() {
        Process p = process;
        return p != null && p.isAlive();
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

    /** Проверить, что версия загрузчика уже стоит (для подписи в UI). */
    public static String resolveLoaderVersion(Instance instance) {
        if (instance.loaderVersion != null && !instance.loaderVersion.isBlank()) {
            return instance.loaderVersion;
        }
        List<String> installed = Loaders.installedLoaderVersions(instance.loader, instance.mcVersion);
        return installed.isEmpty() ? "" : installed.get(0);
    }

    public static Map<String, Object> info() {
        return Map.of(
                "game_dir", Dirs.GAME_DIR.toString(),
                "instances", (long) InstanceStore.loadAll().size(),
                "servers", (long) ServerInstance.loadAll().size());
    }

    /** Проверка свободного места под папку игры. */
    public static long freeSpaceGb() {
        try {
            return Files.getFileStore(Dirs.GAME_DIR).getUsableSpace() / (1024L * 1024 * 1024);
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }
}
