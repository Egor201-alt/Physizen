package dev.egor201.physizen.bukkit.status;

import dev.egor201.physizen.bukkit.display.DisplayBinder;
import dev.egor201.physizen.core.world.PhysEngine;
import dev.egor201.physizen.core.world.PhysWorld;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

public class StatusBarManager implements Listener {

    private static final long UPDATE_TICKS = 10L;

    private final Plugin plugin;
    private final DisplayBinder displays;
    private final IntSupplier pending;
    private final Map<UUID, BossBar[]> bars = new HashMap<>();
    private final Map<String, double[]> rates = new HashMap<>();
    private BukkitTask task;
    private double smoothLoad;
    private double peakLoad;

    public StatusBarManager(Plugin plugin, DisplayBinder displays, IntSupplier pending) {
        this.plugin = plugin;
        this.displays = displays;
        this.pending = pending;
    }

    public boolean toggle(Player player) {
        BossBar[] existing = bars.remove(player.getUniqueId());
        if (existing != null) {
            existing[0].removeAll();
            existing[1].removeAll();
            if (bars.isEmpty()) {
                stop();
            }
            return false;
        }
        BossBar load = Bukkit.createBossBar("Physizen", BarColor.GREEN, BarStyle.SEGMENTED_20);
        BossBar objects = Bukkit.createBossBar("Physizen", BarColor.BLUE, BarStyle.SOLID);
        load.addPlayer(player);
        objects.addPlayer(player);
        bars.put(player.getUniqueId(), new BossBar[]{load, objects});
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 0L, UPDATE_TICKS);
        }
        update();
        return true;
    }

    public void clear() {
        for (BossBar[] pair : bars.values()) {
            pair[0].removeAll();
            pair[1].removeAll();
        }
        bars.clear();
        stop();
    }

    private void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        rates.clear();
        smoothLoad = 0;
        peakLoad = 0;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BossBar[] pair = bars.remove(event.getPlayer().getUniqueId());
        if (pair != null) {
            pair[0].removeAll();
            pair[1].removeAll();
            if (bars.isEmpty()) {
                stop();
            }
        }
    }

    private static String colorCode(double load) {
        return load < 0.5 ? "§a" : load < 0.8 ? "§e" : "§c";
    }

    private static BarColor barColor(double load) {
        return load < 0.5 ? BarColor.GREEN : load < 0.8 ? BarColor.YELLOW : BarColor.RED;
    }

    private void update() {
        if (bars.isEmpty()) {
            return;
        }
        PhysEngine engine = PhysEngine.get();
        String loadTitle;
        String objectsTitle;
        double loadProgress = 0;
        double objectsProgress = 0;
        BarColor loadColor = BarColor.RED;
        if (engine == null) {
            loadTitle = "§b§lPhysizen §8» §cPhysics engine is offline";
            objectsTitle = "§7No data";
        }
        else {
            int worlds = 0;
            int paused = 0;
            int bodies = 0;
            int active = 0;
            int constraints = 0;
            int characters = 0;
            double worstLoad = 0;
            double worstStep = 0;
            double worstBudget = 0;
            int worstTarget = 0;
            double worstHz = 0;
            long now = System.nanoTime();
            for (PhysWorld w : engine.worlds()) {
                worlds++;
                if (w.paused()) {
                    paused++;
                }
                bodies += w.bodyCount();
                constraints += w.constraintCount();
                characters += w.characterCount();
                active += w.activeBodyCount();
                int rate = Math.max(1, w.stepRate());
                double budget = 1000.0 / rate;
                double step = w.lastStepMillis();
                double[] sample = rates.get(w.id);
                double hz = sample == null ? 0 : sample[2];
                long count = w.stepCount();
                if (sample == null) {
                    rates.put(w.id, new double[]{count, now, 0});
                }
                else {
                    double dt = (now - sample[1]) / 1e9;
                    if (dt >= 0.4) {
                        hz = (count - sample[0]) / dt;
                        sample[0] = count;
                        sample[1] = now;
                        sample[2] = hz;
                    }
                }
                double load = step / budget;
                if (load >= worstLoad) {
                    worstLoad = load;
                    worstStep = step;
                    worstBudget = budget;
                    worstTarget = rate;
                    worstHz = hz;
                }
            }
            smoothLoad = smoothLoad * 0.6 + worstLoad * 0.4;
            peakLoad = Math.max(worstLoad, peakLoad * 0.97);
            double shown = Math.min(1.0, smoothLoad);
            String c = colorCode(smoothLoad);
            loadColor = barColor(smoothLoad);
            loadProgress = shown;
            String state = worlds > 0 && paused == worlds ? " §c§lPAUSED §8| " : " §8| ";
            loadTitle = String.format(Locale.ROOT,
                    "§b§lPhysizen §8»%s§7Load %s%d%% §8| §7Step §f%.2f§7/§f%.1f ms §8| §7Rate §f%.0f§7/§f%d Hz §8| §7Peak %s%d%%",
                    state, c, Math.round(smoothLoad * 100), worstStep, worstBudget, worstHz, worstTarget, colorCode(peakLoad), Math.round(peakLoad * 100));
            int displayCount = displays.totalBindings();
            int pendingCount = pending.getAsInt();
            objectsProgress = bodies > 0 ? (double) active / bodies : 0;
            objectsTitle = String.format(Locale.ROOT,
                    "§7Worlds §f%d §8| §7Bodies §e%d §8(§a%d §7active§8) §8| §7Constraints §e%d §8| §7Characters §e%d §8| §7Displays §e%d §8| §7Pending §e%d",
                    worlds, bodies, active, constraints, characters, displayCount, pendingCount);
        }
        for (BossBar[] pair : bars.values()) {
            pair[0].setTitle(loadTitle);
            pair[0].setColor(loadColor);
            pair[0].setProgress(Math.max(0.0, Math.min(1.0, loadProgress)));
            pair[1].setTitle(objectsTitle);
            pair[1].setProgress(Math.max(0.0, Math.min(1.0, objectsProgress)));
        }
    }
}