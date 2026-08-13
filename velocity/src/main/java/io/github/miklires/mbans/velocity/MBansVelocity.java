package io.github.miklires.mbans.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Plugin(id = "mbans-velocity", name = "mBans Velocity", version = "1.0.0", authors = {"miklires"})
public class MBansVelocity {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;
    private BanStore store;

    @Inject
    public MBansVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        try {
            VelocityConfig config = VelocityConfig.load(dataDirectory);
            store = new BanStore(config);
            if (config.bstatsId() > 0) metricsFactory.make(this, config.bstatsId());
            logger.info("mBans Velocity enabled");
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize mBans Velocity", e);
        }
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        return EventTask.async(() -> {
            if (store == null) return;
            String ip = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();
            try {
                store.find(event.getPlayer().getUniqueId(), ip).ifPresent(ban ->
                        event.setResult(LoginEvent.ComponentResult.denied(message(ban))));
            } catch (Exception e) {
                logger.error("Ban lookup failed for {}", event.getPlayer().getUsername(), e);
                event.setResult(LoginEvent.ComponentResult.denied(Component.text("Could not check your ban status. Try again later.")));
            }
        });
    }

    private Component message(BanStore.Ban ban) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("You are banned from this network"));
        lines.add(Component.text("Reason: " + (ban.reason() == null ? "Not specified" : ban.reason())));
        lines.add(Component.text("Issued by: " + ban.issuer()));
        if (ban.expiresAt() != null) {
            lines.add(Component.text("Expires: " + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                    Instant.ofEpochSecond(ban.expiresAt()).atOffset(ZoneOffset.UTC))));
        }
        if (ban.appealId() != null) lines.add(Component.text("Appeal: " + ban.appealId()));
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (store != null) store.close();
    }
}
