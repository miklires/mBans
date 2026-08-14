package io.github.miklires.mbans.gui;

import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.config.ConfigManager;
import io.github.miklires.mbans.database.PlayerRepository;
import io.github.miklires.mbans.model.PunishmentType;
import io.github.miklires.mbans.model.Punishment;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class MUserGui implements Listener, TabExecutor {
    private final MBans plugin;

    public MUserGui(MBans plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player viewer)) {
            plugin.getMessageUtil().send(sender, "errors.player-only");
            return true;
        }
        if (!viewer.hasPermission("mbans.command.muser")) {
            plugin.getMessageUtil().send(viewer, "errors.no-permission");
            return true;
        }
        if (args.length != 1) {
            viewer.sendMessage(Component.text("Usage: /muser <player>"));
            return true;
        }
        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            plugin.getScheduler().entity(online, () -> {
                PlayerRepository.PlayerIdentity identity = new PlayerRepository.PlayerIdentity(online.getUniqueId(), online.getName(),
                        online.getAddress() == null ? null : online.getAddress().getAddress().getHostAddress(),
                        plugin.getConfigManager().getImmunityLevel(online));
                loadAndOpen(viewer, identity);
            });
            return true;
        }
        plugin.getScheduler().async(() -> {
            try {
                PlayerRepository.PlayerIdentity identity = plugin.getPlayerRepository().findByName(args[0]).orElse(null);
                if (identity == null) {
                    plugin.getMessageUtil().send(viewer, "errors.player-not-found");
                    return;
                }
                loadAndOpen(viewer, identity);
            } catch (SQLException e) {
                plugin.getMessageUtil().send(viewer, "errors.db-error");
            }
        });
        return true;
    }

    private void loadAndOpen(Player viewer, PlayerRepository.PlayerIdentity target) {
        plugin.getScheduler().entity(viewer, () -> {
            boolean showIp = viewer.hasPermission("mbans.view.ip");
            plugin.getScheduler().async(() -> {
                try {
                    List<Punishment> history = plugin.getPunishmentRepository().getHistory(target.name(), 7, 0);
                    String country = showIp ? plugin.getGeoIpService().country(target.ip()).orElse("unknown") : null;
                    plugin.getScheduler().entity(viewer, () -> openMain(viewer, target, history, country));
                } catch (SQLException e) {
                    plugin.getMessageUtil().send(viewer, "errors.db-error");
                }
            });
        });
    }

    private void openMain(Player viewer, PlayerRepository.PlayerIdentity target, List<Punishment> history, String country) {
        Menu holder = new Menu(target, null, false, null);
        Inventory inv = Bukkit.createInventory(holder, 36, Component.text("mBans: " + target.name()));
        holder.inventory = inv;
        boolean showIp = viewer.hasPermission("mbans.view.ip");
        inv.setItem(4, item(Material.PLAYER_HEAD, target.name(), "UUID: " + target.uuid(),
                showIp ? "IP: " + target.ip() : "IP: hidden", showIp ? "Country: " + country : null));
        inv.setItem(10, item(Material.BARRIER, "Ban", "Choose a permanent ban template"));
        inv.setItem(11, item(Material.CLOCK, "Tempban", "Choose a timed ban template"));
        inv.setItem(12, item(Material.IRON_BARS, "Mute", "Choose a mute template"));
        inv.setItem(13, item(Material.PAPER, "Warn", "Choose a warning template"));
        inv.setItem(14, item(Material.IRON_BOOTS, "Kick", "Prepare a kick command"));
        inv.setItem(15, item(Material.BOOK, "History", "Open paginated chat history"));
        inv.setItem(16, item(Material.ENDER_EYE, "Alts", "Find matching accounts"));
        inv.setItem(17, item(Material.WRITABLE_BOOK, "Notes", "Read staff notes"));
        int slot = 27;
        for (Punishment entry : history) {
            String state = entry.isActive() && !entry.isExpired() ? "active" : "inactive";
            inv.setItem(slot++, item(Material.MAP, "#" + entry.getId() + " " + entry.getType(),
                    entry.getReason(), "Issued by: " + entry.getIssuedByName(), "State: " + state));
        }
        viewer.openInventory(inv);
    }

    private void openTemplates(Player viewer, PlayerRepository.PlayerIdentity target, PunishmentType type, boolean forceTimed) {
        Menu holder = new Menu(target, type, forceTimed, null);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(type + " templates: " + target.name()));
        holder.inventory = inv;
        int slot = 0;
        for (String name : plugin.getConfig().getConfigurationSection("templates") == null ? List.<String>of()
                : plugin.getConfig().getConfigurationSection("templates").getKeys(false)) {
            Optional<ConfigManager.ReasonTemplate> template = plugin.getConfigManager().getTemplate(name);
            if (template.isEmpty() || template.get().type() != type || slot >= 45) continue;
            ConfigManager.ReasonTemplate value = template.get();
            inv.setItem(slot++, item(Material.NAME_TAG, name,
                    value.reason(), value.duration() == null ? "Permanent" : value.duration().toString()));
        }
        inv.setItem(49, item(Material.ARROW, "Back", "Return to player menu"));
        viewer.openInventory(inv);
    }

    private void openDurations(Player viewer, PlayerRepository.PlayerIdentity target,
                               PunishmentType type, String template) {
        Menu holder = new Menu(target, type, true, template);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Choose duration: " + target.name()));
        holder.inventory = inv;
        int slot = 0;
        for (String duration : plugin.getConfigManager().getDurationPresets(type)) {
            if (duration.equalsIgnoreCase("permanent") || slot >= 45) continue;
            inv.setItem(slot++, item(Material.CLOCK, duration, "Template: " + template));
        }
        inv.setItem(49, item(Material.ARROW, "Back", "Return to templates"));
        viewer.openInventory(inv);
    }

    private void openHistory(Player viewer, PlayerRepository.PlayerIdentity target, int page) {
        plugin.getScheduler().async(() -> {
            try {
                List<Punishment> entries = plugin.getPunishmentRepository().getHistory(target.name(), 45, page * 45);
                plugin.getScheduler().entity(viewer, () -> {
                    Menu holder = new Menu(target, entries, page);
                    Inventory inv = Bukkit.createInventory(holder, 54,
                            Component.text("History: " + target.name() + " (" + (page + 1) + ")"));
                    holder.inventory = inv;
                    for (int i = 0; i < entries.size(); i++) {
                        Punishment entry = entries.get(i);
                        String state = entry.isActive() && !entry.isExpired() ? "active - click to revoke" : "inactive";
                        inv.setItem(i, item(Material.MAP, "#" + entry.getId() + " " + entry.getType(),
                                entry.getReason(), "Issued by: " + entry.getIssuedByName(), "State: " + state,
                                entry.getEvidence() == null ? null : "Evidence: " + entry.getEvidence()));
                    }
                    if (page > 0) inv.setItem(45, item(Material.ARROW, "Previous page"));
                    inv.setItem(49, item(Material.OAK_DOOR, "Player menu"));
                    if (entries.size() == 45) inv.setItem(53, item(Material.ARROW, "Next page"));
                    viewer.openInventory(inv);
                });
            } catch (SQLException e) {
                plugin.getMessageUtil().send(viewer, "errors.db-error");
            }
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer) || event.getClickedInventory() != event.getInventory()) return;
        int slot = event.getSlot();
        if (menu.history != null) {
            if (slot == 45 && menu.page > 0) openHistory(viewer, menu.target, menu.page - 1);
            else if (slot == 49) loadAndOpen(viewer, menu.target);
            else if (slot == 53 && menu.history.size() == 45) openHistory(viewer, menu.target, menu.page + 1);
            else if (slot >= 0 && slot < menu.history.size()) revokeFromHistory(viewer, menu.history.get(slot));
            return;
        }
        if (menu.type == null) {
            switch (slot) {
                case 10 -> openTemplates(viewer, menu.target, PunishmentType.BAN, false);
                case 11 -> openTemplates(viewer, menu.target, PunishmentType.BAN, true);
                case 12 -> openTemplates(viewer, menu.target, PunishmentType.MUTE, false);
                case 13 -> openTemplates(viewer, menu.target, PunishmentType.WARN, false);
                case 14 -> suggest(viewer, "/kick " + menu.target.name() + " ");
                case 15 -> openHistory(viewer, menu.target, 0);
                case 16 -> execute(viewer, "alts " + menu.target.name());
                case 17 -> execute(viewer, "mbans notes " + menu.target.name());
                default -> { }
            }
            return;
        }
        if (slot == 49) {
            if (menu.template != null) openTemplates(viewer, menu.target, menu.type, menu.forceTimed);
            else loadAndOpen(viewer, menu.target);
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String template = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(clicked.getItemMeta().displayName());
        if (menu.template != null) {
            execute(viewer, "tempban " + menu.target.name() + " " + template + " " + menu.template);
            return;
        }
        if (menu.forceTimed) {
            openDurations(viewer, menu.target, menu.type, template);
            return;
        }
        String command = switch (menu.type) {
            case BAN -> "ban";
            case MUTE -> "mute";
            case WARN -> "warn";
            default -> null;
        };
        if (command != null) execute(viewer, command + " " + menu.target.name() + " " + template);
    }

    private void execute(Player player, String command) {
        player.closeInventory();
        player.performCommand(command);
    }

    private void revokeFromHistory(Player viewer, Punishment entry) {
        if (!entry.isActive() || entry.isExpired()) return;
        String command = switch (entry.getType()) {
            case BAN -> "unban " + entry.getTargetName() + " GUI";
            case MUTE -> "unmute " + entry.getTargetName();
            case WARN -> "unwarn " + entry.getTargetName() + " " + entry.getId();
            case IP_BAN -> viewer.hasPermission("mbans.view.ip") && entry.getTargetIp() != null
                    ? "unbanip " + entry.getTargetIp() : null;
            case KICK -> null;
        };
        if (command != null) execute(viewer, command);
    }

    private void suggest(Player player, String command) {
        player.closeInventory();
        player.sendMessage(Component.text("Click to prepare: " + command)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(command)));
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) if (line != null) lines.add(Component.text(line));
        meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private static final class Menu implements InventoryHolder {
        private final PlayerRepository.PlayerIdentity target;
        private final PunishmentType type;
        private final boolean forceTimed;
        private final String template;
        private final List<Punishment> history;
        private final int page;
        private Inventory inventory;
        private Menu(PlayerRepository.PlayerIdentity target, PunishmentType type, boolean forceTimed, String template) {
            this.target = target;
            this.type = type;
            this.forceTimed = forceTimed;
            this.template = template;
            this.history = null;
            this.page = 0;
        }
        private Menu(PlayerRepository.PlayerIdentity target, List<Punishment> history, int page) {
            this.target = target;
            this.type = null;
            this.forceTimed = false;
            this.template = null;
            this.history = history;
            this.page = page;
        }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}
