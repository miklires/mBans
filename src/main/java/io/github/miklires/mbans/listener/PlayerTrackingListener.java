package io.github.miklires.mbans.listener;

import io.github.miklires.mbans.MBans;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.UUID;
import java.util.List;
import io.github.miklires.mbans.model.Punishment;
import io.github.miklires.mbans.util.MessageUtil;

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
        int immunityLevel = plugin.getConfigManager().getImmunityLevel(event.getPlayer());
        plugin.getScheduler().async(() -> {
            try {
                plugin.getPlayerRepository().record(uuid, name, ip, immunityLevel);
                deliverWarnings(uuid, name);
                notifyBannedAlts(uuid, name, ip);
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not update player history: " + e.getMessage());
            }
        });
    }

    private void deliverWarnings(UUID uuid, String name) throws SQLException {
        if (!plugin.getConfigManager().isOfflineWarnDeliveryEnabled()) return;
        List<Punishment> warnings = plugin.getPunishmentRepository().findUndeliveredWarns(uuid);
        if (warnings.isEmpty()) return;
        int total = plugin.getPunishmentRepository().countActiveWarns(uuid);
        plugin.getPunishmentRepository().markWarnsDelivered(uuid);
        Punishment latest = warnings.getFirst();
        plugin.getScheduler().global(() -> {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                plugin.getMessageUtil().send(player, "notify.warn-received",
                        MessageUtil.ph("reason", latest.getReason()),
                        MessageUtil.ph("count", total),
                        MessageUtil.ph("max", plugin.getConfigManager().getWarningDisplayThreshold()));
            }
        });
    }

    private void notifyBannedAlts(UUID uuid, String name, String ip) throws SQLException {
        if (!plugin.getConfigManager().isAltNotificationEnabled() || ip == null) return;
        List<String> alts = plugin.getPlayerRepository().findActiveBannedAlts(ip, uuid, 10);
        if (alts.isEmpty()) return;
        String text = name + " shares an IP with banned accounts: " + String.join(", ", alts);
        plugin.getScheduler().global(() -> plugin.getServer().getOnlinePlayers().stream()
                .filter(player -> player.hasPermission("mbans.notify.alts"))
                .forEach(player -> plugin.getScheduler().entity(player,
                        () -> player.sendMessage(net.kyori.adventure.text.Component.text(text)))));
    }
}
