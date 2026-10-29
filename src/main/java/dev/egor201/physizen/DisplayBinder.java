package dev.egor201.physizen;

import dev.egor201.physizen.core.PhysBody;
import dev.egor201.physizen.core.PhysEngine;
import dev.egor201.physizen.core.PhysWorld;
import dev.egor201.physizen.core.Snapshot;
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

    private final Map<BodyKey, List<Binding>> bindings = new ConcurrentHashMap<>();
    private final Map<BodyKey, Long> lastSteps = new ConcurrentHashMap<>();

    public void bind(String worldId, String bodyName, Binding binding) {
        BodyKey key = new BodyKey(worldId, bodyName);
        List<Binding> list = bindings.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));
        list.removeIf(existing -> existing.entity.getUniqueId().equals(binding.entity.getUniqueId()));
        list.add(binding);
        lastSteps.remove(key);
    }

    public void unbind(String worldId, String bodyName, UUID entityUuid) {
        BodyKey key = new BodyKey(worldId, bodyName);
        List<Binding> list = bindings.get(key);
        if (list != null) {
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
        if (removed != null) {
            for (Binding b : removed) {
                if (b.removeWithBody && b.entity.isValid()) {
                    b.entity.remove();
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
        synchronized (list) {
            for (Binding b : list) {
                b.scale.mul((float) factor);
                b.offset.mul((float) factor);
                b.lastSentRot.set(Float.NaN, Float.NaN, Float.NaN, Float.NaN);
            }
        }
        lastSteps.remove(key);
    }

    public void setScale(String worldId, String bodyName, Vector3f scale) {
        BodyKey key = new BodyKey(worldId, bodyName);
        List<Binding> list = bindings.get(key);
        if (list == null) {
            return;
        }
        synchronized (list) {
            for (Binding b : list) {
                b.scale.set(scale);
                b.lastSentRot.set(Float.NaN, Float.NaN, Float.NaN, Float.NaN);
            }
        }
        lastSteps.remove(key);
    }

    public List<Entity> getBoundDisplays(String worldId, String bodyName) {
        BodyKey key = new BodyKey(worldId, bodyName);
        List<Binding> list = bindings.get(key);
        if (list == null) return Collections.emptyList();
        List<Entity> result = new ArrayList<>();
        synchronized (list) {
            for (Binding b : list) {
                if (b.entity.isValid()) {
                    result.add(b.entity);
                }
            }
        }
        return result;
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
                synchronized (list) {
                    for (Binding b : list) {
                        if (b.removeWithBody && b.entity.isValid()) {
                            b.entity.remove();
                        }
                    }
                }
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

            World bukkitWorld = Physizen.getInstance().getWorld(key.worldId);
            if (bukkitWorld == null) continue;

            Quaternionf bodyRot = new Quaternionf(snapshot.qx, snapshot.qy, snapshot.qz, snapshot.qw);
            Location bodyLoc = new Location(bukkitWorld, snapshot.px, snapshot.py, snapshot.pz);

            synchronized (list) {
                Iterator<Binding> bIt = list.iterator();
                while (bIt.hasNext()) {
                    Binding b = bIt.next();
                    if (!b.entity.isValid()) {
                        bIt.remove();
                        continue;
                    }

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

                            Transformation transform = new Transformation(
                                    translation,
                                    totalRot,
                                    b.scale,
                                    new Quaternionf()
                            );

                            d.setInterpolationDelay(b.interpolationStart);
                            d.setInterpolationDuration(b.interpolationDuration);
                            d.setTransformation(transform);
                        }

                        d.setTeleportDuration(b.teleportDuration);
                        d.teleport(bodyLoc);
                    } else {
                        Vector3f worldOffset = bodyRot.transform(new Vector3f(b.offset));
                        Location target = bodyLoc.clone().add(worldOffset.x, worldOffset.y, worldOffset.z);
                        b.entity.teleport(target);
                    }
                }
            }
        }
    }
}
