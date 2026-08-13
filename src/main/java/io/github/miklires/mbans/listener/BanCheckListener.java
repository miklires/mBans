package io.github.miklires.mbans.listener;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.model.Punishment;
import io.github.miklires.mbans.model.PunishmentType;

import java.sql.SQLException;
import java.util.Optional;

public class BanCheckListener implements Listener {

    private final MBans plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public BanCheckListener(MBans plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            String ip = event.getAddress().getHostAddress();
            Optional<Punishment> ipBan = plugin.getPunishmentRepository().findActiveIpBan(ip);
            if (ipBan.isPresent() && !plugin.getAdministrationRepository().isAllowed(ipBan.get().getId(), event.getUniqueId())) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        plugin.getPunishmentService().buildBanKickComponent(ipBan.get()));
                return;
            }

            Optional<Punishment> ban = plugin.getPunishmentRepository().findActiveByUuid(
                    event.getUniqueId(), PunishmentType.BAN);
            if (ban.isPresent()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        plugin.getPunishmentService().buildBanKickComponent(ban.get()));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ban lookup failed: " + e.getMessage());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    mm.deserialize("<red>Could not check your ban status. Try again later."));
        }
    }
}
