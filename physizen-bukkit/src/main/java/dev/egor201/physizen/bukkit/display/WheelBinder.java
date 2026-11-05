package dev.egor201.physizen.bukkit.display;

import dev.egor201.physizen.bukkit.WorldLinks;
import dev.egor201.physizen.core.model.VehicleTelemetry;
import dev.egor201.physizen.core.world.PhysEngine;
import dev.egor201.physizen.core.world.PhysWorld;
import org.bukkit.Location;
import org.bukkit.World;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class WheelBinder {

    private final WorldLinks links;
    private final DisplayBinder displays;
    private final Map<String, List<DisplayBinder.Binding>> wheels = new ConcurrentHashMap<>();

    public WheelBinder(WorldLinks links, DisplayBinder displays) {
        this.links = links;
        this.displays = displays;
    }

    private static String key(String worldId, String vehicle) {
        return (worldId == null ? "default" : worldId) + "\u0000" + vehicle;
    }

    public void bind(String worldId, String vehicle, List<DisplayBinder.Binding> perWheel) {
        unbind(worldId, vehicle);
        wheels.put(key(worldId, vehicle), new CopyOnWriteArrayList<>(perWheel));
    }

    public void unbind(String worldId, String vehicle) {
        List<DisplayBinder.Binding> old = wheels.remove(key(worldId, vehicle));
        if (old != null) {
            for (DisplayBinder.Binding b : old) {
                if (b != null && b.removeWithBody && b.entity.isValid()) {
                    b.entity.remove();
                }
            }
                }
    }

    public int count() {
        return wheels.size();
    }

    public void clear() {
        for (String k : new ArrayList<>(wheels.keySet())) {
            String[] parts = k.split("\u0000", 2);
            unbind(parts[0], parts[1]);
        }
    }

    public void tick() {
        PhysEngine engine = PhysEngine.get();
        if (engine == null) {
            return;
        }
        Iterator<Map.Entry<String, List<DisplayBinder.Binding>>> it = wheels.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, List<DisplayBinder.Binding>> entry = it.next();
            String[] parts = entry.getKey().split("\u0000", 2);
            PhysWorld world = engine.world(parts[0]);
            VehicleTelemetry t = world != null ? world.vehicleTelemetry(parts[1]) : null;
            List<DisplayBinder.Binding> list = entry.getValue();
            if (t == null) {
                for (DisplayBinder.Binding b : list) {
                    if (b != null && b.removeWithBody && b.entity.isValid()) {
                        b.entity.remove();
                    }
                }
                            it.remove();
                continue;
            }
            World bukkitWorld = links.get(parts[0]);
            if (bukkitWorld == null) {
                continue;
            }
            int n = Math.min(list.size(), t.wheelCount);
            for (int i = 0; i < n; i++) {
                DisplayBinder.Binding b = list.get(i);
                if (b == null || !b.entity.isValid()) {
                    continue;
                }
                double[] p = t.wheelPos[i];
                double[] r = t.wheelRot[i];
                Location loc = new Location(bukkitWorld, p[0], p[1], p[2]);
                displays.applyPose(b, loc, new Quaternionf((float) r[0], (float) r[1], (float) r[2], (float) r[3]));
            }
                }
    }
}
