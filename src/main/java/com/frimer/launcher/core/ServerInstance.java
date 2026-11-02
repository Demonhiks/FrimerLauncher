package com.frimer.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Сервер: ядро (paper/vanilla/fabric/forge/neoforge), порт, playit, EULA. */
public final class ServerInstance {

    public final Path folder;
    public String name;
    public String mcVersion;
    public String core;
    public int ramMb;
    public int port;
    public String playitAddress;
    public boolean autoPlayit;
    public boolean eula;
    public String loaderVersion;

    public ServerInstance(Path folder, String name, String mcVersion, String core, int ramMb,
                          int port, String playitAddress, boolean autoPlayit, boolean eula,
                          String loaderVersion) {
        this.folder = folder;
        this.name = name;
        this.mcVersion = mcVersion;
        this.core = core;
        this.ramMb = ramMb;
        this.port = port;
        this.playitAddress = playitAddress == null ? "" : playitAddress;
        this.autoPlayit = autoPlayit;
        this.eula = eula;
        this.loaderVersion = loaderVersion == null ? "" : loaderVersion;
    }

    public String coreLabel() {
        return ServerStore.coreLabel(core);
    }

    public Path jarPath() {
        switch (core) {
            case "paper":
                return folder.resolve("paper.jar");
            case "fabric":
                return folder.resolve("fabric-server-launch.jar");
            default:
                return folder.resolve("server.jar");
        }
    }

    /** Аргументы java для запуска сервера (без самой java и -Xmx). */
    public List<String> launchArgs() {
        if ("forge".equals(core) || "neoforge".equals(core)) {
            return Loaders.forgeLikeLaunch(folder, core);
        }
        if (!Files.exists(jarPath())) {
            return null;
        }
        return List.of("-jar", jarPath().toString(), "--nogui");
    }

    public void save() {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new RuntimeException("Не могу создать папку сервера", e);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("mc_version", mcVersion);
        data.put("core", core);
        data.put("ram_mb", ramMb);
        data.put("port", port);
        data.put("playit_address", playitAddress);
        data.put("auto_playit", autoPlayit);
        data.put("eula", eula);
        data.put("loader_version", loaderVersion);
        Json.writeFileQuiet(folder.resolve("server.json"), data);
    }

    public static ServerInstance load(Path folder) {
        Path file = folder.resolve("server.json");
        if (!Files.exists(file)) {
            return null;
        }
        Map<String, Object> d = Json.readFileQuiet(file);
        if (d == null) {
            return null;
        }
        return new ServerInstance(folder,
                Json.str(d.get("name"), folder.getFileName().toString()),
                Json.str(d.get("mc_version"), ""),
                Json.str(d.get("core"), "paper"),
                Json.integer(d.get("ram_mb"), 4096),
                Json.integer(d.get("port"), 25565),
                Json.str(d.get("playit_address"), ""),
                Json.bool(d.get("auto_playit"), true),
                Json.bool(d.get("eula"), true),
                Json.str(d.get("loader_version"), ""));
    }

    public static List<ServerInstance> loadAll() {
        List<ServerInstance> out = new ArrayList<>();
        try {
            Files.createDirectories(Dirs.SERVERS_DIR);
        } catch (IOException e) {
            return out;
        }
        List<Path> children = LaunchBuilder.listDir(Dirs.SERVERS_DIR);
        children.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
        for (Path child : children) {
            if (Files.isDirectory(child)) {
                ServerInstance srv = load(child);
                if (srv != null) {
                    out.add(srv);
                }
            }
        }
        return out;
    }
}
