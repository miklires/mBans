package io.github.miklires.mbans.velocity;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class BanStore implements AutoCloseable {

    private final HikariDataSource dataSource;

    public BanStore(VelocityConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.user());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.poolSize());
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(10000);
        hikari.setPoolName("mBans-Velocity");
        dataSource = new HikariDataSource(hikari);
    }

    public Optional<Ban> find(UUID uuid, String ip) throws SQLException {
        String sql = "SELECT id, reason, issued_by_name, expires_at, appeal_id FROM mbans_punishments "
                + "WHERE active = TRUE AND ((type = 'BAN' AND target_uuid = ?) OR (type = 'IP_BAN' AND target_ip = ?)) "
                + "ORDER BY issued_at DESC LIMIT 1";
        try (var connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                long expires = rs.getLong("expires_at");
                boolean permanent = rs.wasNull();
                if (!permanent && expires <= Instant.now().getEpochSecond()) return Optional.empty();
                return Optional.of(new Ban(rs.getLong("id"), rs.getString("reason"), rs.getString("issued_by_name"),
                        permanent ? null : expires, rs.getString("appeal_id")));
            }
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }

    public record Ban(long id, String reason, String issuer, Long expiresAt, String appealId) {}
}
