package com.frimer.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Создание серверов, EULA и server.properties. */
public final class ServerStore {

    private ServerStore() {
    }

    public static final List<String> CORES = List.of("paper", "vanilla", "fabric", "forge", "neoforge");

    private static final Map<String, String> CORE_NAMES = new LinkedHashMap<>();

    static {
        CORE_NAMES.put("paper", "Paper");
        CORE_NAMES.put("vanilla", "Vanilla");
        CORE_NAMES.put("fabric", "Fabric");
        CORE_NAMES.put("forge", "Forge");
        CORE_NAMES.put("neoforge", "NeoForge");
    }

    public static String coreLabel(String core) {
        String label = CORE_NAMES.get(core);
        if (label != null) {
            return label;
        }
        return core == null || core.isEmpty()
                ? "?"
                : core.substring(0, 1).toUpperCase() + core.substring(1);
    }

    public static String coreHint(String core) {
        switch (core) {
            case "paper":
                return "Быстрое ядро с плагинами (Bukkit/Spigot). Модов нет.";
            case "vanilla":
                return "Чистый сервер от Mojang: без плагинов и модов.";
            case "fabric":
                return "Настоящие моды Fabric (папка mods). Первый запуск докачает файлы Minecraft.";
            case "forge":
                return "Настоящие моды Forge (папка mods). Установка занимает несколько минут.";
            case "neoforge":
                return "Настоящие моды NeoForge (папка mods). Установка занимает несколько минут.";
            default:
                return "";
        }
    }

    public static Path uniqueFolder(String name) {
        String base = Utils.slugify(name);
        Path folder = Dirs.SERVERS_DIR.resolve(base);
        int n = 2;
        while (Files.exists(folder)) {
            folder = Dirs.SERVERS_DIR.resolve(base + "-" + n);
            n++;
        }
        return folder;
    }

    public static ServerInstance create(String name, String mcVersion, String core, int ramMb, int port) {
        Path folder = uniqueFolder(name);
        ServerInstance srv = new ServerInstance(folder, name, mcVersion, core, ramMb, port,
                "", true, true, "");
        try {
            Files.createDirectories(folder);
            Files.createDirectories(folder.resolve("mods"));
            Files.createDirectories(folder.resolve("plugins"));
        } catch (IOException e) {
            throw new RuntimeException("Не могу создать папку сервера", e);
        }
        srv.save();
        return srv;
    }

    /** Создать/обновить eula.txt и server.properties . */
    public static void ensureEulaAndProperties(ServerInstance srv, java.util.function.Consumer<String> log) {
        java.util.function.Consumer<String> logger = log == null ? msg -> {
        } : log;
        try {
            Files.createDirectories(srv.folder);
        } catch (IOException e) {
            throw new RuntimeException("Не могу создать папку сервера", e);
        }

        if (srv.eula) {
            String eula = "#By changing the setting below to TRUE you are indicating your agreement"
                    + " to our EULA (https://aka.ms/MinecraftEULA).\neula=true\n";
            try {
                Files.writeString(srv.folder.resolve("eula.txt"), eula);
            } catch (IOException e) {
                logger.accept("Не могу записать eula.txt: " + e.getMessage());
            }
        }

        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("server-port", Integer.toString(srv.port));
        defaults.put("online-mode", "false");
        defaults.put("motd", "Frimer - " + srv.name);
        defaults.put("max-players", "20");
        defaults.put("enable-command-block", "true");
        defaults.put("spawn-protection", "0");
        defaults.put("gamemode", "survival");
        defaults.put("difficulty", "easy");
        defaults.put("pvp", "true");
        defaults.put("allow-flight", "true");
        if (srv.rconPassword == null || srv.rconPassword.isBlank()) {
            srv.rconPassword = Long.toHexString(java.util.concurrent.ThreadLocalRandom.current()
                    .nextLong()).substring(0, 8);
            srv.save();
        }
        defaults.put("enable-rcon", "true");
        defaults.put("rcon.port", Integer.toString(srv.rconPort));
        defaults.put("rcon.password", srv.rconPassword);
        defaults.put("broadcast-rcon-to-ops", "true");

        Path props = srv.folder.resolve("server.properties");
        try {
            if (!Files.exists(props)) {
                StringBuilder sb = new StringBuilder("#Minecraft server properties\n#Frimer setup\n");
                for (Map.Entry<String, String> e : defaults.entrySet()) {
                    sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
                }
                Files.writeString(props, sb.toString());
                logger.accept("Создан server.properties (port=" + srv.port + ", online-mode=false)");
            } else {
                List<String> lines = new java.util.ArrayList<>(Files.readAllLines(props));
                Map<String, Integer> index = new LinkedHashMap<>();
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.contains("=") && !line.stripLeading().startsWith("#")) {
                        index.put(line.split("=", 2)[0].trim(), i);
                    }
                }
                for (Map.Entry<String, String> e : defaults.entrySet()) {
                    String text = e.getKey() + "=" + e.getValue();
                    Integer at = index.get(e.getKey());
                    if (at != null) {
                        lines.set(at, text);
                    } else {
                        lines.add(text);
                    }
                }
                Files.writeString(props, String.join("\n", lines) + "\n");
                logger.accept("Обновил server.properties (port, online-mode)");
            }
        } catch (IOException e) {
            logger.accept("Не могу записать server.properties: " + e.getMessage());
        }
    }
}
