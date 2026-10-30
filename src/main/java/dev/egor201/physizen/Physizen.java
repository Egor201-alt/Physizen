package dev.egor201.physizen;

import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.objects.ObjectFetcher;
import dev.egor201.physizen.bukkit.HitboxManager;
import dev.egor201.physizen.bukkit.TerrainManager;
import dev.egor201.physizen.core.EngineConfig;
import dev.egor201.physizen.core.PhysBody;
import dev.egor201.physizen.core.PhysEngine;
import dev.egor201.physizen.core.PhysEvent;
import dev.egor201.physizen.core.PhysWorld;
import dev.egor201.physizen.denizen.PhysBodyCommand;
import dev.egor201.physizen.denizen.PhysBodyTag;
import dev.egor201.physizen.denizen.PhysConstraintCommand;
import dev.egor201.physizen.denizen.PhysFieldCommand;
import dev.egor201.physizen.denizen.PhysForceCommand;
import dev.egor201.physizen.denizen.PhysHitboxCommand;
import dev.egor201.physizen.denizen.PhysScriptEvents;
import dev.egor201.physizen.denizen.PhysWorldCommand;
import dev.egor201.physizen.denizen.PhysWorldTag;
import dev.egor201.physizen.denizen.Util;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Physizen extends JavaPlugin implements CommandExecutor, org.bukkit.command.TabCompleter {

    private static Physizen instance;
    private DisplayBinder displayBinder;
    private TerrainManager terrainManager;
    private HitboxManager hitboxManager;
    private PersistenceManager persistence;
    private StatusBarManager statusBars;
    private final Map<String, World> worldAttachments = new ConcurrentHashMap<>();
    private long currentTick = 0;

    public static Physizen getInstance() {
        return instance;
    }

    public DisplayBinder getDisplayBinder() {
        return displayBinder;
    }

    public TerrainManager getTerrainManager() {
        return terrainManager;
    }

    public HitboxManager getHitboxManager() {
        return hitboxManager;
    }

    public PersistenceManager getPersistence() {
        return persistence;
    }

    public void attachWorld(String worldId, World world) {
        worldAttachments.put(worldId, world);
    }

    public World getWorld(String worldId) {
        World w = worldAttachments.get(worldId);
        return w != null ? w : Bukkit.getWorld(worldId);
    }

    private boolean checkDenizenM() {
        try {
            Class.forName("com.denizenscript.denizencore.scripts.queues.core.AsyncQueue");
            Class<?> absCmd = Class.forName("com.denizenscript.denizencore.scripts.commands.AbstractCommand");
            Field f = absCmd.getDeclaredField("asyncSafe");
            return f != null;
        }
        catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void onEnable() {
        instance = this;

        if (!checkDenizenM()) {
            getLogger().severe("Physizen requires DenizenM fork! Standard Denizen is not supported. Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        EngineConfig cfg = new EngineConfig();
        cfg.tickRate = getConfig().getInt("tick_rate", 60);
        int configuredThreads = getConfig().getInt("worker_threads", 0);
        cfg.workerThreads = configuredThreads > 0 ? configuredThreads : Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        cfg.tempAllocatorMegabytes = getConfig().getInt("temp_allocator_megabytes", 16);
        cfg.maxBodies = getConfig().getInt("max_bodies", 65536);
        cfg.maxBodyPairs = getConfig().getInt("max_body_pairs", 65536);
        cfg.maxContactConstraints = getConfig().getInt("max_contact_constraints", 20480);
        cfg.maxCatchupSteps = getConfig().getInt("max_catchup_steps", 4);
        cfg.collisionSteps = getConfig().getInt("collision_steps", 1);
        cfg.contactEventQueueLimit = getConfig().getInt("contact_event_queue_limit", 4096);
        cfg.fallOutY = getConfig().getDouble("fall_out_y", -128.0);
        cfg.contactMinSpeed = getConfig().getDouble("contact_min_speed", 0.5);
        cfg.nativeVariant = getConfig().getString("native_variant", "auto");

        try {
            PhysEngine.start(cfg, getDataFolder(), getLogger());
        }
        catch (Exception e) {
            getLogger().severe("Failed to initialize Jolt Physics engine: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        displayBinder = new DisplayBinder();
        terrainManager = new TerrainManager();
        getServer().getPluginManager().registerEvents(terrainManager, this);
        hitboxManager = new HitboxManager();
        getServer().getPluginManager().registerEvents(hitboxManager, this);
        Util.init(this);
        persistence = new PersistenceManager(this, getConfig());
        statusBars = new StatusBarManager(this);
        getServer().getPluginManager().registerEvents(statusBars, this);
        if (persistence.enabled()) {
            getServer().getPluginManager().registerEvents(persistence, this);
        }

        ObjectFetcher.registerWithObjectFetcher(PhysWorldTag.class, PhysWorldTag.tagProcessor);
        ObjectFetcher.registerWithObjectFetcher(PhysBodyTag.class, PhysBodyTag.tagProcessor);
        PhysWorldTag.register();
        PhysBodyTag.register();
        
        com.denizenscript.denizencore.tags.TagManager.registerTagHandler(PhysWorldTag.class, "physworld", (attribute) -> {
            if (!attribute.hasParam()) {
                return null;
            }
            return PhysWorldTag.valueOf(attribute.getParam(), attribute.context);
        });

        com.denizenscript.denizencore.tags.TagManager.registerTagHandler(PhysBodyTag.class, "physbody", (attribute) -> {
            if (!attribute.hasParam()) {
                return null;
            }
            return PhysBodyTag.valueOf(attribute.getParam(), attribute.context);
        });

        DenizenCore.commandRegistry.registerCommand(PhysWorldCommand.class);
        DenizenCore.commandRegistry.registerCommand(PhysBodyCommand.class);
        DenizenCore.commandRegistry.registerCommand(PhysForceCommand.class);
        DenizenCore.commandRegistry.registerCommand(PhysConstraintCommand.class);
        DenizenCore.commandRegistry.registerCommand(PhysFieldCommand.class);
        DenizenCore.commandRegistry.registerCommand(PhysHitboxCommand.class);

        PhysScriptEvents.registerAll();

        Bukkit.getScheduler().runTask(this, persistence::scanLoaded);

        if (getCommand("physizen") != null) {
            getCommand("physizen").setExecutor(this);
            getCommand("physizen").setTabCompleter(this);
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            currentTick++;
            hitboxManager.tick();
            displayBinder.tick();
            persistence.tick(currentTick);
            terrainManager.tick(currentTick);

            PhysEngine engine = PhysEngine.get();
            if (engine != null) {
                for (PhysWorld world : engine.worlds()) {
                    PhysEvent event;
                    while ((event = world.pollEvent()) != null) {
                        PhysScriptEvents.fireEvent(world.id, event);
                    }
                }
            }
        }, 1L, 1L);

        getLogger().info("Physizen v" + getDescription().getVersion() + " by egor201 successfully enabled!");
    }

    @Override
    public void onDisable() {
        if (statusBars != null) {
            statusBars.clear();
        }
        if (persistence != null && PhysEngine.get() != null) {
            persistence.refreshAll();
        }
        if (hitboxManager != null) {
            hitboxManager.clear();
        }
        if (PhysEngine.get() != null) {
            PhysEngine.get().shutdown();
        }
        getLogger().info("Physizen disabled.");
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("physizen.admin")) {
            return java.util.Collections.singletonList("status");
        }
        return java.util.Collections.emptyList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("physizen.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        PhysEngine engine = PhysEngine.get();
        if (engine == null) {
            sender.sendMessage("§cPhysEngine is not running.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage("§cOnly players can use the status bar.");
                return true;
            }
            boolean shown = statusBars.toggle((org.bukkit.entity.Player) sender);
            sender.sendMessage(shown ? "§bPhysizen §7status bar §aenabled§7. Run §f/physizen status §7again to hide it." : "§bPhysizen §7status bar §chidden§7.");
            return true;
        }

        sender.sendMessage("§b--- Physizen Physics Status ---");
        sender.sendMessage("§7Worlds: §f" + engine.worlds().size() + " §7| Native: §a" + engine.nativeSource());
        for (PhysWorld w : engine.worlds()) {
            int active = w.activeBodyCount();
            sender.sendMessage(" §8- §e" + w.id + " §7(Bodies: §f" + w.bodyCount() + "§7, Active: §a" + active + "§7, Paused: §f" + w.paused() + "§7)");
        }
        return true;
    }
}