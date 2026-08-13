package io.github.miklires.mbans.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.model.Punishment;
import io.github.miklires.mbans.service.DurationParser;
import io.github.miklires.mbans.util.MessageUtil;

import java.sql.SQLException;
import java.util.List;

public class BanlistCommand implements CommandExecutor {

    private final MBans plugin;

    public BanlistCommand(MBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();

        if (!sender.hasPermission("mbans.command.banlist")) {
            msg.send(sender, "errors.no-permission");
            return true;
        }

        int page = 1;
        if (args.length >= 1) {
            try { page = Math.max(1, Integer.parseInt(args[0])); } catch (NumberFormatException ignored) {}
        }
        int limit = 10;
        int offset = (page - 1) * limit;

        try {
            List<Punishment> bans = plugin.getPunishmentRepository().getActiveBans(limit, offset);
            if (bans.isEmpty()) {
                sender.sendMessage("§eАктивных банов нет.");
                return true;
            }
            msg.send(sender, "list.ban-header", MessageUtil.ph("count", bans.size()));
            for (Punishment p : bans) {
                String entry = plugin.getMessageUtil().getRawString("list.ban-entry")
                        .replace("<player>", p.getTargetName() != null ? p.getTargetName() : (p.getTargetIp() != null ? p.getTargetIp() : "?"))
                        .replace("<reason>", p.getReason() != null ? p.getReason() : "—")
                        .replace("<expires>", DurationParser.formatExpiresAt(p.getExpiresAt()));
                sender.sendMessage(plugin.getMessageUtil().parse(entry));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("DB ошибка banlist: " + e.getMessage());
            msg.send(sender, "errors.db-error");
        }
        return true;
    }
}
