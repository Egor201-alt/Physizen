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

    static DisplayBinder.Binding create(EntityTag entity, ScriptEntry scriptEntry, ObjectTag offsetArg, ObjectTag scaleArg, ObjectTag rotationOffsetArg, ObjectTag interpolationDurationArg, ObjectTag interpolationArg, ObjectTag teleportDurationArg, ObjectTag removeWithBodyArg) {
        DisplayBinder.Binding binding = new DisplayBinder.Binding(entity.getBukkitEntity());
        if (offsetArg != null) {
            double[] o = Args.vec(offsetArg, scriptEntry.context);
            binding.offset = new Vector3f((float) o[0], (float) o[1], (float) o[2]);
        }
        if (scaleArg != null) {
            double[] s = Args.vec(scaleArg, scriptEntry.context);
            binding.scale = new Vector3f((float) s[0], (float) s[1], (float) s[2]);
        }
        if (rotationOffsetArg != null) {
            double[] q = Args.rotation(rotationOffsetArg, scriptEntry.context);
            binding.rotationOffset = new Quaternionf((float) q[0], (float) q[1], (float) q[2], (float) q[3]);
        }
        ObjectTag interp = (interpolationDurationArg != null)
                ? interpolationDurationArg
                : interpolationArg;
        binding.interpolationDuration = (int) Args.num(interp, 3);
        binding.teleportDuration = (int) Args.num(teleportDurationArg, 3);
        binding.removeWithBody = Args.bool(removeWithBodyArg, true);
        return binding;
    }
}
