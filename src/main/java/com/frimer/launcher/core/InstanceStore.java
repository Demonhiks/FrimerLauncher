package com.frimer.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Загрузка/создание инстансов в ~/.frimerlauncher/instances. */
public final class InstanceStore {

    private InstanceStore() {
    }

    public static List<Instance> loadAll() {
        List<Instance> out = new ArrayList<>();
        try {
            Files.createDirectories(Dirs.INSTANCES_DIR);
        } catch (IOException e) {
            return out;
        }
        List<Path> children = LaunchBuilder.listDir(Dirs.INSTANCES_DIR);
        children.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
        for (Path child : children) {
            if (!Files.isDirectory(child)) {
                continue;
            }
            Instance inst = Instance.load(child);
            if (inst != null) {
                out.add(inst);
            }
        }
        return out;
    }

    /** Свободная папка для нового инстанса с учётом транслитерации. */
    public static Path uniqueFolder(String name) {
        String base = Utils.slugify(name);
        Path folder = Dirs.INSTANCES_DIR.resolve(base);
        int n = 2;
        while (Files.exists(folder)) {
            folder = Dirs.INSTANCES_DIR.resolve(base + "-" + n);
            n++;
        }
        return folder;
    }

    public static Instance create(String name, String loader, String mcVersion, int ramMb,
                                  String loaderVersion) {
        Path folder = uniqueFolder(name);
        Instance inst = new Instance(folder, name, loader, mcVersion, ramMb, loaderVersion);
        inst.prepareFolders();
        inst.save();
        return inst;
    }
}
