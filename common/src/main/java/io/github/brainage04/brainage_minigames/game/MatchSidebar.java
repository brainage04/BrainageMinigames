package io.github.brainage04.brainage_minigames.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * A sidebar for each member of one match, sent only to that member. The objective exists on their
 * client alone, so the server scoreboard, and what everyone else sees, is never touched.
 */
public final class MatchSidebar {
    /**
     * Name of the client-only objective. The colon cannot appear in a {@code /scoreboard} objective
     * name, so it never collides with one of the server's.
     */
    public static final String OBJECTIVE_NAME = "brainage_minigames:sidebar";

    /** The sidebar is rebuilt this often; only changed lines are sent. */
    public static final int REFRESH_TICKS = 10;

    /** The client shows at most 15 sidebar lines. */
    public static final int MAX_LINES = 15;

    private static final Optional<NumberFormat> BLANK = Optional.of(BlankFormat.INSTANCE);

    /** Objectives need a scoreboard; this one is never populated or sent anywhere. */
    private static final Scoreboard DETACHED = new Scoreboard();

    private final Map<UUID, Shown> shown = new HashMap<>();

    private static final class Shown {
        private Component title;
        private final List<Component> lines = new ArrayList<>();

        private Shown(Component title) {
            this.title = title;
        }
    }

    /** Shows or updates the player's sidebar, sending only what changed since the last call. */
    void show(ServerPlayer player, Match match) {
        Component title =
                Component.literal(match.game().displayName() + " " + match.layout())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        List<Component> lines = lines(player, match);

        Shown current = shown.get(player.getUUID());
        if (current == null) {
            current = new Shown(title);
            shown.put(player.getUUID(), current);
            Objective objective = objective(title);
            player.connection.send(
                    new ClientboundSetObjectivePacket(
                            objective, ClientboundSetObjectivePacket.METHOD_ADD));
            player.connection.send(
                    new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
        } else if (!current.title.equals(title)) {
            current.title = title;
            player.connection.send(
                    new ClientboundSetObjectivePacket(
                            objective(title), ClientboundSetObjectivePacket.METHOD_CHANGE));
        }

        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            if (index < current.lines.size()) {
                if (current.lines.get(index).equals(line)) {
                    continue;
                }
                current.lines.set(index, line);
            } else {
                current.lines.add(line);
            }
            // Every line has its own holder, so identical lines (such as blank spacers) stay
            // separate, and a fixed score per position keeps the order.
            player.connection.send(
                    new ClientboundSetScorePacket(
                            holder(index),
                            OBJECTIVE_NAME,
                            MAX_LINES - index,
                            Optional.of(line),
                            BLANK));
        }
        for (int index = current.lines.size() - 1; index >= lines.size(); index--) {
            current.lines.remove(index);
            player.connection.send(new ClientboundResetScorePacket(holder(index), OBJECTIVE_NAME));
        }
    }

    /**
     * Removes the player's sidebar and puts the server's own sidebar objective, if one is
     * displayed, back in its place.
     */
    void hide(ServerPlayer player) {
        Shown current = shown.remove(player.getUUID());
        if (current == null) {
            return;
        }
        player.connection.send(
                new ClientboundSetObjectivePacket(
                        objective(current.title), ClientboundSetObjectivePacket.METHOD_REMOVE));
        Objective serverSidebar =
                player.level().getServer().getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (serverSidebar != null) {
            player.connection.send(
                    new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, serverSidebar));
        }
    }

    /** Drops the state of a player who disconnected; their next login starts a fresh client. */
    void forget(UUID playerId) {
        shown.remove(playerId);
    }

    private static Objective objective(Component title) {
        return new Objective(
                DETACHED,
                OBJECTIVE_NAME,
                ObjectiveCriteria.DUMMY,
                title,
                ObjectiveCriteria.RenderType.INTEGER,
                false,
                BlankFormat.INSTANCE);
    }

    private static String holder(int index) {
        return "brainage_minigames:line_" + index;
    }

    private static List<Component> lines(ServerPlayer viewer, Match match) {
        List<Component> head = new ArrayList<>();
        head.add(Component.literal("Match #" + match.id()).withStyle(ChatFormatting.GRAY));
        match.mapName().ifPresent(name -> head.add(label("Map: ", name)));
        head.add(phaseLine(match));
        head.add(teamLine(viewer, match));

        List<Component> gameLines = new ArrayList<>();
        match.game().addSidebarLines(match, gameLines);

        int budget =
                Math.max(
                        1,
                        MAX_LINES
                                - head.size()
                                - 1
                                - (gameLines.isEmpty() ? 0 : gameLines.size() + 1));
        List<Component> players =
                match.phase() == MatchPhase.LOBBY
                        ? lobbyLines(viewer, match)
                        : teamLines(viewer, match);
        if (players.size() > budget) {
            int hidden = players.size() - budget + 1;
            players = new ArrayList<>(players.subList(0, budget - 1));
            players.add(Component.literal("+" + hidden + " more").withStyle(ChatFormatting.GRAY));
        }

        List<Component> lines = new ArrayList<>(head);
        lines.add(Component.empty());
        lines.addAll(players);
        if (!gameLines.isEmpty()) {
            lines.add(Component.empty());
            lines.addAll(gameLines);
        }
        return lines;
    }

    private static Component phaseLine(Match match) {
        return switch (match.phase()) {
            case LOBBY ->
                    label(
                            "Players: ",
                            match.layout().isFreeForAll()
                                    ? String.valueOf(match.lobbySize())
                                    : match.lobbySize() + "/" + match.layout().capacity());
            case COUNTDOWN ->
                    label(
                            "Starting in: ",
                            countdown(
                                    match.settings().get(GameSetting.COUNTDOWN_SECONDS) * 20
                                            - match.phaseTicks()));
            case ACTIVE -> {
                int limit = match.settings().get(GameSetting.TIME_LIMIT_MINUTES) * 60 * 20;
                yield label(
                        "Time: ",
                        clock(match.activeTicks()) + (limit > 0 ? " / " + clock(limit) : ""));
            }
            case ENDED -> {
                List<MatchTeam> winners = match.winners();
                if (winners.size() == 1) {
                    yield Component.literal("Winner: ")
                            .withStyle(ChatFormatting.WHITE)
                            .append(winners.getFirst().displayName());
                }
                yield Component.literal(winners.isEmpty() ? "No winner" : "Draw")
                        .withStyle(ChatFormatting.WHITE);
            }
        };
    }

    private static Component teamLine(ServerPlayer viewer, Match match) {
        Optional<MatchTeam> team = match.teamOf(viewer.getUUID());
        if (team.isPresent()) {
            return Component.literal("Team: ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(team.get().displayName());
        }
        if (match.phase() == MatchPhase.LOBBY && match.isWaiting(viewer.getUUID())) {
            int requested = match.requestedTeam(viewer.getUUID());
            return label("Team: ", requested == 0 ? "random" : "team " + requested);
        }
        return Component.literal("Spectating").withStyle(ChatFormatting.GRAY);
    }

    private static List<Component> lobbyLines(ServerPlayer viewer, Match match) {
        List<Component> lines = new ArrayList<>();
        for (UUID playerId : match.waiting()) {
            lines.add(name(viewer, match, playerId).withStyle(ChatFormatting.WHITE));
        }
        return lines;
    }

    private static List<Component> teamLines(ServerPlayer viewer, Match match) {
        List<Component> lines = new ArrayList<>();
        boolean freeForAll = match.layout().isFreeForAll();
        List<MatchTeam> teams = new ArrayList<>(match.teams());
        // The viewer's own team comes first.
        match.teamOf(viewer.getUUID())
                .ifPresent(
                        own -> {
                            teams.remove(own);
                            teams.addFirst(own);
                        });
        for (MatchTeam team : teams) {
            Component suffix = match.game().sidebarTeamSuffix(match, team);
            if (!freeForAll) {
                lines.add(Component.empty().append(team.displayName()).append(suffix));
            }
            for (UUID playerId : team.members()) {
                MutableComponent line = Component.empty();
                if (!freeForAll) {
                    line.append(" ");
                }
                MutableComponent name = name(viewer, match, playerId);
                ServerPlayer player = match.server().getPlayerList().getPlayer(playerId);
                if (player == null) {
                    line.append(name.withStyle(ChatFormatting.DARK_GRAY))
                            .append(
                                    Component.literal(" offline")
                                            .withStyle(ChatFormatting.DARK_GRAY));
                } else if (!match.isAlive(playerId)) {
                    line.append(name.withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH))
                            .append(Component.literal(" ✖").withStyle(ChatFormatting.GRAY));
                } else {
                    if (freeForAll) {
                        // Free-for-all teams have no header, so the colour goes on the player.
                        team.color()
                                .ifPresent(
                                        color -> name.withStyle(style -> style.withColor(color)));
                    }
                    line.append(name).append(" ").append(hearts(player.getHealth()));
                }
                if (freeForAll) {
                    line.append(suffix);
                }
                lines.add(line);
            }
        }
        return lines;
    }

    private static MutableComponent name(ServerPlayer viewer, Match match, UUID playerId) {
        MutableComponent name = Component.literal(match.nameOf(playerId));
        return playerId.equals(viewer.getUUID()) ? name.withStyle(ChatFormatting.BOLD) : name;
    }

    /** Health in hearts, rounded up to the nearest half heart like the health bar. */
    private static Component hearts(float health) {
        int halfHearts = (int) Math.ceil(health);
        String value =
                halfHearts % 2 == 0 ? String.valueOf(halfHearts / 2) : (halfHearts / 2) + ".5";
        return Component.literal(value + "❤").withStyle(ChatFormatting.RED);
    }

    /** A white label followed by a yellow value. */
    public static Component label(String label, String value) {
        return Component.literal(label)
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(value).withStyle(ChatFormatting.YELLOW));
    }

    /** Elapsed time as minutes and seconds. */
    public static String clock(long ticks) {
        long seconds = Math.max(0, ticks / 20);
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    /** Remaining time as minutes and seconds, rounding up so it only reads 0:00 once over. */
    public static String countdown(long ticks) {
        return clock(ticks + 19);
    }
}
