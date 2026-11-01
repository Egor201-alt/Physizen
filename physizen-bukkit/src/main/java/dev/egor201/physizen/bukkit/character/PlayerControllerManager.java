package dev.egor201.physizen.bukkit.character;

import dev.egor201.physizen.bukkit.WorldLinks;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.model.CharacterDef;
import dev.egor201.physizen.core.world.PhysCharacter;
import dev.egor201.physizen.core.world.PhysEngine;
import dev.egor201.physizen.core.world.PhysWorld;
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

public final class PlayerControllerManager implements Listener {

    private static final double TICK_SECONDS = 0.05;
    private static final double TELEPORT_DISTANCE_SQUARED = 36.0;
    private static final double MAX_SPEED = 80.0;

    public static final class Controller {

        public final UUID playerId;
        public final String worldId;
        public final String name;
        double lastX;
        double lastY;
        double lastZ;
        boolean hasLast;

        Controller(UUID playerId, String worldId, String name) {
            this.playerId = playerId;
            this.worldId = worldId;
            this.name = name;
        }
    }

    private final WorldLinks links;
    private final List<Controller> controllers = new CopyOnWriteArrayList<>();

    public PlayerControllerManager(WorldLinks links) {
        this.links = links;
    }

    public Controller attach(Player player, String worldId, CharacterDef def) {
        PhysEngine engine = PhysEngine.get();
        PhysWorld world = engine == null ? null : engine.world(worldId);
        if (world == null) {
            throw new PhysException("Unknown physics world '" + worldId + "'.");
        }
        detach(player.getUniqueId(), worldId, def.name);
        Location loc = player.getLocation();
        def.position = new double[]{loc.getX(), loc.getY(), loc.getZ()};
        def.mode = CharacterDef.Mode.TRACK;
        def.managed = true;
        world.createCharacter(def);
        Controller controller = new Controller(player.getUniqueId(), worldId, def.name);
        controllers.add(controller);
        return controller;
    }

    public int detach(UUID playerId, String worldId, String name) {
        int removed = 0;
        for (Controller c : new ArrayList<>(controllers)) {
            if (!c.playerId.equals(playerId)) {
                continue;
            }
            if (worldId != null && !c.worldId.equals(worldId)) {
                continue;
            }
            if (name != null && !c.name.equals(name)) {
                continue;
            }
            destroy(c);
            removed++;
        }
        return removed;
    }

    public UUID ownerOf(String worldId, String name) {
        for (Controller c : controllers) {
            if (c.worldId.equals(worldId) && c.name.equals(name)) {
                return c.playerId;
            }
        }
        return null;
    }

    public List<String> namesOf(UUID playerId) {
        List<String> names = new ArrayList<>();
        for (Controller c : controllers) {
            if (c.playerId.equals(playerId)) {
                names.add(c.worldId + "/" + c.name);
            }
        }
        return names;
    }

    public void tick() {
        if (controllers.isEmpty()) {
            return;
        }
        PhysEngine engine = PhysEngine.get();
        if (engine == null) {
            return;
        }
        for (Controller c : controllers) {
            PhysWorld world = engine.world(c.worldId);
            PhysCharacter character = world == null ? null : world.character(c.name);
            if (character == null || character.isRemoved()) {
                controllers.remove(c);
                continue;
            }
            Player player = Bukkit.getPlayer(c.playerId);
            World attached = links.get(c.worldId);
            if (player == null || !player.isOnline() || attached == null || !attached.equals(player.getWorld())) {
                destroy(c);
                continue;
            }
            follow(c, character, player.getLocation());
        }
    }

    private void follow(Controller c, PhysCharacter character, Location loc) {
        double[] position = {loc.getX(), loc.getY(), loc.getZ()};
        double[] velocity = {0, 0, 0};
        if (c.hasLast) {
            double dx = position[0] - c.lastX;
            double dy = position[1] - c.lastY;
            double dz = position[2] - c.lastZ;
            if (dx * dx + dy * dy + dz * dz > TELEPORT_DISTANCE_SQUARED) {
                character.teleport(position, null);
            }
            else {
                velocity[0] = clamp(dx / TICK_SECONDS);
                velocity[1] = clamp(dy / TICK_SECONDS);
                velocity[2] = clamp(dz / TICK_SECONDS);
            }
        }
        c.lastX = position[0];
        c.lastY = position[1];
        c.lastZ = position[2];
        c.hasLast = true;
        character.track(position, velocity);
    }

    private static double clamp(double v) {
        return Math.max(-MAX_SPEED, Math.min(MAX_SPEED, v));
    }

    private void destroy(Controller c) {
        controllers.remove(c);
        PhysEngine engine = PhysEngine.get();
        PhysWorld world = engine == null ? null : engine.world(c.worldId);
        if (world != null) {
            world.removeCharacter(c.name);
        }
    }

    public void clear() {
        for (Controller c : new ArrayList<>(controllers)) {
            destroy(c);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        detach(event.getPlayer().getUniqueId(), null, null);
    }
}
