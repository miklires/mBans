package io.github.miklires.mbans.command;

import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.database.AdministrationRepository;
import io.github.miklires.mbans.database.PlayerRepository;
import io.github.miklires.mbans.service.DurationParser;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class MBansAdminCommand implements TabExecutor {

    private final MBans plugin;

    public MBansAdminCommand(MBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("mBans " + plugin.getPluginMeta().getVersion()));
            sender.sendMessage(Component.text("/mbans reload|rollback|allow|note|stats"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "reload" -> reload(sender);
            case "rollback" -> rollback(sender, args);
            case "allow" -> allow(sender, args);
            case "note" -> note(sender, args);
            case "stats" -> stats(sender, args);
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand"));
                yield true;
            }
        };
    }

    private boolean reload(CommandSender sender) {
        if (!check(sender, "mbans.admin")) return true;
        plugin.getConfigManager().reload();
        plugin.getMessageUtil().reload();
        sender.sendMessage(Component.text("mBans configuration reloaded. Storage and network settings require a restart."));
        return true;
    }

    private boolean rollback(CommandSender sender, String[] args) {
        if (!check(sender, "mbans.command.rollback")) return true;
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /mbans rollback <staff> [time]"));
            return true;
        }
        Duration window = args.length >= 3 ? DurationParser.parse(args[2]).orElse(null) : Duration.ofDays(30);
        if (window == null) {
            sender.sendMessage(Component.text("Invalid duration"));
            return true;
        }
        String staff = args[1];
        String actor = sender.getName();
        plugin.getScheduler().async(() -> {
            try {
                List<Long> ids = plugin.getAdministrationRepository().rollback(staff, Instant.now().minus(window), actor);
                for (long id : ids) {
                    plugin.getNetworkLogRepository().append(id, "REVOKE", plugin.getConfigManager().getNetworkServerName());
                }
                reply(sender, "Rolled back " + ids.size() + " punishments issued by " + staff);
            } catch (SQLException e) {
                fail(sender, "Rollback failed", e);
            }
        });
        return true;
    }

    private boolean allow(CommandSender sender, String[] args) {
        if (!check(sender, "mbans.command.allow")) return true;
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /mbans allow <punishment-id> <player>"));
            return true;
        }
        long punishmentId;
        try {
            punishmentId = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid punishment id"));
            return true;
        }
        String playerName = args[2];
        plugin.getScheduler().async(() -> {
            try {
                Optional<PlayerRepository.PlayerIdentity> target = plugin.getPlayerRepository().findByName(playerName);
                if (target.isEmpty()) {
                    reply(sender, "Player not found in history");
                    return;
                }
                boolean added = plugin.getAdministrationRepository().allow(punishmentId, target.get().uuid());
                reply(sender, added ? "IP-ban exception added for " + target.get().name() : "That exception already exists");
            } catch (SQLException e) {
                fail(sender, "Could not add IP-ban exception", e);
            }
        });
        return true;
    }

    private boolean note(CommandSender sender, String[] args) {
        if (!check(sender, "mbans.command.note")) return true;
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /mbans note <player> <text>"));
            return true;
        }
        String playerName = args[1];
        String text = CommandHelper.joinFrom(args, 2);
        UUID author = CommandHelper.issuerUuid(sender);
        String authorName = sender.getName();
        plugin.getScheduler().async(() -> {
            try {
                Optional<PlayerRepository.PlayerIdentity> target = plugin.getPlayerRepository().findByName(playerName);
                if (target.isEmpty()) {
                    reply(sender, "Player not found in history");
                    return;
                }
                long id = plugin.getAdministrationRepository().addNote(target.get().uuid(), author, authorName, text);
                reply(sender, "Staff note #" + id + " added for " + target.get().name());
            } catch (SQLException e) {
                fail(sender, "Could not add staff note", e);
            }
        });
        return true;
    }

    private boolean stats(CommandSender sender, String[] args) {
        if (!check(sender, "mbans.command.stats")) return true;
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /mbans stats <staff>"));
            return true;
        }
        String staff = args[1];
        plugin.getScheduler().async(() -> {
            try {
                AdministrationRepository.StaffStats stats = plugin.getAdministrationRepository().stats(staff);
                reply(sender, staff + ": total " + stats.total() + ", bans " + stats.bans()
                        + ", mutes " + stats.mutes() + ", warnings " + stats.warns() + ", revoked " + stats.revoked());
            } catch (SQLException e) {
                fail(sender, "Could not load staff stats", e);
            }
        });
        return true;
    }

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        plugin.getMessageUtil().send(sender, "errors.no-permission");
        return false;
    }

    private void reply(CommandSender sender, String text) {
        plugin.getScheduler().global(() -> sender.sendMessage(Component.text(text)));
    }

    private void fail(CommandSender sender, String text, SQLException error) {
        plugin.getLogger().warning(text + ": " + error.getMessage());
        reply(sender, text);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("reload", "rollback", "allow", "note", "stats").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
