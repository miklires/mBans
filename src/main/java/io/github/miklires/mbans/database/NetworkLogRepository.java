package io.github.miklires.mbans.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class NetworkLogRepository {

    private final DatabaseManager db;

    public NetworkLogRepository(DatabaseManager db) {
        this.db = db;
    }

    public long append(long punishmentId, String action, String serverName) throws SQLException {
        String sql = "INSERT INTO mbans_punishment_log (punishment_id, action, server_name, created_at) VALUES (?, ?, ?, ?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, punishmentId);
            ps.setString(2, action);
            ps.setString(3, serverName);
            ps.setLong(4, Instant.now().getEpochSecond());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0;
            }
        }
    }

    public List<NetworkEvent> after(long id, int limit) throws SQLException {
        String sql = "SELECT id, punishment_id, action, server_name, created_at FROM mbans_punishment_log WHERE id > ? ORDER BY id LIMIT ?";
        List<NetworkEvent> events = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(new NetworkEvent(rs.getLong("id"), rs.getLong("punishment_id"), rs.getString("action"),
                            rs.getString("server_name"), rs.getLong("created_at")));
                }
            }
        }
        return events;
    }

    public long latestId() throws SQLException {
        try (Connection c = db.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT MAX(id) FROM mbans_punishment_log")) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public record NetworkEvent(long id, long punishmentId, String action, String serverName, long createdAt) {}
}
