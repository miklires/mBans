package io.github.miklires.mbans.command;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.model.Punishment;
import io.github.miklires.mbans.service.DurationParser;
import io.github.miklires.mbans.util.MessageUtil;

import java.sql.SQLException;

public class BanCommand implements CommandExecutor {

    private final MBans plugin;

    public BanCommand(MBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();

        if (!sender.hasPermission("mbans.command.ban")) {
            msg.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length < 2) {
            msg.send(sender, "errors.usage-ban");
            return true;
        }

        OfflinePlayer target = CommandHelper.resolveOfflinePlayer(args[0]);
        if (target == null) {
            msg.send(sender, "errors.player-not-found");
            return true;
        }
        if (CommandHelper.hasBypass(target, "mbans.bypass.ban", plugin)) {
            msg.send(sender, "errors.bypass-ban");
            return true;
        }

        CommandHelper.ParsedArgs parsed = CommandHelper.parseDurationAndReason(args, 1);
        if (parsed.reason() == null || parsed.reason().isBlank()) {
            msg.send(sender, "errors.usage-ban");
            return true;
        }

        try {
            if (plugin.getPunishmentRepository()
                    .findActiveByUuid(target.getUniqueId(),
                            io.github.miklires.mbans.model.PunishmentType.BAN).isPresent()) {
                msg.send(sender, "errors.already-banned");
                return true;
            }

            Punishment p = plugin.getPunishmentService().ban(
                    target, parsed.duration(), parsed.reason(),
                    CommandHelper.issuerName(sender), CommandHelper.issuerUuid(sender));

            if (parsed.duration() == null) {
                msg.send(sender, "success.banned",
                        MessageUtil.ph("player", target.getName()),
                        MessageUtil.ph("reason", parsed.reason()));
            } else {
                msg.send(sender, "success.banned-temp",
                        MessageUtil.ph("player", target.getName()),
                        MessageUtil.ph("duration", DurationParser.format(parsed.duration())),
                        MessageUtil.ph("reason", parsed.reason()));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("DB ошибка ban: " + e.getMessage());
            msg.send(sender, "errors.db-error");
        }
        return true;
    }
}
