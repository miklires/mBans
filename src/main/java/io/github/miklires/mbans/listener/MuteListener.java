package io.github.miklires.mbans.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.model.Punishment;
import io.github.miklires.mbans.model.PunishmentType;
import io.github.miklires.mbans.service.DurationParser;
import io.github.miklires.mbans.util.MessageUtil;

import java.sql.SQLException;
import java.util.Optional;

public class MuteListener implements Listener {

    private final MBans plugin;

    public MuteListener(MBans plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        try {
            Optional<Punishment> mute = plugin.getPunishmentRepository().findActiveByUuid(
                    event.getPlayer().getUniqueId(), PunishmentType.MUTE);
            if (mute.isEmpty()) return;

            Punishment p = mute.get();
            event.setCancelled(true);

            if (p.isPermanent()) {
                plugin.getMessageUtil().send(event.getPlayer(), "chat.muted-permanent",
                        MessageUtil.ph("reason", p.getReason()),
                        MessageUtil.ph("support_link", plugin.getConfigManager().getSupportLink()));
            } else {
                plugin.getMessageUtil().send(event.getPlayer(), "chat.muted",
                        MessageUtil.ph("reason", p.getReason()),
                        MessageUtil.ph("expires", DurationParser.formatExpiresAt(p.getExpiresAt())),
                        MessageUtil.ph("support_link", plugin.getConfigManager().getSupportLink()));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Mute lookup failed: " + e.getMessage());
        }
    }
}
