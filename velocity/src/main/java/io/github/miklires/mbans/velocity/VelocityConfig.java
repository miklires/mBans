package io.github.miklires.mbans.velocity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record VelocityConfig(String jdbcUrl, String user, String password, int poolSize, int bstatsId,
                             boolean geoIpEnabled, String geoIpDatabase, String geoIpAllowed,
                             String geoIpBlocked, String geoIpDeniedMessage) {

    public static VelocityConfig load(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("config.properties");
        Properties values = new Properties();
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file)) {
                values.load(reader);
            }
        }
        values.putIfAbsent("jdbc-url", "jdbc:mysql://127.0.0.1:3306/mbans?useSSL=false&serverTimezone=UTC");
        values.putIfAbsent("user", "mbans");
        values.putIfAbsent("password", "");
        values.putIfAbsent("pool-size", "3");
        values.putIfAbsent("bstats-id", "0");
        values.putIfAbsent("geoip-enabled", "false");
        values.putIfAbsent("geoip-database", "GeoLite2-Country.mmdb");
        values.putIfAbsent("geoip-allowed-countries", "");
        values.putIfAbsent("geoip-blocked-countries", "");
        values.putIfAbsent("geoip-denied-message", "Your region is not allowed on this network.");
        try (var writer = Files.newBufferedWriter(file)) {
            values.store(writer, "mBans Velocity");
        }
        String url = values.getProperty("jdbc-url").trim();
        if (!url.startsWith("jdbc:mysql:") && !url.startsWith("jdbc:mariadb:")
                && !url.startsWith("jdbc:postgresql:") && !url.startsWith("jdbc:h2:")) {
            throw new IllegalArgumentException("jdbc-url must use H2, MySQL, MariaDB or PostgreSQL");
        }
        return new VelocityConfig(url, values.getProperty("user"), values.getProperty("password"),
                boundedInt(values.getProperty("pool-size"), 1, 16, 3),
                boundedInt(values.getProperty("bstats-id"), 0, Integer.MAX_VALUE, 0),
                Boolean.parseBoolean(values.getProperty("geoip-enabled")),
                values.getProperty("geoip-database"), values.getProperty("geoip-allowed-countries"),
                values.getProperty("geoip-blocked-countries"), values.getProperty("geoip-denied-message"));
    }

    private static int boundedInt(String value, int min, int max, int fallback) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
