package io.github.brainage04.brainage_minigames.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsGame;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKit;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsKits;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsPerk;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsProgression;
import io.github.brainage04.brainage_minigames.menu.SkyWarsMenus;
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

/** {@code /minigames skywars}: the kits and perks menu, kit choice, perk toggles and operator grants. */
public final class SkyWarsCommand {
    private SkyWarsCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return literal("skywars")
                .executes(context -> {
                    SkyWarsMenus.open(context.getSource().getPlayerOrException());
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
                .then(grantNode("grant", true))
                .then(grantNode("revoke", false));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> grantNode(String name, boolean owned) {
        return literal(name).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("targets", EntityArgument.players())
                        .then(literal("kit").then(argument("kit", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(kitIds(), builder))
                                .executes(context -> grant(context.getSource(), EntityArgument.getPlayers(context, "targets"),
                                        "kit", StringArgumentType.getString(context, "kit"), owned))))
                        .then(literal("perk").then(argument("perk", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(perkIds(), builder))
                                .executes(context -> grant(context.getSource(), EntityArgument.getPlayers(context, "targets"),
                                        "perk", StringArgumentType.getString(context, "perk"), owned)))));
    }

    private static Stream<String> kitIds() {
        return SkyWarsKits.of(SkyWarsGame.MODE).stream().map(SkyWarsKit::id);
    }

    private static Stream<String> perkIds() {
        return SkyWarsPerk.of(SkyWarsGame.MODE).stream().map(SkyWarsPerk::id);
    }

    private static int kits(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        SkyWarsKit selected = SkyWarsProgression.selectedKit(server, player.getUUID(), SkyWarsGame.MODE);
        StringBuilder owned = new StringBuilder();
        for (SkyWarsKit kit : SkyWarsKits.of(SkyWarsGame.MODE)) {
            if (SkyWarsProgression.owns(server, player.getUUID(), kit)) {
                owned.append(owned.isEmpty() ? "" : ", ").append(kit.id());
            }
        }
        source.sendSuccess(() -> Component.literal(SkyWarsGame.MODE.displayName + " kits you own: " + owned
                + ". Selected: " + selected.id() + ". Choose one with /minigames skywars kit <kit>."), false);
        return 1;
    }

    private static int selectKit(CommandSourceStack source, String id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SkyWarsKit kit = SkyWarsKits.find(SkyWarsGame.MODE, id);
        if (kit == null) {
            source.sendFailure(Component.literal("Unknown SkyWars kit " + id + "."));
            return 0;
        }
        if (!SkyWarsProgression.owns(source.getServer(), player.getUUID(), kit)) {
            source.sendFailure(Component.literal("You have not unlocked " + kit.name() + "."));
            return 0;
        }
        SkyWarsProgression.selectKit(source.getServer(), player.getUUID(), kit);
        source.sendSuccess(() -> Component.literal("Selected " + kit.name() + " for your next SkyWars match.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int perks(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        for (SkyWarsPerk perk : SkyWarsPerk.of(SkyWarsGame.MODE)) {
            boolean owned = SkyWarsProgression.owns(server, player.getUUID(), perk);
            boolean on = SkyWarsProgression.enabled(server, player.getUUID(), perk);
            source.sendSuccess(() -> Component.literal(perk.id() + ": ")
                    .append(!owned ? Component.literal("LOCKED").withStyle(ChatFormatting.GRAY)
                            : on ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN)
                            : Component.literal("DISABLED").withStyle(ChatFormatting.RED)), false);
        }
        return 1;
    }

    private static int togglePerk(CommandSourceStack source, String id, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SkyWarsPerk perk = SkyWarsPerk.find(SkyWarsGame.MODE, id);
        if (perk == null) {
            source.sendFailure(Component.literal("Unknown SkyWars perk " + id + "."));
            return 0;
        }
        SkyWarsProgression.setEnabled(source.getServer(), player.getUUID(), perk, enabled);
        source.sendSuccess(() -> Component.literal(perk.name() + (enabled ? " enabled" : " disabled")
                + " for your next SkyWars match."), false);
        return 1;
    }

    private static int grant(CommandSourceStack source, Collection<ServerPlayer> targets, String kind, String id, boolean owned) {
        SkyWarsKit kit = kind.equals("kit") ? SkyWarsKits.find(SkyWarsGame.MODE, id) : null;
        SkyWarsPerk perk = kind.equals("perk") ? SkyWarsPerk.find(SkyWarsGame.MODE, id) : null;
        if (kit == null && perk == null) {
            source.sendFailure(Component.literal("Unknown SkyWars " + kind + " " + id + "."));
            return 0;
        }
        String key = kit != null ? SkyWarsProgression.entry(kit) : SkyWarsProgression.entry(perk);
        for (ServerPlayer target : targets) {
            SkyWarsProgression.setOwned(source.getServer(), target.getUUID(), key, owned);
        }
        source.sendSuccess(() -> Component.literal((owned ? "Granted " : "Revoked ") + kind + " " + id + " for "
                + targets.size() + (targets.size() == 1 ? " player." : " players.")), true);
        return targets.size();
    }
}
