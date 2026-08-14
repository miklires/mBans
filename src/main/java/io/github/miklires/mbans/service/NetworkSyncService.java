package io.github.miklires.mbans.service;

import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.database.NetworkLogRepository;
import io.github.miklires.mbans.model.Punishment;
import io.github.miklires.mbans.model.PunishmentType;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class NetworkSyncService {

    private final MBans plugin;
    private volatile long lastSeen;

    public NetworkSyncService(MBans plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfigManager().isNetworkSyncEnabled()) return;
        plugin.getScheduler().async(() -> {
            try {
                lastSeen = plugin.getNetworkLogRepository().latestId();
                plugin.getScheduler().global(() -> plugin.getScheduler().repeatGlobal(this::poll,
                        plugin.getConfigManager().getPollIntervalTicks(), plugin.getConfigManager().getPollIntervalTicks()));
            } catch (SQLException e) {
                plugin.getLogger().warning("Could not initialize network sync: " + e.getMessage());
            }
        });
    }

    private void poll() {
        plugin.getScheduler().async(() -> {
            try {
                List<NetworkLogRepository.NetworkEvent> events = plugin.getNetworkLogRepository().after(lastSeen, 100);
                for (NetworkLogRepository.NetworkEvent event : events) {
                    lastSeen = Math.max(lastSeen, event.id());
                    if (event.serverName().equals(plugin.getConfigManager().getNetworkServerName())) continue;
                    apply(event);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Network sync failed: " + e.getMessage());
            }
        });
    }

    private void apply(NetworkLogRepository.NetworkEvent event) throws SQLException {
        Optional<Punishment> found = plugin.getPunishmentRepository().findById(event.punishmentId());
        if (found.isEmpty()) return;
        Punishment punishment = found.get();
        if (!"CREATE".equals(event.action()) || !punishment.isActive()) return;
        plugin.getPunishmentService().broadcast(punishment);
        if (punishment.getType() != PunishmentType.BAN && punishment.getType() != PunishmentType.IP_BAN) return;

        plugin.getScheduler().global(() -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                boolean matchesUuid = punishment.getTargetUuid() != null && punishment.getTargetUuid().equals(player.getUniqueId());
                boolean matchesIp = punishment.getTargetIp() != null && player.getAddress() != null
                        && punishment.getTargetIp().equals(player.getAddress().getAddress().getHostAddress());
                if (matchesUuid || matchesIp) plugin.getScheduler().entity(player,
                        () -> player.kick(plugin.getPunishmentService().buildBanKickComponent(punishment)));
            }
        });
    }
}
