package io.github.miklires.mbans.service;

import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.database.PlayerRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

public final class ProfileResolver {
    private static final Pattern ID = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([0-9a-fA-F]{32})\\\"");
    private static final Pattern NAME = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([A-Za-z0-9_]{1,16})\\\"");
    private final MBans plugin;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, CachedProfile> cache = new ConcurrentHashMap<>();
    private final AtomicLong nextRequest = new AtomicLong();

    public ProfileResolver(MBans plugin) { this.plugin = plugin; }

    public Optional<PlayerRepository.PlayerIdentity> resolve(String name) {
        if (!plugin.getConfigManager().isMojangLookupEnabled() || !name.matches("[A-Za-z0-9_]{1,16}")) {
            return Optional.empty();
        }
        String key = name.toLowerCase(java.util.Locale.ROOT);
        CachedProfile cached = cache.get(key);
        long now = System.nanoTime();
        if (cached != null && cached.expiresAtNanos() > now) return Optional.ofNullable(cached.value());
        long allowedAt = nextRequest.get();
        if (now < allowedAt || !nextRequest.compareAndSet(allowedAt, now + Duration.ofSeconds(1).toNanos())) {
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
                    .timeout(Duration.ofSeconds(plugin.getConfigManager().getMojangTimeoutSeconds()))
                    .header("Accept", "application/json")
                    .header("User-Agent", "mBans/" + plugin.getPluginMeta().getVersion())
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                cache.put(key, new CachedProfile(null, now + Duration.ofMinutes(5).toNanos()));
                return Optional.empty();
            }
            Matcher id = ID.matcher(response.body());
            Matcher profileName = NAME.matcher(response.body());
            if (!id.find() || !profileName.find()) return Optional.empty();
            String raw = id.group(1);
            UUID uuid = UUID.fromString(raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-"
                    + raw.substring(12, 16) + "-" + raw.substring(16, 20) + "-" + raw.substring(20));
            PlayerRepository.PlayerIdentity identity = new PlayerRepository.PlayerIdentity(uuid, profileName.group(1), null, 0);
            plugin.getPlayerRepository().record(uuid, identity.name(), null);
            cache.put(key, new CachedProfile(identity, now + Duration.ofDays(1).toNanos()));
            return Optional.of(identity);
        } catch (Exception e) {
            plugin.getLogger().fine("Mojang profile lookup failed for " + name + ": " + e.getMessage());
            cache.put(key, new CachedProfile(null, now + Duration.ofMinutes(1).toNanos()));
            return Optional.empty();
        }
    }

    private record CachedProfile(PlayerRepository.PlayerIdentity value, long expiresAtNanos) {}
}
