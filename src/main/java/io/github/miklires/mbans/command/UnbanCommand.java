package io.github.miklires.mbans.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.util.MessageUtil;

import java.sql.SQLException;

public class UnbanCommand implements CommandExecutor {

    private final MBans plugin;

    public UnbanCommand(MBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();

        if (!sender.hasPermission("mbans.command.unban")) {
            msg.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length < 1) {
            msg.send(sender, "errors.usage-unban");
            return true;
        }

        String name = args[0];
        String revokeReason = args.length > 1 ? CommandHelper.joinFrom(args, 1) : "снят";

        try {
            boolean done = plugin.getPunishmentService().unban(name, sender.getName(), revokeReason);
            if (!done) {
                msg.send(sender, "errors.not-banned");
                return true;
            }
            msg.send(sender, "success.unbanned", MessageUtil.ph("player", name));
        } catch (SQLException e) {
            plugin.getLogger().severe("DB ошибка unban: " + e.getMessage());
            msg.send(sender, "errors.db-error");
        }
        return true;
    }
}
