package dev.egor201.physizen.denizen.command;

import com.denizenscript.denizen.objects.EntityTag;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.scripts.ScriptEntry;
import dev.egor201.physizen.bukkit.display.DisplayBinder;
import dev.egor201.physizen.denizen.args.Args;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class BindingArgs {

    private BindingArgs() {
    }

    static DisplayBinder.Binding create(EntityTag entity, ScriptEntry scriptEntry) {
        DisplayBinder.Binding binding = new DisplayBinder.Binding(entity.getBukkitEntity());
        if (scriptEntry.hasObject("offset")) {
            double[] o = Args.vec(scriptEntry.getObjectTag("offset"), scriptEntry.context);
            binding.offset = new Vector3f((float) o[0], (float) o[1], (float) o[2]);
        }
        if (scriptEntry.hasObject("scale")) {
            double[] s = Args.vec(scriptEntry.getObjectTag("scale"), scriptEntry.context);
            binding.scale = new Vector3f((float) s[0], (float) s[1], (float) s[2]);
        }
        if (scriptEntry.hasObject("rotation_offset")) {
            double[] q = Args.rotation(scriptEntry.getObjectTag("rotation_offset"), scriptEntry.context);
            binding.rotationOffset = new Quaternionf((float) q[0], (float) q[1], (float) q[2], (float) q[3]);
        }
        ObjectTag interp = scriptEntry.hasObject("interpolation_duration")
                ? scriptEntry.getObjectTag("interpolation_duration")
                : scriptEntry.getObjectTag("interpolation");
        binding.interpolationDuration = (int) Args.num(interp, 3);
        binding.teleportDuration = (int) Args.num(scriptEntry.getObjectTag("teleport_duration"), 3);
        binding.removeWithBody = Args.bool(scriptEntry.getObjectTag("remove_with_body"), true);
        return binding;
    }
}
