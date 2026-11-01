package dev.egor201.physizen;

import dev.egor201.physizen.core.engine.EngineConfig;
import org.bukkit.configuration.file.FileConfiguration;

final class PluginSettings {

    private PluginSettings() {
    }

    static EngineConfig engine(FileConfiguration config) {
        EngineConfig cfg = new EngineConfig();
        cfg.tickRate = config.getInt("tick_rate", 60);
        int configuredThreads = config.getInt("worker_threads", 0);
        cfg.workerThreads = configuredThreads > 0 ? configuredThreads : Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        cfg.tempAllocatorMegabytes = config.getInt("temp_allocator_megabytes", 16);
        cfg.maxBodies = config.getInt("max_bodies", 65536);
        cfg.maxBodyPairs = config.getInt("max_body_pairs", 65536);
        cfg.maxContactConstraints = config.getInt("max_contact_constraints", 20480);
        cfg.maxCatchupSteps = config.getInt("max_catchup_steps", 4);
        cfg.collisionSteps = config.getInt("collision_steps", 1);
        cfg.contactEventQueueLimit = config.getInt("contact_event_queue_limit", 4096);
        cfg.contactPersistIntervalSteps = Math.max(0, config.getInt("contact_persist_interval_steps", 6));
        cfg.sensorStayIntervalSteps = Math.max(0, config.getInt("sensor_stay_interval_steps", 0));
        cfg.fallOutY = config.getDouble("fall_out_y", -128.0);
        cfg.contactMinSpeed = config.getDouble("contact_min_speed", 0.5);
        cfg.nativeVariant = config.getString("native_variant", "auto");
        cfg.maxBodiesPerWorld = Math.max(1, config.getInt("limits.max_bodies_per_world", 20000));
        cfg.maxConstraintsPerWorld = Math.max(1, config.getInt("limits.max_constraints_per_world", 4096));
        cfg.maxCharactersPerWorld = Math.max(1, config.getInt("limits.max_characters_per_world", 256));
        cfg.maxCompoundChildren = Math.max(1, config.getInt("limits.max_compound_children", 256));
        cfg.maxInternalCompoundChildren = Math.max(cfg.maxCompoundChildren, config.getInt("limits.max_terrain_compound_children", 8192));
        return cfg;
    }
}
