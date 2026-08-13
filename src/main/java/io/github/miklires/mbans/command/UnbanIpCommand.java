package io.github.miklires.mbans.command;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.util.MessageUtil;

import java.sql.SQLException;
import java.util.Optional;

public class UnbanIpCommand implements CommandExecutor {

    private final MBans plugin;

    public UnbanIpCommand(MBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();

        if (!sender.hasPermission("mbans.command.unbanip")) {
            msg.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length < 1) {
            msg.send(sender, "errors.usage-unbanip");
            return true;
        }

        String input = args[0];
        String ip = input;

        if (!CommandHelper.isValidIp(input)) {
            OfflinePlayer target = CommandHelper.resolveOfflinePlayer(input);
            if (target == null) {
                msg.send(sender, "errors.player-not-found");
                return true;
            }
            try {
                Optional<io.github.miklires.mbans.model.Punishment> existing = plugin.getPunishmentRepository()
                        .findActiveByUuid(target.getUniqueId(), io.github.miklires.mbans.model.PunishmentType.IP_BAN);
                if (existing.isEmpty() || existing.get().getTargetIp() == null) {
                    msg.send(sender, "errors.not-banned");
                    return true;
                }
                ip = existing.get().getTargetIp();
            } catch (SQLException e) {
                plugin.getLogger().severe("DB ошибка unbanip lookup: " + e.getMessage());
                msg.send(sender, "errors.db-error");
                return true;
            }
        }

        try {
            boolean done = plugin.getPunishmentService().unbanIp(ip, sender.getName(), "снят");
            if (!done) {
                msg.send(sender, "errors.not-banned");
                return true;
            }
            msg.send(sender, "success.ip-unbanned", MessageUtil.ph("ip", ip));
        } catch (SQLException e) {
            plugin.getLogger().severe("DB ошибка unbanip: " + e.getMessage());
            msg.send(sender, "errors.db-error");
        }
        return true;
    }
}
