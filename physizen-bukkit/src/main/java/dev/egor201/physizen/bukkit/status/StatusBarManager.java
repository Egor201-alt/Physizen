package dev.egor201.physizen.bukkit.status;

import dev.egor201.physizen.bukkit.display.DisplayBinder;
import dev.egor201.physizen.core.world.PhysEngine;
import dev.egor201.physizen.core.world.PhysWorld;
import org.bukkit.Bukkit;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
    private static final MiniMessage MM = MiniMessage.miniMessage();

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
            hide(player, existing);
            if (bars.isEmpty()) {
                stop();
            }
            return false;
        }
        BossBar load = BossBar.bossBar(Component.text("Physizen"), 1.0f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_20);
        BossBar objects = BossBar.bossBar(Component.text("Physizen"), 1.0f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_20);
        player.showBossBar(load);
        player.showBossBar(objects);
        bars.put(player.getUniqueId(), new BossBar[]{load, objects});
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 0L, UPDATE_TICKS);
        }
        update();
        return true;
    }

    public void clear() {
        for (Map.Entry<UUID, BossBar[]> entry : bars.entrySet()) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null) {
                hide(viewer, entry.getValue());
            }
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
            hide(event.getPlayer(), pair);
            if (bars.isEmpty()) {
                stop();
            }
        }
    }

    private static String colorCode(double load) {
        return load < 0.5 ? "green" : load < 0.8 ? "yellow" : "red";
    }

    private static String val(String color, String text) {
        return "<" + color + ">" + text + "</" + color + ">";
    }

    private static void hide(Player player, BossBar[] pair) {
        player.hideBossBar(pair[0]);
        player.hideBossBar(pair[1]);
    }

    private static BossBar.Color barColor(double load) {
        return load < 0.5 ? BossBar.Color.GREEN : load < 0.8 ? BossBar.Color.YELLOW : BossBar.Color.RED;
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
        BossBar.Color loadColor = BossBar.Color.RED;
        if (engine == null) {
            loadTitle = "<red>Physics engine is offline</red>";
            objectsTitle = "<gray>No data</gray>";
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
            String state = worlds > 0 && paused == worlds ? "<red><bold>PAUSED</bold></red> " : "";
            loadTitle = state + "<gray>Load: " + val(c, Math.round(smoothLoad * 100) + "%")
                    + " Step: " + val(c, String.format(Locale.ROOT, "%.2f", worstStep)) + "/" + String.format(Locale.ROOT, "%.1f", worstBudget) + " ms"
                    + " Rate: " + val(c, String.format(Locale.ROOT, "%.0f", worstHz)) + "/" + worstTarget + " Hz"
                    + " Peak: " + val(colorCode(peakLoad), Math.round(peakLoad * 100) + "%");
            int displayCount = displays.totalBindings();
            int pendingCount = pending.getAsInt();
            objectsProgress = bodies > 0 ? (double) active / bodies : 0;
            objectsTitle = "<gray>Worlds: " + val("white", String.valueOf(worlds))
                    + " Bodies: " + val("yellow", String.valueOf(bodies)) + " (" + val("green", String.valueOf(active)) + " active)"
                    + " Constraints: " + val("yellow", String.valueOf(constraints))
                    + " Characters: " + val("yellow", String.valueOf(characters))
                    + " Displays: " + val("yellow", String.valueOf(displayCount))
                    + " Pending: " + val("yellow", String.valueOf(pendingCount));
        }
        Component loadName = MM.deserialize(loadTitle);
        Component objectsName = MM.deserialize(objectsTitle);
        float loadValue = (float) Math.max(0.0, Math.min(1.0, loadProgress));
        float objectsValue = (float) Math.max(0.0, Math.min(1.0, objectsProgress));
        for (BossBar[] pair : bars.values()) {
            pair[0].name(loadName);
            pair[0].color(loadColor);
            pair[0].progress(loadValue);
            pair[1].name(objectsName);
            pair[1].progress(objectsValue);
        }
    }
}