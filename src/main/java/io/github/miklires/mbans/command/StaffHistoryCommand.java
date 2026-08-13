package io.github.miklires.mbans.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.model.Punishment;
import io.github.miklires.mbans.util.MessageUtil;

import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class StaffHistoryCommand implements CommandExecutor {

    private static final DateTimeFormatter FMT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final MBans plugin;

    public StaffHistoryCommand(MBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();

        if (!sender.hasPermission("mbans.command.staffhistory")) {
            msg.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/staffhistory <модер> [страница]");
            return true;
        }

        String staff = args[0];
        int page = 1;
        if (args.length >= 2) {
            try { page = Math.max(1, Integer.parseInt(args[1])); } catch (NumberFormatException ignored) {}
        }
        int limit = 10;
        int offset = (page - 1) * limit;

        try {
            List<Punishment> entries = plugin.getPunishmentRepository().getStaffHistory(staff, limit, offset);
            if (entries.isEmpty()) {
                msg.send(sender, "errors.no-history");
                return true;
            }
            sender.sendMessage("§6Наказания, выданные §f" + staff + "§6 (страница " + page + "):");
            for (Punishment p : entries) {
                sender.sendMessage("§7#" + p.getId() + " §f" + p.getType().name()
                        + " §7-> §f" + (p.getTargetName() != null ? p.getTargetName() : p.getTargetIp())
                        + " §7| §f" + (p.getReason() != null ? p.getReason() : "—")
                        + " §8(" + FMT.format(p.getIssuedAt()) + ")"
                        + (p.isActive() ? " §a[активно]" : " §7[снято]"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("DB ошибка staffhistory: " + e.getMessage());
            msg.send(sender, "errors.db-error");
        }
        return true;
    }
}
