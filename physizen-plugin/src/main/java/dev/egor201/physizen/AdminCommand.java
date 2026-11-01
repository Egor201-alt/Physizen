package dev.egor201.physizen;

import dev.egor201.physizen.bukkit.status.StatusBarManager;
import dev.egor201.physizen.core.world.PhysEngine;
import dev.egor201.physizen.core.world.PhysWorld;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

final class AdminCommand implements CommandExecutor, TabCompleter {

    private final StatusBarManager statusBars;

    AdminCommand(StatusBarManager statusBars) {
        this.statusBars = statusBars;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("physizen.admin")) {
            return Collections.singletonList("statusbar");
        }
        return Collections.emptyList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("physizen.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        PhysEngine engine = PhysEngine.get();
        if (engine == null) {
            sender.sendMessage("§cPhysEngine is not running.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("statusbar")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can use the status bar.");
                return true;
            }
            boolean shown = statusBars.toggle((Player) sender);
            sender.sendMessage(shown ? "§bPhysizen §7status bar §aenabled§7. Run §f/physizen statusbar §7again to hide it." : "§bPhysizen §7status bar §chidden§7.");
            return true;
        }
        sender.sendMessage("§b--- Physizen Physics Status ---");
        sender.sendMessage("§7Worlds: §f" + engine.worlds().size() + " §7| Native: §a" + engine.nativeSource());
        for (PhysWorld w : engine.worlds()) {
            sender.sendMessage(" §8- §e" + w.id + " §7(Bodies: §f" + w.bodyCount() + "§7, Active: §a" + w.activeBodyCount()
                    + "§7, Characters: §f" + w.characterCount() + "§7, Paused: §f" + w.paused() + "§7)");
        }
        return true;
    }
}