package dev.egor201.physizen.bukkit.display;

import dev.egor201.physizen.bukkit.WorldLinks;
import dev.egor201.physizen.core.world.PhysBody;
import dev.egor201.physizen.core.world.PhysEngine;
import dev.egor201.physizen.core.world.PhysWorld;
import dev.egor201.physizen.core.model.Snapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DisplayBinder {

    public static class Binding {
        public Entity entity;
        public Vector3f offset = new Vector3f();
        public Vector3f scale = new Vector3f(1, 1, 1);
        public Quaternionf rotationOffset = new Quaternionf();
        public Quaternionf lastAppliedRot = new Quaternionf();
        public Quaternionf lastSentRot = new Quaternionf(Float.NaN, Float.NaN, Float.NaN, Float.NaN);
        public int interpolationDuration = 1;
        public int teleportDuration = 1;
        public int interpolationStart = 0;
        public boolean removeWithBody = true;

        public Binding(Entity entity) {
            this.entity = entity;
        }
    }

    private static class BodyKey {
        final String worldId;
        final String bodyName;

        BodyKey(String worldId, String bodyName) {
            this.worldId = worldId != null ? worldId : "default";
            this.bodyName = bodyName != null ? bodyName : "";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BodyKey)) return false;
            BodyKey other = (BodyKey) o;
            return worldId.equals(other.worldId) && bodyName.equals(other.bodyName);
        }

        @Override
        public int hashCode() {
            return 31 * worldId.hashCode() + bodyName.hashCode();
        }
    }

    private final WorldLinks links;
    private volatile BindingHooks hooks = BindingHooks.NONE;
    private final Map<BodyKey, List<Binding>> bindings = new ConcurrentHashMap<>();
    private final Map<BodyKey, Long> lastSteps = new ConcurrentHashMap<>();

    public DisplayBinder(WorldLinks links) {
        this.links = links;
    }

    public void setHooks(BindingHooks hooks) {
        this.hooks = hooks == null ? BindingHooks.NONE : hooks;
    }

    public void bind(String worldId, String bodyName, Binding binding) {
        BodyKey key = new BodyKey(worldId, bodyName);
        List<Binding> list = bindings.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        list.removeIf(existing -> existing.entity.getUniqueId().equals(binding.entity.getUniqueId()));
        list.add(binding);
        lastSteps.remove(key);
    }

    public interface Visitor {

        void visit(String worldId, String bodyName, Binding binding);
    }

    private void dropPending(String worldId, String bodyName) {
        hooks.dropPending(worldId, bodyName);
    }

    public int totalBindings() {
        int total = 0;
        for (List<Binding> list : bindings.values()) {
            total += list.size();
        }
        return total;
    }

    private void forget(Entity entity) {
        hooks.forget(entity);
    }

    public Binding bindingOf(String worldId, String bodyName, UUID entityUuid) {
        List<Binding> list = bindings.get(new BodyKey(worldId, bodyName));
        if (list == null) {
            return null;
        }
        for (Binding b : list) {
            if (b.entity.getUniqueId().equals(entityUuid)) {
                return b;
            }
        }
            return null;
    }

    public boolean isBound(String worldId, String bodyName, UUID entityUuid) {
        return bindingOf(worldId, bodyName, entityUuid) != null;
    }

    public void forEachBinding(Visitor visitor) {
        for (Map.Entry<BodyKey, List<Binding>> entry : bindings.entrySet()) {
            List<Binding> copy;
            copy = new ArrayList<>(entry.getValue());
                    for (Binding b : copy) {
                visitor.visit(entry.getKey().worldId, entry.getKey().bodyName, b);
            }
        }
    }

    public int release(String worldId, String bodyName, UUID entityUuid) {
        BodyKey key = new BodyKey(worldId, bodyName);
        List<Binding> list = bindings.get(key);
        if (list == null) {
            return 0;
        }
        int left;
        list.removeIf(b -> b.entity.getUniqueId().equals(entityUuid));
        left = list.size();
            if (left == 0) {
            bindings.remove(key);
            lastSteps.remove(key);
        }
        return left;
    }

    public void unbind(String worldId, String bodyName, UUID entityUuid) {
        BodyKey key = new BodyKey(worldId, bodyName);
        List<Binding> list = bindings.get(key);
        if (list != null) {
            for (Binding b : list) {
                if (b.entity.getUniqueId().equals(entityUuid)) {
                    forget(b.entity);
                }
            }
                    list.removeIf(b -> b.entity.getUniqueId().equals(entityUuid));
            if (list.isEmpty()) {
                bindings.remove(key);
                lastSteps.remove(key);
            }
        }
    }

    public void unbindAll(String worldId, String bodyName) {
        BodyKey key = new BodyKey(worldId, bodyName);
        List<Binding> removed = bindings.remove(key);
        lastSteps.remove(key);
        dropPending(worldId, bodyName);
        if (removed != null) {
            for (Binding b : removed) {
                if (b.removeWithBody && b.entity.isValid()) {
                    b.entity.remove();
                }
                else {
                    forget(b.entity);
                }
            }
        }
    }

    public void rescale(String worldId, String bodyName, double factor) {
        BodyKey key = new BodyKey(worldId, bodyName);
        List<Binding> list = bindings.get(key);
        if (list == null) {
            return;
        }
        for (Binding b : list) {
            b.scale.mul((float) factor);
            b.offset.mul((float) factor);
            b.lastSentRot.set(Float.NaN, Float.NaN, Float.NaN, Float.NaN);
        }
            lastSteps.remove(key);
    }

    public void setScale(String worldId, String bodyName, Vector3f scale) {
        BodyKey key = new BodyKey(worldId, bodyName);
        List<Binding> list = bindings.get(key);
        if (list == null) {
            return;
        }
        for (Binding b : list) {
            b.scale.set(scale);
            b.lastSentRot.set(Float.NaN, Float.NaN, Float.NaN, Float.NaN);
        }
            lastSteps.remove(key);
    }

    public List<Entity> getBoundDisplays(String worldId, String bodyName) {
        BodyKey key = new BodyKey(worldId, bodyName);
        List<Binding> list = bindings.get(key);
        if (list == null) return Collections.emptyList();
        List<Entity> result = new ArrayList<>();
        for (Binding b : list) {
            if (b.entity.isValid()) {
                result.add(b.entity);
            }
        }
            return result;
    }

    public List<Binding> getBindings(String worldId, String bodyName) {
        BodyKey key = new BodyKey(worldId, bodyName);
        List<Binding> list = bindings.get(key);
        if (list == null) return Collections.emptyList();
        return new ArrayList<>(list);
        }

    private static boolean moved(Entity entity, Location target) {
        Location current = entity.getLocation();
        if (current.getWorld() != target.getWorld()) {
            return true;
        }
        double dx = current.getX() - target.getX();
        double dy = current.getY() - target.getY();
        double dz = current.getZ() - target.getZ();
        return dx * dx + dy * dy + dz * dz > 1.0e-6;
    }

    public void applyPose(Binding b, Location bodyLoc, Quaternionf bodyRot) {
        if (b.entity instanceof Display) {
            Display d = (Display) b.entity;

            Quaternionf totalRot = new Quaternionf(bodyRot).mul(b.rotationOffset);

            float dot = b.lastAppliedRot.x * totalRot.x + b.lastAppliedRot.y * totalRot.y
                      + b.lastAppliedRot.z * totalRot.z + b.lastAppliedRot.w * totalRot.w;
            if (dot < 0) {
                totalRot.x = -totalRot.x;
                totalRot.y = -totalRot.y;
                totalRot.z = -totalRot.z;
                totalRot.w = -totalRot.w;
            }
            b.lastAppliedRot.set(totalRot);

            boolean rotChanged = Float.isNaN(b.lastSentRot.x)
                    || Math.abs(totalRot.x - b.lastSentRot.x) > 0.0005f
                    || Math.abs(totalRot.y - b.lastSentRot.y) > 0.0005f
                    || Math.abs(totalRot.z - b.lastSentRot.z) > 0.0005f
                    || Math.abs(totalRot.w - b.lastSentRot.w) > 0.0005f;

            if (rotChanged) {
                b.lastSentRot.set(totalRot);

                Vector3f translation = new Vector3f();
                if (d instanceof BlockDisplay) {
                    Vector3f halfScale = new Vector3f(b.scale).mul(0.5f);
                    Vector3f rotatedHalf = totalRot.transform(halfScale, new Vector3f());
                    Vector3f worldOffset = bodyRot.transform(new Vector3f(b.offset), new Vector3f());
                    translation.set(worldOffset).sub(rotatedHalf);
                } else {
                    translation.set(bodyRot.transform(new Vector3f(b.offset), new Vector3f()));
                }

                Transformation transform = new Transformation(translation, totalRot, b.scale, new Quaternionf());

                d.setInterpolationDelay(b.interpolationStart);
                d.setInterpolationDuration(b.interpolationDuration);
                d.setTransformation(transform);
            }

            d.setTeleportDuration(b.teleportDuration);
            if (moved(d, bodyLoc)) {
                d.teleport(bodyLoc);
            }
        } else {
            Vector3f worldOffset = bodyRot.transform(new Vector3f(b.offset));
            Location target = bodyLoc.clone().add(worldOffset.x, worldOffset.y, worldOffset.z);
            if (moved(b.entity, target)) {
                b.entity.teleport(target);
            }
        }
    }

    public void tick() {
        PhysEngine engine = PhysEngine.get();
        if (engine == null) return;

        Iterator<Map.Entry<BodyKey, List<Binding>>> it = bindings.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BodyKey, List<Binding>> entry = it.next();
            BodyKey key = entry.getKey();
            List<Binding> list = entry.getValue();

            PhysWorld world = engine.world(key.worldId);
            PhysBody body = world != null ? world.body(key.bodyName) : null;

            if (body == null || body.isRemoved()) {
                for (Binding b : list) {
                    if (b.removeWithBody && b.entity.isValid()) {
                        b.entity.remove();
                    }
                    else {
                        forget(b.entity);
                    }
                }
                            dropPending(key.worldId, key.bodyName);
                it.remove();
                lastSteps.remove(key);
                continue;
            }

            Snapshot snapshot = body.snapshot;
            Long prevStep = lastSteps.get(key);
            if (prevStep != null && prevStep == snapshot.step) {
                continue;
            }
            lastSteps.put(key, snapshot.step);

            World bukkitWorld = links.get(key.worldId);
            if (bukkitWorld == null) continue;

            Quaternionf bodyRot = new Quaternionf(snapshot.qx, snapshot.qy, snapshot.qz, snapshot.qw);
            Location bodyLoc = new Location(bukkitWorld, snapshot.px, snapshot.py, snapshot.pz);

            for (Binding b : list) {
                if (!b.entity.isValid()) {
                    list.remove(b);
                    continue;
                }

                applyPose(b, bodyLoc, bodyRot);
            }
                }
    }
}