package dev.egor201.physizen.denizen;

import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import dev.egor201.physizen.Physizen;
import dev.egor201.physizen.core.PhysEvent;
import org.bukkit.World;

public class PhysScriptEvents {

    public static class CollidesEvent extends ScriptEvent {
        public static CollidesEvent instance;
        public PhysBodyTag body;
        public ObjectTag other;
        public LocationTag point;
        public LocationTag normal;
        public ElementTag speed;

        public CollidesEvent() {
            instance = this;
            registerCouldMatcher("physbody collides");
        }

        @Override
        public boolean matches(ScriptPath path) {
            return super.matches(path);
        }

        @Override
        public ObjectTag getContext(String name) {
            switch (name) {
                case "body": return body;
                case "world": return new PhysWorldTag(body.worldId);
                case "other": return other;
                case "point": return point;
                case "normal": return normal;
                case "speed": return speed;
            }
            return super.getContext(name);
        }
    }

    public static class SleepsEvent extends ScriptEvent {
        public static SleepsEvent instance;
        public PhysBodyTag body;

        public SleepsEvent() {
            instance = this;
            registerCouldMatcher("physbody sleeps");
        }

        @Override
        public boolean matches(ScriptPath path) {
            return super.matches(path);
        }

        @Override
        public ObjectTag getContext(String name) {
            if (name.equals("body")) return body;
            if (name.equals("world")) return new PhysWorldTag(body.worldId);
            return super.getContext(name);
        }
    }

    public static class WakesEvent extends ScriptEvent {
        public static WakesEvent instance;
        public PhysBodyTag body;

        public WakesEvent() {
            instance = this;
            registerCouldMatcher("physbody wakes");
        }

        @Override
        public boolean matches(ScriptPath path) {
            return super.matches(path);
        }

        @Override
        public ObjectTag getContext(String name) {
            if (name.equals("body")) return body;
            if (name.equals("world")) return new PhysWorldTag(body.worldId);
            return super.getContext(name);
        }
    }

    public static class FallsOutEvent extends ScriptEvent {
        public static FallsOutEvent instance;
        public PhysBodyTag body;

        public FallsOutEvent() {
            instance = this;
            registerCouldMatcher("physbody falls out");
        }

        @Override
        public boolean matches(ScriptPath path) {
            return super.matches(path);
        }

        @Override
        public ObjectTag getContext(String name) {
            if (name.equals("body")) return body;
            if (name.equals("world")) return new PhysWorldTag(body.worldId);
            return super.getContext(name);
        }
    }

    public static void registerAll() {
        ScriptEvent.registerScriptEvent(new CollidesEvent());
        ScriptEvent.registerScriptEvent(new SleepsEvent());
        ScriptEvent.registerScriptEvent(new WakesEvent());
        ScriptEvent.registerScriptEvent(new FallsOutEvent());
    }

    public static void fireEvent(String worldId, PhysEvent e) {
        World bukkitWorld = Physizen.getInstance().getWorld(worldId);
        PhysBodyTag b = new PhysBodyTag(worldId, e.body.name);

        switch (e.kind) {
            case COLLIDE:
                if (CollidesEvent.instance != null) {
                    CollidesEvent.instance.body = b;
                    CollidesEvent.instance.other = e.other != null ? new PhysBodyTag(worldId, e.other.name) : new ElementTag("terrain");
                    CollidesEvent.instance.point = e.point != null ? new LocationTag(bukkitWorld, e.point[0], e.point[1], e.point[2]) : null;
                    CollidesEvent.instance.normal = e.normal != null ? new LocationTag(bukkitWorld, e.normal[0], e.normal[1], e.normal[2]) : null;
                    CollidesEvent.instance.speed = new ElementTag(e.speed);
                    CollidesEvent.instance.fire();
                }
                break;
            case SLEEP:
                if (SleepsEvent.instance != null) {
                    SleepsEvent.instance.body = b;
                    SleepsEvent.instance.fire();
                }
                break;
            case WAKE:
                if (WakesEvent.instance != null) {
                    WakesEvent.instance.body = b;
                    WakesEvent.instance.fire();
                }
                break;
            case FALL_OUT:
                if (FallsOutEvent.instance != null) {
                    FallsOutEvent.instance.body = b;
                    FallsOutEvent.instance.fire();
                }
                break;
        }
    }
}