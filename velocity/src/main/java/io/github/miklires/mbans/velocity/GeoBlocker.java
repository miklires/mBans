package io.github.miklires.mbans.velocity;

import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class GeoBlocker implements AutoCloseable {
    private final DatabaseReader reader;
    private final Set<String> allowed;
    private final Set<String> blocked;

    GeoBlocker(Path dataDirectory, VelocityConfig config) throws Exception {
        if (!config.geoIpEnabled()) {
            reader = null;
            allowed = Set.of();
            blocked = Set.of();
            return;
        }
        Path file = dataDirectory.resolve(config.geoIpDatabase()).normalize();
        if (!file.startsWith(dataDirectory.normalize()) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("GeoIP MMDB file is missing: " + file);
        }
        reader = new DatabaseReader.Builder(file.toFile()).fileMode(Reader.FileMode.MEMORY).build();
        allowed = countries(config.geoIpAllowed());
        blocked = countries(config.geoIpBlocked());
    }

    boolean denied(InetAddress address) {
        if (reader == null || address.isLoopbackAddress() || address.isSiteLocalAddress()) return false;
        try {
            String code = reader.tryCountry(address).map(value -> value.country().isoCode()).orElse(null);
            if (code == null) return false;
            code = code.toUpperCase(Locale.ROOT);
            return (!allowed.isEmpty() && !allowed.contains(code)) || blocked.contains(code);
        } catch (Exception ignored) {
            return false;
        }
    }

    private Set<String> countries(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return java.util.Arrays.stream(csv.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    @Override public void close() throws Exception { if (reader != null) reader.close(); }
}
