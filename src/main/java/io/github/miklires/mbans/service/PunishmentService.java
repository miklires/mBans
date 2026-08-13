package io.github.miklires.mbans.service;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.model.Punishment;
import io.github.miklires.mbans.model.PunishmentType;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.security.SecureRandom;

public class PunishmentService {

    private final MBans plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final SecureRandom random = new SecureRandom();

    public PunishmentService(MBans plugin) {
        this.plugin = plugin;
    }

    public Punishment ban(OfflinePlayer target, Duration duration, String reason,
                          String issuerName, UUID issuerUuid) throws SQLException {
        Punishment p = newBase(PunishmentType.BAN, target, reason, issuerName, issuerUuid);
        if (duration != null) p.setExpiresAt(Instant.now().plus(duration));
        plugin.getPunishmentRepository().insert(p);
        recordChange(p, "CREATE");
        kickIfOnline(target, buildBanKickComponent(p));
        plugin.getDiscordWebhook().sendPunishment(p);
        return p;
    }

    public Punishment ipBan(String ip, String targetName, UUID targetUuid, Duration duration,
                            String reason, String issuerName, UUID issuerUuid) throws SQLException {
        Punishment p = new Punishment();
        p.setType(PunishmentType.IP_BAN);
        p.setTargetUuid(targetUuid);
        p.setTargetName(targetName);
        p.setTargetIp(ip);
        p.setReason(reason);
        p.setIssuedByName(issuerName);
        p.setIssuedByUuid(issuerUuid);
        p.setIssuedAt(Instant.now());
        p.setActive(true);
        p.setAppealId(newAppealId());
        p.setServerName(plugin.getConfigManager().getNetworkServerName());
        if (duration != null) p.setExpiresAt(Instant.now().plus(duration));
        plugin.getPunishmentRepository().insert(p);
        recordChange(p, "CREATE");

        plugin.getScheduler().global(() -> Bukkit.getOnlinePlayers().stream()
                .filter(pl -> pl.getAddress() != null && ip.equals(pl.getAddress().getAddress().getHostAddress()))
                .forEach(pl -> plugin.getScheduler().entity(pl, () -> pl.kick(buildBanKickComponent(p)))));
        plugin.getDiscordWebhook().sendPunishment(p);
        return p;
    }

    public Punishment mute(OfflinePlayer target, Duration duration, String reason,
                           String issuerName, UUID issuerUuid) throws SQLException {
        Punishment p = newBase(PunishmentType.MUTE, target, reason, issuerName, issuerUuid);
        if (duration != null) p.setExpiresAt(Instant.now().plus(duration));
        plugin.getPunishmentRepository().insert(p);
        recordChange(p, "CREATE");
        plugin.getDiscordWebhook().sendPunishment(p);
        return p;
    }

    public Punishment kick(Player target, String reason, String issuerName, UUID issuerUuid) throws SQLException {
        Punishment p = newBase(PunishmentType.KICK, target, reason, issuerName, issuerUuid);
        plugin.getPunishmentRepository().insert(p);
        recordChange(p, "CREATE");
        plugin.getScheduler().entity(target, () -> target.kick(buildKickComponent(p)));
        plugin.getDiscordWebhook().sendPunishment(p);
        return p;
    }

    public Punishment warn(OfflinePlayer target, String reason,
                           String issuerName, UUID issuerUuid) throws SQLException {
        Punishment p = newBase(PunishmentType.WARN, target, reason, issuerName, issuerUuid);
        plugin.getPunishmentRepository().insert(p);
        recordChange(p, "CREATE");
        plugin.getDiscordWebhook().sendPunishment(p);

        if (target.getUniqueId() != null) {
            int total = plugin.getPunishmentRepository().countActiveWarns(target.getUniqueId());
            int threshold = plugin.getConfigManager().getAutoBanThreshold();
            if (total >= threshold) {
                autoBan(target, total, issuerName);
            }
        }
        return p;
    }

    private void autoBan(OfflinePlayer target, int warnCount, String issuer) throws SQLException {
        String reason = plugin.getConfigManager().getAutoBanReason().replace("{count}", String.valueOf(warnCount));
        Duration dur = DurationParser.parse(plugin.getConfigManager().getAutoBanDuration()).orElse(null);
        ban(target, dur, reason, "AUTO (" + issuer + ")", null);
    }

    public boolean unban(String name, String revokedBy, String revokeReason) throws SQLException {
        Optional<Punishment> opt = plugin.getPunishmentRepository().findActiveByName(name, PunishmentType.BAN);
        if (opt.isEmpty()) return false;
        Punishment p = opt.get();
        plugin.getPunishmentRepository().deactivate(p.getId(), revokedBy, revokeReason);
        recordChange(p, "REVOKE");
        plugin.getDiscordWebhook().sendRevocation(p, revokedBy);
        return true;
    }

    public boolean unbanIp(String ip, String revokedBy, String revokeReason) throws SQLException {
        Optional<Punishment> opt = plugin.getPunishmentRepository().findActiveIpBan(ip);
        if (opt.isEmpty()) return false;
        Punishment p = opt.get();
        plugin.getPunishmentRepository().deactivate(p.getId(), revokedBy, revokeReason);
        recordChange(p, "REVOKE");
        plugin.getDiscordWebhook().sendRevocation(p, revokedBy);
        return true;
    }

    public boolean unmute(String name, String revokedBy) throws SQLException {
        Optional<Punishment> opt = plugin.getPunishmentRepository().findActiveByName(name, PunishmentType.MUTE);
        if (opt.isEmpty()) return false;
        Punishment p = opt.get();
        plugin.getPunishmentRepository().deactivate(p.getId(), revokedBy, "размут");
        recordChange(p, "REVOKE");
        plugin.getDiscordWebhook().sendRevocation(p, revokedBy);
        return true;
    }

    public boolean unwarn(UUID targetUuid, long warnId, String revokedBy) throws SQLException {
        Optional<Punishment> opt = plugin.getPunishmentRepository().findById(warnId);
        if (opt.isEmpty()) return false;
        Punishment p = opt.get();
        if (p.getType() != PunishmentType.WARN) return false;
        if (!p.isActive()) return false;
        if (!targetUuid.equals(p.getTargetUuid())) return false;
        plugin.getPunishmentRepository().deactivate(p.getId(), revokedBy, "снятие варна");
        recordChange(p, "REVOKE");
        return true;
    }

    public void unwarnAll(UUID targetUuid, String revokedBy) throws SQLException {
        plugin.getPunishmentRepository().deactivateAllWarns(targetUuid, revokedBy);
    }

    public List<Punishment> getActiveWarns(UUID uuid) throws SQLException {
        return plugin.getPunishmentRepository().findActiveWarns(uuid);
    }

    private Punishment newBase(PunishmentType type, OfflinePlayer target, String reason,
                                String issuerName, UUID issuerUuid) {
        Punishment p = new Punishment();
        p.setType(type);
        p.setTargetUuid(target.getUniqueId());
        p.setTargetName(target.getName() != null ? target.getName() : "?");
        if (target instanceof Player pl && pl.getAddress() != null) {
            p.setTargetIp(pl.getAddress().getAddress().getHostAddress());
        }
        p.setReason(reason);
        p.setIssuedByName(issuerName);
        p.setIssuedByUuid(issuerUuid);
        p.setIssuedAt(Instant.now());
        p.setActive(true);
        p.setAppealId(newAppealId());
        p.setServerName(plugin.getConfigManager().getNetworkServerName());
        return p;
    }

    private String newAppealId() {
        char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
        StringBuilder id = new StringBuilder(8);
        for (int i = 0; i < 8; i++) id.append(alphabet[random.nextInt(alphabet.length)]);
        return id.toString();
    }

    private void recordChange(Punishment punishment, String action) throws SQLException {
        plugin.getNetworkLogRepository().append(punishment.getId(), action, plugin.getConfigManager().getNetworkServerName());
    }

    private void kickIfOnline(OfflinePlayer target, net.kyori.adventure.text.Component message) {
        if (target instanceof Player pl && pl.isOnline()) {
            plugin.getScheduler().entity(pl, () -> pl.kick(message));
        } else {
            plugin.getScheduler().global(() -> Bukkit.getOnlinePlayers().stream()
                    .filter(pl -> pl.getUniqueId().equals(target.getUniqueId()))
                    .findFirst()
                    .ifPresent(pl -> plugin.getScheduler().entity(pl, () -> pl.kick(message))));
        }
    }

    public net.kyori.adventure.text.Component buildBanKickComponent(Punishment p) {
        String template = p.isPermanent()
                ? plugin.getConfigManager().getPermanentBanKickMessage()
                : plugin.getConfigManager().getBanKickMessage();
        String expires = p.isPermanent() ? "—" : DurationParser.formatExpiresAt(p.getExpiresAt());
        String formatted = template
                .replace("<reason>", p.getReason() != null ? p.getReason() : "—")
                .replace("<expires>", expires)
                .replace("<issued_by>", p.getIssuedByName())
                .replace("<appeal_id>", p.getAppealId() == null ? "-" : p.getAppealId())
                .replace("<server_name>", plugin.getConfigManager().getServerName())
                .replace("<support_link>", plugin.getConfigManager().getSupportLink());
        return mm.deserialize(formatted);
    }

    public net.kyori.adventure.text.Component buildKickComponent(Punishment p) {
        String template = plugin.getConfigManager().getKickMessage();
        String formatted = template
                .replace("<reason>", p.getReason() != null ? p.getReason() : "—")
                .replace("<issued_by>", p.getIssuedByName())
                .replace("<server_name>", plugin.getConfigManager().getServerName())
                .replace("<support_link>", plugin.getConfigManager().getSupportLink());
        return mm.deserialize(formatted);
    }
}
