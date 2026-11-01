package dev.egor201.physizen.denizen;

import dev.egor201.physizen.denizen.save.PersistenceManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class DenizenServices {

    private static volatile File modelFolder;
    private static volatile File saveFolder;
    private static volatile PersistenceManager persistence;

    private DenizenServices() {
    }

    public static void init(JavaPlugin plugin, PersistenceManager manager) {
        modelFolder = new File(plugin.getDataFolder(), "models");
        modelFolder.mkdirs();
        saveFolder = new File(plugin.getDataFolder(), "saves");
        saveFolder.mkdirs();
        persistence = manager;
    }

    public static File modelFolder() {
        return modelFolder;
    }

    public static File saveFolder() {
        return saveFolder;
    }

    public static PersistenceManager persistence() {
        return persistence;
    }
}
