package io.github.miklires.mbans.velocity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record VelocityConfig(String jdbcUrl, String user, String password, int poolSize, int bstatsId) {

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
        try (var writer = Files.newBufferedWriter(file)) {
            values.store(writer, "mBans Velocity");
        }
        String url = values.getProperty("jdbc-url").trim();
        if (!url.startsWith("jdbc:mysql:") && !url.startsWith("jdbc:mariadb:") && !url.startsWith("jdbc:postgresql:")) {
            throw new IllegalArgumentException("jdbc-url must use MySQL, MariaDB or PostgreSQL");
        }
        return new VelocityConfig(url, values.getProperty("user"), values.getProperty("password"),
                boundedInt(values.getProperty("pool-size"), 1, 16, 3),
                boundedInt(values.getProperty("bstats-id"), 0, Integer.MAX_VALUE, 0));
    }

    private static int boundedInt(String value, int min, int max, int fallback) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
