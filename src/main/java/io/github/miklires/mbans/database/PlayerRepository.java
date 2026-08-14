package io.github.miklires.mbans.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PlayerRepository {

    private final DatabaseManager db;

    public PlayerRepository(DatabaseManager db) {
        this.db = db;
    }

    public void record(UUID uuid, String name, String ip) throws SQLException {
        record(uuid, name, ip, 0);
    }

    public void record(UUID uuid, String name, String ip, int immunityLevel) throws SQLException {
        String latest = "SELECT player_name, player_ip, immunity_level FROM mbans_player_history WHERE player_uuid = ? ORDER BY seen_at DESC LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(latest)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && name.equals(rs.getString("player_name"))
                        && java.util.Objects.equals(ip, rs.getString("player_ip"))
                        && immunityLevel == rs.getInt("immunity_level")) return;
            }
        }
        String sql = "INSERT INTO mbans_player_history (player_uuid, player_name, player_ip, seen_at, immunity_level) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, ip);
            ps.setLong(4, Instant.now().getEpochSecond());
            ps.setInt(5, Math.max(0, immunityLevel));
            ps.executeUpdate();
        }
    }

    public Optional<PlayerIdentity> findByName(String name) throws SQLException {
        String sql = "SELECT player_uuid, player_name, player_ip, immunity_level FROM mbans_player_history WHERE LOWER(player_name) = LOWER(?) ORDER BY seen_at DESC LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public Optional<PlayerIdentity> findByUuid(UUID uuid) throws SQLException {
        String sql = "SELECT player_uuid, player_name, player_ip, immunity_level FROM mbans_player_history WHERE player_uuid = ? ORDER BY seen_at DESC LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public List<PlayerIdentity> findByIp(String ip, int limit) throws SQLException {
        String sql = "SELECT h.player_uuid, h.player_name, h.player_ip, h.immunity_level FROM mbans_player_history h "
                + "WHERE h.player_ip = ? AND h.seen_at = (SELECT MAX(h2.seen_at) FROM mbans_player_history h2 "
                + "WHERE h2.player_uuid = h.player_uuid AND h2.player_ip = ?) ORDER BY h.seen_at DESC LIMIT ?";
        List<PlayerIdentity> players = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ip);
            ps.setString(2, ip);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) players.add(map(rs));
            }
        }
        return players;
    }

    public List<String> findActiveBannedAlts(String ip, UUID currentUuid, int limit) throws SQLException {
        String sql = "SELECT DISTINCT p.target_name FROM mbans_punishments p "
                + "JOIN mbans_player_history h ON p.target_uuid = h.player_uuid "
                + "WHERE h.player_ip = ? AND p.target_uuid <> ? AND p.type = 'BAN' AND p.active = TRUE "
                + "AND (p.expires_at IS NULL OR p.expires_at > ?) LIMIT ?";
        List<String> names = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ip);
            ps.setString(2, currentUuid.toString());
            ps.setLong(3, Instant.now().getEpochSecond());
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
        }
        return names;
    }

    public int purgeOldIps(Instant before) throws SQLException {
        String sql = "UPDATE mbans_player_history SET player_ip = NULL WHERE player_ip IS NOT NULL AND seen_at < ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, before.getEpochSecond());
            return ps.executeUpdate();
        }
    }

    private PlayerIdentity map(ResultSet rs) throws SQLException {
        return new PlayerIdentity(UUID.fromString(rs.getString("player_uuid")), rs.getString("player_name"),
                rs.getString("player_ip"), rs.getInt("immunity_level"));
    }

    public record PlayerIdentity(UUID uuid, String name, String ip, int immunityLevel) {}
}
