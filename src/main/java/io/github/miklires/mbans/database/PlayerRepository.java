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
        String sql = "INSERT INTO mbans_player_history (player_uuid, player_name, player_ip, seen_at) VALUES (?, ?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, ip);
            ps.setLong(4, Instant.now().getEpochSecond());
            ps.executeUpdate();
        }
    }

    public Optional<PlayerIdentity> findByName(String name) throws SQLException {
        String sql = "SELECT player_uuid, player_name, player_ip FROM mbans_player_history WHERE LOWER(player_name) = LOWER(?) ORDER BY seen_at DESC LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public Optional<PlayerIdentity> findByUuid(UUID uuid) throws SQLException {
        String sql = "SELECT player_uuid, player_name, player_ip FROM mbans_player_history WHERE player_uuid = ? ORDER BY seen_at DESC LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public List<PlayerIdentity> findByIp(String ip, int limit) throws SQLException {
        String sql = "SELECT player_uuid, MAX(player_name) AS player_name, MAX(player_ip) AS player_ip FROM mbans_player_history WHERE player_ip = ? GROUP BY player_uuid LIMIT ?";
        List<PlayerIdentity> players = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ip);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) players.add(map(rs));
            }
        }
        return players;
    }

    private PlayerIdentity map(ResultSet rs) throws SQLException {
        return new PlayerIdentity(UUID.fromString(rs.getString("player_uuid")), rs.getString("player_name"), rs.getString("player_ip"));
    }

    public record PlayerIdentity(UUID uuid, String name, String ip) {}
}
