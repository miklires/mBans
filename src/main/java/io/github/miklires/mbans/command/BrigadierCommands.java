package io.github.miklires.mbans.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.function.Predicate;

/** Registers mBans commands through Paper's Brigadier command API. */
public final class BrigadierCommands {

    private final CommandExecutor punishments;
    private final CommandExecutor admin;
    private final CommandExecutor muser;

    public BrigadierCommands(CommandExecutor punishments, CommandExecutor admin, CommandExecutor muser) {
        this.punishments = punishments;
        this.admin = admin;
        this.muser = muser;
    }

    public void register(Commands commands) {
        command(commands, "ban", "Permanently or temporarily ban a player", List.of(), punishments);
        command(commands, "tempban", "Temporarily ban a player", List.of(), punishments);
        command(commands, "unban", "Remove a player ban", List.of("pardon"), punishments);
        command(commands, "banlist", "List active bans", List.of(), punishments);
        command(commands, "banip", "Ban a player or IP address", List.of(), punishments);
        command(commands, "unbanip", "Remove an IP ban", List.of(), punishments);
        command(commands, "mute", "Permanently or temporarily mute a player", List.of(), punishments);
        command(commands, "tempmute", "Temporarily mute a player", List.of(), punishments);
        command(commands, "unmute", "Remove a player mute", List.of(), punishments);
        command(commands, "kick", "Kick an online player", List.of(), punishments);
        command(commands, "warn", "Warn a player", List.of(), punishments);
        command(commands, "unwarn", "Remove a warning", List.of(), punishments);
        command(commands, "history", "View punishment history", List.of(), punishments);
        command(commands, "check", "Check active punishments", List.of(), punishments);
        command(commands, "staffhistory", "View punishments issued by a staff member", List.of(), punishments);
        command(commands, "alts", "Find accounts that share an IP address", List.of("dupeip"), punishments);
        command(commands, "mbans", "Manage mBans", List.of(), admin, source -> true);
        command(commands, "muser", "Open the moderation menu for a player", List.of(), muser);
    }

    private void command(Commands commands, String name, String description, List<String> aliases,
                         CommandExecutor executor) {
        command(commands, name, description, aliases, executor,
                source -> source.getSender().hasPermission("mbans.command." + name));
    }

    private void command(Commands commands, String name, String description, List<String> aliases,
                         CommandExecutor executor, Predicate<CommandSourceStack> requirement) {
        BridgeCommand command = new BridgeCommand(name);
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
                .requires(requirement)
                .executes(context -> invoke(executor, command, name, context, new String[0]));

        root.then(Commands.argument("arguments", StringArgumentType.greedyString())
                .suggests((context, builder) -> {
                    if (!(executor instanceof TabCompleter completer)) return builder.buildFuture();
                    String remaining = builder.getRemaining();
                    String[] args = remaining.isEmpty() ? new String[]{""} : remaining.split(" ", -1);
                    List<String> suggestions = completer.onTabComplete(
                            context.getSource().getSender(), command, name, args);
                    if (suggestions != null) suggestions.forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> invoke(executor, command, name, context,
                        split(StringArgumentType.getString(context, "arguments")))));
        commands.register(root.build(), description, aliases);
    }

    private int invoke(CommandExecutor executor, BridgeCommand command, String name,
                       CommandContext<CommandSourceStack> context, String[] args) {
        CommandSender sender = context.getSource().getSender();
        return executor.onCommand(sender, command, name, args) ? 1 : 0;
    }

    private String[] split(String input) {
        String trimmed = input.trim();
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
    }

    private static final class BridgeCommand extends Command {
        private BridgeCommand(String name) {
            super(name);
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return false;
        }
    }
}
