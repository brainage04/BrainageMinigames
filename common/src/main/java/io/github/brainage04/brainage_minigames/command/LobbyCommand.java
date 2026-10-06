package io.github.brainage04.brainage_minigames.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.brainage04.brainage_minigames.api.MatchBots;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /minigames vote}: a waiting player votes to start their lobby now. {@code /minigames bots
 * <match> ...}: a player waiting in a lobby, or a game master, reserves its slots for bots.
 */
public final class LobbyCommand {
    private static final String MATCH = "match";
    private static final String COUNT = "count";
    private static final String TEAM = "team";
    private static final String DIFFICULTY = "difficulty";

    private LobbyCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> voteNode() {
        return literal("vote").executes(context -> vote(context.getSource()));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> botsNode() {
        return literal("bots")
                .requires(source -> MatchBots.available())
                .then(
                        argument(MATCH, IntegerArgumentType.integer(1))
                                .suggests(
                                        (context, builder) ->
                                                SharedSuggestionProvider.suggest(
                                                        MatchManager.matches().stream()
                                                                .filter(match -> match.phase() == MatchPhase.LOBBY)
                                                                .map(match -> String.valueOf(match.id()))
                                                                .toList(),
                                                        builder))
                                .then(
                                        literal("add")
                                                .then(
                                                        argument(COUNT, IntegerArgumentType.integer(1))
                                                                .executes(context -> add(context, 0))
                                                                .then(
                                                                        argument(TEAM, IntegerArgumentType.integer(1))
                                                                                .executes(
                                                                                        context ->
                                                                                                add(
                                                                                                        context,
                                                                                                        IntegerArgumentType.getInteger(
                                                                                                                context, TEAM))))))
                                .then(literal("fill").executes(LobbyCommand::fill))
                                .then(literal("clear").executes(LobbyCommand::clear))
                                .then(
                                        literal(DIFFICULTY)
                                                .then(
                                                        argument(DIFFICULTY, StringArgumentType.word())
                                                                .suggests(
                                                                        (context, builder) ->
                                                                                SharedSuggestionProvider.suggest(
                                                                                        MatchBots.DIFFICULTIES, builder))
                                                                .executes(LobbyCommand::difficulty))));
    }

    private static int vote(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return MinigamesCommand.run(
                source,
                () -> {
                    Match match =
                            MatchManager.matchOf(player.getUUID())
                                    .orElseThrow(
                                            () -> new MatchException("You are not waiting in a lobby."));
                    match.voteStart(player);
                    return 1;
                });
    }

    /** The lobby named by the command, if the source may change its bots. */
    private static Match lobby(CommandContext<CommandSourceStack> context) throws MatchException {
        int id = IntegerArgumentType.getInteger(context, MATCH);
        Match match =
                MatchManager.get(id)
                        .orElseThrow(() -> new MatchException("There is no match #" + id + "."));
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        boolean waiting = player != null && match.isWaiting(player.getUUID());
        if (!waiting && !Commands.<CommandSourceStack>hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)) {
            throw new MatchException("Only players waiting in match #" + id + " can change its bots.");
        }
        return match;
    }

    private static int add(CommandContext<CommandSourceStack> context, int team)
            throws CommandSyntaxException {
        return MinigamesCommand.run(
                context.getSource(),
                () -> {
                    lobby(context).addBots(team, IntegerArgumentType.getInteger(context, COUNT));
                    return 1;
                });
    }

    /** Reserves every free slot of a fixed layout, which starts the match. */
    private static int fill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return MinigamesCommand.run(
                context.getSource(),
                () -> {
                    Match match = lobby(context);
                    if (match.layout().isFreeForAll()) {
                        throw new MatchException(
                                "Choose how many bots join a free-for-all: /minigames bots %d add <count>."
                                        .formatted(match.id()));
                    }
                    int free = match.freeSlots();
                    if (free == 0) {
                        throw new MatchException("Match #" + match.id() + " has no free slots.");
                    }
                    match.addBots(0, free);
                    return free;
                });
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return MinigamesCommand.run(
                context.getSource(),
                () -> {
                    lobby(context).clearBots();
                    return 1;
                });
    }

    private static int difficulty(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return MinigamesCommand.run(
                context.getSource(),
                () -> {
                    Match match = lobby(context);
                    match.setBotDifficulty(StringArgumentType.getString(context, DIFFICULTY));
                    context.getSource()
                            .sendSuccess(
                                    () ->
                                            Component.literal(
                                                    "Bots in match #%d play on %s."
                                                            .formatted(match.id(), match.botDifficulty())),
                                    false);
                    return 1;
                });
    }
}
