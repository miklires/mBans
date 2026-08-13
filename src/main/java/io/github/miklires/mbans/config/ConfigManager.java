package io.github.miklires.mbans.config;

import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.database.StorageType;
import org.bukkit.configuration.file.FileConfiguration;

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

    public StorageType getStorageType() { return StorageType.parse(cfg().getString("storage.type", "h2")); }
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
    public String getSharedSecret() { return cfg().getString("network.shared-secret", ""); }

    public boolean isDiscordEnabled() { return cfg().getBoolean("discord.enabled", false); }
    public String getWebhookUrl() { return cfg().getString("discord.webhook-url", ""); }
    public String getWebhookBotName() { return cfg().getString("discord.bot-name", "mBans"); }
    public String getWebhookBotAvatar() { return cfg().getString("discord.bot-avatar", ""); }
    public boolean isShowIssuer() { return cfg().getBoolean("discord.show-issuer", true); }
    public boolean isShowIp() { return cfg().getBoolean("discord.show-ip", false); }

    public int getAutoBanThreshold() { return Math.max(0, cfg().getInt("warns.auto-ban-threshold", 3)); }
    public String getAutoBanDuration() { return cfg().getString("warns.auto-ban-duration", "30d"); }
    public String getAutoBanReason() { return cfg().getString("warns.auto-ban-reason", "Accumulated <count> warnings"); }
    public boolean isOfflineWarnDeliveryEnabled() { return cfg().getBoolean("warns.deliver-offline-on-join", true); }

    public boolean isMetricsEnabled() { return cfg().getBoolean("metrics.enabled", true); }
    public int getBstatsId() { return Math.max(0, cfg().getInt("metrics.bstats-id", 0)); }
    public boolean isUpdateCheckEnabled() { return cfg().getBoolean("updates.enabled", true); }
    public String getModrinthProjectId() { return cfg().getString("updates.modrinth-project-id", ""); }

    public String getServerName() { return cfg().getString("server.display-name", "Minecraft Server"); }
    public String getSupportLink() { return cfg().getString("server.support-link", ""); }
    public String getBanKickMessage() { return cfg().getString("defaults.ban-kick-message", ""); }
    public String getPermanentBanKickMessage() { return cfg().getString("defaults.permanent-ban-kick-message", ""); }
    public String getKickMessage() { return cfg().getString("defaults.kick-message", ""); }
}
