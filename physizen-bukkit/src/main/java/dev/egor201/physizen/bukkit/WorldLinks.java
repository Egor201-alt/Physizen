package dev.egor201.physizen.bukkit;

import dev.egor201.physizen.core.world.PhysEngine;
import dev.egor201.physizen.core.world.PhysWorld;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldLinks {

    private final Map<String, World> attachments = new ConcurrentHashMap<>();

    public void attach(String physWorldId, World world) {
        attachments.put(physWorldId, world);
    }

    public void detach(String physWorldId) {
        attachments.remove(physWorldId);
    }

    public World get(String physWorldId) {
        World attached = attachments.get(physWorldId);
        return attached != null ? attached : Bukkit.getWorld(physWorldId);
    }

    public String physWorldFor(World bukkitWorld) {
        PhysEngine engine = PhysEngine.get();
        if (engine == null || bukkitWorld == null) {
            return null;
        }
        for (PhysWorld world : engine.worlds()) {
            World attached = attachments.get(world.id);
            if (attached != null && attached.equals(bukkitWorld)) {
                return world.id;
            }
        }
        return null;
    }
}
