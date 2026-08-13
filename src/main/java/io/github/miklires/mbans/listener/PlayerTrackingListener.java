package io.github.miklires.mbans.listener;

import io.github.miklires.mbans.MBans;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.SQLException;
import java.util.UUID;

public class PlayerTrackingListener implements Listener {

    private final MBans plugin;

    public PlayerTrackingListener(MBans plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String ip = event.getPlayer().getAddress() == null ? null
                : event.getPlayer().getAddress().getAddress().getHostAddress();
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        plugin.getScheduler().async(() -> {
            try {
                plugin.getPlayerRepository().record(uuid, name, ip);
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not update player history: " + e.getMessage());
            }
        });
    }
}
