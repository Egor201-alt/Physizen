package dev.egor201.physizen;

import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.denizenscript.denizencore.objects.core.QuaternionTag;
import com.denizenscript.denizencore.utilities.CoreUtilities;
import dev.egor201.physizen.core.BodyDef;
import dev.egor201.physizen.core.PhysBody;
import dev.egor201.physizen.core.PhysEngine;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.PhysWorld;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Quaternionf;
import dev.egor201.physizen.denizen.Util;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PersistenceManager implements Listener {

    private final Physizen plugin;
    private final NamespacedKey key;
    private final boolean enabled;
    private final boolean autoCreateWorld;
    private final boolean unloadBodies;
    private final long refreshTicks;
    private final List<Entity> pending = new ArrayList<>();

    private static final class Ref {

        final String worldId;
        final String bodyName;
        final DisplayBinder.Binding binding;

        Ref(String worldId, String bodyName, DisplayBinder.Binding binding) {
            this.worldId = worldId;
            this.bodyName = bodyName;
            this.binding = binding;
        }
    }

    public PersistenceManager(Physizen plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "body_data");
        this.enabled = config.getBoolean("persistence.enabled", true);
        this.autoCreateWorld = config.getBoolean("persistence.auto_create_world", true);
        this.unloadBodies = config.getBoolean("persistence.unload_bodies", true);
        this.refreshTicks = Math.max(0, config.getInt("persistence.refresh_seconds", 30)) * 20L;
    }

    public boolean enabled() {
        return enabled;
    }

    public void persist(Entity entity, String worldId, String bodyName) {
        if (!enabled || entity == null || !entity.isValid()) {
            return;
        }
        PhysEngine engine = PhysEngine.get();
        if (engine == null) {
            return;
        }
        PhysWorld world = engine.world(worldId);
        PhysBody body = world == null ? null : world.body(bodyName);
        if (body == null || body.isRemoved()) {
            return;
        }
        DisplayBinder.Binding binding = plugin.getDisplayBinder().bindingOf(worldId, bodyName, entity.getUniqueId());
        if (binding == null) {
            return;
        }
        try {
            MapTag full = SaveManager.serializeBody(worldId, body);
            MapTag blob = new MapTag();
            for (String k : full.keys()) {
                if (!k.equals("displays")) {
                    blob.putObject(k, full.getObject(k));
                }
            }
            blob.putObject("world", new ElementTag(worldId));
            blob.putObject("offset", new ElementTag(binding.offset.x + "," + binding.offset.y + "," + binding.offset.z));
            blob.putObject("scale", new ElementTag(binding.scale.x + "," + binding.scale.y + "," + binding.scale.z));
            blob.putObject("rotation_offset", new QuaternionTag(binding.rotationOffset.x, binding.rotationOffset.y, binding.rotationOffset.z, binding.rotationOffset.w));
            blob.putObject("interpolation_duration", new ElementTag(binding.interpolationDuration));
            blob.putObject("teleport_duration", new ElementTag(binding.teleportDuration));
            blob.putObject("interpolation_start", new ElementTag(binding.interpolationStart));
            blob.putObject("remove_with_body", new ElementTag(binding.removeWithBody));
            entity.getPersistentDataContainer().set(key, PersistentDataType.BYTE_ARRAY, SaveManager.toBlob(blob));
        }
        catch (Exception e) {
            plugin.getLogger().warning("Failed to store physics data: " + e.getMessage());
        }
    }

    public void forget(Entity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentDataContainer().remove(key);
    }

    public void refreshAll() {
        if (!enabled) {
            return;
        }
        plugin.getDisplayBinder().forEachBinding((worldId, bodyName, binding) -> persist(binding.entity, worldId, bodyName));
    }

    public void tick(long currentTick) {
        if (!enabled) {
            return;
        }
        if (currentTick % 20 == 0 && !pending.isEmpty()) {
            for (Entity e : new ArrayList<>(pending)) {
                if (!e.isValid() || restore(e)) {
                    pending.remove(e);
                }
            }
        }
        if (refreshTicks > 0 && currentTick % refreshTicks == 0) {
            refreshAll();
        }
    }

    public void scanLoaded() {
        if (!enabled) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getPersistentDataContainer().has(key, PersistentDataType.BYTE_ARRAY) && !restore(e) && !pending.contains(e)) {
                    pending.add(e);
                }
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (e.getPersistentDataContainer().has(key, PersistentDataType.BYTE_ARRAY) && !restore(e) && !pending.contains(e)) {
                pending.add(e);
            }
        }
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        Set<UUID> ids = new HashSet<>();
        for (Entity e : event.getEntities()) {
            pending.remove(e);
            ids.add(e.getUniqueId());
        }
        if (ids.isEmpty()) {
            return;
        }
        List<Ref> found = new ArrayList<>();
        plugin.getDisplayBinder().forEachBinding((worldId, bodyName, binding) -> {
            if (binding.entity != null && ids.contains(binding.entity.getUniqueId())) {
                found.add(new Ref(worldId, bodyName, binding));
            }
        });
        if (found.isEmpty()) {
            return;
        }
        PhysEngine engine = PhysEngine.get();
        for (Ref ref : found) {
            persist(ref.binding.entity, ref.worldId, ref.bodyName);
            int left = plugin.getDisplayBinder().release(ref.worldId, ref.bodyName, ref.binding.entity.getUniqueId());
            if (left == 0 && unloadBodies && engine != null) {
                PhysWorld world = engine.world(ref.worldId);
                if (world != null) {
                    world.removeBody(ref.bodyName);
                }
            }
        }
    }

    private boolean restore(Entity entity) {
        byte[] data = entity.getPersistentDataContainer().get(key, PersistentDataType.BYTE_ARRAY);
        if (data == null) {
            return true;
        }
        PhysEngine engine = PhysEngine.get();
        if (engine == null) {
            return true;
        }
        MapTag blob;
        String worldId;
        String bodyName;
        try {
            blob = SaveManager.fromBlob(data);
            worldId = blob.getElement("world").asString();
            bodyName = blob.getElement("name").asString();
        }
        catch (Exception e) {
            plugin.getLogger().warning("Corrupted physics data on entity " + entity.getUniqueId() + ": " + e.getMessage());
            return true;
        }
        DisplayBinder binder = plugin.getDisplayBinder();
        if (binder.isBound(worldId, bodyName, entity.getUniqueId())) {
            return true;
        }
        PhysWorld world = engine.world(worldId);
        if (world == null) {
            if (!autoCreateWorld) {
                return false;
            }
            try {
                world = engine.createWorld(worldId, engine.config().tickRate);
            }
            catch (PhysException e) {
                world = engine.world(worldId);
            }
            if (world == null) {
                return false;
            }
        }
        if (plugin.getWorld(worldId) == null) {
            plugin.attachWorld(worldId, entity.getWorld());
        }
        DisplayBinder.Binding binding = new DisplayBinder.Binding(entity);
        double[] off = Util.vec(blob.getObject("offset"), CoreUtilities.noDebugContext);
        double[] scl = Util.vec(blob.getObject("scale"), CoreUtilities.noDebugContext);
        double[] rot = Util.rotation(blob.getObject("rotation_offset"), CoreUtilities.noDebugContext);
        binding.offset = new Vector3f((float) off[0], (float) off[1], (float) off[2]);
        binding.scale = new Vector3f((float) scl[0], (float) scl[1], (float) scl[2]);
        binding.rotationOffset = new Quaternionf((float) rot[0], (float) rot[1], (float) rot[2], (float) rot[3]);
        binding.interpolationDuration = (int) Util.num(blob.getObject("interpolation_duration"), 3);
        binding.teleportDuration = (int) Util.num(blob.getObject("teleport_duration"), 3);
        binding.interpolationStart = (int) Util.num(blob.getObject("interpolation_start"), 0);
        binding.removeWithBody = Util.bool(blob.getObject("remove_with_body"), true);
        if (world.body(bodyName) == null) {
            BodyDef def = SaveManager.defFromMap(blob, bodyName);
            Location loc = entity.getLocation();
            if (entity instanceof Display) {
                Quaternionf total = new Quaternionf(((Display) entity).getTransformation().getLeftRotation());
                Quaternionf bodyRot = total.mul(new Quaternionf(binding.rotationOffset).invert()).normalize();
                def.rotation = new double[]{bodyRot.x, bodyRot.y, bodyRot.z, bodyRot.w};
                def.position = new double[]{loc.getX(), loc.getY(), loc.getZ()};
            }
            else {
                def.rotation = new double[]{0, 0, 0, 1};
                def.position = new double[]{loc.getX() - binding.offset.x, loc.getY() - binding.offset.y, loc.getZ() - binding.offset.z};
            }
            try {
                world.createBody(def);
            }
            catch (PhysException e) {
                plugin.getLogger().warning("Could not restore body " + def.name + ": " + e.getMessage());
                return true;
            }
        }
        binder.bind(worldId, bodyName, binding);
        return true;
    }
}