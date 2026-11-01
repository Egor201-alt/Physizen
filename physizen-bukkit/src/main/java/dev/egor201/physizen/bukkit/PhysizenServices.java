package dev.egor201.physizen.bukkit;

import dev.egor201.physizen.bukkit.character.PlayerControllerManager;
import dev.egor201.physizen.bukkit.display.DisplayBinder;
import dev.egor201.physizen.bukkit.hitbox.HitboxManager;
import dev.egor201.physizen.bukkit.terrain.TerrainManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class PhysizenServices {

    private static volatile PhysizenServices instance;

    public final JavaPlugin plugin;
    public final WorldLinks worlds;
    public final DisplayBinder displays;
    public final TerrainManager terrain;
    public final HitboxManager hitboxes;
    public final PlayerControllerManager controllers;

    private PhysizenServices(JavaPlugin plugin) {
        this.plugin = plugin;
        this.worlds = new WorldLinks();
        this.displays = new DisplayBinder(worlds);
        this.terrain = new TerrainManager(worlds, plugin.getLogger());
        this.hitboxes = new HitboxManager(worlds, displays);
        this.controllers = new PlayerControllerManager(worlds);
    }

    public static PhysizenServices create(JavaPlugin plugin) {
        instance = new PhysizenServices(plugin);
        return instance;
    }

    public static PhysizenServices get() {
        PhysizenServices services = instance;
        if (services == null) {
            throw new IllegalStateException("Physizen services are not available.");
        }
        return services;
    }

    public static void release() {
        instance = null;
    }

    public void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(terrain, plugin);
        plugin.getServer().getPluginManager().registerEvents(hitboxes, plugin);
        plugin.getServer().getPluginManager().registerEvents(controllers, plugin);
    }

    public void tick() {
        hitboxes.tick();
        controllers.tick();
        displays.tick();
    }

    public void shutdown() {
        hitboxes.clear();
        controllers.clear();
    }
}
