package io.github.brainage04.brainage_minigames.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.brainage04.brainage_minigames.api.MatchBots;
import io.github.brainage04.brainage_minigames.game.DuelRequests;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /duel}: any player challenges others to a match; it starts once every invitee accepts.
 * {@code /duel <game> <layout> bots <counts> [player ...]} puts bots in some slots: one count per
 * team, e.g. {@code 0,2}, or one count in a free-for-all.
 */
public final class DuelCommand {
    private static final String PLAYER = "player";
    private static final String CHALLENGER = "challenger";
    private static final String BOTS = "bots";

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
                                                        .then(playerArgument(1, false))
                                                        .then(
                                                                literal(BOTS)
                                                                        .requires(source -> MatchBots.available())
                                                                        .then(
                                                                                argument(
                                                                                                BOTS,
                                                                                                StringArgumentType
                                                                                                        .word())
                                                                                        .executes(
                                                                                                context ->
                                                                                                        challenge(
                                                                                                                context,
                                                                                                                0,
                                                                                                                true))
                                                                                        .then(
                                                                                                playerArgument(
                                                                                                        1,
                                                                                                        true)))))));
    }

    /** {@code <player> [<player> ...]}: one required slot followed by optional further slots. */
    private static RequiredArgumentBuilder<CommandSourceStack, ?> playerArgument(
            int slot, boolean bots) {
        RequiredArgumentBuilder<CommandSourceStack, ?> argument =
                argument(PLAYER + slot, EntityArgument.player())
                        .executes(context -> challenge(context, slot, bots));
        if (slot < DuelRequests.MAX_INVITEES) {
            argument.then(playerArgument(slot + 1, bots));
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

    private static int challenge(CommandContext<CommandSourceStack> context, int invitees, boolean bots)
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
                    List<Optional<ServerPlayer>> slots =
                            bots
                                    ? slots(layout, StringArgumentType.getString(context, BOTS), players)
                                    : players.stream().map(Optional::of).toList();
                    DuelRequests.challenge(
                            challenger,
                            game,
                            layout,
                            slots,
                            null,
                            (server, settings) -> game.openArena(server, settings, layout));
                    return 1;
                });
    }

    /**
     * The slots after the challenger's for bot counts per team ({@code 0,2}): each team's players
     * first, in the order listed, then its bots. The challenger takes the first slot of team 1.
     */
    static List<Optional<ServerPlayer>> slots(
            TeamLayout layout, String counts, List<ServerPlayer> invitees) throws MatchException {
        String[] parts = counts.split(",", -1);
        int teams = layout.isFreeForAll() ? 1 : layout.teamSizes().size();
        if (parts.length != teams) {
            throw new MatchException(
                    layout.isFreeForAll()
                            ? "Give one bot count for a free-for-all, e.g. 3."
                            : "Give one bot count per team, separated by commas, e.g. %s."
                                    .formatted("0," + "1,".repeat(teams - 2) + "1"));
        }
        Deque<Optional<ServerPlayer>> humans = new ArrayDeque<>();
        humans.add(Optional.empty()); // the challenger, removed below
        invitees.forEach(player -> humans.add(Optional.of(player)));
        List<Optional<ServerPlayer>> slots = new ArrayList<>();
        for (int team = 0; team < teams; team++) {
            int bots;
            try {
                bots = Integer.parseInt(parts[team].trim());
            } catch (NumberFormatException exception) {
                throw new MatchException("Bot counts are whole numbers, e.g. 0,2.");
            }
            if (layout.isFreeForAll()) {
                if (bots < 0 || bots > TeamLayout.MAX_TEAMS) {
                    throw new MatchException("Add between 0 and %d bots.".formatted(TeamLayout.MAX_TEAMS));
                }
                slots.addAll(humans);
                humans.clear();
                for (int bot = 0; bot < bots; bot++) slots.add(Optional.empty());
                continue;
            }
            int size = layout.teamSizes().get(team);
            if (bots < 0 || bots > size || team == 0 && bots == size) {
                throw new MatchException(
                        team == 0
                                ? "Team 1 is yours; leave a slot on it for you."
                                : "Team %d has %d slots.".formatted(team + 1, size));
            }
            for (int slot = 0; slot < size - bots; slot++) {
                if (humans.isEmpty()) {
                    throw new MatchException(
                            "List a player for every slot that is not a bot; team %d needs %d."
                                    .formatted(team + 1, size - bots));
                }
                slots.add(humans.poll());
            }
            for (int bot = 0; bot < bots; bot++) slots.add(Optional.empty());
        }
        if (!humans.isEmpty()) {
            throw new MatchException("You listed more players than there are slots without bots.");
        }
        slots.removeFirst();
        return slots;
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
