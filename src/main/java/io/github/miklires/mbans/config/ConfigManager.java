package io.github.miklires.mbans.config;

import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.database.StorageType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import io.github.miklires.mbans.model.PunishmentType;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import io.github.miklires.mbans.service.DurationParser;

public class ConfigManager {

    public static final int CONFIG_VERSION = 1;
    private final MBans plugin;

    public ConfigManager(MBans plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = cfg();
        cfg.options().copyDefaults(true);
        if (cfg.getInt("config-version", 0) < CONFIG_VERSION) {
            cfg.set("config-version", CONFIG_VERSION);
        }
        plugin.saveConfig();
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public StorageType getStorageType() {
        String value = cfg().getString("storage.type", "h2");
        StorageType type = StorageType.parse(value);
        if (value != null && !value.equalsIgnoreCase(type.name())) {
            plugin.getLogger().warning("Invalid storage.type '" + value + "'; using h2");
        }
        return type;
    }
    public String getStorageFile() { return cfg().getString("storage.file", "mbans"); }
    public String getJdbcUrl() { return cfg().getString("storage.jdbc-url", ""); }
    public String getDbHost() { return cfg().getString("storage.host", "localhost"); }
    public int getDbPort() { return Math.max(1, cfg().getInt("storage.port", 3306)); }
    public String getDbName() { return cfg().getString("storage.name", "mbans"); }
    public String getDbUser() { return cfg().getString("storage.user", "mbans"); }
    public String getDbPassword() { return cfg().getString("storage.password", ""); }
    public int getDbPoolSize() { return Math.max(1, cfg().getInt("storage.pool-size", 6)); }
    public long getConnectionTimeoutMillis() { return Math.max(1000, cfg().getLong("storage.connection-timeout-millis", 10000)); }
    public long getMaxLifetimeMillis() { return Math.max(30000, cfg().getLong("storage.max-lifetime-millis", 1800000)); }

    public String getLanguage() { return cfg().getString("language.default", "en_US"); }
    public String getNetworkServerName() { return cfg().getString("network.server-name", "server"); }
    public boolean isNetworkSyncEnabled() { return cfg().getBoolean("network.sync-enabled", true); }
    public long getPollIntervalTicks() { return Math.max(20, cfg().getLong("network.poll-interval-ticks", 40)); }
    public boolean isBroadcastEnabled() { return cfg().getBoolean("broadcast.enabled", true); }

    public boolean isDiscordEnabled() { return cfg().getBoolean("discord.enabled", false); }
    public String getWebhookUrl() { return cfg().getString("discord.webhook-url", ""); }
    public String getWebhookUrl(PunishmentType type) {
        String specific = cfg().getString("discord.webhooks." + type.name().toLowerCase(Locale.ROOT).replace('_', '-'), "");
        return specific == null || specific.isBlank() ? getWebhookUrl() : specific;
    }
    public String getWebhookBotName() { return cfg().getString("discord.bot-name", "mBans"); }
    public String getWebhookBotAvatar() { return cfg().getString("discord.bot-avatar", ""); }
    public boolean isShowIssuer() { return cfg().getBoolean("discord.show-issuer", true); }
    public boolean isShowIp() { return cfg().getBoolean("discord.show-ip", false); }
    public String getDiscordAppealUrl() { return cfg().getString("discord.appeal-url", ""); }

    public int getAutoBanThreshold() { return Math.max(0, cfg().getInt("warns.auto-ban-threshold", 3)); }
    public String getAutoBanDuration() { return cfg().getString("warns.auto-ban-duration", "30d"); }
    public String getAutoBanReason() { return cfg().getString("warns.auto-ban-reason", "Accumulated <count> warnings"); }
    public boolean isOfflineWarnDeliveryEnabled() { return cfg().getBoolean("warns.deliver-offline-on-join", true); }
    public boolean isAltNotificationEnabled() { return cfg().getBoolean("alts.notify-on-join", true); }
    public int getIpRetentionDays() { return Math.max(0, cfg().getInt("alts.ip-retention-days", 90)); }
    public long getCleanupIntervalTicks() { return Math.max(200L, cfg().getLong("cleanup.interval-ticks", 1200L)); }
    public boolean preventHigherLevelTargets() { return cfg().getBoolean("immunity.prevent-higher-level-targets", true); }

    public int getImmunityLevel(CommandSender sender) {
        if (!(sender instanceof Player player)) return Integer.MAX_VALUE;
        int level = player.isOp() ? cfg().getInt("immunity.operator-level", 100) : 0;
        org.bukkit.configuration.ConfigurationSection section = cfg().getConfigurationSection("immunity.levels");
        if (section == null) return level;
        for (String key : section.getKeys(false)) {
            if (player.hasPermission(key)) level = Math.max(level, section.getInt(key));
        }
        return level;
    }

    public List<EscalationRule> getEscalationRules() {
        if (!cfg().getBoolean("escalation.enabled", true)) return List.of();
        List<EscalationRule> rules = new ArrayList<>();
        for (java.util.Map<?, ?> row : cfg().getMapList("escalation.rules")) {
            try {
                PunishmentType source = PunishmentType.valueOf(value(row, "type", "WARN").toUpperCase(Locale.ROOT));
                PunishmentType action = PunishmentType.valueOf(value(row, "action", "MUTE").toUpperCase(Locale.ROOT));
                int count = Integer.parseInt(value(row, "count", "1"));
                Duration window = DurationParser.parse(value(row, "window", "7d")).orElse(Duration.ofDays(7));
                Object durationValue = row.get("duration");
                Duration duration = durationValue == null || String.valueOf(durationValue).equalsIgnoreCase("permanent")
                        ? null : DurationParser.parse(String.valueOf(durationValue)).orElse(null);
                String reason = value(row, "reason", "Automatic escalation");
                if (count > 0 && (action == PunishmentType.BAN || action == PunishmentType.MUTE)) {
                    rules.add(new EscalationRule(source, count, window, action, duration, reason));
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring invalid escalation rule: " + row);
            }
        }
        rules.sort(java.util.Comparator.comparingInt(EscalationRule::count));
        return List.copyOf(rules);
    }

    private String value(java.util.Map<?, ?> row, String key, String fallback) {
        Object value = row.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public int getWarningDisplayThreshold() {
        int legacy = getAutoBanThreshold();
        return getEscalationRules().stream().filter(rule -> rule.source() == PunishmentType.WARN)
                .mapToInt(EscalationRule::count).max().orElse(legacy);
    }

    public Optional<ReasonTemplate> getTemplate(String name) {
        String path = "templates." + name.toLowerCase(Locale.ROOT);
        if (!cfg().isConfigurationSection(path)) return Optional.empty();
        String typeName = cfg().getString(path + ".type", "WARN");
        PunishmentType type;
        try {
            type = PunishmentType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid punishment template type at " + path);
            return Optional.empty();
        }
        String durationText = cfg().getString(path + ".duration", "permanent");
        Duration duration = durationText.equalsIgnoreCase("permanent") ? null : DurationParser.parse(durationText).orElse(null);
        String reason = cfg().getString(path + ".reason", name);
        return Optional.of(new ReasonTemplate(type, duration, reason));
    }

    public List<String> getDurationPresets(PunishmentType type) {
        String key = type == PunishmentType.MUTE ? "mute" : "ban";
        return cfg().getStringList("durations." + key).stream()
                .filter(value -> value.equalsIgnoreCase("permanent") || DurationParser.parse(value).isPresent())
                .toList();
    }

    public List<String> getTemplateNames(PunishmentType type) {
        org.bukkit.configuration.ConfigurationSection section = cfg().getConfigurationSection("templates");
        if (section == null) return List.of();
        return section.getKeys(false).stream().filter(name -> getTemplate(name)
                .map(template -> template.type() == type).orElse(false)).sorted().toList();
    }

    public boolean isMetricsEnabled() { return cfg().getBoolean("metrics.enabled", true); }
    public int getBstatsId() { return Math.max(0, cfg().getInt("metrics.bstats-id", 0)); }
    public boolean isUpdateCheckEnabled() { return cfg().getBoolean("updates.enabled", true); }
    public String getModrinthProjectId() { return cfg().getString("updates.modrinth-project-id", ""); }
    public boolean isMojangLookupEnabled() { return cfg().getBoolean("profiles.mojang-lookup", true); }
    public int getMojangTimeoutSeconds() { return Math.max(2, cfg().getInt("profiles.timeout-seconds", 5)); }
    public boolean isRestApiEnabled() { return cfg().getBoolean("rest-api.enabled", false); }
    public String getRestApiBind() { return cfg().getString("rest-api.bind", "127.0.0.1"); }
    public int getRestApiPort() { return Math.max(1, Math.min(65535, cfg().getInt("rest-api.port", 8766))); }
    public String getRestApiToken() { return cfg().getString("rest-api.token", ""); }
    public int getRestApiRequestsPerMinute() { return Math.max(1, cfg().getInt("rest-api.requests-per-minute", 120)); }
    public String getImportSource(String profile) { return cfg().getString("imports." + profile + ".source", ""); }
    public String getImportUser(String profile) { return cfg().getString("imports." + profile + ".user", ""); }
    public String getImportPassword(String profile) { return cfg().getString("imports." + profile + ".password", ""); }
    public boolean isChatEvidenceEnabled() { return cfg().getBoolean("chat-evidence.enabled", true); }
    public int getChatEvidenceBufferSize() { return Math.max(1, Math.min(100, cfg().getInt("chat-evidence.buffer-size", 20))); }
    public List<String> getExemptWorlds() { return cfg().getStringList("exemptions.worlds"); }
    public List<String> getExemptIpRanges() { return cfg().getStringList("exemptions.ip-ranges"); }
    public boolean isIpExempt(String ip) {
        return ip != null && getExemptIpRanges().stream().anyMatch(range -> io.github.miklires.mbans.util.IpRange.contains(range, ip));
    }
    public boolean isGeoIpEnabled() { return cfg().getBoolean("geoip.enabled", false); }
    public String getGeoIpDatabase() { return cfg().getString("geoip.database", "GeoLite2-Country.mmdb"); }
    public Set<String> getGeoIpAllowedCountries() { return countries("geoip.allowed-countries"); }
    public Set<String> getGeoIpBlockedCountries() { return countries("geoip.blocked-countries"); }
    public String getGeoIpDeniedMessage() { return cfg().getString("geoip.denied-message", "Your region is not allowed on this server."); }
    private Set<String> countries(String path) {
        return cfg().getStringList(path).stream().map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public String getServerName() { return cfg().getString("server.display-name", "Minecraft Server"); }
    public String getSupportLink() { return cfg().getString("server.support-link", ""); }
    public String getBanKickMessage() { return cfg().getString("defaults.ban-kick-message", ""); }
    public String getPermanentBanKickMessage() { return cfg().getString("defaults.permanent-ban-kick-message", ""); }
    public String getKickMessage() { return cfg().getString("defaults.kick-message", ""); }

    public record ReasonTemplate(PunishmentType type, Duration duration, String reason) {}
    public record EscalationRule(PunishmentType source, int count, Duration window,
                                 PunishmentType action, Duration duration, String reason) {}
}
