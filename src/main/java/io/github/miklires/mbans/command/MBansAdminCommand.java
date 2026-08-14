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
import java.nio.file.Path;

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
            sender.sendMessage(Component.text("/mbans reload|rollback|allow|note|notes|stats|import|export"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "reload" -> reload(sender);
            case "rollback" -> rollback(sender, args);
            case "allow" -> allow(sender, args);
            case "note" -> note(sender, args);
            case "notes" -> notes(sender, args);
            case "stats" -> stats(sender, args);
            case "import" -> importData(sender, args);
            case "export" -> exportData(sender, args);
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
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /mbans allow <player> [ip-ban-id]"));
            return true;
        }
        String playerName = args[1];
        plugin.getScheduler().async(() -> {
            try {
                Optional<PlayerRepository.PlayerIdentity> target = plugin.getPlayerRepository().findByName(playerName);
                if (target.isEmpty()) {
                    reply(sender, "Player not found in history");
                    return;
                }
                long punishmentId;
                if (args.length >= 3) {
                    try { punishmentId = Long.parseLong(args[2]); }
                    catch (NumberFormatException e) { reply(sender, "Invalid punishment id"); return; }
                } else {
                    if (target.get().ip() == null) { reply(sender, "No recorded IP address for that player"); return; }
                    Optional<io.github.miklires.mbans.model.Punishment> ban = plugin.getPunishmentRepository()
                            .findActiveIpBan(target.get().ip());
                    if (ban.isEmpty()) { reply(sender, "No active IP ban covers that player"); return; }
                    punishmentId = ban.get().getId();
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
                        + ", mutes " + stats.mutes() + ", warnings " + stats.warns() + ", revoked " + stats.revoked()
                        + " (" + String.format(Locale.ROOT, "%.1f", stats.revocationRate() * 100) + "%), average timed duration "
                        + io.github.miklires.mbans.service.DurationParser.format(java.time.Duration.ofSeconds(Math.round(stats.averageDurationSeconds()))));
            } catch (SQLException e) {
                fail(sender, "Could not load staff stats", e);
            }
        });
        return true;
    }

    private boolean notes(CommandSender sender, String[] args) {
        if (!check(sender, "mbans.command.notes")) return true;
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /mbans notes <player>"));
            return true;
        }
        plugin.getScheduler().async(() -> {
            try {
                Optional<PlayerRepository.PlayerIdentity> target = plugin.getPlayerRepository().findByName(args[1]);
                if (target.isEmpty()) {
                    reply(sender, "Player not found in history");
                    return;
                }
                List<AdministrationRepository.StaffNote> notes = plugin.getAdministrationRepository()
                        .notes(target.get().uuid(), 20);
                reply(sender, "Staff notes for " + target.get().name() + " (" + notes.size() + ")");
                for (AdministrationRepository.StaffNote note : notes) {
                    reply(sender, "#" + note.id() + " " + note.author() + ": " + note.text());
                }
            } catch (SQLException e) {
                fail(sender, "Could not load staff notes", e);
            }
        });
        return true;
    }

    private boolean importData(CommandSender sender, String[] args) {
        if (!check(sender, "mbans.command.import")) return true;
        if (args.length < 2) {
            reply(sender, "Usage: /mbans import <vanilla|litebans|libertybans|advancedban|banmanager> [directory|jdbc-url] [--dry-run]");
            return true;
        }
        String profile = args[1].toLowerCase(Locale.ROOT);
        String source = args.length > 2 && !args[2].startsWith("--")
                ? args[2] : plugin.getConfigManager().getImportSource(profile);
        if (source.isBlank()) {
            reply(sender, "No import source configured for " + profile);
            return true;
        }
        boolean dryRun = java.util.Arrays.stream(args).anyMatch("--dry-run"::equalsIgnoreCase);
        plugin.getScheduler().async(() -> {
            try {
                io.github.miklires.mbans.service.DataTransferService.TransferResult result;
                if (profile.equals("vanilla") || profile.equals("essentials")) {
                    result = plugin.getDataTransferService().importVanilla(Path.of(source), dryRun);
                } else if (List.of("litebans", "libertybans", "advancedban", "banmanager").contains(profile)) {
                    String user = plugin.getConfigManager().getImportUser(profile);
                    String password = plugin.getConfigManager().getImportPassword(profile);
                    result = plugin.getDataTransferService().importJdbc(profile, source, user, password, dryRun);
                } else {
                    reply(sender, "Unsupported import profile");
                    return;
                }
                reply(sender, (result.dryRun() ? "Dry run: " : "Import complete: ") + result.imported()
                        + " accepted, " + result.skipped() + " skipped, " + result.read() + " read"
                        + (result.backup() == null ? "" : ". Backup: " + result.backup().toAbsolutePath()));
            } catch (Exception e) {
                plugin.getLogger().warning("Import failed: " + e.getMessage());
                reply(sender, "Import failed. See the server log for details.");
            }
        });
        return true;
    }

    private boolean exportData(CommandSender sender, String[] args) {
        if (!check(sender, "mbans.command.export")) return true;
        if (args.length < 2) {
            reply(sender, "Usage: /mbans export <player> [json|csv]");
            return true;
        }
        String format = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "json";
        if (!format.equals("json") && !format.equals("csv")) {
            reply(sender, "Format must be json or csv");
            return true;
        }
        plugin.getScheduler().async(() -> {
            try {
                Path path = plugin.getDataTransferService().exportHistory(args[1], format);
                reply(sender, "Export written to " + path.toAbsolutePath());
            } catch (Exception e) {
                plugin.getLogger().warning("Export failed: " + e.getMessage());
                reply(sender, "Export failed. See the server log for details.");
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
        if (args.length == 1) return List.of("reload", "rollback", "allow", "note", "notes", "stats", "import", "export").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("import")) return List.of("vanilla", "litebans",
                "libertybans", "advancedban", "banmanager").stream()
                .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("export")) return List.of("json", "csv").stream()
                .filter(value -> value.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
