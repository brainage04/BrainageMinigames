package io.github.brainage04.brainage_minigames.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.brainage04.brainage_minigames.game.uhc.SpeedUhcKit;
import io.github.brainage04.brainage_minigames.game.uhc.SpeedUhcMastery;
import io.github.brainage04.brainage_minigames.game.uhc.SpeedUhcPerk;
import io.github.brainage04.brainage_minigames.game.uhc.SpeedUhcProgression;
import io.github.brainage04.brainage_minigames.menu.SpeedUhcMenus;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** {@code /minigames speed_uhc}: the Speed UHC Shop, kit, perk and Mastery choices, and operator grants. */
public final class SpeedUhcCommand {
    private SpeedUhcCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return literal("speed_uhc")
                .executes(context -> {
                    SpeedUhcMenus.open(context.getSource().getPlayerOrException());
                    return 1;
                })
                .then(literal("kit")
                        .executes(context -> kits(context.getSource()))
                        .then(argument("kit", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(kitIds(), builder))
                                .executes(context -> selectKit(context.getSource(), StringArgumentType.getString(context, "kit")))))
                .then(literal("perk")
                        .executes(context -> perks(context.getSource()))
                        .then(argument("perk", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(perkIds(), builder))
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> togglePerk(context.getSource(),
                                                StringArgumentType.getString(context, "perk"),
                                                BoolArgumentType.getBool(context, "enabled"))))))
                .then(literal("mastery")
                        .executes(context -> masteries(context.getSource()))
                        .then(argument("mastery", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(masteryIds(), builder))
                                .executes(context -> selectMastery(context.getSource(),
                                        StringArgumentType.getString(context, "mastery")))))
                .then(grantNode("grant", true))
                .then(grantNode("revoke", false));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> grantNode(String name, boolean owned) {
        LiteralArgumentBuilder<CommandSourceStack> node = literal(name).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        var targets = argument("targets", EntityArgument.players());
        for (String kind : new String[] {"kit", "perk", "mastery"}) {
            targets.then(literal(kind).then(argument("id", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                            kind.equals("kit") ? kitIds() : kind.equals("perk") ? perkIds() : masteryIds(), builder))
                    .executes(context -> grant(context.getSource(), EntityArgument.getPlayers(context, "targets"),
                            kind, StringArgumentType.getString(context, "id"), owned))));
        }
        return node.then(targets);
    }

    private static Stream<String> kitIds() {
        return SpeedUhcKit.ALL.stream().map(SpeedUhcKit::id);
    }

    private static Stream<String> perkIds() {
        return Arrays.stream(SpeedUhcPerk.values()).map(perk -> perk.id);
    }

    private static Stream<String> masteryIds() {
        return Arrays.stream(SpeedUhcMastery.values()).map(mastery -> mastery.id);
    }

    private static int kits(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        StringBuilder owned = new StringBuilder();
        for (SpeedUhcKit kit : SpeedUhcKit.ALL) {
            if (SpeedUhcProgression.owns(server, player.getUUID(), kit)) owned.append(owned.isEmpty() ? "" : ", ").append(kit.id());
        }
        String selected = SpeedUhcProgression.selectedKit(server, player.getUUID()).id();
        source.sendSuccess(() -> Component.literal("Speed UHC kits you own: " + owned + ". Selected: " + selected
                + ". Choose one with /minigames speed_uhc kit <kit>."), false);
        return 1;
    }

    private static int selectKit(CommandSourceStack source, String id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SpeedUhcKit kit = SpeedUhcKit.find(id);
        if (kit == null) {
            source.sendFailure(Component.literal("Unknown Speed UHC kit " + id + "."));
            return 0;
        }
        if (!SpeedUhcProgression.owns(source.getServer(), player.getUUID(), kit)) {
            source.sendFailure(Component.literal("You have not unlocked " + kit.name() + "."));
            return 0;
        }
        SpeedUhcProgression.selectKit(source.getServer(), player.getUUID(), kit);
        source.sendSuccess(() -> Component.literal("Selected " + kit.name() + " for your next Speed UHC match.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int perks(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        for (SpeedUhcPerk perk : SpeedUhcPerk.values()) {
            boolean owned = SpeedUhcProgression.owns(server, player.getUUID(), perk);
            boolean on = SpeedUhcProgression.enabled(server, player.getUUID(), perk);
            source.sendSuccess(() -> Component.literal(perk.id + ": ")
                    .append(!owned ? Component.literal("LOCKED").withStyle(ChatFormatting.GRAY)
                            : on ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN)
                            : Component.literal("DISABLED").withStyle(ChatFormatting.RED)), false);
        }
        return 1;
    }

    private static int togglePerk(CommandSourceStack source, String id, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SpeedUhcPerk perk = SpeedUhcPerk.find(id);
        if (perk == null) {
            source.sendFailure(Component.literal("Unknown Speed UHC perk " + id + "."));
            return 0;
        }
        SpeedUhcProgression.setEnabled(source.getServer(), player.getUUID(), perk, enabled);
        source.sendSuccess(() -> Component.literal(perk.displayName + (enabled ? " enabled" : " disabled")
                + " for your next Speed UHC match."), false);
        return 1;
    }

    private static int masteries(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        StringBuilder owned = new StringBuilder();
        for (SpeedUhcMastery mastery : SpeedUhcMastery.values()) {
            if (SpeedUhcProgression.owns(server, player.getUUID(), mastery)) owned.append(owned.isEmpty() ? "" : ", ").append(mastery.id);
        }
        String active = SpeedUhcProgression.mastery(server, player.getUUID()).id;
        source.sendSuccess(() -> Component.literal("Masteries you own: " + owned + ". Active: " + active
                + ". Choose one with /minigames speed_uhc mastery <mastery>."), false);
        return 1;
    }

    private static int selectMastery(CommandSourceStack source, String id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SpeedUhcMastery mastery = SpeedUhcMastery.find(id);
        if (mastery == null) {
            source.sendFailure(Component.literal("Unknown Speed UHC Mastery " + id + "."));
            return 0;
        }
        if (!SpeedUhcProgression.owns(source.getServer(), player.getUUID(), mastery)) {
            source.sendFailure(Component.literal("You have not unlocked " + mastery.displayName + "."));
            return 0;
        }
        SpeedUhcProgression.selectMastery(source.getServer(), player.getUUID(), mastery);
        source.sendSuccess(() -> Component.literal("Selected the " + mastery.displayName + " Mastery.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int grant(CommandSourceStack source, Collection<ServerPlayer> targets, String kind, String id, boolean owned) {
        String key = switch (kind) {
            case "kit" -> SpeedUhcKit.find(id) == null ? null : SpeedUhcProgression.entry(SpeedUhcKit.find(id));
            case "perk" -> SpeedUhcPerk.find(id) == null ? null : SpeedUhcProgression.entry(SpeedUhcPerk.find(id));
            default -> SpeedUhcMastery.find(id) == null ? null : SpeedUhcProgression.entry(SpeedUhcMastery.find(id));
        };
        if (key == null) {
            source.sendFailure(Component.literal("Unknown Speed UHC " + kind + " " + id + "."));
            return 0;
        }
        for (ServerPlayer target : targets) SpeedUhcProgression.setOwned(source.getServer(), target.getUUID(), key, owned);
        source.sendSuccess(() -> Component.literal((owned ? "Granted " : "Revoked ") + kind + " " + id + " for "
                + targets.size() + (targets.size() == 1 ? " player." : " players.")), true);
        return targets.size();
    }
}
