package dev.egor201.physizen.denizen.event;

import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import dev.egor201.physizen.bukkit.PhysizenServices;
import dev.egor201.physizen.core.world.PhysEvent;
import dev.egor201.physizen.denizen.tag.PhysBodyTag;
import dev.egor201.physizen.denizen.tag.PhysWorldTag;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class PhysScriptEvents {

    private static final Map<PhysEvent.Kind, BaseEvent> EVENTS = new EnumMap<>(PhysEvent.Kind.class);

    private PhysScriptEvents() {
    }

    public abstract static class BaseEvent extends ScriptEvent {

        public PhysBodyTag body;
        public PhysBodyTag sensor;
        public ObjectTag other;
        public LocationTag point;
        public LocationTag normal;
        public ElementTag speed;
        public ElementTag depth;
        public PlayerTag player;
        public ElementTag owner;

        @Override
        public boolean matches(ScriptPath path) {
            return super.matches(path);
        }

        @Override
        public ObjectTag getContext(String name) {
            switch (name) {
                case "body":
                    return body;
                case "world":
                    return new PhysWorldTag((sensor != null ? sensor : body).worldId);
                case "sensor":
                    return sensor;
                case "other":
                    return other;
                case "point":
                    return point;
                case "normal":
                    return normal;
                case "speed":
                    return speed;
                case "depth":
                    return depth;
                case "ragdoll":
                case "vehicle":
                case "constraint":
                    return owner;
                case "player":
                    return player;
                case "is_player":
                    return new ElementTag(player != null);
                default:
                    return super.getContext(name);
            }
        }
    }

    // <--[event]
    // @Events
    // physbody collides
    //
    // @Group Physizen
    //
    // @Cancellable false
    //
    // @Triggers when a contact between two physbodies (or a physbody and terrain) appears, and the relative speed is at least contact_min_speed.
    //
    // @Context
    // <context.body> returns the PhysBodyTag that has notify enabled.
    // <context.other> returns the other PhysBodyTag, or an ElementTag 'terrain'.
    // <context.point> returns the contact point as a LocationTag, computed from the real shape overlap.
    // <context.normal> returns the real contact normal, pointing from the other body towards <context.body>.
    // <context.speed> returns the relative speed.
    // <context.depth> returns the penetration depth.
    // <context.world> returns the PhysWorldTag.
    // -->
    public static final class Collides extends BaseEvent {

        public Collides() {
            registerCouldMatcher("physbody collides");
        }
    }

    // <--[event]
    // @Events
    // physbody contact persisted
    //
    // @Group Physizen
    //
    // @Cancellable false
    //
    // @Triggers every contact_persist_interval_steps physics steps while two bodies stay in contact. Only fires for bodies created with notify_persist:true.
    //
    // @Context
    // <context.body> returns the PhysBodyTag that has notify_persist enabled.
    // <context.other> returns the other PhysBodyTag, or an ElementTag 'terrain'.
    // <context.point> returns the contact point as a LocationTag, computed from the real shape overlap.
    // <context.normal> returns the contact normal, pointing from the other body towards <context.body>.
    // <context.speed> returns the relative speed.
    // <context.depth> returns the penetration depth.
    // <context.world> returns the PhysWorldTag.
    // -->
    public static final class ContactPersisted extends BaseEvent {

        public ContactPersisted() {
            registerCouldMatcher("physbody contact persisted");
        }
    }

    // <--[event]
    // @Events
    // physbody contact removed
    //
    // @Group Physizen
    //
    // @Cancellable false
    //
    // @Triggers when the last contact point between two bodies (or a body and terrain) disappears. Fires for every pair that was tracked, including slow contacts that never fired 'collides'.
    //
    // @Context
    // <context.body> returns the PhysBodyTag that has notify enabled.
    // <context.other> returns the other PhysBodyTag, or an ElementTag 'terrain'.
    // <context.point> returns the approximate last contact point as a LocationTag.
    // <context.world> returns the PhysWorldTag.
    // -->
    public static final class ContactRemoved extends BaseEvent {

        public ContactRemoved() {
            registerCouldMatcher("physbody contact removed");
        }
    }

    // <--[event]
    // @Events
    // physsensor enter
    //
    // @Group Physizen
    //
    // @Cancellable false
    //
    // @Triggers when a body (or a character, or a player hitbox) starts overlapping a sensor zone.
    //
    // @Context
    // <context.sensor> returns the PhysBodyTag of the sensor zone.
    // <context.body> returns the PhysBodyTag that entered.
    // <context.player> returns the PlayerTag if the entering body belongs to a player hitbox or controller.
    // <context.is_player> returns whether the entering body belongs to a player.
    // <context.point> returns the approximate overlap point.
    // <context.speed> returns the relative speed on entry.
    // <context.world> returns the PhysWorldTag.
    // -->
    public static final class SensorEnter extends BaseEvent {

        public SensorEnter() {
            registerCouldMatcher("physsensor enter");
        }
    }

    // <--[event]
    // @Events
    // physsensor exit
    //
    // @Group Physizen
    //
    // @Cancellable false
    //
    // @Triggers when a body stops overlapping a sensor zone, including when the body or the zone is removed.
    //
    // @Context
    // <context.sensor> returns the PhysBodyTag of the sensor zone.
    // <context.body> returns the PhysBodyTag that left.
    // <context.player> returns the PlayerTag if the leaving body belongs to a player hitbox or controller.
    // <context.is_player> returns whether the leaving body belongs to a player.
    // <context.point> returns the approximate overlap point.
    // <context.world> returns the PhysWorldTag.
    // -->
    public static final class SensorExit extends BaseEvent {

        public SensorExit() {
            registerCouldMatcher("physsensor exit");
        }
    }

    // <--[event]
    // @Events
    // physsensor stay
    //
    // @Group Physizen
    //
    // @Cancellable false
    //
    // @Triggers every sensor_stay_interval_steps physics steps while a body stays inside a sensor zone. Disabled when sensor_stay_interval_steps is 0.
    //
    // @Context
    // <context.sensor> returns the PhysBodyTag of the sensor zone.
    // <context.body> returns the PhysBodyTag inside the zone.
    // <context.player> returns the PlayerTag if the body belongs to a player hitbox or controller.
    // <context.is_player> returns whether the body belongs to a player.
    // <context.point> returns the approximate overlap point.
    // <context.world> returns the PhysWorldTag.
    // -->
    public static final class SensorStay extends BaseEvent {

        public SensorStay() {
            registerCouldMatcher("physsensor stay");
        }
    }

    // <--[event]
    // @Events
    // physbody sleeps
    //
    // @Group Physizen
    //
    // @Cancellable false
    //
    // @Triggers when a body with notify enabled falls asleep.
    //
    // @Context
    // <context.body> returns the PhysBodyTag.
    // <context.world> returns the PhysWorldTag.
    // -->
    public static final class Sleeps extends BaseEvent {

        public Sleeps() {
            registerCouldMatcher("physbody sleeps");
        }
    }

    // <--[event]
    // @Events
    // physbody wakes
    //
    // @Group Physizen
    //
    // @Cancellable false
    //
    // @Triggers when a body with notify enabled wakes up.
    //
    // @Context
    // <context.body> returns the PhysBodyTag.
    // <context.world> returns the PhysWorldTag.
    // -->
    public static final class Wakes extends BaseEvent {

        public Wakes() {
            registerCouldMatcher("physbody wakes");
        }
    }

    // <--[event]
    // @Events
    // physbody falls out
    //
    // @Group Physizen
    //
    // @Cancellable false
    //
    // @Triggers when a body drops below fall_out_y. The body is removed afterwards unless remove_on_fall_out is false.
    //
    // @Context
    // <context.body> returns the PhysBodyTag.
    // <context.world> returns the PhysWorldTag.
    // -->
    public static final class FallsOut extends BaseEvent {

        public FallsOut() {
            registerCouldMatcher("physbody falls out");
        }
    }

    // <--[event]
    // @Events
    // physragdoll hit
    //
    // @Group Physizen
    //
    // @Cancellable false
    //
    // @Triggers when a bone of a ragdoll touches something else at a relative speed of at least ragdoll_hit_min_speed. Bones of the same ragdoll never trigger it.
    //
    // @Context
    // <context.body> returns the PhysBodyTag that was hit (the ragdoll bone or the vehicle chassis).
    // <context.other> returns the other PhysBodyTag, or an ElementTag 'terrain'.
    // <context.point> returns the contact point as a LocationTag.
    // <context.normal> returns the real contact normal, pointing from the other body towards <context.body>.
    // <context.speed> returns the relative speed.
    // <context.depth> returns the penetration depth.
    // <context.ragdoll> returns the ragdoll id as an ElementTag.
    // <context.world> returns the PhysWorldTag.
    // -->
    public static final class RagdollHit extends BaseEvent {

        public RagdollHit() {
            registerCouldMatcher("physragdoll hit");
        }
    }

    // <--[event]
    // @Events
    // physvehicle crash
    //
    // @Group Physizen
    //
    // @Cancellable false
    //
    // @Triggers when the chassis of a vehicle touches something else at a relative speed of at least vehicle_hit_min_speed.
    //
    // @Context
    // <context.body> returns the PhysBodyTag that was hit (the ragdoll bone or the vehicle chassis).
    // <context.other> returns the other PhysBodyTag, or an ElementTag 'terrain'.
    // <context.point> returns the contact point as a LocationTag.
    // <context.normal> returns the real contact normal, pointing from the other body towards <context.body>.
    // <context.speed> returns the relative speed.
    // <context.depth> returns the penetration depth.
    // <context.vehicle> returns the vehicle name as an ElementTag.
    // <context.world> returns the PhysWorldTag.
    // -->
    public static final class VehicleCrash extends BaseEvent {

        public VehicleCrash() {
            registerCouldMatcher("physvehicle crash");
        }
    }

    // <--[event]
    // @Events
    // physconstraint breaks
    //
    // @Group Physizen
    //
    // @Cancellable false
    //
    // @Triggers when a constraint created with break_distance is stretched past that distance. The constraint is removed.
    //
    // @Context
    // <context.body> returns the first PhysBodyTag of the constraint.
    // <context.other> returns the second PhysBodyTag, if the constraint had one.
    // <context.point> returns the middle of the two stretched anchors as a LocationTag.
    // <context.constraint> returns the constraint name as an ElementTag.
    // <context.world> returns the PhysWorldTag.
    // -->
    public static final class ConstraintBreak extends BaseEvent {

        public ConstraintBreak() {
            registerCouldMatcher("physconstraint breaks");
        }
    }

    public static void registerAll() {
        register(PhysEvent.Kind.COLLIDE, new Collides());
        register(PhysEvent.Kind.CONTACT_PERSISTED, new ContactPersisted());
        register(PhysEvent.Kind.CONTACT_REMOVED, new ContactRemoved());
        register(PhysEvent.Kind.SENSOR_ENTER, new SensorEnter());
        register(PhysEvent.Kind.SENSOR_EXIT, new SensorExit());
        register(PhysEvent.Kind.SENSOR_STAY, new SensorStay());
        register(PhysEvent.Kind.SLEEP, new Sleeps());
        register(PhysEvent.Kind.WAKE, new Wakes());
        register(PhysEvent.Kind.FALL_OUT, new FallsOut());
        register(PhysEvent.Kind.RAGDOLL_HIT, new RagdollHit());
        register(PhysEvent.Kind.VEHICLE_HIT, new VehicleCrash());
        register(PhysEvent.Kind.CONSTRAINT_BREAK, new ConstraintBreak());
    }

    private static void register(PhysEvent.Kind kind, BaseEvent event) {
        EVENTS.put(kind, event);
        ScriptEvent.registerScriptEvent(event);
    }

    public static void fire(String worldId, PhysEvent e) {
        BaseEvent event = EVENTS.get(e.kind);
        if (event == null) {
            return;
        }
        World bukkitWorld = PhysizenServices.get().worlds.get(worldId);
        event.body = null;
        event.sensor = null;
        event.other = null;
        event.point = null;
        event.normal = null;
        event.speed = null;
        event.depth = null;
        event.player = null;
        event.owner = null;
        boolean sensorEvent = e.kind == PhysEvent.Kind.SENSOR_ENTER || e.kind == PhysEvent.Kind.SENSOR_EXIT || e.kind == PhysEvent.Kind.SENSOR_STAY;
        if (sensorEvent) {
            event.sensor = new PhysBodyTag(worldId, e.body.name);
            event.body = new PhysBodyTag(worldId, e.other.name);
            event.player = playerOf(worldId, e.other.name);
        }
        else {
            event.body = new PhysBodyTag(worldId, e.body.name);
            event.other = e.other != null ? new PhysBodyTag(worldId, e.other.name) : e.kind == PhysEvent.Kind.CONSTRAINT_BREAK ? null : new ElementTag("terrain");
            event.normal = e.normal != null ? new LocationTag(bukkitWorld, e.normal[0], e.normal[1], e.normal[2]) : null;
            event.depth = new ElementTag(e.depth);
        }
        event.point = e.point != null ? new LocationTag(bukkitWorld, e.point[0], e.point[1], e.point[2]) : null;
        event.speed = new ElementTag(e.speed);
        event.owner = e.owner != null ? new ElementTag(e.owner) : null;
        event.fire();
    }

    private static PlayerTag playerOf(String worldId, String bodyName) {
        PhysizenServices services = PhysizenServices.get();
        UUID id = services.hitboxes.ownerOf(worldId, bodyName);
        if (id == null) {
            id = services.controllers.ownerOf(worldId, bodyName);
        }
        if (id == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(id);
        return player == null ? null : new PlayerTag(player);
    }
}
