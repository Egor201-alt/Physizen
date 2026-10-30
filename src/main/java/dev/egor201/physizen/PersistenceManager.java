package dev.egor201.physizen;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    private final Map<String, List<MapTag>> pendingConstraints = new HashMap<>();

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

    public int pendingCount() {
        int n = pending.size();
        for (List<MapTag> list : pendingConstraints.values()) {
            n += list.size();
        }
        return n;
    }

    private static String nameOf(MapTag map, String field) {
        return map.containsKey(field) ? map.getElement(field).asString() : null;
    }

    private static boolean touches(MapTag c, String bodyName) {
        return bodyName.equals(nameOf(c, "body_a")) || bodyName.equals(nameOf(c, "body_b"));
    }

    public void dropPending(String worldId, String bodyName) {
        List<MapTag> list = pendingConstraints.get(worldId);
        if (list != null) {
            list.removeIf(c -> touches(c, bodyName));
            if (list.isEmpty()) {
                pendingConstraints.remove(worldId);
            }
        }
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
        if (plugin.getDisplayBinder().getBindings(worldId, bodyName).stream().noneMatch(b -> b.entity != null && b.entity.getUniqueId().equals(entity.getUniqueId()))) {
            return;
        }
        try {
            MapTag root = new MapTag();
            root.putObject("world", new ElementTag(worldId));
            ListTag bodies = new ListTag();
            bodies.addObject(SaveManager.serializeBody(worldId, body, entity));
            root.putObject("bodies", bodies);
            ListTag constraints = new ListTag();
            for (MapTag c : SaveManager.constraintsOf(world, bodyName)) {
                constraints.addObject(c);
            }
            List<MapTag> waiting = pendingConstraints.get(worldId);
            if (waiting != null) {
                for (MapTag c : waiting) {
                    if (touches(c, bodyName)) {
                        constraints.addObject(c);
                    }
                }
            }
            root.putObject("constraints", constraints);
            entity.getPersistentDataContainer().set(key, PersistentDataType.BYTE_ARRAY, SaveManager.toBlob(root));
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
        if (currentTick % 20 == 0) {
            if (!pending.isEmpty()) {
                for (Entity e : new ArrayList<>(pending)) {
                    if (!e.isValid() || restore(e)) {
                        pending.remove(e);
                    }
                }
            }
            if (!pendingConstraints.isEmpty()) {
                for (String worldId : new ArrayList<>(pendingConstraints.keySet())) {
                    resolveConstraints(worldId);
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

    private boolean bodiesExist(PhysWorld world, MapTag c) {
        String a = nameOf(c, "body_a");
        String b = nameOf(c, "body_b");
        return (a == null || world.body(a) != null) && (b == null || world.body(b) != null);
    }

    private void applyConstraint(PhysWorld world, MapTag c) {
        try {
            world.addConstraint(SaveManager.constraintFromBlob(world, c));
        }
        catch (PhysException e) {
            plugin.getLogger().warning("Could not restore constraint " + nameOf(c, "name") + ": " + e.getMessage());
        }
    }

    private void offerConstraint(PhysWorld world, MapTag c) {
        String name = nameOf(c, "name");
        if (name == null || world.constraintDef(name) != null) {
            return;
        }
        if (bodiesExist(world, c)) {
            applyConstraint(world, c);
            return;
        }
        List<MapTag> list = pendingConstraints.computeIfAbsent(world.id, k -> new ArrayList<>());
        for (MapTag other : list) {
            if (name.equals(nameOf(other, "name"))) {
                return;
            }
        }
        list.add(c);
    }

    private void resolveConstraints(String worldId) {
        List<MapTag> list = pendingConstraints.get(worldId);
        PhysEngine engine = PhysEngine.get();
        PhysWorld world = engine == null ? null : engine.world(worldId);
        if (list == null || world == null) {
            return;
        }
        Iterator<MapTag> it = list.iterator();
        while (it.hasNext()) {
            MapTag c = it.next();
            String name = nameOf(c, "name");
            if (name != null && world.constraintDef(name) != null) {
                it.remove();
            }
            else if (bodiesExist(world, c)) {
                it.remove();
                applyConstraint(world, c);
            }
        }
        if (list.isEmpty()) {
            pendingConstraints.remove(worldId);
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
        MapTag root;
        MapTag bMap;
        MapTag dMap;
        String worldId;
        String bodyName;
        try {
            root = SaveManager.fromBlob(data);
            worldId = root.getElement("world").asString();
            bMap = root.getObject("bodies").asType(ListTag.class, CoreUtilities.noDebugContext).objectForms.get(0).asType(MapTag.class, CoreUtilities.noDebugContext);
            bodyName = bMap.getElement("name").asString();
            dMap = bMap.getObject("displays").asType(ListTag.class, CoreUtilities.noDebugContext).objectForms.get(0).asType(MapTag.class, CoreUtilities.noDebugContext);
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
        DisplayBinder.Binding binding = SaveManager.bindingFromMap(dMap, entity);
        if (world.body(bodyName) == null) {
            BodyDef def = SaveManager.defFromMap(bMap, bodyName);
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
        if (root.containsKey("constraints")) {
            for (ObjectTag obj : root.getObject("constraints").asType(ListTag.class, CoreUtilities.noDebugContext).objectForms) {
                MapTag c = obj.asType(MapTag.class, CoreUtilities.noDebugContext);
                if (c != null) {
                    offerConstraint(world, c);
                }
            }
        }
        resolveConstraints(worldId);
        return true;
    }
}