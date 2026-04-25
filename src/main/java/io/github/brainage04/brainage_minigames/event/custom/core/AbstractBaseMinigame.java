package io.github.brainage04.brainage_minigames.event.custom.core;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.event.custom.CustomEventManager;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractBaseMinigame implements IBaseMinigame {
    private final int minTeams;
    private final int maxTeams;
    private final int minPlayersPerTeam;
    private final int maxPlayersPerTeam;
    private final Identifier kitId;

    protected AbstractBaseMinigame(
            int minTeams,
            int maxTeams,
            int minPlayersPerTeam,
            int maxPlayersPerTeam,
            Identifier kitId
    ) {
        if (minTeams < 1 || maxTeams < minTeams || minPlayersPerTeam < 1 || maxPlayersPerTeam < minPlayersPerTeam) {
            throw new IllegalArgumentException("Invalid team constraints");
        }
        this.minTeams = minTeams;
        this.maxTeams = maxTeams;
        this.minPlayersPerTeam = minPlayersPerTeam;
        this.maxPlayersPerTeam = maxPlayersPerTeam;
        this.kitId = Objects.requireNonNull(kitId, "kitId");
    }

    @Override
    public int getMinTeams() {
        return minTeams;
    }

    @Override
    public int getMaxTeams() {
        return maxTeams;
    }

    @Override
    public int getMinPlayersPerTeam() {
        return minPlayersPerTeam;
    }

    @Override
    public int getMaxPlayersPerTeam() {
        return maxPlayersPerTeam;
    }

    @Override
    public Identifier getKitId() {
        return kitId;
    }

    @Override
    public boolean isReadyToStart(ServerLevel level) {
        List<ServerPlayer> players = MinigameState.getParticipatingPlayers(level);
        return findFeasibleTeamCount(players) >= 0;
    }

    @Override
    public String getRequirementsToStart() {
        return "The event needs %d-%d teams with %d-%d players per team in Adventure mode."
                .formatted(minTeams, maxTeams, minPlayersPerTeam, maxPlayersPerTeam);
    }

    @Override
    public void setupEvent(ServerLevel level) {
        List<ServerPlayer> players = new ArrayList<>(MinigameState.getParticipatingPlayers(level));
        int targetTeamCount = findFeasibleTeamCount(players);
        if (targetTeamCount < 0) {
            BrainageMinigames.LOGGER.error(getRequirementsToStart());
            CustomEventManager.stop(level.getServer());
            return;
        }

        ServerScoreboard scoreboard = level.getScoreboard();
        List<PlayerTeam> teams = existingEventTeams(players);
        List<ServerPlayer> unassigned = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (player.getTeam() == null || !player.getTeam().getName().startsWith(MinigameState.EVENT_TEAM_PREFIX)) {
                unassigned.add(player);
            }
        }
        Collections.shuffle(unassigned);

        if (!unassigned.isEmpty()) {
            String message = "Assigning players without event teams automatically.";
            BrainageMinigames.LOGGER.info(message);
            for (ServerPlayer operator : PlayerUtils.getOperators(level.getServer().getPlayerList())) {
                operator.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.YELLOW));
            }
        }

        while (teams.size() < targetTeamCount) {
            teams.add(createEventTeam(scoreboard));
        }

        Map<PlayerTeam, Integer> sizes = teamSizes(players, teams);
        for (PlayerTeam team : teams) {
            while (sizes.get(team) < minPlayersPerTeam && !unassigned.isEmpty()) {
                assign(scoreboard, unassigned.removeLast(), team);
                sizes.put(team, sizes.get(team) + 1);
            }
        }
        while (!unassigned.isEmpty()) {
            PlayerTeam team = teams.stream()
                    .filter(candidate -> sizes.get(candidate) < maxPlayersPerTeam)
                    .min(Comparator.comparingInt(sizes::get))
                    .orElseThrow();
            assign(scoreboard, unassigned.removeLast(), team);
            sizes.put(team, sizes.get(team) + 1);
        }

        MinigameState.aliveEventPlayers = List.copyOf(players);
        MinigameState.aliveEventTeams = MinigameState.getAliveEventTeams(players);
    }

    @Override
    public boolean pvpPhase(ServerLevel level) {
        MinigameState.aliveEventPlayers = MinigameState.getAliveEventPlayers(level);
        MinigameState.aliveEventTeams = MinigameState.getAliveEventTeams(MinigameState.aliveEventPlayers);
        if (MinigameState.aliveEventTeams.size() <= 1) {
            CustomEventManager.nextPhase(level.getServer());
            return true;
        }
        return false;
    }

    private int findFeasibleTeamCount(List<ServerPlayer> players) {
        List<PlayerTeam> existingTeams = existingEventTeams(players);
        Map<PlayerTeam, Integer> sizes = teamSizes(players, existingTeams);
        if (sizes.values().stream().anyMatch(size -> size > maxPlayersPerTeam)) {
            return -1;
        }

        int assigned = sizes.values().stream().mapToInt(Integer::intValue).sum();
        int unassigned = players.size() - assigned;
        for (int teamCount = Math.max(minTeams, existingTeams.size()); teamCount <= maxTeams; teamCount++) {
            int missingTeams = teamCount - existingTeams.size();
            int required = missingTeams * minPlayersPerTeam;
            int capacity = missingTeams * maxPlayersPerTeam;
            for (int size : sizes.values()) {
                required += Math.max(0, minPlayersPerTeam - size);
                capacity += maxPlayersPerTeam - size;
            }
            if (unassigned >= required && unassigned <= capacity) {
                return teamCount;
            }
        }
        return -1;
    }

    private static List<PlayerTeam> existingEventTeams(List<ServerPlayer> players) {
        return players.stream()
                .map(ServerPlayer::getTeam)
                .filter(team -> team != null && team.getName().startsWith(MinigameState.EVENT_TEAM_PREFIX))
                .distinct()
                .toList();
    }

    private static Map<PlayerTeam, Integer> teamSizes(List<ServerPlayer> players, List<PlayerTeam> teams) {
        Map<PlayerTeam, Integer> sizes = new HashMap<>();
        for (PlayerTeam team : teams) {
            sizes.put(team, 0);
        }
        for (ServerPlayer player : players) {
            if (sizes.containsKey(player.getTeam())) {
                sizes.computeIfPresent(player.getTeam(), (team, count) -> count + 1);
            }
        }
        return sizes;
    }

    private static PlayerTeam createEventTeam(ServerScoreboard scoreboard) {
        int index = 1;
        while (scoreboard.getPlayerTeam(MinigameState.EVENT_TEAM_PREFIX + index) != null) {
            index++;
        }
        PlayerTeam team = scoreboard.addPlayerTeam(MinigameState.EVENT_TEAM_PREFIX + index);
        team.setAllowFriendlyFire(false);
        return team;
    }

    private static void assign(ServerScoreboard scoreboard, ServerPlayer player, PlayerTeam team) {
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
    }
}
