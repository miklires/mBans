package io.github.miklires.mbans.command;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.util.MessageUtil;

import java.sql.SQLException;

public class UnwarnCommand implements CommandExecutor {

    private final MBans plugin;

    public UnwarnCommand(MBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();

        if (!sender.hasPermission("mbans.command.unwarn")) {
            msg.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length < 2) {
            msg.send(sender, "errors.usage-unwarn");
            return true;
        }

        OfflinePlayer target = CommandHelper.resolveOfflinePlayer(args[0]);
        if (target == null) {
            msg.send(sender, "errors.player-not-found");
            return true;
        }

        try {
            if (args[1].equalsIgnoreCase("all")) {
                plugin.getPunishmentService().unwarnAll(target.getUniqueId(), sender.getName());
                msg.send(sender, "success.warns-cleared", MessageUtil.ph("player", target.getName()));
                return true;
            }

            long warnId;
            try {
                warnId = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                msg.send(sender, "errors.usage-unwarn");
                return true;
            }

            boolean done = plugin.getPunishmentService().unwarn(target.getUniqueId(), warnId, sender.getName());
            if (!done) {
                msg.send(sender, "errors.warn-not-found");
                return true;
            }
            msg.send(sender, "success.warn-removed");
        } catch (SQLException e) {
            plugin.getLogger().severe("DB ошибка unwarn: " + e.getMessage());
            msg.send(sender, "errors.db-error");
        }
        return true;
    }
}
