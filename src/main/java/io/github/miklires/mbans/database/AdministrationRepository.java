package io.github.miklires.mbans.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdministrationRepository {

    private final DatabaseManager db;

    public AdministrationRepository(DatabaseManager db) {
        this.db = db;
    }

    public List<Long> rollback(String staff, Instant since, String revokedBy) throws SQLException {
        String find = "SELECT id FROM mbans_punishments WHERE LOWER(issued_by_name) = LOWER(?) AND issued_at >= ? AND active = TRUE";
        List<Long> ids = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(find)) {
            ps.setString(1, staff);
            ps.setLong(2, since.getEpochSecond());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        String update = "UPDATE mbans_punishments SET active = FALSE, revoked_by_name = ?, revoked_at = ?, revoke_reason = ? WHERE id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(update)) {
            for (long id : ids) {
                ps.setString(1, revokedBy);
                ps.setLong(2, Instant.now().getEpochSecond());
                ps.setString(3, "staff rollback");
                ps.setLong(4, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return ids;
    }

    public boolean allow(long punishmentId, UUID playerUuid) throws SQLException {
        String check = "SELECT id FROM mbans_ip_allowlist WHERE punishment_id = ? AND player_uuid = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(check)) {
            ps.setLong(1, punishmentId);
            ps.setString(2, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return false;
            }
        }
        String insert = "INSERT INTO mbans_ip_allowlist (punishment_id, player_uuid) VALUES (?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(insert)) {
            ps.setLong(1, punishmentId);
            ps.setString(2, playerUuid.toString());
            ps.executeUpdate();
            return true;
        }
    }

    public boolean isAllowed(long punishmentId, UUID playerUuid) throws SQLException {
        String sql = "SELECT id FROM mbans_ip_allowlist WHERE punishment_id = ? AND player_uuid = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, punishmentId);
            ps.setString(2, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public long addNote(UUID playerUuid, UUID authorUuid, String authorName, String note) throws SQLException {
        String sql = "INSERT INTO mbans_staff_notes (player_uuid, author_uuid, author_name, note, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, authorUuid == null ? null : authorUuid.toString());
            ps.setString(3, authorName);
            ps.setString(4, note);
            ps.setLong(5, Instant.now().getEpochSecond());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public StaffStats stats(String staff) throws SQLException {
        String sql = "SELECT COUNT(*) AS total, "
                + "SUM(CASE WHEN active = FALSE AND revoked_at IS NOT NULL THEN 1 ELSE 0 END) AS revoked, "
                + "SUM(CASE WHEN type = 'BAN' THEN 1 ELSE 0 END) AS bans, "
                + "SUM(CASE WHEN type = 'MUTE' THEN 1 ELSE 0 END) AS mutes, "
                + "SUM(CASE WHEN type = 'WARN' THEN 1 ELSE 0 END) AS warns "
                + "FROM mbans_punishments WHERE LOWER(issued_by_name) = LOWER(?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, staff);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new StaffStats(rs.getInt("total"), rs.getInt("revoked"),
                        rs.getInt("bans"), rs.getInt("mutes"), rs.getInt("warns"));
            }
        }
        return new StaffStats(0, 0, 0, 0, 0);
    }

    public record StaffStats(int total, int revoked, int bans, int mutes, int warns) {}
}
