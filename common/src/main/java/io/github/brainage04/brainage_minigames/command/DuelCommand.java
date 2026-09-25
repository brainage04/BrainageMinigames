package io.github.brainage04.brainage_minigames.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.brainage04.brainage_minigames.game.DuelRequests;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/** {@code /duel}: any player challenges others to a match; it starts once every invitee accepts. */
public final class DuelCommand {
    private static final String PLAYER = "player";
    private static final String CHALLENGER = "challenger";

    private DuelCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("duel")
                        .then(
                                literal("accept")
                                        .then(challengerArgument().executes(DuelCommand::accept)))
                        .then(
                                literal("deny")
                                        .then(challengerArgument().executes(DuelCommand::deny)))
                        .then(literal("cancel").executes(context -> cancel(context.getSource())))
                        .then(
                                MinigamesCommand.gameArgument()
                                        .then(
                                                MinigamesCommand.layoutArgument()
                                                        .then(playerArgument(1)))));
    }

    /** {@code <player> [<player> ...]}: one required slot followed by optional further slots. */
    private static RequiredArgumentBuilder<CommandSourceStack, ?> playerArgument(int slot) {
        RequiredArgumentBuilder<CommandSourceStack, ?> argument =
                argument(PLAYER + slot, EntityArgument.player())
                        .executes(context -> challenge(context, slot));
        if (slot < DuelRequests.MAX_INVITEES) {
            argument.then(playerArgument(slot + 1));
        }
        return argument;
    }

    /** Suggests the players who have challenged the command source. */
    private static RequiredArgumentBuilder<CommandSourceStack, ?> challengerArgument() {
        return argument(CHALLENGER, EntityArgument.player())
                .suggests(
                        (context, builder) ->
                                SharedSuggestionProvider.suggest(
                                        context.getSource().getPlayer() == null
                                                ? List.of()
                                                : DuelRequests.challengersOf(
                                                        context.getSource().getPlayer().getUUID()),
                                        builder));
    }

    private static int challenge(CommandContext<CommandSourceStack> context, int invitees)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer challenger = source.getPlayerOrException();
        List<ServerPlayer> players = new ArrayList<>(invitees);
        for (int slot = 1; slot <= invitees; slot++) {
            players.add(EntityArgument.getPlayer(context, PLAYER + slot));
        }
        return MinigamesCommand.run(
                source,
                () -> {
                    Minigame game = MinigamesCommand.game(context);
                    TeamLayout layout = MinigamesCommand.layout(context);
                    DuelRequests.challenge(
                            challenger,
                            game,
                            layout,
                            players,
                            (server, settings) -> game.openArena(server, settings, layout));
                    return 1;
                });
    }

    private static int accept(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer invitee = context.getSource().getPlayerOrException();
        ServerPlayer challenger = EntityArgument.getPlayer(context, CHALLENGER);
        return MinigamesCommand.run(
                context.getSource(),
                () -> {
                    DuelRequests.accept(invitee, challenger);
                    return 1;
                });
    }

    private static int deny(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer invitee = context.getSource().getPlayerOrException();
        ServerPlayer challenger = EntityArgument.getPlayer(context, CHALLENGER);
        return MinigamesCommand.run(
                context.getSource(),
                () -> {
                    DuelRequests.deny(invitee, challenger);
                    return 1;
                });
    }

    private static int cancel(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer challenger = source.getPlayerOrException();
        return MinigamesCommand.run(
                source,
                () -> {
                    DuelRequests.cancel(challenger);
                    return 1;
                });
    }
}
