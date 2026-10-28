package dev.egor201.physizen.bukkit;

import dev.egor201.physizen.Physizen;
import dev.egor201.physizen.core.BodyDef;
import dev.egor201.physizen.core.MotionKind;
import dev.egor201.physizen.core.PhysBody;
import dev.egor201.physizen.core.PhysEngine;
import dev.egor201.physizen.core.PhysWorld;
import dev.egor201.physizen.core.ShapeSpec;
import dev.egor201.physizen.core.Snapshot;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TerrainManager implements Listener {

    private static final int TILE_SIZE = 16;
    private static final int TILE_SHIFT = 4;

    private static class TileKey {
        final String worldId;
        final int tx;
        final int ty;
        final int tz;

        TileKey(String worldId, int tx, int ty, int tz) {
            this.worldId = worldId;
            this.tx = tx;
            this.ty = ty;
            this.tz = tz;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TileKey)) return false;
            TileKey other = (TileKey) o;
            return tx == other.tx && ty == other.ty && tz == other.tz && worldId.equals(other.worldId);
        }

        @Override
        public int hashCode() {
            int result = worldId.hashCode();
            result = 31 * result + tx;
            result = 31 * result + ty;
            result = 31 * result + tz;
            return result;
        }
    }

    private static class TileData {
        final TileKey key;
        final String bodyName;
        long lastSeenStep;
        boolean dirty = false;
        PhysBody body;

        TileData(TileKey key, String bodyName) {
            this.key = key;
            this.bodyName = bodyName;
        }
    }

    private final Map<TileKey, TileData> activeTiles = new ConcurrentHashMap<>();
    private final Map<BlockData, List<BoundingBox>> shapeCache = new ConcurrentHashMap<>();
    private final Set<TileKey> dirtyTiles = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void invalidateBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        World w = loc.getWorld();
        int tx = loc.getBlockX() >> TILE_SHIFT;
        int ty = loc.getBlockY() >> TILE_SHIFT;
        int tz = loc.getBlockZ() >> TILE_SHIFT;

        PhysEngine engine = PhysEngine.get();
        if (engine == null) return;

        for (PhysWorld pw : engine.worlds()) {
            World attached = Physizen.getInstance().getWorld(pw.id);
            if (attached == null || !attached.getName().equals(w.getName())) continue;

            TileKey key = new TileKey(pw.id, tx, ty, tz);
            TileData tile = activeTiles.get(key);
            if (tile != null) {
                tile.dirty = true;
                dirtyTiles.add(key);
            }

            int baseX = tx << TILE_SHIFT;
            int baseY = ty << TILE_SHIFT;
            int baseZ = tz << TILE_SHIFT;
            double[] min = {baseX - 1, baseY - 1, baseZ - 1};
            double[] max = {baseX + TILE_SIZE + 1, baseY + TILE_SIZE + 3, baseZ + TILE_SIZE + 1};
            for (PhysBody pb : pw.inBox(min, max)) {
                if (pb.def.type != MotionKind.STATIC && !pb.def.internal) {
                    pb.activate();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        invalidateBlock(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        invalidateBlock(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosion(BlockExplodeEvent event) {
        for (Block b : event.blockList()) {
            invalidateBlock(b.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        for (Block b : event.blockList()) {
            invalidateBlock(b.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        invalidateBlock(event.getBlock().getLocation());
    }

    public void tick(long currentStep) {
        PhysEngine engine = PhysEngine.get();
        if (engine == null) return;

        try {
            boolean hasActiveBodies = false;
            Set<TileKey> neededTiles = new HashSet<>();

            for (PhysWorld world : engine.worlds()) {
                World bukkitWorld = Physizen.getInstance().getWorld(world.id);
                if (bukkitWorld == null) continue;

                int worldMinTy = bukkitWorld.getMinHeight() >> TILE_SHIFT;
                int worldMaxTy = (bukkitWorld.getMaxHeight() - 1) >> TILE_SHIFT;

                for (PhysBody body : world.bodies()) {
                    if (body.def.type == MotionKind.STATIC) continue;
                    Snapshot s = body.snapshot;

                    if (!s.active) continue;
                    hasActiveBodies = true;

                    int minTx = ((int) Math.floor(s.minX)) >> TILE_SHIFT;
                    int maxTx = ((int) Math.floor(s.maxX)) >> TILE_SHIFT;

                    int rawMinTy = ((int) Math.floor(s.minY - 0.5)) >> TILE_SHIFT;
                    int rawMaxTy = ((int) Math.floor(s.maxY + 0.5)) >> TILE_SHIFT;

                    int minTy = Math.max(worldMinTy, Math.min(worldMaxTy, rawMinTy - 1));
                    int maxTy = Math.max(worldMinTy, Math.min(worldMaxTy, rawMaxTy + 1));

                    int minTz = ((int) Math.floor(s.minZ)) >> TILE_SHIFT;
                    int maxTz = ((int) Math.floor(s.maxZ)) >> TILE_SHIFT;

                    for (int x = minTx - 1; x <= maxTx + 1; x++) {
                        for (int y = minTy; y <= maxTy; y++) {
                            for (int z = minTz - 1; z <= maxTz + 1; z++) {
                                TileKey key = new TileKey(world.id, x, y, z);
                                neededTiles.add(key);
                                TileData existing = activeTiles.get(key);
                                if (existing != null) {
                                    existing.lastSeenStep = currentStep;
                                }
                            }
                        }
                    }
                }
            }

            neededTiles.addAll(dirtyTiles);
            dirtyTiles.clear();

            if (neededTiles.isEmpty() && !hasActiveBodies && (currentStep % 100 != 0)) {
                return;
            }

            int snapshotBudget = 8;
            for (TileKey key : neededTiles) {
                TileData tile = activeTiles.get(key);
                World bukkitWorld = Physizen.getInstance().getWorld(key.worldId);
                if (bukkitWorld == null) continue;

                if (tile != null && !tile.dirty) {
                    continue;
                }

                int chunkX = key.tx;
                int chunkZ = key.tz;

                if (snapshotBudget <= 0 && tile == null) continue;
                if (!bukkitWorld.isChunkLoaded(chunkX, chunkZ)) continue;

                ChunkSnapshot chunk = bukkitWorld.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, false, false);
                snapshotBudget--;

                if (tile == null) {
                    String bodyName = "__terrain_" + key.tx + "_" + key.ty + "_" + key.tz;
                    tile = new TileData(key, bodyName);
                    tile.lastSeenStep = currentStep;
                    rebuildTile(key, tile, chunk, bukkitWorld);
                    if (tile.body != null) {
                        activeTiles.put(key, tile);
                    }
                } else if (tile.dirty) {
                    tile.dirty = false;
                    tile.lastSeenStep = currentStep;
                    rebuildTile(key, tile, chunk, bukkitWorld);
                }
            }

            if (currentStep % 200 == 0) {
                Iterator<Map.Entry<TileKey, TileData>> it = activeTiles.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<TileKey, TileData> entry = it.next();
                    TileData tile = entry.getValue();
                    if (currentStep - tile.lastSeenStep > 400) {
                        PhysWorld pw = engine.world(tile.key.worldId);
                        if (pw != null) {
                            int baseX = tile.key.tx << TILE_SHIFT;
                            int baseY = tile.key.ty << TILE_SHIFT;
                            int baseZ = tile.key.tz << TILE_SHIFT;
                            double[] min = {baseX, baseY, baseZ};
                            double[] max = {baseX + TILE_SIZE, baseY + TILE_SIZE, baseZ + TILE_SIZE};

                            if (!pw.inBox(min, max).isEmpty()) {
                                tile.lastSeenStep = currentStep;
                                continue;
                            }
                            pw.removeBody(tile.bodyName);
                        }
                        it.remove();
                    }
                }
            }
        } catch (Throwable t) {
            Physizen.getInstance().getLogger().warning("Terrain tick warning: " + t.getMessage());
        }
    }

    private void rebuildTile(TileKey key, TileData tile, ChunkSnapshot chunk, World bukkitWorld) {
        PhysWorld pw = PhysEngine.get().world(key.worldId);
        if (pw == null) return;

        if (tile.body != null) {
            pw.removeBody(tile.bodyName);
            tile.body = null;
        }

        int baseX = key.tx << TILE_SHIFT;
        int baseY = key.ty << TILE_SHIFT;
        int baseZ = key.tz << TILE_SHIFT;

        int minH = bukkitWorld.getMinHeight();
        int maxH = bukkitWorld.getMaxHeight();

        ShapeSpec compound = new ShapeSpec();
        compound.kind = ShapeSpec.Kind.COMPOUND;

        boolean[][][] fullCubes = new boolean[TILE_SIZE][TILE_SIZE][TILE_SIZE];
        boolean[][][] visited = new boolean[TILE_SIZE][TILE_SIZE][TILE_SIZE];
        boolean hasAnyBlocks = false;

        for (int y = 0; y < TILE_SIZE; y++) {
            int actualY = baseY + y;
            if (actualY < minH || actualY >= maxH) continue;

            for (int x = 0; x < TILE_SIZE; x++) {
                for (int z = 0; z < TILE_SIZE; z++) {
                    BlockData data = chunk.getBlockData(x, actualY, z);
                    if (data.getMaterial().isAir()) continue;

                    List<BoundingBox> boxes = getBlockBoxes(data, bukkitWorld, baseX + x, actualY, baseZ + z);
                    if (boxes.isEmpty()) continue;

                    hasAnyBlocks = true;
                    if (boxes.size() == 1 && isFullCube(boxes.get(0))) {
                        fullCubes[x][y][z] = true;
                    } else {
                        for (BoundingBox b : boxes) {
                            addBoxToCompound(compound,
                                    x + (b.getMinX() + b.getMaxX()) * 0.5,
                                    y + (b.getMinY() + b.getMaxY()) * 0.5,
                                    z + (b.getMinZ() + b.getMaxZ()) * 0.5,
                                    b.getWidthX(), b.getHeight(), b.getWidthZ());
                        }
                    }
                }
            }
        }

        if (!hasAnyBlocks) return;

        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                for (int z = 0; z < TILE_SIZE; z++) {
                    if (!fullCubes[x][y][z] || visited[x][y][z]) continue;

                    int lenZ = 1;
                    while (z + lenZ < TILE_SIZE && fullCubes[x][y][z + lenZ] && !visited[x][y][z + lenZ]) {
                        lenZ++;
                    }

                    int lenX = 1;
                    boolean canExpandX = true;
                    while (x + lenX < TILE_SIZE && canExpandX) {
                        for (int k = 0; k < lenZ; k++) {
                            if (!fullCubes[x + lenX][y][z + k] || visited[x + lenX][y][z + k]) {
                                canExpandX = false;
                                break;
                            }
                        }
                        if (canExpandX) lenX++;
                    }

                    int lenY = 1;
                    boolean canExpandY = true;
                    while (y + lenY < TILE_SIZE && canExpandY) {
                        for (int ix = 0; ix < lenX; ix++) {
                            for (int iz = 0; iz < lenZ; iz++) {
                                if (!fullCubes[x + ix][y + lenY][z + iz] || visited[x + ix][y + lenY][z + iz]) {
                                    canExpandY = false;
                                    break;
                                }
                            }
                            if (!canExpandY) break;
                        }
                        if (canExpandY) lenY++;
                    }

                    for (int dy = 0; dy < lenY; dy++) {
                        for (int dx = 0; dx < lenX; dx++) {
                            for (int dz = 0; dz < lenZ; dz++) {
                                visited[x + dx][y + dy][z + dz] = true;
                            }
                        }
                    }

                    double cx = x + lenX * 0.5;
                    double cy = y + lenY * 0.5;
                    double cz = z + lenZ * 0.5;
                    addBoxToCompound(compound, cx, cy, cz, lenX, lenY, lenZ);
                }
            }
        }

        if (compound.children.isEmpty()) return;

        BodyDef def = new BodyDef();
        def.name = tile.bodyName;
        def.type = MotionKind.STATIC;
        def.shape = compound;
        def.position = new double[]{baseX, baseY, baseZ};
        def.internal = true;
        def.friction = 0.6;
        def.restitution = 0.05;

        tile.body = pw.createBody(def);

        double[] min = {baseX, baseY, baseZ};
        double[] max = {baseX + TILE_SIZE, baseY + TILE_SIZE, baseZ + TILE_SIZE};
        for (PhysBody pb : pw.inBox(min, max)) {
            if (pb.def.type != MotionKind.STATIC && !pb.def.internal) {
                pb.activate();
            }
        }
    }

    private void addBoxToCompound(ShapeSpec compound, double ox, double oy, double oz, double sx, double sy, double sz) {
        ShapeSpec.Child child = new ShapeSpec.Child();
        child.shape = ShapeSpec.box(sx, sy, sz);
        child.offset = new double[]{ox, oy, oz};
        compound.children.add(child);
    }

    private boolean isFullCube(BoundingBox b) {
        return Math.abs(b.getWidthX() - 1.0) < 0.001
                && Math.abs(b.getHeight() - 1.0) < 0.001
                && Math.abs(b.getWidthZ() - 1.0) < 0.001;
    }

    private List<BoundingBox> getBlockBoxes(BlockData data, World world, int x, int y, int z) {
        List<BoundingBox> cached = shapeCache.get(data);
        if (cached != null) return cached;

        Location loc = new Location(world, x, y, z);
        VoxelShape shape = data.getCollisionShape(loc);
        Collection<BoundingBox> raw = shape.getBoundingBoxes();

        List<BoundingBox> normalized = new ArrayList<>();
        for (BoundingBox b : raw) {
            normalized.add(new BoundingBox(
                    b.getMinX() - x, b.getMinY() - y, b.getMinZ() - z,
                    b.getMaxX() - x, b.getMaxY() - y, b.getMaxZ() - z
            ));
        }

        if (shapeCache.size() < 4096) {
            shapeCache.put(data, normalized);
        }
        return normalized;
    }
}
