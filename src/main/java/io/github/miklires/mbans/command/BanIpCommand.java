package io.github.miklires.mbans.command;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.service.DurationParser;
import io.github.miklires.mbans.util.MessageUtil;

import java.sql.SQLException;
import java.util.UUID;

public class BanIpCommand implements CommandExecutor {

    private final MBans plugin;

    public BanIpCommand(MBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();

        if (!sender.hasPermission("mbans.command.banip")) {
            msg.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length < 2) {
            msg.send(sender, "errors.usage-banip");
            return true;
        }

        String first = args[0];
        String ip;
        String targetName = first;
        UUID targetUuid = null;

        if (CommandHelper.isValidIp(first)) {
            ip = first;
            targetName = first;
        } else {
            OfflinePlayer target = CommandHelper.resolveOfflinePlayer(first);
            if (target == null) {
                msg.send(sender, "errors.player-not-found");
                return true;
            }
            if (CommandHelper.hasBypass(target, "mbans.bypass.ban", plugin)) {
                msg.send(sender, "errors.bypass-ban");
                return true;
            }
            if (target instanceof Player p && p.getAddress() != null) {
                ip = p.getAddress().getAddress().getHostAddress();
            } else {
                msg.send(sender, "errors.invalid-ip");
                sender.sendMessage("§eИгрок не онлайн и IP не известен. Укажи IP вручную: /banip <ip> [время] <причина>");
                return true;
            }
            targetName = target.getName();
            targetUuid = target.getUniqueId();
        }

        CommandHelper.ParsedArgs parsed = CommandHelper.parseDurationAndReason(args, 1);
        if (parsed.reason() == null || parsed.reason().isBlank()) {
            msg.send(sender, "errors.usage-banip");
            return true;
        }

        try {
            plugin.getPunishmentService().ipBan(ip, targetName, targetUuid,
                    parsed.duration(), parsed.reason(),
                    CommandHelper.issuerName(sender), CommandHelper.issuerUuid(sender));
            msg.send(sender, "success.ip-banned",
                    MessageUtil.ph("ip", ip),
                    MessageUtil.ph("player", targetName),
                    MessageUtil.ph("reason", parsed.reason()));
        } catch (SQLException e) {
            plugin.getLogger().severe("DB ошибка banip: " + e.getMessage());
            msg.send(sender, "errors.db-error");
        }
        return true;
    }
}
