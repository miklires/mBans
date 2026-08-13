package io.github.miklires.mbans.database;

import io.github.miklires.mbans.model.Punishment;
import io.github.miklires.mbans.model.PunishmentType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PunishmentRepository {

    private final DatabaseManager db;

    public PunishmentRepository(DatabaseManager db) {
        this.db = db;
    }

    public long insert(Punishment p) throws SQLException {
        String sql = """
            INSERT INTO mbans_punishments
                (type, target_uuid, target_name, target_ip, reason,
                 issued_by_uuid, issued_by_name, issued_at, expires_at, active,
                 evidence, appeal_id, silent, server_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getType().name());
            ps.setString(2, p.getTargetUuid() != null ? p.getTargetUuid().toString() : null);
            ps.setString(3, p.getTargetName() != null ? p.getTargetName().toLowerCase() : null);
            ps.setString(4, p.getTargetIp());
            ps.setString(5, p.getReason());
            ps.setString(6, p.getIssuedByUuid() != null ? p.getIssuedByUuid().toString() : null);
            ps.setString(7, p.getIssuedByName());
            ps.setLong(8, p.getIssuedAt().getEpochSecond());
            if (p.getExpiresAt() != null) ps.setLong(9, p.getExpiresAt().getEpochSecond());
            else ps.setNull(9, Types.BIGINT);
            ps.setBoolean(10, p.isActive());
            ps.setString(11, p.getEvidence());
            ps.setString(12, p.getAppealId());
            ps.setBoolean(13, p.isSilent());
            ps.setString(14, p.getServerName());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    p.setId(id);
                    return id;
                }
            }
            return -1;
        }
    }

    public Optional<Punishment> findActiveByUuid(UUID uuid, PunishmentType type) throws SQLException {
        String sql = """
            SELECT * FROM mbans_punishments
            WHERE target_uuid = ? AND type = ? AND active = TRUE
            ORDER BY issued_at DESC LIMIT 1
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Punishment p = map(rs);
                    if (p.getExpiresAt() != null && Instant.now().isAfter(p.getExpiresAt())) {
                        deactivate(p.getId(), "система", "истёк по времени");
                        return Optional.empty();
                    }
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Punishment> findActiveByName(String name, PunishmentType type) throws SQLException {
        String sql = """
            SELECT * FROM mbans_punishments
            WHERE target_name = ? AND type = ? AND active = TRUE
            ORDER BY issued_at DESC LIMIT 1
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name.toLowerCase());
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Punishment p = map(rs);
                    if (p.getExpiresAt() != null && Instant.now().isAfter(p.getExpiresAt())) {
                        deactivate(p.getId(), "система", "истёк по времени");
                        return Optional.empty();
                    }
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Punishment> findActiveIpBan(String ip) throws SQLException {
        String sql = """
            SELECT * FROM mbans_punishments
            WHERE target_ip = ? AND type = 'IP_BAN' AND active = TRUE
            ORDER BY issued_at DESC LIMIT 1
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Punishment p = map(rs);
                    if (p.getExpiresAt() != null && Instant.now().isAfter(p.getExpiresAt())) {
                        deactivate(p.getId(), "система", "истёк по времени");
                        return Optional.empty();
                    }
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Punishment> findById(long id) throws SQLException {
        String sql = "SELECT * FROM mbans_punishments WHERE id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public int countActiveWarns(UUID uuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM mbans_punishments WHERE target_uuid = ? AND type = 'WARN' AND active = TRUE";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public List<Punishment> findActiveWarns(UUID uuid) throws SQLException {
        String sql = """
            SELECT * FROM mbans_punishments
            WHERE target_uuid = ? AND type = 'WARN' AND active = TRUE
            ORDER BY issued_at DESC
            """;
        List<Punishment> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public List<Punishment> getHistory(String name, int limit, int offset) throws SQLException {
        String sql = """
            SELECT * FROM mbans_punishments
            WHERE target_name = ?
            ORDER BY issued_at DESC
            LIMIT ? OFFSET ?
            """;
        List<Punishment> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name.toLowerCase());
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public List<Punishment> getStaffHistory(String issuerName, int limit, int offset) throws SQLException {
        String sql = """
            SELECT * FROM mbans_punishments
            WHERE issued_by_name = ?
            ORDER BY issued_at DESC
            LIMIT ? OFFSET ?
            """;
        List<Punishment> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, issuerName);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public List<Punishment> getActiveBans(int limit, int offset) throws SQLException {
        String sql = """
            SELECT * FROM mbans_punishments
            WHERE (type = 'BAN' OR type = 'IP_BAN') AND active = TRUE
            ORDER BY issued_at DESC
            LIMIT ? OFFSET ?
            """;
        List<Punishment> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public void deactivate(long id, String revokedBy, String revokeReason) throws SQLException {
        String sql = """
            UPDATE mbans_punishments
            SET active = FALSE, revoked_by_name = ?, revoked_at = ?, revoke_reason = ?
            WHERE id = ?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, revokedBy);
            ps.setLong(2, Instant.now().getEpochSecond());
            ps.setString(3, revokeReason);
            ps.setLong(4, id);
            ps.executeUpdate();
        }
    }

    public void deactivateAllWarns(UUID uuid, String revokedBy) throws SQLException {
        String sql = """
            UPDATE mbans_punishments
            SET active = FALSE, revoked_by_name = ?, revoked_at = ?, revoke_reason = ?
            WHERE target_uuid = ? AND type = 'WARN' AND active = TRUE
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, revokedBy);
            ps.setLong(2, Instant.now().getEpochSecond());
            ps.setString(3, "снятие всех варнов");
            ps.setString(4, uuid.toString());
            ps.executeUpdate();
        }
    }

    private Punishment map(ResultSet rs) throws SQLException {
        Punishment p = new Punishment();
        p.setId(rs.getLong("id"));
        p.setType(PunishmentType.valueOf(rs.getString("type")));
        String uuidStr = rs.getString("target_uuid");
        if (uuidStr != null) p.setTargetUuid(UUID.fromString(uuidStr));
        p.setTargetName(rs.getString("target_name"));
        p.setTargetIp(rs.getString("target_ip"));
        p.setReason(rs.getString("reason"));
        String issuerUuid = rs.getString("issued_by_uuid");
        if (issuerUuid != null) p.setIssuedByUuid(UUID.fromString(issuerUuid));
        p.setIssuedByName(rs.getString("issued_by_name"));
        p.setIssuedAt(Instant.ofEpochSecond(rs.getLong("issued_at")));
        long expires = rs.getLong("expires_at");
        if (!rs.wasNull()) p.setExpiresAt(Instant.ofEpochSecond(expires));
        p.setActive(rs.getBoolean("active"));
        p.setRevokedByName(rs.getString("revoked_by_name"));
        long revokedAt = rs.getLong("revoked_at");
        if (!rs.wasNull()) p.setRevokedAt(Instant.ofEpochSecond(revokedAt));
        p.setRevokeReason(rs.getString("revoke_reason"));
        p.setEvidence(rs.getString("evidence"));
        p.setAppealId(rs.getString("appeal_id"));
        p.setSilent(rs.getBoolean("silent"));
        p.setServerName(rs.getString("server_name"));
        return p;
    }
}
