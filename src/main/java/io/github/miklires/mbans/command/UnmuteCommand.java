package io.github.miklires.mbans.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.util.MessageUtil;

import java.sql.SQLException;

public class UnmuteCommand implements CommandExecutor {

    private final MBans plugin;

    public UnmuteCommand(MBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();

        if (!sender.hasPermission("mbans.command.unmute")) {
            msg.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length < 1) {
            msg.send(sender, "errors.usage-unmute");
            return true;
        }

        try {
            boolean done = plugin.getPunishmentService().unmute(args[0], sender.getName());
            if (!done) {
                msg.send(sender, "errors.not-muted");
                return true;
            }
            msg.send(sender, "success.unmuted", MessageUtil.ph("player", args[0]));
        } catch (SQLException e) {
            plugin.getLogger().severe("DB ошибка unmute: " + e.getMessage());
            msg.send(sender, "errors.db-error");
        }
        return true;
    }
}
