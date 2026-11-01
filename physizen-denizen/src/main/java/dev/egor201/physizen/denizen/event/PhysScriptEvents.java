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
    // <context.point> returns the approximate contact point as a LocationTag.
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
    // <context.point> returns the approximate contact point as a LocationTag.
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
        boolean sensorEvent = e.kind == PhysEvent.Kind.SENSOR_ENTER || e.kind == PhysEvent.Kind.SENSOR_EXIT || e.kind == PhysEvent.Kind.SENSOR_STAY;
        if (sensorEvent) {
            event.sensor = new PhysBodyTag(worldId, e.body.name);
            event.body = new PhysBodyTag(worldId, e.other.name);
            event.player = playerOf(worldId, e.other.name);
        }
        else {
            event.body = new PhysBodyTag(worldId, e.body.name);
            event.other = e.other != null ? new PhysBodyTag(worldId, e.other.name) : new ElementTag("terrain");
            event.normal = e.normal != null ? new LocationTag(bukkitWorld, e.normal[0], e.normal[1], e.normal[2]) : null;
            event.depth = new ElementTag(e.depth);
        }
        event.point = e.point != null ? new LocationTag(bukkitWorld, e.point[0], e.point[1], e.point[2]) : null;
        event.speed = new ElementTag(e.speed);
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
