package io.github.miklires.mbans.command;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.model.Punishment;
import io.github.miklires.mbans.model.PunishmentType;
import io.github.miklires.mbans.service.DurationParser;
import io.github.miklires.mbans.util.MessageUtil;

import java.sql.SQLException;
import java.util.Optional;

public class CheckCommand implements CommandExecutor {

    private final MBans plugin;

    public CheckCommand(MBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();

        if (!sender.hasPermission("mbans.command.check")) {
            msg.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length < 1) {
            msg.send(sender, "errors.usage-check");
            return true;
        }

        OfflinePlayer target = CommandHelper.resolveOfflinePlayer(args[0]);
        if (target == null) {
            msg.send(sender, "errors.player-not-found");
            return true;
        }

        try {
            sender.sendMessage("§6Статус игрока §f" + target.getName() + "§6:");

            Optional<Punishment> ban = plugin.getPunishmentRepository()
                    .findActiveByUuid(target.getUniqueId(), PunishmentType.BAN);
            if (ban.isPresent()) {
                Punishment p = ban.get();
                sender.sendMessage("§c  Бан: §f" + p.getReason()
                        + " §7(до: " + DurationParser.formatExpiresAt(p.getExpiresAt())
                        + ", выдал: " + p.getIssuedByName() + ", id=" + p.getId() + ")");
            } else {
                sender.sendMessage("§a  Не забанен");
            }

            Optional<Punishment> mute = plugin.getPunishmentRepository()
                    .findActiveByUuid(target.getUniqueId(), PunishmentType.MUTE);
            if (mute.isPresent()) {
                Punishment p = mute.get();
                sender.sendMessage("§e  Мут: §f" + p.getReason()
                        + " §7(до: " + DurationParser.formatExpiresAt(p.getExpiresAt())
                        + ", выдал: " + p.getIssuedByName() + ", id=" + p.getId() + ")");
            } else {
                sender.sendMessage("§a  Не замучен");
            }

            int warns = plugin.getPunishmentRepository().countActiveWarns(target.getUniqueId());
            int max = plugin.getConfigManager().getAutoBanThreshold();
            sender.sendMessage("§b  Варны: §f" + warns + "/" + max);

            Optional<Punishment> ipBan = plugin.getPunishmentRepository()
                    .findActiveByUuid(target.getUniqueId(), PunishmentType.IP_BAN);
            if (ipBan.isPresent()) {
                Punishment p = ipBan.get();
                sender.sendMessage("§4  IP-бан: §f" + p.getReason()
                        + " §7(ip=" + p.getTargetIp() + ", id=" + p.getId() + ")");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("DB ошибка check: " + e.getMessage());
            msg.send(sender, "errors.db-error");
        }
        return true;
    }
}
