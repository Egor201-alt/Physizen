<div align="center">

# Physizen

**True real-time 3D rigid body physics for Denizen.**  
*Powered by Jolt Physics Engine via `jolt-jni`.*

[![Paper](https://img.shields.io/badge/Platform-Paper%201.21%2B-00a8ff?style=for-the-badge&logo=papermc&logoColor=white)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21%2B-ED8B00?style=for-the-badge)](https://adoptium.net)
[![Physics](https://img.shields.io/badge/Engine-Jolt%206.2.0-ff4757?style=for-the-badge)](https://github.com/jrouwe/JoltPhysics)
[![Addon](https://img.shields.io/badge/Addon%20For-DenizenM-2ed573?style=for-the-badge)](https://github.com/Energobro/DenizenM-Tjtoxshpilivili1)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg?style=for-the-badge)](LICENSE)

[Features](#key-features) • [Architecture](#architecture) • [Project Layout](#project-layout) • [Requirements](#requirements) • [Quick Start](#quick-start) • [Script Commands](#denizen-script-commands) • [Sensors & Contacts](#sensor-zones--contact-events) • [Characters](#character-controller) • [Configuration](#configuration) • [Building](#building)

</div>

---

## Overview

**Physizen** is a physics addon that bridges high-performance native rigid body simulation into Minecraft server environments through **Denizen** scripting.

Instead of running physics on the Minecraft main thread, Physizen isolates all mathematical calculations, contact solvers, and broadphase queries onto an independent daemon thread (**`Physizen-Sim`**). Visuals are mirrored in real time using vanilla **Display Entities** (`block_display`, `item_display`, `text_display`) with smooth client-side interpolation.

---

## Key Features

- **Zero-Lag Daemon Simulation**: Physics ticks run asynchronously at configurable rates (up to 480 Hz) on `Physizen-Sim` without affecting server MSPT.
- **Display Entity Binding**: Attach single or composite display entities directly to physical bodies with automatic quaternion rotation and position interpolation.
- **Automatic Terrain Colliders**: Bridges Minecraft blocks into static compound colliders with dynamic chunk rebuilds.
- **Full Constraint and Motor System**: Hinges, sliders, fixed joints, springs, and ragdoll cones with motorized target positions and velocities.
- **Environmental Force Fields**: Global and local force fields including radial gravity, wind currents, buoyancy, drag, and vortexes.
- **Advanced Spatial Queries**: Sub-tick raycasting, shape-casting (sweeps), and penetration overlap checks directly from scripts.
- **Kinematic Player Hitboxes**: Attach physical colliders to players to physically push props, trigger joints, and step on pressure sensors.
- **CharacterVirtual Controllers**: Jolt's kinematic character controller with slopes, stairs, floor sticking, moving platforms and body pushing. Use it for scripted NPCs or let it shadow a real player.
- **Sensor Zones with ENTER / EXIT**: Trigger volumes that report exactly when a body, a hitbox or a character enters or leaves, with optional `STAY` ticks and live `sensor_contents` tags.
- **Full Contact Lifecycle**: `collides` (added), `contact persisted` and `contact removed` events with the real manifold normal and penetration depth.
- **Binary Prefab Persistence**: Save individual bodies, mechanical contraptions, or entire worlds into compact `.phys` binary files or human-readable `.json`.

---

## Architecture

```
Main Server Thread (20 Hz)                         Physizen-Sim Daemon Thread
--------------------------                         --------------------------
Denizen Commands ───post(Runnable)───► [ Inbox ] ──► drainInbox(): create, force...
                                                     time accumulator -> fixed dt steps
                                                     fields -> character controllers -> Jolt update -> publish(snapshot)
Tags / DisplayBinder ◄──read immutable Snapshot────┘
Event Dispatch       ◄──poll(PhysEvent)───────────── contact tracker (contacts + sensor zones) & activation listeners
TerrainManager       ◄──blocks -> static compounds
```

---

## Project Layout

Physizen is a single repository split into four Gradle modules. Dependencies only point downwards.

```
physizen-plugin     Paper plugin entry point, plugin.yml, config.yml, shaded jar (the only module that ships)
   │
physizen-denizen    Denizen layer: commands, tags, script events, argument parsing, save/load prefabs
   │
physizen-bukkit     Bukkit layer: display binding, terrain colliders, player hitboxes, player controllers, status bars
   │
physizen-core       Pure physics on Jolt, no Bukkit and no Denizen
```

Inside `physizen-core`:

| Package | Contents |
|---|---|
| `core.model` | Plain data: `BodyDef`, `ShapeSpec`, `ConstraintDef`, `FieldDef`, `CharacterDef`, `Snapshot`, `QueryFilter` |
| `core.world` | `PhysEngine`, `PhysWorld` facade, `PhysBody`, `PhysCharacter`, contact tracker, field solver, queries, events |
| `core.shape` | `ShapeBuilder` (spec to Jolt shape), `ObjParser` |
| `core.constraint` | `ConstraintFactory`, `ConstraintMotors` |
| `core.validate` | `Guard` input validation |
| `core.engine` | `EngineConfig`, `NativeLoader` |
| `core.io` | `PhysBinary` (`.phys` format) |

---

## Requirements

- **Server**: Paper 1.21+ (or forks)
- **Runtime**: Java 21+
- **Dependency**: **[DenizenM](https://github.com/Energobro/DenizenM-Tjtoxshpilivili1)** fork *(requires `AsyncQueue` and `AbstractCommand#asyncSafe` support)*

---

## Quick Start

Creating a physical crate with visuals in Denizen:

```yaml
# Place this task inside your Denizen scripts folder (e.g. crate.dsc)
spawn_physics_crate:
    type: task
    script:
        # 1. Initialize world linked to Minecraft world
        - physworld create id:arena attach:<player.world.name> rate:60

        # 2. Spawn a visual block display
        - define loc <player.location.forward[3].above[2]>
        - spawn block_display[material=iron_block] <[loc]> save:disp

        # 3. Create physical body and bind visual entity
        - physbody create world:arena name:iron_crate shape:box:1,1,1 at:<[loc]> mass:50 friction:0.6
        - physbody bind world:arena name:iron_crate entities:<entry[disp].spawned_entity> scale:1,1,1
        
        # 4. Apply an impulse
        - physforce impulse world:arena body:iron_crate vector:0,5,3
```

---

## Denizen Script Commands

These script commands are executed inside your `.dsc` tasks and events:

| Command | Actions | Description |
|---|---|---|
| `physworld` | `create`, `remove`, `pause`, `step`, `gravity`, `explosion`, `save`, `load` | Lifecycle, time dilation, and global physics settings. |
| `physbody` | `create`, `remove`, `teleport`, `move`, `set`, `bind`, `activate`, `save` | Rigid body management, shapes, visuals, and mass properties. |
| `physforce` | `force`, `impulse`, `torque`, `velocity`, `point_impulse` | Momentum, velocities, and off-center impact forces. |
| `physconstraint` | `create`, `remove`, `motor`, `set` | Joints (hinge, slider, fixed, distance, cone) and motor drives. |
| `physfield` | `create`, `remove` | Ambient volume fields (radial, wind, buoyancy, vortex, drag). |
| `physhitbox` | `attach`, `detach` | Kinematic player colliders that interact with physical dynamic bodies. |
| `physcharacter` | `create`, `attach`, `detach`, `remove`, `move`, `jump`, `set`, `teleport` | CharacterVirtual controllers for NPCs and players. |

---

## Supported Collision Shapes

Physizen supports primitive, procedural, and complex mesh colliders:

```yaml
# Box: Extents in X, Y, Z
shape: box:1,2,1

# Sphere: Radius
shape: sphere:0.5

# Capsule & Cylinder: Radius and Total Height
shape: capsule:0.4,1.8
shape: cylinder:0.5,2.0

# Convex Hull / Mesh: Custom vertex data or OBJ models
shape: <map[type=hull;file=custom_prop.obj]>
shape: <map[type=mesh;file=arena_terrain.obj]>  # Static/Kinematic only

# Compound Shapes: Multiple offset shapes linked as one
shape: <map[type=compound;children=<list[<map[type=box;size=1,1,1]>]>]>
```

---

## Sensor Zones & Contact Events

A sensor is a body with `sensor:true`. It never collides physically, it only reports who is inside.
Static zones detect dynamic bodies, player hitboxes and characters. A kinematic zone that must also detect static bodies needs `detect_static:true`.

```yaml
zone_setup:
    type: task
    script:
        - physbody create world:arena name:lava_zone shape:box:6,1,6 at:<location[0,64,0]> type:static sensor:true

zone_events:
    type: world
    events:
        on physsensor enter:
        - if <context.is_player>:
          - narrate "You entered <context.sensor.name>" targets:<context.player>
        on physsensor exit:
        - narrate "<context.body.name> left <context.sensor.name>"
        on physsensor stay:
        - hurt 1 <context.player> if:<context.is_player>
```

| Event | Fires when |
|---|---|
| `on physsensor enter:` | a body starts overlapping a sensor zone |
| `on physsensor exit:` | the last overlap ends, also when the body or the zone is removed |
| `on physsensor stay:` | every `sensor_stay_interval_steps` steps while inside (disabled by default) |
| `on physbody collides:` | a contact appears and the relative speed reaches `contact_min_speed` |
| `on physbody contact persisted:` | every `contact_persist_interval_steps` steps while touching, for bodies with `notify_persist:true` |
| `on physbody contact removed:` | the last contact point of a tracked pair disappears |

Contact events carry the real Jolt manifold normal (`<context.normal>`, pointing from the other body to `<context.body>`) and the penetration depth (`<context.depth>`).

Tags: `<physbody[arena/lava_zone].sensor_contents>`, `<physbody[arena/crate].in_sensors>`, `<physbody[...].is_sensor>`.

---

## Character Controller

`physcharacter` wraps Jolt's `CharacterVirtual`: a capsule that walks over terrain and bodies, climbs stairs, sticks to slopes, rides moving platforms and pushes dynamic props.

```yaml
npc_demo:
    type: task
    script:
        # Driven character: you control its velocity
        - physcharacter create world:arena name:guard at:<location[0,65,0]> shape:capsule:0.3,1.8
        - physcharacter move world:arena name:guard velocity:0,0,3
        - physcharacter jump world:arena name:guard speed:7

player_controller:
    type: world
    events:
        on player joins:
        # Tracking character: shadows the real player, pushes props and triggers sensor zones
        - physcharacter attach
```

Tags: `<physcharacter[arena/guard].on_ground>`, `.ground_state`, `.ground_normal`, `.ground_body`, `.ground_velocity`, `.velocity`, `.location`.

With `inner_body:true` (default) a character also exists as a kinematic `physbody` with the same name, so sensors, queries and contact events see it.

---

## Spatial Queries & Raycasting

Execute native Jolt broadphase checks directly from Denizen tags:

```yaml
# Cast a ray 30 blocks forward, ignoring own hitbox
- define hit <physworld[arena].ray_cast[<player.eye_location>|<player.location.direction.vector>|30]>
- if <[hit].get[hit]>:
    - narrate "Surface normal: <[hit].get[normal]> at distance <[hit].get[distance]>"
    - if <[hit].contains[body]>:
        - physforce impulse world:arena body:<[hit].get[body].name> vector:0,8,0
```

---

## Configuration

```yaml
# Default physics tick rate per second for new worlds
tick_rate: 60

# Jolt worker threads (0 = auto-detect half of the CPU cores)
worker_threads: 0

# Native binaries variant: auto, baseline, or optimized (AVX2/FMA)
native_variant: auto

# Contact lifecycle events
contact_persist_interval_steps: 6
sensor_stay_interval_steps: 0

persistence:
  enabled: true
  auto_create_world: true
  unload_bodies: true
  refresh_seconds: 30

limits:
  max_bodies_per_world: 20000
  max_constraints_per_world: 4096
  max_characters_per_world: 256
```

---

## Building

```bash
gradle :physizen-plugin:shadowJar
```

The shaded jar is written to `physizen-plugin/build/libs/Physizen-<version>.jar`. Only that module needs to be deployed, the other three are bundled into it.

---

## License & Credits

- Developed by **[egor201](https://github.com/Egor201-alt)**.
- Simulation engine powered by **[Jolt Physics](https://github.com/jrouwe/JoltPhysics)** (Jorrit Rouwe).
- Java bindings via **[jolt-jni](https://github.com/aecsocket/jolt-jni)**.
- Licensed under the **[GNU General Public License v3.0 (GPLv3)](LICENSE)**. Anyone distributing modified versions or forks is strictly legally required to keep the full source code open under the same license and preserve all original author copyright notices.
