package io.github.brainage04.brainage_minigames.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchPhase;
import io.github.brainage04.brainage_minigames.game.MatchService;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.Minigames;
import io.github.brainage04.brainage_minigames.game.SettingsStorage;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.uhc.NaturalArena;
import io.github.brainage04.brainage_minigames.game.uhc.UhcArena;
import io.github.brainage04.brainage_minigames.scoreboard.EloRatings;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

public final class MinigamesCommand {
    private static final String MATCH = "match";
    private static final String TEAM = "team";
    static final String GAME = "game";
    static final String LAYOUT = "layout";
    private static final String SETTING = "setting";
    private static final String VALUE = "value";
    static final List<String> LAYOUT_SUGGESTIONS =
            List.of("1v1", "2v2", "3v3", "4v4", "1v1v1v1", "2v2v2v2", "ffa");

    private MinigamesCommand() {}

    private static int elo(CommandSourceStack source, ServerPlayer player) {
        int rating = EloRatings.publish(player);
        source.sendSuccess(() -> Component.literal(player.getScoreboardName() + ": " + rating + " Elo"), false);
        return rating;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("minigames")
                        .executes(context -> list(context.getSource()))
                        .then(literal("list").executes(context -> list(context.getSource())))
                        .then(literal("help").executes(context -> help(context.getSource())))
                        .then(literal("elo")
                                .executes(context -> elo(context.getSource(), context.getSource().getPlayerOrException()))
                                .then(argument("player", EntityArgument.player())
                                        .executes(context -> elo(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                        .then(
                                literal("status")
                                        .then(
                                                matchArgument(match -> true)
                                                        .executes(context -> status(context))))
                        .then(
                                literal("join")
                                        .executes(context -> joinOnlyLobby(context.getSource()))
                                        .then(
                                                matchArgument(Match::isOpenLobby)
                                                        .executes(context -> join(context, 0))
                                                        .then(
                                                                argument(
                                                                                TEAM,
                                                                                IntegerArgumentType
                                                                                        .integer(1))
                                                                        .executes(
                                                                                context ->
                                                                                        join(
                                                                                                context,
                                                                                                IntegerArgumentType
                                                                                                        .getInteger(
                                                                                                                context,
                                                                                                                TEAM))))))
                        .then(
                                literal("watch")
                                        .then(
                                                matchArgument(
                                                                match ->
                                                                        match.phase()
                                                                                != MatchPhase.ENDED)
                                                        .executes(MinigamesCommand::watch)))
                        .then(literal("leave").executes(context -> leave(context.getSource())))
                        .then(
                                literal("open")
                                        .then(
                                                gameArgument()
                                                        .then(
                                                                layoutArgument()
                                                                        .executes(
                                                                                context ->
                                                                                        open(
                                                                                                context,
                                                                                                null))
                                                                        .then(
                                                                                KitCommand
                                                                                        .kitArgument()
                                                                                        .executes(
                                                                                                context ->
                                                                                                        open(
                                                                                                                context,
                                                                                                                IdentifierArgument
                                                                                                                        .getId(
                                                                                                                                context,
                                                                                                                                KitCommand
                                                                                                                                        .KIT_ARGUMENT)))))))
                        .then(
                                literal("start")
                                        .then(
                                                managedMatchArgument(
                                                                match ->
                                                                        match.phase()
                                                                                == MatchPhase.LOBBY)
                                                        .executes(MinigamesCommand::start)))
                        .then(
                                literal("stop")
                                        .then(
                                                managedMatchArgument(match -> true)
                                                        .executes(MinigamesCommand::stop)))
                        .then(
                                literal("settings")
                                        .requires(
                                                Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                        .then(
                                                gameArgument()
                                                        .executes(MinigamesCommand::listSettings)
                                                        .then(
                                                                argument(
                                                                                SETTING,
                                                                                StringArgumentType
                                                                                        .word())
                                                                        .suggests(
                                                                                (context,
                                                                                        builder) ->
                                                                                        SharedSuggestionProvider
                                                                                                .suggest(
                                                                                                        Minigames
                                                                                                                .byId(
                                                                                                                        StringArgumentType
                                                                                                                                .getString(
                                                                                                                                        context,
                                                                                                                                        GAME))
                                                                                                                .map(
                                                                                                                        game ->
                                                                                                                                game
                                                                                                                                        .settings()
                                                                                                                                        .stream()
                                                                                                                                        .map(
                                                                                                                                                GameSetting
                                                                                                                                                        ::key)
                                                                                                                                        .toList())
                                                                                                                .orElse(
                                                                                                                        List
                                                                                                                                .of()),
                                                                                                        builder))
                                                                        .then(
                                                                                literal("reset")
                                                                                        .executes(
                                                                                                context ->
                                                                                                        resetSetting(
                                                                                                                context)))
                                                                        .then(
                                                                                argument(
                                                                                                VALUE,
                                                                                                IntegerArgumentType
                                                                                                        .integer())
                                                                                        .executes(
                                                                                                context ->
                                                                                                        setSetting(
                                                                                                                context))))))
                        .then(UhcCommand.node())
                        .then(KitCommand.node()));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> matchArgument(
            Predicate<Match> suggested) {
        return argument(MATCH, IntegerArgumentType.integer(1))
                .suggests(
                        (context, builder) ->
                                SharedSuggestionProvider.suggest(
                                        MatchManager.matches().stream()
                                                .filter(suggested)
                                                .map(match -> String.valueOf(match.id()))
                                                .toList(),
                                        builder));
    }

    /** A match argument that suggests only the matches the source may start or stop. */
    private static RequiredArgumentBuilder<CommandSourceStack, Integer> managedMatchArgument(
            Predicate<Match> suggested) {
        return argument(MATCH, IntegerArgumentType.integer(1))
                .suggests(
                        (context, builder) ->
                                SharedSuggestionProvider.suggest(
                                        MatchManager.matches().stream()
                                                .filter(suggested)
                                                .filter(match -> manages(context.getSource(), match))
                                                .map(match -> String.valueOf(match.id()))
                                                .toList(),
                                        builder));
    }

    private static boolean manages(CommandSourceStack source, Match match) {
        ServerPlayer player = source.getPlayer();
        return player == null
                ? Commands.LEVEL_GAMEMASTERS.check(source.permissions())
                : MatchService.canManage(player, match);
    }

    /**
     * The player running a match-managing command, or null for the console, command blocks and
     * other sources with game-master permission, which act for the server; other sources are
     * refused.
     */
    private static @Nullable ServerPlayer actor(CommandSourceStack source) throws MatchException {
        ServerPlayer player = source.getPlayer();
        if (player == null && !Commands.LEVEL_GAMEMASTERS.check(source.permissions())) {
            throw new MatchException("Only players and operators can open, start or stop matches.");
        }
        return player;
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> gameArgument() {
        return argument(GAME, StringArgumentType.word())
                .suggests(
                        (context, builder) ->
                                SharedSuggestionProvider.suggest(
                                        Minigames.ALL.stream().map(Minigame::id).toList(),
                                        builder));
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> layoutArgument() {
        return argument(LAYOUT, StringArgumentType.word())
                .suggests(
                        (context, builder) ->
                                SharedSuggestionProvider.suggest(LAYOUT_SUGGESTIONS, builder));
    }

    @FunctionalInterface
    interface Action {
        int run() throws MatchException, CommandSyntaxException;
    }

    /**
     * Runs an action, reporting a refused request to the command source instead of failing the
     * command tree.
     */
    static int run(CommandSourceStack source, Action action) throws CommandSyntaxException {
        try {
            return action.run();
        } catch (MatchException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static Match match(CommandContext<CommandSourceStack> context) throws MatchException {
        int id = IntegerArgumentType.getInteger(context, MATCH);
        return MatchManager.get(id)
                .orElseThrow(() -> new MatchException("There is no match #" + id + "."));
    }

    static Minigame game(CommandContext<CommandSourceStack> context) throws MatchException {
        String id = StringArgumentType.getString(context, GAME);
        return Minigames.byId(id).orElseThrow(() -> new MatchException("Unknown game " + id + "."));
    }

    static TeamLayout layout(CommandContext<CommandSourceStack> context) throws MatchException {
        String text = StringArgumentType.getString(context, LAYOUT);
        return TeamLayout.parse(text)
                .orElseThrow(
                        () ->
                                new MatchException(
                                        "Invalid team layout "
                                                + text
                                                + "; use any number of teams, each of any size, e.g. 1v2 or 2v3v4 (2–100 teams, 1–100 players each), or ffa."));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Layouts: ffa, or any number of teams, each of any size, e.g. 1v2 or 2v3v4. "
                        + "Use v between sizes; 2–100 teams, 1–100 players each. Maps may have fewer slots."), false);
        source.sendSuccess(() -> Component.literal(
                "/minigames list | join <match> [team] | watch <match> | leave | status <match>"), false);
        source.sendSuccess(() -> Component.literal(
                "/minigames open <game> <layout> [kit] opens a match you own; start <match> and stop <match> work on your own matches. "
                        + "Private matches: /duel <game> <layout> <player> [player ...]."), false);
        source.sendSuccess(() -> Component.literal(
                "Operators: start or stop any match, and /minigames settings <game>."), false);
        return 1;
    }

    private static GameSetting setting(CommandContext<CommandSourceStack> context, Minigame game)
            throws MatchException {
        String key = StringArgumentType.getString(context, SETTING);
        return game.setting(key)
                .orElseThrow(
                        () ->
                                new MatchException(
                                        game.displayName() + " has no setting " + key + "."));
    }

    private static int list(CommandSourceStack source) {
        source.sendSuccess(
                () ->
                        Component.literal(
                                        "Games: "
                                                + String.join(
                                                        ", ",
                                                        Minigames.ALL.stream()
                                                                .map(Minigame::id)
                                                                .toList()))
                                .withStyle(ChatFormatting.YELLOW),
                false);
        if (MatchManager.matches().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No matches are open."), false);
        }
        for (Match match : MatchManager.matches()) {
            Component line =
                    match.isOpenLobby()
                            ? Component.empty()
                                    .append(match.describe())
                                    .append(" ")
                                    .append(
                                            Component.literal("[Join]")
                                                    .withStyle(
                                                            style ->
                                                                    style.withColor(
                                                                                    ChatFormatting
                                                                                            .GREEN)
                                                                            .withClickEvent(
                                                                                    new ClickEvent
                                                                                            .RunCommand(
                                                                                            "/minigames join "
                                                                                                    + match
                                                                                                            .id()))))
                            : match.describe();
            source.sendSuccess(() -> line, false);
        }
        return MatchManager.matches().size();
    }

    private static int status(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        return run(
                source,
                () -> {
                    Component line = match(context).describe();
                    source.sendSuccess(() -> line, false);
                    return 1;
                });
    }

    private static int joinOnlyLobby(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return run(
                source,
                () -> {
                    List<Match> lobbies =
                            MatchManager.matches().stream().filter(Match::isOpenLobby).toList();
                    if (lobbies.size() != 1) {
                        throw new MatchException(
                                lobbies.isEmpty()
                                        ? "No match is accepting players."
                                        : "Several matches are open; choose one with /minigames join <match>.");
                    }
                    MatchManager.join(player, lobbies.getFirst(), 0);
                    return 1;
                });
    }

    private static int join(CommandContext<CommandSourceStack> context, int team)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return run(
                context.getSource(),
                () -> {
                    MatchManager.join(player, match(context), team);
                    return 1;
                });
    }

    private static int watch(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return run(
                context.getSource(),
                () -> {
                    MatchManager.watch(player, match(context));
                    return 1;
                });
    }

    private static int leave(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return run(
                source,
                () -> {
                    Match match = MatchManager.leave(player);
                    source.sendSuccess(
                            () -> Component.literal("You left ").append(match.title()).append("."),
                            false);
                    return 1;
                });
    }

    private static int open(CommandContext<CommandSourceStack> context, Identifier kit)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        return run(
                source,
                () -> {
                    ServerPlayer actor = actor(source);
                    Match match =
                            actor == null
                                    ? MatchManager.open(source.getServer(), game(context), layout(context), kit)
                                    : MatchService.open(actor, game(context), layout(context), kit);
                    var opened = Component.literal("Opened ").append(match.title());
                    if (match.arena() instanceof UhcArena arena) {
                        opened.append(" (region centre: %.0f, %.0f)"
                                .formatted(arena.border().getCenterX(), arena.border().getCenterZ()));
                    } else if (match.arena() instanceof NaturalArena arena) {
                        opened.append(" (region centre: %d, %d)".formatted(arena.centerX(), arena.centerZ()));
                    }
                    source.sendSuccess(() -> opened, true);
                    return 1;
                });
    }

    private static int start(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return run(
                context.getSource(),
                () -> {
                    Match match = match(context);
                    ServerPlayer actor = actor(context.getSource());
                    if (actor == null) {
                        match.start();
                    } else {
                        MatchService.start(actor, match);
                    }
                    context.getSource()
                            .sendSuccess(
                                    () -> Component.literal("Started ").append(match.title()),
                                    true);
                    return 1;
                });
    }

    private static int stop(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return run(
                context.getSource(),
                () -> {
                    Match match = match(context);
                    ServerPlayer actor = actor(context.getSource());
                    if (actor == null) {
                        MatchManager.stop(match);
                    } else {
                        MatchService.stop(actor, match);
                    }
                    context.getSource()
                            .sendSuccess(
                                    () -> Component.literal("Stopped ").append(match.title()),
                                    true);
                    return 1;
                });
    }

    private static int listSettings(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        return run(
                source,
                () -> {
                    Minigame game = game(context);
                    GameSettings values = SettingsStorage.resolve(source.getServer(), game);
                    source.sendSuccess(
                            () ->
                                    Component.literal(game.displayName() + " settings:")
                                            .withStyle(ChatFormatting.YELLOW),
                            false);
                    for (GameSetting setting : game.settings()) {
                        int value = values.get(setting);
                        source.sendSuccess(
                                () ->
                                        Component.literal("%s = %d".formatted(setting.key(), value))
                                                .append(Component.literal(values.isOverridden(setting) ? "" : " (unset)")
                                                        .withStyle(ChatFormatting.GRAY))
                                                .append(
                                                        Component.literal(
                                                                        value
                                                                                        == setting
                                                                                                .defaultValue()
                                                                                ? ""
                                                                                : " (default %d)"
                                                                                        .formatted(
                                                                                                setting
                                                                                                        .defaultValue()))
                                                                .withStyle(ChatFormatting.GRAY))
                                                .append(
                                                        Component.literal(
                                                                        " - "
                                                                                + setting
                                                                                        .description())
                                                                .withStyle(ChatFormatting.GRAY)),
                                false);
                    }
                    return game.settings().size();
                });
    }

    private static int setSetting(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        return run(
                source,
                () -> {
                    Minigame game = game(context);
                    GameSetting setting = setting(context, game);
                    int value = IntegerArgumentType.getInteger(context, VALUE);
                    if (value < setting.min() || value > setting.max()) {
                        throw new MatchException(
                                "%s must be between %d and %d."
                                        .formatted(setting.key(), setting.min(), setting.max()));
                    }
                    SettingsStorage.set(source.getServer(), game, setting, value);
                    source.sendSuccess(
                            () ->
                                    Component.literal(
                                            "Set %s %s to %d for new matches."
                                                    .formatted(game.id(), setting.key(), value)),
                            true);
                    game.validate(SettingsStorage.resolve(source.getServer(), game))
                            .ifPresent(
                                    problem ->
                                            source.sendFailure(
                                                    Component.literal(
                                                            "New %s matches cannot open until this is fixed: %s"
                                                                    .formatted(
                                                                            game.id(), problem))));
                    return 1;
                });
    }

    private static int resetSetting(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        return run(
                source,
                () -> {
                    Minigame game = game(context);
                    GameSetting setting = setting(context, game);
                    SettingsStorage.reset(source.getServer(), game, setting);
                    source.sendSuccess(
                            () ->
                                    Component.literal(
                                            "Reset %s %s to %d."
                                                    .formatted(
                                                            game.id(),
                                                            setting.key(),
                                                            setting.defaultValue())),
                            true);
                    return 1;
                });
    }
}
