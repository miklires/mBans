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
        String ip = target instanceof Player pl && pl.getAddress() != null
                ? pl.getAddress().getAddress().getHostAddress() : null;
        return ban(target.getUniqueId(), target.getName(), ip, duration, reason, issuerName, issuerUuid);
    }

    public Punishment ban(UUID targetUuid, String targetName, String targetIp, Duration duration, String reason,
                          String issuerName, UUID issuerUuid) throws SQLException {
        return ban(targetUuid, targetName, targetIp, duration, reason, issuerName, issuerUuid, false, null);
    }

    public Punishment ban(UUID targetUuid, String targetName, String targetIp, Duration duration, String reason,
                          String issuerName, UUID issuerUuid, boolean silent, String evidence) throws SQLException {
        Punishment p = newBase(PunishmentType.BAN, targetUuid, targetName, targetIp, reason, issuerName, issuerUuid);
        p.setSilent(silent);
        p.setEvidence(evidence);
        if (duration != null) p.setExpiresAt(Instant.now().plus(duration));
        plugin.getPunishmentRepository().insert(p);
        recordChange(p, "CREATE");
        kickIfOnline(targetUuid, buildBanKickComponent(p));
        if (!silent) {
            plugin.getDiscordWebhook().sendPunishment(p);
            broadcast(p);
        }
        return p;
    }

    public Punishment ipBan(String ip, String targetName, UUID targetUuid, Duration duration,
                            String reason, String issuerName, UUID issuerUuid) throws SQLException {
        return ipBan(ip, targetName, targetUuid, duration, reason, issuerName, issuerUuid, false, null);
    }

    public Punishment ipBan(String ip, String targetName, UUID targetUuid, Duration duration,
                            String reason, String issuerName, UUID issuerUuid, boolean silent, String evidence) throws SQLException {
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
        p.setSilent(silent);
        p.setEvidence(evidence);
        if (duration != null) p.setExpiresAt(Instant.now().plus(duration));
        plugin.getPunishmentRepository().insert(p);
        recordChange(p, "CREATE");

        plugin.getScheduler().global(() -> Bukkit.getOnlinePlayers().stream()
                .filter(pl -> pl.getAddress() != null && ip.equals(pl.getAddress().getAddress().getHostAddress()))
                .forEach(pl -> plugin.getScheduler().entity(pl, () -> pl.kick(buildBanKickComponent(p)))));
        if (!silent) {
            plugin.getDiscordWebhook().sendPunishment(p);
            broadcast(p);
        }
        return p;
    }

    public Punishment mute(OfflinePlayer target, Duration duration, String reason,
                           String issuerName, UUID issuerUuid) throws SQLException {
        String ip = target instanceof Player pl && pl.getAddress() != null
                ? pl.getAddress().getAddress().getHostAddress() : null;
        return mute(target.getUniqueId(), target.getName(), ip, duration, reason, issuerName, issuerUuid);
    }

    public Punishment mute(UUID targetUuid, String targetName, String targetIp, Duration duration, String reason,
                           String issuerName, UUID issuerUuid) throws SQLException {
        return mute(targetUuid, targetName, targetIp, duration, reason, issuerName, issuerUuid, false, null);
    }

    public Punishment mute(UUID targetUuid, String targetName, String targetIp, Duration duration, String reason,
                           String issuerName, UUID issuerUuid, boolean silent, String evidence) throws SQLException {
        Punishment p = newBase(PunishmentType.MUTE, targetUuid, targetName, targetIp, reason, issuerName, issuerUuid);
        p.setSilent(silent);
        p.setEvidence(evidence);
        if (duration != null) p.setExpiresAt(Instant.now().plus(duration));
        plugin.getPunishmentRepository().insert(p);
        recordChange(p, "CREATE");
        if (!silent) {
            plugin.getDiscordWebhook().sendPunishment(p);
            broadcast(p);
        }
        return p;
    }

    public Punishment kick(Player target, String reason, String issuerName, UUID issuerUuid) throws SQLException {
        String ip = target.getAddress() == null ? null : target.getAddress().getAddress().getHostAddress();
        return kick(target.getUniqueId(), target.getName(), ip, reason, issuerName, issuerUuid);
    }

    public Punishment kick(UUID targetUuid, String targetName, String targetIp, String reason,
                           String issuerName, UUID issuerUuid) throws SQLException {
        Punishment p = newBase(PunishmentType.KICK, targetUuid, targetName, targetIp, reason, issuerName, issuerUuid);
        plugin.getPunishmentRepository().insert(p);
        recordChange(p, "CREATE");
        kickIfOnline(targetUuid, buildKickComponent(p));
        plugin.getDiscordWebhook().sendPunishment(p);
        broadcast(p);
        return p;
    }

    public Punishment warn(OfflinePlayer target, String reason,
                           String issuerName, UUID issuerUuid) throws SQLException {
        String ip = target instanceof Player pl && pl.getAddress() != null
                ? pl.getAddress().getAddress().getHostAddress() : null;
        return warn(target.getUniqueId(), target.getName(), ip, reason, issuerName, issuerUuid);
    }

    public Punishment warn(UUID targetUuid, String targetName, String targetIp, String reason,
                           String issuerName, UUID issuerUuid) throws SQLException {
        return warn(targetUuid, targetName, targetIp, reason, issuerName, issuerUuid, false, null);
    }

    public Punishment warn(UUID targetUuid, String targetName, String targetIp, String reason,
                           String issuerName, UUID issuerUuid, boolean silent, String evidence) throws SQLException {
        Punishment p = newBase(PunishmentType.WARN, targetUuid, targetName, targetIp, reason, issuerName, issuerUuid);
        p.setSilent(silent);
        p.setEvidence(evidence);
        plugin.getPunishmentRepository().insert(p);
        recordChange(p, "CREATE");
        if (!silent) {
            plugin.getDiscordWebhook().sendPunishment(p);
            broadcast(p);
        }

        if (targetUuid != null) {
            int total = plugin.getPunishmentRepository().countActiveWarns(targetUuid);
            int threshold = plugin.getConfigManager().getAutoBanThreshold();
            if (threshold > 0 && total >= threshold
                    && plugin.getPunishmentRepository().findActiveByUuid(targetUuid, PunishmentType.BAN).isEmpty()) {
                autoBan(targetUuid, targetName, targetIp, total, issuerName);
            }
            applyEscalation(targetUuid, targetName, targetIp, PunishmentType.WARN, issuerName);
        }
        return p;
    }

    private void applyEscalation(UUID targetUuid, String targetName, String targetIp,
                                 PunishmentType source, String issuer) throws SQLException {
        for (io.github.miklires.mbans.config.ConfigManager.EscalationRule rule
                : plugin.getConfigManager().getEscalationRules()) {
            if (rule.source() != source) continue;
            int count = plugin.getPunishmentRepository().countSince(targetUuid, source,
                    Instant.now().minus(rule.window()));
            if (count < rule.count()) continue;
            if (plugin.getPunishmentRepository().findActiveByUuid(targetUuid, rule.action()).isPresent()) continue;
            String reason = rule.reason().replace("<count>", String.valueOf(count))
                    .replace("{count}", String.valueOf(count));
            if (rule.action() == PunishmentType.BAN) {
                ban(targetUuid, targetName, targetIp, rule.duration(), reason, "AUTO (" + issuer + ")", null);
            } else if (rule.action() == PunishmentType.MUTE) {
                mute(targetUuid, targetName, targetIp, rule.duration(), reason, "AUTO (" + issuer + ")", null);
            }
        }
    }

    private void autoBan(UUID targetUuid, String targetName, String targetIp, int warnCount, String issuer) throws SQLException {
        String reason = plugin.getConfigManager().getAutoBanReason()
                .replace("{count}", String.valueOf(warnCount)).replace("<count>", String.valueOf(warnCount));
        Duration dur = DurationParser.parse(plugin.getConfigManager().getAutoBanDuration()).orElse(null);
        ban(targetUuid, targetName, targetIp, dur, reason, "AUTO (" + issuer + ")", null);
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
        plugin.getPunishmentRepository().deactivate(p.getId(), revokedBy, "mute removed");
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
        plugin.getPunishmentRepository().deactivate(p.getId(), revokedBy, "warning removed");
        recordChange(p, "REVOKE");
        return true;
    }

    public void unwarnAll(UUID targetUuid, String revokedBy) throws SQLException {
        plugin.getPunishmentRepository().deactivateAllWarns(targetUuid, revokedBy);
    }

    public List<Punishment> getActiveWarns(UUID uuid) throws SQLException {
        return plugin.getPunishmentRepository().findActiveWarns(uuid);
    }

    private Punishment newBase(PunishmentType type, UUID targetUuid, String targetName, String targetIp,
                                String reason, String issuerName, UUID issuerUuid) {
        Punishment p = new Punishment();
        p.setType(type);
        p.setTargetUuid(targetUuid);
        p.setTargetName(targetName == null ? "?" : targetName);
        p.setTargetIp(targetIp);
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

    public void broadcast(Punishment punishment) {
        if (punishment.isSilent() || !plugin.getConfigManager().isBroadcastEnabled()) return;
        String key = "broadcast." + punishment.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        plugin.getScheduler().global(() -> Bukkit.getOnlinePlayers().forEach(player ->
                plugin.getMessageUtil().send(player, key,
                        io.github.miklires.mbans.util.MessageUtil.ph("player", punishment.getTargetName()),
                        io.github.miklires.mbans.util.MessageUtil.ph("reason", punishment.getReason()),
                        io.github.miklires.mbans.util.MessageUtil.ph("issuer", punishment.getIssuedByName()),
                        io.github.miklires.mbans.util.MessageUtil.ph("duration", punishment.isPermanent() ? "permanent"
                                : DurationParser.format(Duration.between(punishment.getIssuedAt(), punishment.getExpiresAt()))))));
    }

    private void kickIfOnline(UUID targetUuid, net.kyori.adventure.text.Component message) {
        if (targetUuid == null) return;
        plugin.getScheduler().global(() -> Bukkit.getOnlinePlayers().stream()
                .filter(pl -> pl.getUniqueId().equals(targetUuid))
                .findFirst()
                .ifPresent(pl -> plugin.getScheduler().entity(pl, () -> pl.kick(message))));
    }

    public net.kyori.adventure.text.Component buildBanKickComponent(Punishment p) {
        String template = p.isPermanent()
                ? plugin.getConfigManager().getPermanentBanKickMessage()
                : plugin.getConfigManager().getBanKickMessage();
        String expires = p.isPermanent() ? "never" : DurationParser.formatExpiresAt(p.getExpiresAt());
        String formatted = template
                .replace("<reason>", p.getReason() != null ? p.getReason() : "Not specified")
                .replace("<expires>", expires)
                .replace("<issued_by>", p.getIssuedByName())
                .replace("<appeal_id>", p.getAppealId() == null ? "-" : p.getAppealId())
                .replace("<evidence>", p.getEvidence() == null ? "-" : p.getEvidence())
                .replace("<server_name>", plugin.getConfigManager().getServerName())
                .replace("<support_link>", plugin.getConfigManager().getSupportLink());
        return mm.deserialize(formatted);
    }

    public net.kyori.adventure.text.Component buildKickComponent(Punishment p) {
        String template = plugin.getConfigManager().getKickMessage();
        String formatted = template
                .replace("<reason>", p.getReason() != null ? p.getReason() : "Not specified")
                .replace("<issued_by>", p.getIssuedByName())
                .replace("<server_name>", plugin.getConfigManager().getServerName())
                .replace("<support_link>", plugin.getConfigManager().getSupportLink());
        return mm.deserialize(formatted);
    }
}
