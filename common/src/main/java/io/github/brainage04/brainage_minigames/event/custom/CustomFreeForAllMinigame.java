package io.github.brainage04.brainage_minigames.event.custom;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.event.custom.core.AbstractBaseMinigame;
import io.github.brainage04.brainage_minigames.event.custom.core.MinigameState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.List;

public final class CustomFreeForAllMinigame extends AbstractBaseMinigame {
    public CustomFreeForAllMinigame(
            int minTeams,
            int maxTeams,
            int minPlayersPerTeam,
            int maxPlayersPerTeam,
            Identifier kitId
    ) {
        super(
                totalParticipants(minTeams, maxTeams, minPlayersPerTeam, maxPlayersPerTeam, true),
                totalParticipants(minTeams, maxTeams, minPlayersPerTeam, maxPlayersPerTeam, false),
                1,
                1,
                kitId
        );
    }

    private static int totalParticipants(
            int minTeams,
            int maxTeams,
            int minPlayersPerTeam,
            int maxPlayersPerTeam,
            boolean minimum
    ) {
        if (minTeams < 1 || maxTeams < minTeams
                || minPlayersPerTeam < 1 || maxPlayersPerTeam < minPlayersPerTeam) {
            throw new IllegalArgumentException("Invalid team constraints");
        }
        try {
            return Math.multiplyExact(
                    minimum ? minTeams : maxTeams,
                    minimum ? minPlayersPerTeam : maxPlayersPerTeam
            );
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Team constraints exceed the supported player count", exception);
        }
    }

    @Override
    public String getName() {
        return "Custom Free-for-All";
    }


    @Override
    public Identifier getRewardsId() {
        return BrainageMinigames.id("empty");
    }

    @Override
    public boolean isReadyToStart(ServerLevel level) {
        int playerCount = MinigameState.getParticipatingPlayers(level).size();
        return playerCount >= getMinTeams() && playerCount <= getMaxTeams();
    }

    @Override
    public void setupEvent(ServerLevel level) {
        List<ServerPlayer> players = MinigameState.getParticipatingPlayers(level);
        ServerScoreboard scoreboard = level.getScoreboard();
        for (ServerPlayer player : players) {
            PlayerTeam team = player.getTeam();
            if (team != null && team.getName().startsWith(MinigameState.EVENT_TEAM_PREFIX)) {
                scoreboard.removePlayerFromTeam(player.getScoreboardName(), team);
            }
        }
        super.setupEvent(level);
        if (MinigameState.event == this) {
            CustomEventManager.nextPhase(level.getServer());
        }
    }
}
