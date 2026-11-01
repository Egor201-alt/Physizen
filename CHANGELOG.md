# Changelog

## 0.1.1

Cleanup release after the modular refactor.

### Changed
- `PhysWorld` is now a thin facade. Constraint handling moved to `ConstraintService`, the simulation step and snapshot publishing to `StepLoop`, explosions and area lookups to `WorldSpatial`.
- `SaveManager` split into `SaveManager` (bodies, prefabs, display bindings), `ConstraintCodec` (constraints) and `PrefabFiles` (`.phys`/`.json` files and blobs).
- Dropped physics events are now counted. The plugin warns about them every 5 seconds and `<physworld.dropped_events>` shows the total.
- Single step requests (`physworld step`) are now atomic.
- Terrain tiles fail one by one: a broken tile is logged and retried after 30 seconds, the other tiles keep updating.
- Queries re-check that the world still exists after taking the lock.
- Player controllers smooth the velocity measured from position deltas, which removes jitter in pushing and ground detection.

### Added
- `PhysBodyTag`: `kinetic_energy`, `momentum`, `touching`, `is_touching`, `friction`, `restitution`, `gravity_factor`, `damping`, `notifies`, `half_extents`, `character`.
- `PhysWorldTag`: `characters`, `character`, `sensors`, `bodies_with_tag`, `active_body_count`, `pending_events`, `dropped_events`.
- `PhysCharacterTag`: `speed`, `horizontal_speed`, `is_falling`, `is_rising`, `is_tracking`, `mass`, `shape`, `sensors`.

## 0.1.0

- Split into the `core`, `bukkit`, `denizen` and `plugin` Gradle modules.
- Added the `physcharacter` command (CharacterVirtual controllers).
- Added `physsensor enter/exit/stay` and `physbody contact persisted/removed` events.
