package io.github.miklires.mbans;

import io.github.miklires.mbans.command.MBansAdminCommand;
import io.github.miklires.mbans.command.PunishmentCommand;
import io.github.miklires.mbans.command.BrigadierCommands;
import io.github.miklires.mbans.config.ConfigManager;
import io.github.miklires.mbans.database.DatabaseManager;
import io.github.miklires.mbans.database.AdministrationRepository;
import io.github.miklires.mbans.database.NetworkLogRepository;
import io.github.miklires.mbans.database.PlayerRepository;
import io.github.miklires.mbans.database.PunishmentRepository;
import io.github.miklires.mbans.listener.BanCheckListener;
import io.github.miklires.mbans.listener.MuteListener;
import io.github.miklires.mbans.listener.PlayerTrackingListener;
import io.github.miklires.mbans.gui.MUserGui;
import io.github.miklires.mbans.service.NetworkSyncService;
import io.github.miklires.mbans.service.PunishmentService;
import io.github.miklires.mbans.service.ProfileResolver;
import io.github.miklires.mbans.service.RestApiService;
import io.github.miklires.mbans.service.ChatEvidenceService;
import io.github.miklires.mbans.service.GeoIpService;
import io.github.miklires.mbans.service.DataTransferService;
import io.github.miklires.mbans.service.UpdateChecker;
import io.github.miklires.mbans.service.CleanupService;
import io.github.miklires.mbans.placeholder.MBansExpansion;
import io.github.miklires.mbans.util.MessageUtil;
import io.github.miklires.mbans.util.PluginScheduler;
import io.github.miklires.mbans.webhook.DiscordWebhook;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
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
    private ProfileResolver profileResolver;
    private RestApiService restApiService;
    private ChatEvidenceService chatEvidenceService;
    private GeoIpService geoIpService;
    private DataTransferService dataTransferService;
    private UpdateChecker updateChecker;
    private CleanupService cleanupService;
    private MUserGui muserGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        messageUtil = new MessageUtil(this);
        scheduler = new PluginScheduler(this);
        databaseManager = new DatabaseManager(this);

        PunishmentCommand punishments = new PunishmentCommand(this);
        MBansAdminCommand admin = new MBansAdminCommand(this);
        muserGui = new MUserGui(this);
        BrigadierCommands commands = new BrigadierCommands(punishments, admin, muserGui);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> commands.register(event.registrar()));

        scheduler.async(() -> {
            try {
                databaseManager.initialize();
                punishmentRepository = new PunishmentRepository(databaseManager);
                playerRepository = new PlayerRepository(databaseManager);
                networkLogRepository = new NetworkLogRepository(databaseManager);
                administrationRepository = new AdministrationRepository(databaseManager);
                discordWebhook = new DiscordWebhook(this);
                punishmentService = new PunishmentService(this);
                profileResolver = new ProfileResolver(this);
                restApiService = new RestApiService(this);
                chatEvidenceService = new ChatEvidenceService(this);
                geoIpService = new GeoIpService(this);
                dataTransferService = new DataTransferService(this);
                updateChecker = new UpdateChecker(this);
                cleanupService = new CleanupService(this);
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
        getServer().getPluginManager().registerEvents(chatEvidenceService, this);
        getServer().getPluginManager().registerEvents(muserGui, this);

        int bstatsId = configManager.getBstatsId();
        if (configManager.isMetricsEnabled() && bstatsId > 0) new org.bstats.bukkit.Metrics(this, bstatsId);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new MBansExpansion(this).register();
        }
        networkSyncService.start();
        geoIpService.start();
        restApiService.start();
        updateChecker.start();
        cleanupService.start();
        getLogger().info("mBans " + getPluginMeta().getVersion() + " enabled (" + databaseManager.getType().name().toLowerCase() + ")");
    }

    @Override
    public void onDisable() {
        if (restApiService != null) restApiService.stop();
        if (scheduler != null) scheduler.shutdown();
        if (geoIpService != null) geoIpService.stop();
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
    public ProfileResolver getProfileResolver() { return profileResolver; }
    public ChatEvidenceService getChatEvidenceService() { return chatEvidenceService; }
    public GeoIpService getGeoIpService() { return geoIpService; }
    public DataTransferService getDataTransferService() { return dataTransferService; }
}
