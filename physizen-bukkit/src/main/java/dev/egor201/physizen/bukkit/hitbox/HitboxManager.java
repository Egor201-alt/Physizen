package dev.egor201.physizen.bukkit.hitbox;

import dev.egor201.physizen.bukkit.WorldLinks;
import dev.egor201.physizen.bukkit.display.DisplayBinder;
import dev.egor201.physizen.core.world.PhysBody;
import dev.egor201.physizen.core.world.PhysEngine;
import dev.egor201.physizen.core.world.PhysWorld;
import dev.egor201.physizen.core.model.Snapshot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class HitboxManager implements Listener {

    private static final double TELEPORT_DISTANCE_SQUARED = 36.0;
    private static final double STEP_SECONDS = 0.05;

    public static final class Hitbox {

        public final UUID playerId;
        public final String worldId;
        public final String bodyName;
        public final double[] offset;
        public final boolean followRotation;

        Hitbox(UUID playerId, String worldId, String bodyName, double[] offset, boolean followRotation) {
            this.playerId = playerId;
            this.worldId = worldId;
            this.bodyName = bodyName;
            this.offset = offset;
            this.followRotation = followRotation;
        }
    }

    private final WorldLinks links;
    private final DisplayBinder displays;
    private final List<Hitbox> hitboxes = new CopyOnWriteArrayList<>();

    public HitboxManager(WorldLinks links, DisplayBinder displays) {
        this.links = links;
        this.displays = displays;
    }

    public Hitbox register(UUID playerId, String worldId, String bodyName, double[] offset, boolean followRotation) {
        removeEntry(playerId, worldId, bodyName);
        Hitbox hitbox = new Hitbox(playerId, worldId, bodyName, offset, followRotation);
        hitboxes.add(hitbox);
        return hitbox;
    }

    public int detach(UUID playerId, String worldId, String bodyName) {
        int removed = 0;
        for (Hitbox hitbox : new ArrayList<>(hitboxes)) {
            if (!hitbox.playerId.equals(playerId)) {
                continue;
            }
            if (worldId != null && !hitbox.worldId.equals(worldId)) {
                continue;
            }
            if (bodyName != null && !hitbox.bodyName.equals(bodyName)) {
                continue;
            }
            destroy(hitbox);
            removed++;
        }
        return removed;
    }

    public UUID ownerOf(String worldId, String bodyName) {
        for (Hitbox hitbox : hitboxes) {
            if (hitbox.worldId.equals(worldId) && hitbox.bodyName.equals(bodyName)) {
                return hitbox.playerId;
            }
        }
        return null;
    }

    public List<String> namesOf(UUID playerId) {
        List<String> names = new ArrayList<>();
        for (Hitbox hitbox : hitboxes) {
            if (hitbox.playerId.equals(playerId)) {
                names.add(hitbox.worldId + "/" + hitbox.bodyName);
            }
        }
        return names;
    }

    public String defaultWorldFor(Player player) {
        return links.physWorldFor(player.getWorld());
    }

    public void tick() {
        if (hitboxes.isEmpty()) {
            return;
        }
        PhysEngine engine = PhysEngine.get();
        if (engine == null) {
            return;
        }
        for (Hitbox hitbox : hitboxes) {
            PhysWorld world = engine.world(hitbox.worldId);
            PhysBody body = world == null ? null : world.body(hitbox.bodyName);
            if (body == null || body.isRemoved()) {
                hitboxes.remove(hitbox);
                continue;
            }
            Player player = Bukkit.getPlayer(hitbox.playerId);
            if (player == null || !player.isOnline()) {
                destroy(hitbox);
                continue;
            }
            World attached = links.get(hitbox.worldId);
            if (attached == null || !attached.equals(player.getWorld())) {
                destroy(hitbox);
                continue;
            }
            update(hitbox, body, player);
        }
    }

    private void update(Hitbox hitbox, PhysBody body, Player player) {
        Location loc = player.getLocation();
        double yaw = Math.toRadians(-loc.getYaw());
        double ox = hitbox.offset[0];
        double oy = hitbox.offset[1];
        double oz = hitbox.offset[2];
        double[] rotation = null;
        if (hitbox.followRotation) {
            double cos = Math.cos(yaw);
            double sin = Math.sin(yaw);
            double rx = ox * cos + oz * sin;
            double rz = -ox * sin + oz * cos;
            ox = rx;
            oz = rz;
            rotation = new double[]{0, Math.sin(yaw / 2), 0, Math.cos(yaw / 2)};
        }
        double[] target = {loc.getX() + ox, loc.getY() + oy, loc.getZ() + oz};

        Snapshot s = body.snapshot;
        double dx = target[0] - s.px;
        double dy = target[1] - s.py;
        double dz = target[2] - s.pz;
        if (dx * dx + dy * dy + dz * dz > TELEPORT_DISTANCE_SQUARED) {
            body.teleport(target, rotation);
        }
        else {
            body.moveKinematic(target, rotation, STEP_SECONDS);
        }
    }

    private void destroy(Hitbox hitbox) {
        hitboxes.remove(hitbox);
        PhysEngine engine = PhysEngine.get();
        if (engine == null) {
            return;
        }
        PhysWorld world = engine.world(hitbox.worldId);
        if (world != null) {
            world.removeBody(hitbox.bodyName);
        }
        displays.unbindAll(hitbox.worldId, hitbox.bodyName);
    }

    private void removeEntry(UUID playerId, String worldId, String bodyName) {
        for (Hitbox hitbox : new ArrayList<>(hitboxes)) {
            if (hitbox.playerId.equals(playerId) && hitbox.worldId.equals(worldId) && hitbox.bodyName.equals(bodyName)) {
                destroy(hitbox);
            }
        }
    }

    public void clear() {
        for (Hitbox hitbox : new ArrayList<>(hitboxes)) {
            destroy(hitbox);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        detach(event.getPlayer().getUniqueId(), null, null);
    }
}
