package io.github.miklires.mbans;

import io.github.miklires.mbans.command.BanCommand;
import io.github.miklires.mbans.command.BanIpCommand;
import io.github.miklires.mbans.command.BanlistCommand;
import io.github.miklires.mbans.command.CheckCommand;
import io.github.miklires.mbans.command.HistoryCommand;
import io.github.miklires.mbans.command.KickCommand;
import io.github.miklires.mbans.command.MBansAdminCommand;
import io.github.miklires.mbans.command.MuteCommand;
import io.github.miklires.mbans.command.StaffHistoryCommand;
import io.github.miklires.mbans.command.TempBanCommand;
import io.github.miklires.mbans.command.TempMuteCommand;
import io.github.miklires.mbans.command.UnbanCommand;
import io.github.miklires.mbans.command.UnbanIpCommand;
import io.github.miklires.mbans.command.UnmuteCommand;
import io.github.miklires.mbans.command.UnwarnCommand;
import io.github.miklires.mbans.command.WarnCommand;
import io.github.miklires.mbans.config.ConfigManager;
import io.github.miklires.mbans.database.DatabaseManager;
import io.github.miklires.mbans.database.AdministrationRepository;
import io.github.miklires.mbans.database.NetworkLogRepository;
import io.github.miklires.mbans.database.PlayerRepository;
import io.github.miklires.mbans.database.PunishmentRepository;
import io.github.miklires.mbans.listener.BanCheckListener;
import io.github.miklires.mbans.listener.MuteListener;
import io.github.miklires.mbans.listener.PlayerTrackingListener;
import io.github.miklires.mbans.service.NetworkSyncService;
import io.github.miklires.mbans.service.PunishmentService;
import io.github.miklires.mbans.util.MessageUtil;
import io.github.miklires.mbans.util.PluginScheduler;
import io.github.miklires.mbans.webhook.DiscordWebhook;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public class MBans extends JavaPlugin {

    private ConfigManager configManager;
    private MessageUtil messageUtil;
    private PluginScheduler scheduler;
    private DatabaseManager databaseManager;
    private PunishmentRepository punishmentRepository;
    private PlayerRepository playerRepository;
    private NetworkLogRepository networkLogRepository;
    private AdministrationRepository administrationRepository;
    private PunishmentService punishmentService;
    private DiscordWebhook discordWebhook;
    private NetworkSyncService networkSyncService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        messageUtil = new MessageUtil(this);
        scheduler = new PluginScheduler(this);
        databaseManager = new DatabaseManager(this);

        scheduler.async(() -> {
            try {
                databaseManager.initialize();
                punishmentRepository = new PunishmentRepository(databaseManager);
                playerRepository = new PlayerRepository(databaseManager);
                networkLogRepository = new NetworkLogRepository(databaseManager);
                administrationRepository = new AdministrationRepository(databaseManager);
                discordWebhook = new DiscordWebhook(this);
                punishmentService = new PunishmentService(this);
                networkSyncService = new NetworkSyncService(this);
                scheduler.global(this::finishEnable);
            } catch (Exception e) {
                getLogger().severe("Storage initialization failed: " + e.getMessage());
                scheduler.global(() -> getServer().getPluginManager().disablePlugin(this));
            }
        });
    }

    private void finishEnable() {
        getServer().getPluginManager().registerEvents(new BanCheckListener(this), this);
        getServer().getPluginManager().registerEvents(new MuteListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerTrackingListener(this), this);

        register("ban", new BanCommand(this));
        register("tempban", new TempBanCommand(this));
        register("unban", new UnbanCommand(this));
        register("banlist", new BanlistCommand(this));
        register("banip", new BanIpCommand(this));
        register("unbanip", new UnbanIpCommand(this));
        register("mute", new MuteCommand(this));
        register("tempmute", new TempMuteCommand(this));
        register("unmute", new UnmuteCommand(this));
        register("kick", new KickCommand(this));
        register("warn", new WarnCommand(this));
        register("unwarn", new UnwarnCommand(this));
        register("history", new HistoryCommand(this));
        register("check", new CheckCommand(this));
        register("staffhistory", new StaffHistoryCommand(this));
        register("mbans", new MBansAdminCommand(this));

        int bstatsId = configManager.getBstatsId();
        if (configManager.isMetricsEnabled() && bstatsId > 0) new org.bstats.bukkit.Metrics(this, bstatsId);
        networkSyncService.start();
        getLogger().info("mBans " + getPluginMeta().getVersion() + " enabled (" + databaseManager.getType().name().toLowerCase() + ")");
    }

    private void register(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) throw new IllegalStateException("Missing command in plugin.yml: " + name);
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) command.setTabCompleter(completer);
    }

    @Override
    public void onDisable() {
        if (scheduler != null) scheduler.shutdown();
        if (databaseManager != null) databaseManager.shutdown();
        getLogger().info("mBans disabled");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public MessageUtil getMessageUtil() { return messageUtil; }
    public PluginScheduler getScheduler() { return scheduler; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public PunishmentRepository getPunishmentRepository() { return punishmentRepository; }
    public PlayerRepository getPlayerRepository() { return playerRepository; }
    public NetworkLogRepository getNetworkLogRepository() { return networkLogRepository; }
    public AdministrationRepository getAdministrationRepository() { return administrationRepository; }
    public PunishmentService getPunishmentService() { return punishmentService; }
    public DiscordWebhook getDiscordWebhook() { return discordWebhook; }
}
