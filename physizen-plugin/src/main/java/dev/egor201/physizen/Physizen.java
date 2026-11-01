package dev.egor201.physizen;

import dev.egor201.physizen.bukkit.PhysizenServices;
import dev.egor201.physizen.bukkit.status.StatusBarManager;
import dev.egor201.physizen.core.world.PhysEngine;
import dev.egor201.physizen.core.world.PhysEvent;
import dev.egor201.physizen.core.world.PhysWorld;
import dev.egor201.physizen.denizen.DenizenBridge;
import dev.egor201.physizen.denizen.DenizenServices;
import dev.egor201.physizen.denizen.event.PhysScriptEvents;
import dev.egor201.physizen.denizen.save.PersistenceManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Physizen extends JavaPlugin {

    private PhysizenServices services;
    private PersistenceManager persistence;
    private StatusBarManager statusBars;
    private long currentTick = 0;

    @Override
    public void onEnable() {
        if (!DenizenBridge.isDenizenM()) {
            getLogger().severe("Physizen requires DenizenM fork! Standard Denizen is not supported. Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        try {
            PhysEngine.start(PluginSettings.engine(getConfig()), getDataFolder(), getLogger());
        }
        catch (Exception e) {
            getLogger().severe("Failed to initialize Jolt Physics engine: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        services = PhysizenServices.create(this);
        services.registerListeners();
        persistence = new PersistenceManager(this, getConfig());
        services.displays.setHooks(persistence);
        DenizenServices.init(this, persistence);
        statusBars = new StatusBarManager(this, services.displays, persistence::pendingCount);
        getServer().getPluginManager().registerEvents(statusBars, this);
        if (persistence.enabled()) {
            getServer().getPluginManager().registerEvents(persistence, this);
        }

        DenizenBridge.registerAll();
        Bukkit.getScheduler().runTask(this, persistence::scanLoaded);

        AdminCommand admin = new AdminCommand(statusBars);
        if (getCommand("physizen") != null) {
            getCommand("physizen").setExecutor(admin);
            getCommand("physizen").setTabCompleter(admin);
        }

        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        getLogger().info("Physizen v" + getDescription().getVersion() + " by egor201 successfully enabled!");
    }

    private void tick() {
        currentTick++;
        services.tick();
        persistence.tick(currentTick);
        services.terrain.tick(currentTick);
        PhysEngine engine = PhysEngine.get();
        if (engine == null) {
            return;
        }
        for (PhysWorld world : engine.worlds()) {
            PhysEvent event;
            while ((event = world.pollEvent()) != null) {
                try {
                    PhysScriptEvents.fire(world.id, event);
                }
                catch (Throwable t) {
                    getLogger().warning("Physizen event " + event.kind + " failed: " + t);
                }
            }
        }
    }

    @Override
    public void onDisable() {
        if (statusBars != null) {
            statusBars.clear();
        }
        if (persistence != null && PhysEngine.get() != null) {
            persistence.refreshAll();
        }
        if (services != null) {
            services.shutdown();
        }
        if (PhysEngine.get() != null) {
            PhysEngine.get().shutdown();
        }
        PhysizenServices.release();
        getLogger().info("Physizen disabled.");
    }
}
