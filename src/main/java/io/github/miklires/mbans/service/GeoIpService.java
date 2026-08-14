package io.github.miklires.mbans.service;

import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import io.github.miklires.mbans.MBans;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Set;
import java.util.Optional;

public final class GeoIpService {
    private final MBans plugin;
    private DatabaseReader reader;

    public GeoIpService(MBans plugin) { this.plugin = plugin; }

    public void start() {
        if (!plugin.getConfigManager().isGeoIpEnabled()) return;
        File file = new File(plugin.getDataFolder(), plugin.getConfigManager().getGeoIpDatabase());
        if (!file.isFile()) {
            plugin.getLogger().warning("GeoIP is enabled but the MMDB file is missing: " + file.getAbsolutePath());
            return;
        }
        try {
            reader = new DatabaseReader.Builder(file).fileMode(Reader.FileMode.MEMORY).build();
            plugin.getLogger().info("GeoIP country filtering enabled");
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().warning("Could not open GeoIP database: " + e.getMessage());
        }
    }

    public boolean isDenied(InetAddress address) {
        if (reader == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isSiteLocalAddress()) return false;
        try {
            String code = reader.tryCountry(address).map(response -> response.country().isoCode()).orElse(null);
            if (code == null) return false;
            code = code.toUpperCase(Locale.ROOT);
            Set<String> allowed = plugin.getConfigManager().getGeoIpAllowedCountries();
            return (!allowed.isEmpty() && !allowed.contains(code))
                    || plugin.getConfigManager().getGeoIpBlockedCountries().contains(code);
        } catch (Exception e) {
            plugin.getLogger().fine("GeoIP lookup failed: " + e.getMessage());
            return false;
        }
    }

    public Optional<String> country(String address) {
        if (reader == null || address == null) return Optional.empty();
        try {
            InetAddress ip = InetAddress.getByName(address);
            if (ip.isLoopbackAddress() || ip.isSiteLocalAddress()) return Optional.empty();
            return reader.tryCountry(ip).map(response -> response.country().isoCode());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public void stop() {
        if (reader == null) return;
        try { reader.close(); }
        catch (IOException e) { plugin.getLogger().fine("Could not close GeoIP database: " + e.getMessage()); }
    }
}
