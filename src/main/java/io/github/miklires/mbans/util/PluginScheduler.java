package io.github.miklires.mbans.util;

import io.github.miklires.mbans.MBans;
import org.bukkit.entity.Player;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PluginScheduler {

    private final MBans plugin;
    private final ExecutorService databaseExecutor;

    public PluginScheduler(MBans plugin) {
        this.plugin = plugin;
        this.databaseExecutor = Executors.newFixedThreadPool(4, Thread.ofPlatform().name("mBans-db-", 0).factory());
    }

    public void async(Runnable task) {
        databaseExecutor.execute(task);
    }

    public void global(Runnable task) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
    }

    public void entity(Player player, Runnable task) {
        player.getScheduler().execute(plugin, task, null, 1L);
    }

    public void repeatGlobal(Runnable task, long delayTicks, long periodTicks) {
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, scheduled -> task.run(), delayTicks, periodTicks);
    }

    public void shutdown() {
        databaseExecutor.shutdown();
        try {
            if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) databaseExecutor.shutdownNow();
        } catch (InterruptedException e) {
            databaseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
