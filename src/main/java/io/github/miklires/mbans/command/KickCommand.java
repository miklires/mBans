package io.github.miklires.mbans.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.util.MessageUtil;

import java.sql.SQLException;

public class KickCommand implements CommandExecutor {

    private final MBans plugin;

    public KickCommand(MBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();

        if (!sender.hasPermission("mbans.command.kick")) {
            msg.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length < 2) {
            msg.send(sender, "errors.usage-kick");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            msg.send(sender, "errors.player-not-found");
            return true;
        }
        if (target.hasPermission("mbans.bypass.kick")) {
            msg.send(sender, "errors.bypass-kick");
            return true;
        }

        String reason = CommandHelper.joinFrom(args, 1);

        try {
            plugin.getPunishmentService().kick(target, reason,
                    CommandHelper.issuerName(sender), CommandHelper.issuerUuid(sender));
            msg.send(sender, "success.kicked", MessageUtil.ph("player", target.getName()));
        } catch (SQLException e) {
            plugin.getLogger().severe("DB ошибка kick: " + e.getMessage());
            msg.send(sender, "errors.db-error");
        }
        return true;
    }
}
