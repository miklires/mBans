package io.github.miklires.mbans.placeholder;

import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.model.PunishmentType;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MBansExpansion extends PlaceholderExpansion {

    private static final long CACHE_MILLIS = 5_000;
    private final MBans plugin;
    private final ConcurrentHashMap<UUID, State> cache = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();

    public MBansExpansion(MBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "mbans";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String parameters) {
        if (parameters.equalsIgnoreCase("version")) return getVersion();
        if (player == null) return null;

        UUID uuid = player.getUniqueId();
        State state = cache.get(uuid);
        long now = System.currentTimeMillis();
        if (state == null || now - state.loadedAt() >= CACHE_MILLIS) refresh(uuid);
        if (state == null) return "";

        return switch (parameters.toLowerCase(Locale.ROOT)) {
            case "banned" -> Boolean.toString(state.banned());
            case "muted" -> Boolean.toString(state.muted());
            case "warnings" -> Integer.toString(state.warnings());
            case "status" -> state.banned() ? "banned" : state.muted() ? "muted" : "clear";
            default -> null;
        };
    }

    private void refresh(UUID uuid) {
        if (!loading.add(uuid)) return;
        plugin.getScheduler().async(() -> {
            try {
                boolean banned = plugin.getPunishmentRepository().findActiveByUuid(uuid, PunishmentType.BAN).isPresent();
                boolean muted = plugin.getPunishmentRepository().findActiveByUuid(uuid, PunishmentType.MUTE).isPresent();
                int warnings = plugin.getPunishmentRepository().countActiveWarns(uuid);
                cache.put(uuid, new State(banned, muted, warnings, System.currentTimeMillis()));
                if (cache.size() > 4_096) {
                    long cutoff = System.currentTimeMillis() - CACHE_MILLIS * 2;
                    cache.entrySet().removeIf(entry -> entry.getValue().loadedAt() < cutoff);
                }
            } catch (SQLException ignored) {
                // Keep the last successful snapshot while storage is temporarily unavailable.
            } finally {
                loading.remove(uuid);
            }
        });
    }

    private record State(boolean banned, boolean muted, int warnings, long loadedAt) {}
}
