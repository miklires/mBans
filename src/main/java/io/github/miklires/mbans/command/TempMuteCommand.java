package io.github.miklires.mbans.command;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.service.DurationParser;
import io.github.miklires.mbans.util.MessageUtil;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;

public class TempMuteCommand implements CommandExecutor {

    private final MBans plugin;

    public TempMuteCommand(MBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();

        if (!sender.hasPermission("mbans.command.tempmute")) {
            msg.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length < 3) {
            msg.send(sender, "errors.usage-tempmute");
            return true;
        }

        OfflinePlayer target = CommandHelper.resolveOfflinePlayer(args[0]);
        if (target == null) {
            msg.send(sender, "errors.player-not-found");
            return true;
        }
        if (CommandHelper.hasBypass(target, "mbans.bypass.mute", plugin)) {
            msg.send(sender, "errors.bypass-mute");
            return true;
        }

        Optional<Duration> dur = DurationParser.parse(args[1]);
        if (dur.isEmpty()) {
            msg.send(sender, "errors.invalid-duration");
            return true;
        }
        String reason = CommandHelper.joinFrom(args, 2);
        if (reason.isBlank()) {
            msg.send(sender, "errors.usage-tempmute");
            return true;
        }

        try {
            if (plugin.getPunishmentRepository()
                    .findActiveByUuid(target.getUniqueId(),
                            io.github.miklires.mbans.model.PunishmentType.MUTE).isPresent()) {
                msg.send(sender, "errors.already-muted");
                return true;
            }

            plugin.getPunishmentService().mute(target, dur.get(), reason,
                    CommandHelper.issuerName(sender), CommandHelper.issuerUuid(sender));

            msg.send(sender, "success.muted-temp",
                    MessageUtil.ph("player", target.getName()),
                    MessageUtil.ph("duration", DurationParser.format(dur.get())),
                    MessageUtil.ph("reason", reason));
        } catch (SQLException e) {
            plugin.getLogger().severe("DB ошибка tempmute: " + e.getMessage());
            msg.send(sender, "errors.db-error");
        }
        return true;
    }
}
