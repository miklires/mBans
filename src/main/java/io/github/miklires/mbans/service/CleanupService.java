package io.github.miklires.mbans.service;

import io.github.miklires.mbans.MBans;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class CleanupService {
    private final MBans plugin;

    public CleanupService(MBans plugin) { this.plugin = plugin; }

    public void start() {
        long period = plugin.getConfigManager().getCleanupIntervalTicks();
        plugin.getScheduler().repeatGlobal(() -> plugin.getScheduler().async(this::run), period, period);
    }

    private void run() {
        try {
            List<Long> expired = plugin.getPunishmentRepository().expireDue(Instant.now());
            for (long id : expired) plugin.getNetworkLogRepository().append(id, "EXPIRE",
                    plugin.getConfigManager().getNetworkServerName());
            int retention = plugin.getConfigManager().getIpRetentionDays();
            if (retention > 0) plugin.getPlayerRepository().purgeOldIps(Instant.now().minus(retention, ChronoUnit.DAYS));
        } catch (SQLException e) {
            plugin.getLogger().warning("Punishment cleanup failed: " + e.getMessage());
        }
    }
}
