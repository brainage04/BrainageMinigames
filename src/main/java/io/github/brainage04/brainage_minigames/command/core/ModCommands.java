package io.github.brainage04.brainage_minigames.command.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.brainage04.brainage_minigames.event.custom.CustomEventManager;
import io.github.brainage04.brainage_minigames.event.custom.CustomFreeForAllMinigame;
import io.github.brainage04.brainage_minigames.event.custom.CustomTournamentMinigame;
import io.github.brainage04.brainage_minigames.event.custom.core.IBaseMinigame;
import io.github.brainage04.brainage_minigames.event.custom.core.MinigameState;
import io.github.brainage04.brainage_minigames.event.custom.core.TeamPair;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.TeamArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

import java.util.Collection;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ModCommands {
    private ModCommands() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("startevent")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("eventType", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"custom"}, builder))
                        .then(argument("minTeams", IntegerArgumentType.integer(1))
                                .then(argument("maxTeams", IntegerArgumentType.integer(1))
                                        .then(argument("minPlayersPerTeam", IntegerArgumentType.integer(1))
                                                .then(argument("maxPlayersPerTeam", IntegerArgumentType.integer(1))
                                                        .then(argument("isFreeForAll", BoolArgumentType.bool())
                                                                .executes(context -> startEvent(context, KitStorage.EMPTY_KIT))
                                                                .then(argument("kit", IdentifierArgument.id())
                                                                        .suggests((context, builder) ->
                                                                                SharedSuggestionProvider.suggest(
                                                                                        KitStorage.suggestions(context.getSource().getServer()),
                                                                                        builder
                                                                                ))
                                                                        .executes(context -> startEvent(
                                                                                context,
                                                                                IdentifierArgument.getId(context, "kit")
                                                                        ))))))))));

        dispatcher.register(literal("joinevent").executes(context -> join(context.getSource())));
        dispatcher.register(literal("watchevent").executes(context -> watch(context.getSource())));
        dispatcher.register(literal("leaveevent").executes(context -> leave(context.getSource())));
        dispatcher.register(literal("nextphase")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> result(context.getSource(), CustomEventManager.nextPhase(context.getSource().getServer()),
                        "Starting the next phase.", "The event cannot advance from its current state.")));
        dispatcher.register(literal("stopevent")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> result(context.getSource(), CustomEventManager.stop(context.getSource().getServer()),
                        "Stopped the event.", "There is no event running.")));
        dispatcher.register(literal("stopcountdown")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> result(context.getSource(), CustomEventManager.stopCountdown(),
                        "Stopped the countdown.", "There is no active countdown.")));

        dispatcher.register(literal("teamup")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("players", EntityArgument.players())
                        .executes(context -> teamUp(context.getSource(), EntityArgument.getPlayers(context, "players")))));

        dispatcher.register(literal("matchteams")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("clear").executes(context -> clearMatches(context.getSource())))
                .then(literal("list").executes(context -> listMatches(context.getSource())))
                .then(literal("add")
                        .then(argument("team1", TeamArgument.team())
                                .then(argument("team2", TeamArgument.team())
                                        .executes(context -> addMatch(
                                                context.getSource(),
                                                TeamArgument.getTeam(context, "team1"),
                                                TeamArgument.getTeam(context, "team2")
                                        ))))));

        dispatcher.register(literal("spawn")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> teleportToSpawn(context.getSource())));
    }

    private static int startEvent(CommandContext<CommandSourceStack> context, Identifier kitId) {
        return startEvent(
                context.getSource(),
                StringArgumentType.getString(context, "eventType"),
                IntegerArgumentType.getInteger(context, "minTeams"),
                IntegerArgumentType.getInteger(context, "maxTeams"),
                IntegerArgumentType.getInteger(context, "minPlayersPerTeam"),
                IntegerArgumentType.getInteger(context, "maxPlayersPerTeam"),
                BoolArgumentType.getBool(context, "isFreeForAll"),
                kitId
        );
    }

    private static int startEvent(
            CommandSourceStack source,
            String eventType,
            int minTeams,
            int maxTeams,
            int minPlayersPerTeam,
            int maxPlayersPerTeam,
            boolean freeForAll,
            Identifier kitId
    ) {
        if (!"custom".equals(eventType)) {
            return failure(source, "Unknown event type: " + eventType);
        }
        IBaseMinigame event;
        try {
            event = freeForAll
                    ? new CustomFreeForAllMinigame(minTeams, maxTeams, minPlayersPerTeam, maxPlayersPerTeam, kitId)
                    : new CustomTournamentMinigame(minTeams, maxTeams, minPlayersPerTeam, maxPlayersPerTeam, kitId);
        } catch (IllegalArgumentException exception) {
            return failure(source, exception.getMessage());
        }
        return result(source, CustomEventManager.start(source.getServer(), event),
                "Started " + event.getName() + ".", "An event is already running or the minigames dimension is unavailable.");
    }

    private static int join(CommandSourceStack source) throws CommandSyntaxException {
        return result(source, CustomEventManager.join(source.getPlayerOrException()),
                "Joined the event.", "You cannot join this event now.");
    }

    private static int watch(CommandSourceStack source) throws CommandSyntaxException {
        return result(source, CustomEventManager.watch(source.getPlayerOrException()),
                "Watching the event.", "You cannot watch this event now.");
    }

    private static int leave(CommandSourceStack source) throws CommandSyntaxException {
        return result(source, CustomEventManager.leave(source.getPlayerOrException()),
                "Left the event.", "You are not in the event.");
    }

    private static int teamUp(CommandSourceStack source, Collection<ServerPlayer> players) {
        if (players.isEmpty()) {
            return failure(source, "Select at least one player.");
        }
        if (MinigameState.event == null || !MinigameState.acceptingParticipants) {
            return failure(source, "Teams can only be prepared while an event is waiting to start.");
        }

        int index = 1;
        while (source.getServer().getScoreboard().getPlayerTeam(MinigameState.EVENT_TEAM_PREFIX + index) != null) {
            index++;
        }
        PlayerTeam team = source.getServer().getScoreboard().addPlayerTeam(MinigameState.EVENT_TEAM_PREFIX + index);
        team.setAllowFriendlyFire(false);
        for (ServerPlayer player : players) {
            source.getServer().getScoreboard().addPlayerToTeam(player.getScoreboardName(), team);
        }
        return success(source, "Created %s with %d player(s).".formatted(team.getName(), players.size()), true);
    }

    private static int clearMatches(CommandSourceStack source) {
        if (!(MinigameState.event instanceof CustomTournamentMinigame tournament)) {
            return failure(source, "There is no tournament event running.");
        }
        tournament.clearPairs();
        return success(source, "Cleared configured matches.", true);
    }

    private static int listMatches(CommandSourceStack source) {
        if (!(MinigameState.event instanceof CustomTournamentMinigame tournament)) {
            return failure(source, "There is no tournament event running.");
        }
        if (tournament.teamPairs().isEmpty()) {
            return failure(source, "No matches are configured.");
        }
        for (int index = 0; index < tournament.teamPairs().size(); index++) {
            TeamPair pair = tournament.teamPairs().get(index);
            int matchNumber = index + 1;
            Component second = pair.second == null ? Component.literal("bye") : pair.second.getFormattedDisplayName();
            source.sendSuccess(() -> Component.literal("Match %d: ".formatted(matchNumber))
                    .append(pair.first.getFormattedDisplayName())
                    .append(" vs ")
                    .append(second), false);
        }
        return 1;
    }

    private static int addMatch(CommandSourceStack source, PlayerTeam first, PlayerTeam second) {
        if (!(MinigameState.event instanceof CustomTournamentMinigame tournament)) {
            return failure(source, "There is no tournament event running.");
        }
        if (!tournament.addPair(first, second)) {
            return failure(source, "Teams must be distinct and cannot appear in more than one match.");
        }
        return success(source, "Configured %s vs %s.".formatted(first.getName(), second.getName()), false);
    }

    private static int teleportToSpawn(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) {
            return failure(source, "The Overworld is unavailable.");
        }
        BlockPos spawn = level.getServer().getRespawnData().pos();
        ServerPlayer result = player.teleport(new TeleportTransition(
                level,
                Vec3.atBottomCenterOf(spawn),
                Vec3.ZERO,
                0.0F,
                0.0F,
                TeleportTransition.DO_NOTHING
        ));
        return result(source, result != null, "Teleported to spawn.", "Could not teleport to spawn.");
    }

    private static int result(CommandSourceStack source, boolean success, String successMessage, String failureMessage) {
        return success ? success(source, successMessage, false) : failure(source, failureMessage);
    }

    private static int success(CommandSourceStack source, String message, boolean broadcast) {
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), broadcast);
        return 1;
    }

    private static int failure(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message).withStyle(ChatFormatting.RED));
        return 0;
    }
}
