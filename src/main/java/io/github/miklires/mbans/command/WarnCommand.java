package io.github.miklires.mbans.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.util.MessageUtil;

import java.sql.SQLException;

public class WarnCommand implements CommandExecutor {

    private final MBans plugin;

    public WarnCommand(MBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();

        if (!sender.hasPermission("mbans.command.warn")) {
            msg.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length < 2) {
            msg.send(sender, "errors.usage-warn");
            return true;
        }

        OfflinePlayer target = CommandHelper.resolveOfflinePlayer(args[0]);
        if (target == null) {
            msg.send(sender, "errors.player-not-found");
            return true;
        }

        String reason = CommandHelper.joinFrom(args, 1);

        try {
            plugin.getPunishmentService().warn(target, reason,
                    CommandHelper.issuerName(sender), CommandHelper.issuerUuid(sender));

            int count = plugin.getPunishmentRepository().countActiveWarns(target.getUniqueId());
            int max = plugin.getConfigManager().getAutoBanThreshold();

            msg.send(sender, "success.warned",
                    MessageUtil.ph("player", target.getName()),
                    MessageUtil.ph("count", count),
                    MessageUtil.ph("max", max),
                    MessageUtil.ph("reason", reason));

            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online != null) {
                msg.send(online, "notify.warn-received",
                        MessageUtil.ph("reason", reason),
                        MessageUtil.ph("count", count),
                        MessageUtil.ph("max", max));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("DB ошибка warn: " + e.getMessage());
            msg.send(sender, "errors.db-error");
        }
        return true;
    }
}
