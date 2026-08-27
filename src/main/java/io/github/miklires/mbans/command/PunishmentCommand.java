package io.github.miklires.mbans.command;

import io.github.miklires.mbans.MBans;
import io.github.miklires.mbans.database.PlayerRepository;
import io.github.miklires.mbans.config.ConfigManager;
import io.github.miklires.mbans.model.Punishment;
import io.github.miklires.mbans.model.PunishmentType;
import io.github.miklires.mbans.service.DurationParser;
import io.github.miklires.mbans.service.PunishmentService;
import io.github.miklires.mbans.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class PunishmentCommand implements TabExecutor {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final MBans plugin;

    public PunishmentCommand(MBans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("mbans.command." + name)) {
            plugin.getMessageUtil().send(sender, "errors.no-permission");
            return true;
        }
        return switch (name) {
            case "ban", "mute", "shadowmute" -> timedPunishment(sender, name, args, false);
            case "tempban", "tempmute", "tempshadowmute" -> timedPunishment(sender, name, args, true);
            case "warn" -> warn(sender, args);
            case "kick" -> kick(sender, args);
            case "unban", "unmute", "unshadowmute" -> revoke(sender, name, args);
            case "banip" -> banIp(sender, args);
            case "unbanip" -> unbanIp(sender, args);
            case "ipmute" -> muteIp(sender,args);
            case "unipmute" -> unmuteIp(sender,args);
            case "unwarn" -> unwarn(sender, args);
            case "history" -> history(sender, args, false);
            case "staffhistory" -> history(sender, args, true);
            case "check" -> check(sender, args);
            case "banlist" -> banlist(sender, args);
            case "mutelist" -> activeList(sender,args,PunishmentType.MUTE);
            case "warns" -> warns(sender,args);
            case "punishment" -> punishment(sender,args);
            case "alts" -> alts(sender, args);
            case "appeal" -> appeal(sender,args);
            default -> true;
        };
    }

    private boolean timedPunishment(CommandSender sender, String command, String[] args, boolean durationRequired) {
        int reasonStart = durationRequired ? 2 : 1;
        if (args.length < 1 || (durationRequired && args.length < 2)) {
            plugin.getMessageUtil().send(sender, "errors.usage-" + command);
            return true;
        }
        Duration duration = null;
        if (durationRequired) {
            Optional<Duration> parsed = DurationParser.parse(args[1]);
            if (parsed.isEmpty()) {
                plugin.getMessageUtil().send(sender, "errors.invalid-duration");
                return true;
            }
            duration = parsed.get();
        } else {
            CommandHelper.ParsedArgs parsed = CommandHelper.parseDurationAndReason(args, 1);
            duration = parsed.duration();
            reasonStart = duration == null ? 1 : 2;
        }
        CommandHelper.Options options = CommandHelper.parseOptions(args, reasonStart);
        if(!silentAllowed(sender,options))return true;
        String reason = options.text();
        if (!reason.isBlank() && !reason.contains(" ")) {
            Optional<ConfigManager.ReasonTemplate> template = plugin.getConfigManager().getTemplate(reason);
            if (template.isPresent()) {
                PunishmentType expected = typeFromCommand(command);
                if (template.get().type() == expected) {
                    reason = template.get().reason();
                    if (duration == null) duration = template.get().duration();
                }
            }
        }
        if (reason.isBlank()) reason = plugin.getConfigManager().getDefaultReason();
        Duration finalDuration = duration;
        String finalReason = reason;
        String type = command.contains("ban") ? "ban" : "mute";
        int issuerLevel = plugin.getConfigManager().getImmunityLevel(sender);
        boolean canOverride = sender.hasPermission("mbans.override");
        resolve(args[0], target -> {
            if (!canTarget(sender, issuerLevel, target)) return;
            if (target.online() != null && target.online().hasPermission("mbans.bypass." + type)) {
                message(sender, "errors.bypass-" + type);
                return;
            }
            run(sender, () -> {
            PunishmentType punishmentType = typeFromCommand(command);
            String evidence = options.evidence() != null ? options.evidence()
                    : (options.lastMessages() > 0 ? plugin.getChatEvidenceService().snapshot(target.uuid(), options.lastMessages()) : null);
            Optional<Punishment> active = plugin.getPunishmentRepository().findActiveByUuid(target.uuid(), punishmentType);
            if (active.isPresent()) {
                if (!canOverride) {
                    message(sender, type.equals("ban") ? "errors.already-banned" : "errors.already-muted");
                    return;
                }
                if(isShorter(active.get(),finalDuration)&&!sender.hasPermission("mbans.override.shorten")){message(sender,"errors.cannot-shorten");return;}
                plugin.getPunishmentRepository().deactivate(active.get().getId(), sender.getName(), "overridden");
                plugin.getNetworkLogRepository().append(active.get().getId(), "REVOKE",
                        plugin.getConfigManager().getNetworkServerName());
                if(PunishmentService.isMute(active.get().getType()))plugin.getMuteCacheService().invalidatePunishment(active.get().getId());
            }
            if (punishmentType==PunishmentType.BAN) {
                plugin.getPunishmentService().ban(target.uuid(), target.name(), target.ip(), finalDuration, finalReason,
                        sender.getName(), CommandHelper.issuerUuid(sender), options.silent(), evidence);
            } else if(punishmentType==PunishmentType.SHADOW_MUTE) {
                plugin.getPunishmentService().shadowMute(target.uuid(),target.name(),target.ip(),finalDuration,finalReason,
                        sender.getName(),CommandHelper.issuerUuid(sender),options.silent(),evidence);
            } else {
                plugin.getPunishmentService().mute(target.uuid(), target.name(), target.ip(), finalDuration, finalReason,
                        sender.getName(), CommandHelper.issuerUuid(sender), options.silent(), evidence);
            }
            String key = "success." + (punishmentType==PunishmentType.BAN?"banned":punishmentType==PunishmentType.SHADOW_MUTE?"shadow-muted":"muted") + (finalDuration == null ? "" : "-temp");
            if (finalDuration == null) {
                message(sender, key, MessageUtil.ph("player", target.name()), MessageUtil.ph("reason", finalReason));
            } else {
                message(sender, key, MessageUtil.ph("player", target.name()), MessageUtil.ph("reason", finalReason),
                        MessageUtil.ph("duration", DurationParser.format(finalDuration)));
            }
            });
        }, sender);
        return true;
    }

    private PunishmentType typeFromCommand(String command) {
        if(command.contains("shadow"))return PunishmentType.SHADOW_MUTE;
        return command.contains("ban") ? PunishmentType.BAN : PunishmentType.MUTE;
    }

    private boolean warn(CommandSender sender, String[] args) {
        if (args.length < 1) {
            message(sender, "errors.usage-warn");
            return true;
        }
        CommandHelper.Options options = CommandHelper.parseOptions(args, 1);
        if(!silentAllowed(sender,options))return true;
        String reason = options.text();
        if (!reason.isBlank()&&!reason.contains(" ")) {
            Optional<ConfigManager.ReasonTemplate> template = plugin.getConfigManager().getTemplate(reason);
            if (template.isPresent() && template.get().type() == PunishmentType.WARN) reason = template.get().reason();
        }
        String finalReason = reason.isBlank()?plugin.getConfigManager().getDefaultReason():reason;
        int issuerLevel = plugin.getConfigManager().getImmunityLevel(sender);
        resolve(args[0], target -> {
            if (!canTarget(sender, issuerLevel, target)) return;
            if (target.online() != null && target.online().hasPermission("mbans.bypass.warn")) {
                message(sender, "errors.bypass-warn");
                return;
            }
            run(sender, () -> {
            String evidence = options.evidence() != null ? options.evidence()
                    : (options.lastMessages() > 0 ? plugin.getChatEvidenceService().snapshot(target.uuid(), options.lastMessages()) : null);
            plugin.getPunishmentService().warn(target.uuid(), target.name(), target.ip(), finalReason,
                    sender.getName(), CommandHelper.issuerUuid(sender), options.silent(), evidence);
            int count = plugin.getPunishmentRepository().countActiveWarns(target.uuid());
            int max = plugin.getConfigManager().getWarningDisplayThreshold();
            message(sender, "success.warned", MessageUtil.ph("player", target.name()), MessageUtil.ph("reason", finalReason),
                    MessageUtil.ph("count", count), MessageUtil.ph("max", max));
            if (target.online() != null) message(target.online(), "notify.warn-received", MessageUtil.ph("reason", finalReason),
                    MessageUtil.ph("count", count), MessageUtil.ph("max", max));
            if (target.online() != null) plugin.getPunishmentRepository().markWarnsDelivered(target.uuid());
            });
        }, sender);
        return true;
    }

    private boolean kick(CommandSender sender, String[] args) {
        if (args.length < 1) {
            message(sender, "errors.usage-kick");
            return true;
        }
        Player player = Bukkit.getPlayerExact(args[0]);
        if (player == null) {
            message(sender, "errors.player-not-found");
            return true;
        }
        CommandHelper.Options options=CommandHelper.parseOptions(args,1);if(!silentAllowed(sender,options))return true;
        String reason = options.text().isBlank()?plugin.getConfigManager().getDefaultReason():options.text();
        int issuerLevel = plugin.getConfigManager().getImmunityLevel(sender);
        plugin.getScheduler().entity(player, () -> {
            if (player.hasPermission("mbans.bypass.kick")) {
                message(sender, "errors.bypass-kick");
                return;
            }
            Target target = snapshot(player);
            if (!canTarget(sender, issuerLevel, target)) return;
            run(sender, () -> {
                plugin.getPunishmentService().kick(target.uuid(), target.name(), target.ip(), reason,
                        sender.getName(), CommandHelper.issuerUuid(sender),options.silent(),options.evidence());
                message(sender, "success.kicked", MessageUtil.ph("player", target.name()));
            });
        });
        return true;
    }

    private boolean revoke(CommandSender sender, String command, String[] args) {
        if (args.length < 1) {
            message(sender, "errors.usage-" + command);
            return true;
        }
        String target = args[0];
        String reason = args.length > 1 ? CommandHelper.joinFrom(args, 1) : "removed";
        run(sender, () -> {
            boolean done = command.equals("unban")?plugin.getPunishmentService().unban(target,sender.getName(),reason)
                    :command.equals("unshadowmute")?plugin.getPunishmentService().unshadowMute(target,sender.getName())
                    :plugin.getPunishmentService().unmute(target,sender.getName());
            if (!done) {
                message(sender, command.equals("unban") ? "errors.not-banned" : "errors.not-muted");
                return;
            }
            message(sender, command.equals("unban") ? "success.unbanned" : "success.unmuted", MessageUtil.ph("player", target));
        });
        return true;
    }

    private boolean banIp(CommandSender sender, String[] args) {
        if (args.length < 1) {
            message(sender, "errors.usage-banip");
            return true;
        }
        Optional<Duration> first = args.length>1?DurationParser.parse(args[1]):Optional.empty();
        int reasonStart = first.isPresent() ? 2 : 1;
        CommandHelper.Options options = CommandHelper.parseOptions(args, reasonStart);
        if(!silentAllowed(sender,options))return true;
        if(options.text().isBlank())options=new CommandHelper.Options(options.silent(),options.evidence(),options.lastMessages(),plugin.getConfigManager().getDefaultReason());
        CommandHelper.Options finalOptions=options;
        if (CommandHelper.isValidIp(args[0])) {
            String ip = CommandHelper.normalizeIp(args[0]);
            issueIpBan(sender, new Target(null, ip, ip, null, 0), first.orElse(null), finalOptions);
        } else {
            int issuerLevel = plugin.getConfigManager().getImmunityLevel(sender);
            resolve(args[0], target -> {
                if (!canTarget(sender, issuerLevel, target)) return;
                if (target.online() != null && target.online().hasPermission("mbans.bypass.ban")) {
                    message(sender, "errors.bypass-ban");
                    return;
                }
                issueIpBan(sender, target, first.orElse(null), finalOptions);
            }, sender);
        }
        return true;
    }

    private void issueIpBan(CommandSender sender, Target target, Duration duration, CommandHelper.Options options) {
        if (target.ip() == null) {
            message(sender, "errors.invalid-ip");
            return;
        }
        if (plugin.getConfigManager().isIpExempt(target.ip())) {
            reply(sender, "That IP address is covered by an exempt range.");
            return;
        }
        run(sender, () -> {
            plugin.getPunishmentService().ipBan(target.ip(), target.name(), target.uuid(), duration, options.text(),
                    sender.getName(), CommandHelper.issuerUuid(sender), options.silent(), options.evidence());
            message(sender, "success.ip-banned", MessageUtil.ph("ip", target.ip()),
                    MessageUtil.ph("player", target.name()), MessageUtil.ph("reason", options.text()));
        });
    }

    private boolean unbanIp(CommandSender sender, String[] args) {
        if (args.length < 1) {
            message(sender, "errors.usage-unbanip");
            return true;
        }
        if (CommandHelper.isValidIp(args[0])) {
            revokeIp(sender, CommandHelper.normalizeIp(args[0]));
        } else {
            resolve(args[0], target -> {
                if (target.ip() == null) message(sender, "errors.invalid-ip");
                else revokeIp(sender, target.ip());
            }, sender);
        }
        return true;
    }

    private void revokeIp(CommandSender sender, String ip) {
        run(sender, () -> {
            if (!plugin.getPunishmentService().unbanIp(ip, sender.getName(), "removed")) {
                message(sender, "errors.not-banned");
                return;
            }
            message(sender, "success.ip-unbanned", MessageUtil.ph("ip", ip));
        });
    }

    private boolean muteIp(CommandSender sender,String[]args){if(args.length<1){message(sender,"errors.usage-ipmute");return true;}
        Optional<Duration> duration=args.length>1?DurationParser.parse(args[1]):Optional.empty();int reasonStart=duration.isPresent()?2:1;
        CommandHelper.Options options=CommandHelper.parseOptions(args,reasonStart);if(!silentAllowed(sender,options))return true;
        if(options.text().isBlank())options=new CommandHelper.Options(options.silent(),options.evidence(),options.lastMessages(),plugin.getConfigManager().getDefaultReason());
        CommandHelper.Options finalOptions=options;if(CommandHelper.isValidIp(args[0]))issueIpMute(sender,new Target(null,args[0],CommandHelper.normalizeIp(args[0]),null,0),duration.orElse(null),finalOptions);
        else resolve(args[0],target->{if(!canTarget(sender,plugin.getConfigManager().getImmunityLevel(sender),target))return;if(target.online()!=null&&target.online().hasPermission("mbans.bypass.mute")){message(sender,"errors.bypass-mute");return;}issueIpMute(sender,target,duration.orElse(null),finalOptions);},sender);return true;}

    private void issueIpMute(CommandSender sender,Target target,Duration duration,CommandHelper.Options options){if(target.ip()==null){message(sender,"errors.invalid-ip");return;}
        if(plugin.getConfigManager().isIpExempt(target.ip())){reply(sender,"That IP address is covered by an exempt range.");return;}
        run(sender,()->{if(plugin.getPunishmentRepository().findActiveByIp(target.ip(),PunishmentType.IP_MUTE).isPresent()){message(sender,"errors.already-muted");return;}
            plugin.getPunishmentService().ipMute(target.ip(),target.name(),target.uuid(),duration,options.text(),sender.getName(),CommandHelper.issuerUuid(sender),options.silent(),options.evidence());
            message(sender,"success.ip-muted",MessageUtil.ph("ip",target.ip()),MessageUtil.ph("player",target.name()),MessageUtil.ph("reason",options.text()));});}

    private boolean unmuteIp(CommandSender sender,String[]args){if(args.length<1){message(sender,"errors.usage-unipmute");return true;}
        if(CommandHelper.isValidIp(args[0]))revokeIpMute(sender,CommandHelper.normalizeIp(args[0]));else resolve(args[0],target->{if(target.ip()==null)message(sender,"errors.invalid-ip");else revokeIpMute(sender,target.ip());},sender);return true;}

    private void revokeIpMute(CommandSender sender,String ip){run(sender,()->{if(!plugin.getPunishmentService().unmuteIp(ip,sender.getName())){message(sender,"errors.not-ip-muted");return;}message(sender,"success.ip-unmuted",MessageUtil.ph("ip",ip));});}

    private boolean unwarn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            message(sender, "errors.usage-unwarn");
            return true;
        }
        resolve(args[0], target -> run(sender, () -> {
            if (args[1].equalsIgnoreCase("all")) {
                plugin.getPunishmentService().unwarnAll(target.uuid(), sender.getName());
                message(sender, "success.warns-cleared", MessageUtil.ph("player", target.name()));
                return;
            }
            long id;
            try { id = Long.parseLong(args[1]); }
            catch (NumberFormatException e) { message(sender, "errors.usage-unwarn"); return; }
            if (!plugin.getPunishmentService().unwarn(target.uuid(), id, sender.getName())) {
                message(sender, "errors.warn-not-found");
                return;
            }
            message(sender, "success.warn-removed");
        }), sender);
        return true;
    }

    private boolean history(CommandSender sender, String[] args, boolean staff) {
        if (args.length < 1) {
            reply(sender, "Usage: /" + (staff ? "staffhistory" : "history") + " <name> [page]");
            return true;
        }
        int page = page(args, 1);
        boolean viewIp = sender.hasPermission("mbans.view.ip");
        run(sender, () -> {
            List<Punishment> entries = staff
                    ? plugin.getPunishmentRepository().getStaffHistory(args[0], 10, (page - 1) * 10)
                    : plugin.getPunishmentRepository().getHistory(args[0], 10, (page - 1) * 10);
            if (entries.isEmpty()) { message(sender, "errors.no-history"); return; }
            Component header = Component.text((staff ? "Staff history for " : "History for ") + args[0] + " (page " + page + ")");
            if (page > 1) header = header.append(Component.text(" [previous]")
                    .clickEvent(ClickEvent.runCommand("/" + (staff ? "staffhistory" : "history") + " " + args[0] + " " + (page - 1))));
            if (entries.size() == 10) header = header.append(Component.text(" [next]")
                    .clickEvent(ClickEvent.runCommand("/" + (staff ? "staffhistory" : "history") + " " + args[0] + " " + (page + 1))));
            reply(sender, header);
            for (Punishment entry : entries) {
                Component line = Component.text("#" + entry.getId() + " " + entry.getType() + " -> " + entry.getTargetName()
                        + " | " + entry.getReason() + " | " + DATE.format(entry.getIssuedAt())
                        + (entry.isActive() ? " [active]" : " [revoked]"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to prepare a moderation command")));
                String suggestion = switch (entry.getType()) {
                    case BAN -> "/unban " + entry.getTargetName() + " ";
                    case IP_BAN -> "/unbanip " + (!viewIp || entry.getTargetIp() == null ? "" : entry.getTargetIp());
                    case MUTE -> "/unmute " + entry.getTargetName();
                    case IP_MUTE -> "/unipmute " + (!viewIp || entry.getTargetIp() == null ? "" : entry.getTargetIp());
                    case SHADOW_MUTE -> "/unshadowmute " + entry.getTargetName();
                    case WARN -> "/unwarn " + entry.getTargetName() + " " + entry.getId();
                    case KICK -> "/check " + entry.getTargetName();
                };
                reply(sender, line.clickEvent(ClickEvent.suggestCommand(suggestion)));
            }
        });
        return true;
    }

    private boolean check(CommandSender sender, String[] args) {
        if (args.length < 1) { message(sender, "errors.usage-check"); return true; }
        resolve(args[0], target -> run(sender, () -> {
            Optional<Punishment> ban = plugin.getPunishmentRepository().findActiveByUuid(target.uuid(), PunishmentType.BAN);
            Optional<Punishment> mute = plugin.getPunishmentRepository().findActiveByUuid(target.uuid(), PunishmentType.MUTE);
            if(mute.isEmpty()&&target.ip()!=null)mute=plugin.getPunishmentRepository().findActiveByIp(target.ip(),PunishmentType.IP_MUTE);
            if(mute.isEmpty())mute=plugin.getPunishmentRepository().findActiveByUuid(target.uuid(),PunishmentType.SHADOW_MUTE);
            int warns = plugin.getPunishmentRepository().countActiveWarns(target.uuid());
            reply(sender, target.name() + ": ban=" + status(ban) + ", mute=" + status(mute) + ", warnings=" + warns);
        }), sender);
        return true;
    }

    private boolean banlist(CommandSender sender, String[] args) {
        int page = page(args, 0);
        run(sender, () -> {
            List<Punishment> bans = plugin.getPunishmentRepository().getActiveBans(10, (page - 1) * 10);
            if (bans.isEmpty()) { reply(sender, "No active bans"); return; }
            reply(sender, "Active bans (page " + page + ")");
            for (Punishment ban : bans) reply(sender, "#" + ban.getId() + " " + ban.getTargetName() + " | " + ban.getReason()
                    + " | " + DurationParser.formatExpiresAt(ban.getExpiresAt()));
        });
        return true;
    }

    private boolean activeList(CommandSender sender,String[]args,PunishmentType type){int page=page(args,0);run(sender,()->{List<Punishment> entries=type==PunishmentType.MUTE?plugin.getPunishmentRepository().getActiveMutes(10,(page-1)*10):plugin.getPunishmentRepository().getActive(type,10,(page-1)*10);if(entries.isEmpty()){reply(sender,"No active "+type.name().toLowerCase(Locale.ROOT)+"s");return;}reply(sender,"Active "+type.name().toLowerCase(Locale.ROOT)+"s (page "+page+")");for(Punishment entry:entries)reply(sender,"#"+entry.getId()+" "+entry.getType()+" "+entry.getTargetName()+" | "+entry.getReason()+" | "+DurationParser.formatExpiresAt(entry.getExpiresAt()));});return true;}

    private boolean warns(CommandSender sender,String[]args){if(args.length<1){reply(sender,"Usage: /warns <player>");return true;}resolve(args[0],target->run(sender,()->{List<Punishment> entries=plugin.getPunishmentRepository().findActiveWarns(target.uuid());if(entries.isEmpty()){reply(sender,"No active warnings for "+target.name());return;}reply(sender,"Active warnings for "+target.name()+":");for(Punishment entry:entries)reply(sender,"#"+entry.getId()+" "+entry.getReason()+" | "+DATE.format(entry.getIssuedAt()));}),sender);return true;}

    private boolean punishment(CommandSender sender,String[]args){if(args.length<1){reply(sender,"Usage: /punishment <id>");return true;}long id;try{id=Long.parseLong(args[0]);}catch(NumberFormatException error){reply(sender,"Invalid punishment ID");return true;}run(sender,()->{Optional<Punishment> found=plugin.getPunishmentRepository().findById(id);if(found.isEmpty()){reply(sender,"Punishment not found");return;}Punishment value=found.get();String ip=sender.hasPermission("mbans.view.ip")&&value.getTargetIp()!=null?" | ip="+value.getTargetIp():"";reply(sender,"#"+value.getId()+" "+value.getType()+" | "+value.getTargetName()+ip+" | "+value.getReason()+" | by "+value.getIssuedByName()+" | "+(value.isActive()?"active":"inactive")+" | appeal="+value.getAppealId());});return true;}

    private boolean alts(CommandSender sender, String[] args) {
        if (args.length < 1) {
            reply(sender, "Usage: /alts <player>");
            return true;
        }
        resolve(args[0], target -> run(sender, () -> {
            if (target.ip() == null) {
                reply(sender, "No recorded IP address for " + target.name());
                return;
            }
            List<PlayerRepository.PlayerIdentity> matches = plugin.getPlayerRepository().findByIp(target.ip(), 50);
            String shownIp = sender.hasPermission("mbans.view.ip") ? target.ip() : "hidden";
            reply(sender, "Accounts sharing " + shownIp + " with " + target.name() + ":");
            for (PlayerRepository.PlayerIdentity match : matches) {
                Optional<Punishment> ban = plugin.getPunishmentRepository()
                        .findActiveByUuid(match.uuid(), PunishmentType.BAN);
                reply(sender, "- " + match.name() + (ban.isPresent() ? " [banned]" : ""));
            }
        }), sender);
        return true;
    }

    private boolean appeal(CommandSender sender,String[]args){if(!(sender instanceof Player player)){message(sender,"errors.player-only");return true;}
        if(!plugin.getConfigManager().isInGameAppealsEnabled()){message(sender,"errors.appeals-disabled");return true;}
        if(args.length<2){message(sender,"errors.usage-appeal");return true;}String text=CommandHelper.joinFrom(args,1).strip();
        if(text.length()>plugin.getConfigManager().getAppealMaxLength()){message(sender,"errors.appeal-too-long",MessageUtil.ph("max",plugin.getConfigManager().getAppealMaxLength()));return true;}
        UUID playerUuid=player.getUniqueId();String playerName=player.getName();
        run(sender,()->{Optional<Punishment> punishment=plugin.getPunishmentRepository().findByAppealId(args[0]);
            if(punishment.isEmpty()||punishment.get().getTargetUuid()==null||!punishment.get().getTargetUuid().equals(playerUuid)){message(sender,"errors.appeal-not-found");return;}
            long id=plugin.getAdministrationRepository().submitAppeal(punishment.get().getId(),playerUuid,playerName,text);
            if(id<0){message(sender,"errors.appeal-exists");return;}message(sender,"success.appeal-submitted",MessageUtil.ph("id",String.valueOf(id)));});return true;}

    private String status(Optional<Punishment> value) {
        return value.map(p -> p.getReason() + " (#" + p.getId() + ")").orElse("none");
    }

    private boolean canTarget(CommandSender sender, int issuer, Target target) {
        Player online = target.online();
        if (online != null && plugin.getConfigManager().getExemptWorlds().stream()
                .anyMatch(world -> world.equalsIgnoreCase(online.getWorld().getName()))) {
            reply(sender, "That player is in an exempt world.");
            return false;
        }
        if (plugin.getConfigManager().isIpExempt(target.ip())) {
            reply(sender, "That player's IP address is exempt.");
            return false;
        }
        if (!plugin.getConfigManager().preventHigherLevelTargets()) return true;
        int targetLevel = online == null ? target.immunityLevel() : plugin.getConfigManager().getImmunityLevel(online);
        if (issuer > targetLevel) return true;
        reply(sender, "You cannot punish a player with an equal or higher immunity level.");
        return false;
    }

    private boolean silentAllowed(CommandSender sender,CommandHelper.Options options){if(!options.silent()||sender.hasPermission("mbans.silent"))return true;message(sender,"errors.no-silent-permission");return false;}
    private boolean isShorter(Punishment current,Duration replacement){if(current.isPermanent())return replacement!=null;if(replacement==null)return false;return current.getExpiresAt()!=null&&Instant.now().plus(replacement).isBefore(current.getExpiresAt());}

    private int page(String[] args, int index) {
        if (args.length <= index) return 1;
        try { return Math.max(1, Integer.parseInt(args[index])); }
        catch (NumberFormatException e) { return 1; }
    }

    private void resolve(String name, java.util.function.Consumer<Target> action, CommandSender sender) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            plugin.getScheduler().entity(online, () -> action.accept(snapshot(online)));
            return;
        }
        plugin.getScheduler().async(() -> {
            try {
                Optional<PlayerRepository.PlayerIdentity> found = plugin.getPlayerRepository().findByName(name);
                if (found.isEmpty()) found = plugin.getProfileResolver().resolve(name);
                if (found.isEmpty()) { message(sender, "errors.player-not-found"); return; }
                PlayerRepository.PlayerIdentity player = found.get();
                action.accept(new Target(player.uuid(), player.name(), player.ip(), null, player.immunityLevel()));
            } catch (SQLException e) {
                fail(sender, e);
            }
        });
    }

    private Target snapshot(Player player) {
        String ip = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
        return new Target(player.getUniqueId(), player.getName(), ip, player,
                plugin.getConfigManager().getImmunityLevel(player));
    }

    private void run(CommandSender sender, SqlAction action) {
        plugin.getScheduler().async(() -> {
            try { action.run(); }
            catch (SQLException e) { fail(sender, e); }
        });
    }

    private void fail(CommandSender sender, SQLException error) {
        plugin.getLogger().warning("Command storage operation failed: " + error.getMessage());
        message(sender, "errors.db-error");
    }

    private void message(CommandSender sender, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... values) {
        plugin.getMessageUtil().send(sender, key, values);
    }

    private void reply(CommandSender sender, String text) {
        plugin.getScheduler().global(() -> sender.sendMessage(Component.text(text)));
    }

    private void reply(CommandSender sender, Component text) {
        plugin.getScheduler().global(() -> sender.sendMessage(text));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("mbans.command." + name)) return List.of();
        if (args.length == 1 && !List.of("banlist","mutelist","punishment","appeal").contains(name)) return filter(Bukkit.getOnlinePlayers().stream()
                .map(Player::getName).toList(), args[0]);
        if (args.length == 2) {
            List<String> values = switch (name) {
                case "ban", "tempban" -> concat(plugin.getConfigManager().getDurationPresets(PunishmentType.BAN),
                        plugin.getConfigManager().getTemplateNames(PunishmentType.BAN));
                case "mute", "tempmute", "shadowmute", "tempshadowmute", "ipmute" -> concat(plugin.getConfigManager().getDurationPresets(PunishmentType.MUTE),
                        plugin.getConfigManager().getTemplateNames(PunishmentType.MUTE));
                case "warn" -> plugin.getConfigManager().getTemplateNames(PunishmentType.WARN);
                case "unwarn" -> List.of("all");
                case "history", "staffhistory" -> List.of("1", "2", "3");
                default -> List.of();
            };
            return filter(values, args[1]);
        }
        return List.of();
    }

    private List<String> concat(List<String> left, List<String> right) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>(left);
        values.addAll(right);
        return values;
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).distinct().toList();
    }

    private record Target(UUID uuid, String name, String ip, Player online, int immunityLevel) {}

    @FunctionalInterface
    private interface SqlAction { void run() throws SQLException; }
}
