package dev.egor201.physizen.denizen;

import com.denizenscript.denizencore.DenizenCore;
import com.denizenscript.denizencore.objects.ObjectFetcher;
import com.denizenscript.denizencore.tags.TagManager;
import dev.egor201.physizen.denizen.command.PhysBodyCommand;
import dev.egor201.physizen.denizen.command.PhysCharacterCommand;
import dev.egor201.physizen.denizen.command.PhysConstraintCommand;
import dev.egor201.physizen.denizen.command.PhysRagdollCommand;
import dev.egor201.physizen.denizen.command.PhysVehicleCommand;
import dev.egor201.physizen.denizen.command.PhysFieldCommand;
import dev.egor201.physizen.denizen.command.PhysForceCommand;
import dev.egor201.physizen.denizen.command.PhysHitboxCommand;
import dev.egor201.physizen.denizen.command.PhysWorldCommand;
import dev.egor201.physizen.denizen.event.PhysScriptEvents;
import dev.egor201.physizen.denizen.tag.PhysBodyTag;
import dev.egor201.physizen.denizen.tag.PhysCharacterTag;
import dev.egor201.physizen.denizen.tag.PhysWorldTag;

public final class DenizenBridge {

    private DenizenBridge() {
    }

    public static boolean isDenizenM() {
        try {
            Class.forName("com.denizenscript.denizencore.scripts.queues.core.AsyncQueue");
            Class<?> command = Class.forName("com.denizenscript.denizencore.scripts.commands.AbstractCommand");
            return command.getDeclaredField("asyncSafe") != null;
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static void registerAll() {
        ObjectFetcher.registerWithObjectFetcher(PhysWorldTag.class, PhysWorldTag.tagProcessor);
        ObjectFetcher.registerWithObjectFetcher(PhysBodyTag.class, PhysBodyTag.tagProcessor);
        ObjectFetcher.registerWithObjectFetcher(PhysCharacterTag.class, PhysCharacterTag.tagProcessor);
        PhysWorldTag.register();
        PhysBodyTag.register();
        PhysCharacterTag.register();

        TagManager.registerTagHandler(PhysWorldTag.class, "physworld", attribute -> {
            if (!attribute.hasParam()) {
                return null;
            }
            return PhysWorldTag.valueOf(attribute.getParam(), attribute.context);
        });
        TagManager.registerTagHandler(PhysBodyTag.class, "physbody", attribute -> {
            if (!attribute.hasParam()) {
                return null;
            }
            return PhysBodyTag.valueOf(attribute.getParam(), attribute.context);
        });
        TagManager.registerTagHandler(PhysCharacterTag.class, "physcharacter", attribute -> {
            if (!attribute.hasParam()) {
                return null;
            }
            return PhysCharacterTag.valueOf(attribute.getParam(), attribute.context);
        });

        DenizenCore.commandRegistry.registerCommand(PhysWorldCommand.class);
        DenizenCore.commandRegistry.registerCommand(PhysBodyCommand.class);
        DenizenCore.commandRegistry.registerCommand(PhysForceCommand.class);
        DenizenCore.commandRegistry.registerCommand(PhysConstraintCommand.class);
        DenizenCore.commandRegistry.registerCommand(PhysFieldCommand.class);
        DenizenCore.commandRegistry.registerCommand(PhysHitboxCommand.class);
        DenizenCore.commandRegistry.registerCommand(PhysCharacterCommand.class);
        DenizenCore.commandRegistry.registerCommand(PhysRagdollCommand.class);
        DenizenCore.commandRegistry.registerCommand(PhysVehicleCommand.class);

        PhysScriptEvents.registerAll();
    }
}
