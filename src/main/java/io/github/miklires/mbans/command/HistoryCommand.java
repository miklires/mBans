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

public class HistoryCommand implements CommandExecutor {

    private static final DateTimeFormatter FMT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final MBans plugin;

    public HistoryCommand(MBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();

        if (!sender.hasPermission("mbans.command.history")) {
            msg.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length < 1) {
            msg.send(sender, "errors.usage-history");
            return true;
        }

        String name = args[0];
        int page = 1;
        if (args.length >= 2) {
            try { page = Math.max(1, Integer.parseInt(args[1])); } catch (NumberFormatException ignored) {}
        }
        int limit = 10;
        int offset = (page - 1) * limit;

        try {
            List<Punishment> entries = plugin.getPunishmentRepository().getHistory(name, limit, offset);
            if (entries.isEmpty()) {
                msg.send(sender, "errors.no-history");
                return true;
            }
            msg.send(sender, "list.history-header",
                    MessageUtil.ph("player", name),
                    MessageUtil.ph("count", entries.size()));
            for (Punishment p : entries) {
                sender.sendMessage(plugin.getMessageUtil().parse(
                        plugin.getMessageUtil().getRawString("list.history-entry")
                                .replace("<id>", String.valueOf(p.getId()))
                                .replace("<type>", p.getType().name() + (p.isActive() ? " §a[активно]§r" : " §7[снято]§r"))
                                .replace("<reason>", p.getReason() != null ? p.getReason() : "—")
                                .replace("<issuer>", p.getIssuedByName())
                                .replace("<date>", FMT.format(p.getIssuedAt()))));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("DB ошибка history: " + e.getMessage());
            msg.send(sender, "errors.db-error");
        }
        return true;
    }
}
