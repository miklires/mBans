package io.github.miklires.mbans.listener;

import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.model.Punishment;
import io.github.miklires.mbans.model.PunishmentType;
import io.github.miklires.mbans.service.DurationParser;
import io.github.miklires.mbans.util.MessageUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class MuteListener implements Listener {
    private final MBans plugin;
    public MuteListener(MBans plugin){this.plugin=plugin;}

    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void onChat(AsyncChatEvent event){Optional<Punishment> mute=plugin.getMuteCacheService().get(event.getPlayer().getUniqueId());if(mute.isEmpty())return;
        if(mute.get().getType()==PunishmentType.SHADOW_MUTE){event.viewers().removeIf(audience->{if(audience instanceof org.bukkit.entity.Player viewer)return !viewer.equals(event.getPlayer())&&!viewer.hasPermission("mbans.notify.shadow");return !(audience instanceof org.bukkit.command.ConsoleCommandSender);});return;}
        event.setCancelled(true);notifyMuted(event.getPlayer(),mute.get());}

    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void onCommand(PlayerCommandPreprocessEvent event){String input=event.getMessage().strip();if(input.length()<2)return;String command=input.substring(1).split("\\s+",2)[0].toLowerCase(Locale.ROOT);int colon=command.indexOf(':');if(colon>=0)command=command.substring(colon+1);if(!plugin.getConfigManager().getMuteBlockedCommands().contains(command))return;Optional<Punishment> mute=plugin.getMuteCacheService().get(event.getPlayer().getUniqueId());if(mute.isEmpty())return;event.setCancelled(true);if(mute.get().getType()==PunishmentType.SHADOW_MUTE)plugin.getMessageUtil().send(event.getPlayer(),"chat.shadow-command");else notifyMuted(event.getPlayer(),mute.get());}

    @EventHandler public void onQuit(PlayerQuitEvent event){plugin.getMuteCacheService().remove(event.getPlayer().getUniqueId());}

    private void notifyMuted(org.bukkit.entity.Player player,Punishment punishment){if(punishment.isPermanent())plugin.getMessageUtil().send(player,"chat.muted-permanent",MessageUtil.ph("reason",punishment.getReason()),MessageUtil.ph("support_link",plugin.getConfigManager().getSupportLink()));else plugin.getMessageUtil().send(player,"chat.muted",MessageUtil.ph("reason",punishment.getReason()),MessageUtil.ph("expires",DurationParser.formatExpiresAt(punishment.getExpiresAt())),MessageUtil.ph("support_link",plugin.getConfigManager().getSupportLink()));}
}
