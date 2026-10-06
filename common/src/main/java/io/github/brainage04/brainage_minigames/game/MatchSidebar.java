package io.github.brainage04.brainage_minigames.game;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
 * A sidebar for each member of one match, sent only to that member, and every participant's health
 * as a number in that member's tab list. Both objectives exist on their client alone, so the server
 * scoreboard, and what everyone else sees, is never touched. Who is playing is the tab list's job;
 * the sidebar only lists teams, and players only where they have a score.
 */
public final class MatchSidebar {
    /**
     * Name of the client-only objective. The colon cannot appear in a {@code /scoreboard} objective
     * name, so it never collides with one of the server's.
     */
    public static final String OBJECTIVE_NAME = "brainage_minigames:sidebar";

    /** Name of the client-only tab list objective holding each participant's health. */
    public static final String HEALTH_OBJECTIVE_NAME = "brainage_minigames:health";

    /** The sidebar is rebuilt this often; only changed lines are sent. */
    public static final int REFRESH_TICKS = 10;

    /** The client shows at most 15 sidebar lines. */
    public static final int MAX_LINES = 15;

    private static final Optional<NumberFormat> BLANK = Optional.of(BlankFormat.INSTANCE);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("MM/dd/yy HH:mm:ss", java.util.Locale.ROOT);
    private static long footerSecond = Long.MIN_VALUE;
    private static Component footer = Component.empty();

    /** Objectives need a scoreboard; this one is never populated or sent anywhere. */
    private static final Scoreboard DETACHED = new Scoreboard();

    private final Map<UUID, Shown> shown = new HashMap<>();

    private static final class Shown {
        private Component title;
        private final List<Component> lines = new ArrayList<>();

        /** Health last sent for each participant, by scoreboard name. */
        private final Map<String, Integer> health = new HashMap<>();

        private Shown(Component title) {
            this.title = title;
        }
    }

    /** Shows or updates the player's sidebar, sending only what changed since the last call. */
    void show(ServerPlayer player, Match match) {
        Component title =
                Component.literal(match.game().displayName() + " " + match.layout().displayName())
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
            Objective health = healthObjective();
            player.connection.send(
                    new ClientboundSetObjectivePacket(
                            health, ClientboundSetObjectivePacket.METHOD_ADD));
            player.connection.send(
                    new ClientboundSetDisplayObjectivePacket(DisplaySlot.LIST, health));
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
        showHealth(player, match, current);
    }

    /**
     * Sends each alive participant's actual health, rounded up to a whole point (normally 20 is
     * full; double-health UHC is 40), and clears it for those no longer alive.
     */
    private static void showHealth(ServerPlayer viewer, Match match, Shown current) {
        Map<String, Integer> health = new HashMap<>();
        for (MatchTeam team : match.teams()) {
            for (UUID playerId : team.members()) {
                ServerPlayer player = match.server().getPlayerList().getPlayer(playerId);
                if (player != null && match.isAlive(playerId)) {
                    health.put(player.getScoreboardName(), (int) Math.ceil(player.getHealth()));
                }
            }
        }
        for (Map.Entry<String, Integer> entry : health.entrySet()) {
            if (!entry.getValue().equals(current.health.put(entry.getKey(), entry.getValue()))) {
                viewer.connection.send(
                        new ClientboundSetScorePacket(
                                entry.getKey(),
                                HEALTH_OBJECTIVE_NAME,
                                entry.getValue(),
                                Optional.empty(),
                                Optional.empty()));
            }
        }
        current.health
                .keySet()
                .removeIf(
                        name -> {
                            if (health.containsKey(name)) {
                                return false;
                            }
                            viewer.connection.send(
                                    new ClientboundResetScorePacket(name, HEALTH_OBJECTIVE_NAME));
                            return true;
                        });
    }

    /**
     * Removes the player's sidebar and tab list health and puts the server's own objectives, if any
     * are displayed in those slots, back in their place.
     */
    void hide(ServerPlayer player) {
        Shown current = shown.remove(player.getUUID());
        if (current == null) {
            return;
        }
        player.connection.send(
                new ClientboundSetObjectivePacket(
                        objective(current.title), ClientboundSetObjectivePacket.METHOD_REMOVE));
        player.connection.send(
                new ClientboundSetObjectivePacket(
                        healthObjective(), ClientboundSetObjectivePacket.METHOD_REMOVE));
        // Put the server's own objectives back in the slots ours took over.
        for (DisplaySlot slot : List.of(DisplaySlot.SIDEBAR, DisplaySlot.LIST)) {
            Objective serverObjective =
                    player.level().getServer().getScoreboard().getDisplayObjective(slot);
            if (serverObjective != null) {
                player.connection.send(
                        new ClientboundSetDisplayObjectivePacket(slot, serverObjective));
            }
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

    private static Objective healthObjective() {
        return new Objective(
                DETACHED,
                HEALTH_OBJECTIVE_NAME,
                ObjectiveCriteria.DUMMY,
                Component.literal("Health"),
                ObjectiveCriteria.RenderType.INTEGER,
                false,
                null);
    }

    private static String holder(int index) {
        return "brainage_minigames:line_" + index;
    }

    /** Every line of {@code viewer}'s sidebar for {@code match}, top to bottom. */
    static List<Component> lines(ServerPlayer viewer, Match match) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Match #" + match.id()).withStyle(ChatFormatting.GRAY));
        match.mapName().ifPresent(name -> lines.add(label("Map: ", name)));
        lines.add(phaseLine(match));
        lines.add(teamLine(viewer, match));
        lines.addAll(teammateLines(viewer, match));
        if (match.phase() == MatchPhase.LOBBY) {
            if (match.reservedBots() > 0) {
                lines.add(label("Bots: ", String.valueOf(match.reservedBots())));
            }
            match.autoStartSecondsLeft()
                    .ifPresent(seconds -> lines.add(label("Starts in: ", clock(seconds * 20L))));
            if (match.startVotes() > 0) {
                lines.add(label("Votes to start: ", match.startVotes() + "/" + match.votesNeeded()));
            }
        }

        List<Component> gameLines = new ArrayList<>();
        match.game().addSidebarLines(match, gameLines);

        List<Component> standings =
                match.phase() == MatchPhase.LOBBY ? List.of() : standingLines(viewer, match);
        int budget =
                MAX_LINES - lines.size() - (gameLines.isEmpty() ? 0 : gameLines.size() + 1) - 2;
        if (standings.size() > budget && budget > 0) {
            int hidden = standings.size() - budget + 1;
            standings = new ArrayList<>(standings.subList(0, budget - 1));
            standings.add(Component.literal("+" + hidden + " more").withStyle(ChatFormatting.GRAY));
        }
        if (!standings.isEmpty() && budget > 0) {
            lines.add(Component.empty());
            lines.addAll(standings);
        }
        if (!gameLines.isEmpty()) {
            lines.add(Component.empty());
            lines.addAll(gameLines);
        }
        lines.add(dateTimeLine());
        return lines;
    }

    private static Component dateTimeLine() {
        return dateTimeLine(System.currentTimeMillis() / 1000);
    }

    private static Component dateTimeLine(long second) {
        if (second != footerSecond) {
            footer = Component.literal(DATE_TIME.format(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochSecond(second), java.time.ZoneId.systemDefault()))).withStyle(ChatFormatting.GRAY);
            footerSecond = second;
        }
        return footer;
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
                    match.preparingSpawns()
                            ? label("Preparing: ", "spawn terrain")
                            : label(
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

    /** Teammates shown under the viewer's team; more are summed up in one line. */
    private static final int MAX_TEAMMATE_LINES = 3;

    /**
     * The viewer's teammates once teams exist, each with their health while alive, or struck
     * through once eliminated.
     */
    private static List<Component> teammateLines(ServerPlayer viewer, Match match) {
        Optional<MatchTeam> team = match.teamOf(viewer.getUUID());
        if (team.isEmpty() || team.get().members().size() < 2) return List.of();
        List<UUID> mates = new ArrayList<>(team.get().members());
        mates.remove(viewer.getUUID());
        List<Component> lines = new ArrayList<>();
        int shown = mates.size() > MAX_TEAMMATE_LINES ? MAX_TEAMMATE_LINES - 1 : mates.size();
        for (UUID mate : mates.subList(0, shown)) {
            MutableComponent line = Component.literal(" ").append(name(viewer, match, mate));
            ServerPlayer player = match.server().getPlayerList().getPlayer(mate);
            if (!match.isAlive(mate)) {
                line.withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH);
            } else if (player == null) {
                line.withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(" offline").withStyle(ChatFormatting.GRAY));
            } else {
                line.withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(" " + (int) Math.ceil(player.getHealth()) + "\u2764")
                                .withStyle(ChatFormatting.RED));
            }
            lines.add(line);
        }
        if (shown < mates.size()) {
            lines.add(Component.literal(" +" + (mates.size() - shown) + " more").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /**
     * One line per team with its score and how many of it are alive; in a free-for-all, where every
     * player is a team, only the players with a score (kills, points, laps), so the sidebar reads
     * as standings rather than a player list.
     */
    private static List<Component> standingLines(ServerPlayer viewer, Match match) {
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
                long alive = team.members().stream().filter(match::isAlive).count();
                lines.add(
                        Component.empty()
                                .append(team.displayName())
                                .append(suffix)
                                .append(
                                        Component.literal(
                                                        " ("
                                                                + alive
                                                                + "/"
                                                                + team.members().size()
                                                                + " alive)")
                                                .withStyle(ChatFormatting.GRAY)));
                continue;
            }
            if (suffix.getString().isEmpty()) {
                continue;
            }
            for (UUID playerId : team.members()) {
                MutableComponent name = name(viewer, match, playerId);
                if (!match.isAlive(playerId)) {
                    name.withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH);
                } else {
                    team.color()
                            .ifPresent(color -> name.withStyle(style -> style.withColor(color)));
                }
                lines.add(Component.empty().append(name).append(suffix));
            }
        }
        return lines;
    }

    private static MutableComponent name(ServerPlayer viewer, Match match, UUID playerId) {
        MutableComponent name =
                Component.literal((match.isBot(playerId) ? "[BOT] " : "") + match.nameOf(playerId));
        return playerId.equals(viewer.getUUID()) ? name.withStyle(ChatFormatting.BOLD) : name;
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
