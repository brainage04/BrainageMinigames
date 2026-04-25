package io.github.brainage04.brainage_minigames.event.custom;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.event.custom.core.AbstractBaseMinigame;
import io.github.brainage04.brainage_minigames.event.custom.core.MinigamePhase;
import io.github.brainage04.brainage_minigames.event.custom.core.MinigameState;
import io.github.brainage04.brainage_minigames.event.custom.core.TeamPair;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CustomTournamentMinigame extends AbstractBaseMinigame {
    private final List<TeamPair> teamPairs = new ArrayList<>();
    private boolean initialBracket = true;

    public CustomTournamentMinigame(
            int minTeams,
            int maxTeams,
            int minPlayersPerTeam,
            int maxPlayersPerTeam,
            Identifier kitId
    ) {
        super(minTeams, maxTeams, minPlayersPerTeam, maxPlayersPerTeam, kitId);
    }

    @Override
    public String getName() {
        return "Custom Tournament";
    }

    @Override
    public GameType getGameMode() {
        return GameType.ADVENTURE;
    }


    @Override
    public Identifier getRewardsId() {
        return BrainageMinigames.id("empty");
    }

    @Override
    public boolean isReadyToStart(ServerLevel level) {
        if (initialBracket) {
            return super.isReadyToStart(level);
        }
        return MinigameState.getAliveEventTeams(MinigameState.getAliveEventPlayers(level)).size() > 1;
    }

    @Override
    public void setupEvent(ServerLevel level) {
        if (initialBracket) {
            super.setupEvent(level);
            if (MinigameState.event != this) {
                return;
            }
            initialBracket = false;
        } else {
            MinigameState.aliveEventPlayers = MinigameState.getAliveEventPlayers(level);
            MinigameState.aliveEventTeams = MinigameState.getAliveEventTeams(MinigameState.aliveEventPlayers);
        }

        discardInvalidPairs(MinigameState.aliveEventTeams);
        List<PlayerTeam> unpaired = new ArrayList<>(MinigameState.aliveEventTeams.stream()
                .filter(team -> !isPaired(team))
                .toList());
        Collections.shuffle(unpaired);
        if (!unpaired.isEmpty()) {
            String message = "Creating matches automatically for unpaired teams.";
            BrainageMinigames.LOGGER.info(message);
            for (ServerPlayer operator : PlayerUtils.getOperators(level.getServer().getPlayerList())) {
                operator.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.YELLOW));
            }
            for (int index = 0; index < unpaired.size(); index += 2) {
                PlayerTeam first = unpaired.get(index);
                PlayerTeam second = index + 1 < unpaired.size() ? unpaired.get(index + 1) : null;
                teamPairs.add(new TeamPair(first, second));
            }
        }

        List<BlockPos> centers = MinigameState.setupTournament(level, teamPairs.size());
        for (int index = 0; index < teamPairs.size(); index++) {
            TeamPair pair = teamPairs.get(index);
            Vec3 center = Vec3.atBottomCenterOf(centers.get(index));
            teleportTeamToArena(level, pair.first, center, false);
            if (pair.second == null) {
                for (ServerPlayer player : alivePlayersOnTeam(level, pair.first)) {
                    player.sendSystemMessage(Component.literal(
                            "Your team has a bye because there is no opposing team in this bracket."));
                }
            } else {
                teleportTeamToArena(level, pair.second, center, true);
            }
        }
        CustomEventManager.nextPhase(level.getServer());
    }

    @Override
    public boolean pvpPhase(ServerLevel level) {
        if (super.pvpPhase(level)) {
            return true;
        }

        int matchesComplete = 0;
        for (int index = 0; index < teamPairs.size(); index++) {
            TeamPair pair = teamPairs.get(index);
            int aliveTeams = (alivePlayersOnTeam(level, pair.first).isEmpty() ? 0 : 1)
                    + (pair.second == null || alivePlayersOnTeam(level, pair.second).isEmpty() ? 0 : 1);

            if (aliveTeams == 0) {
                if (pair.second == null) {
                    continue;
                }
                restartTiedMatch(level, pair, index);
            } else if (aliveTeams == 1) {
                notifyOutcome(level, pair);
                matchesComplete++;
            }
        }

        if (!teamPairs.isEmpty() && matchesComplete == teamPairs.size()) {
            for (ServerPlayer operator : PlayerUtils.getOperators(level.getServer().getPlayerList())) {
                operator.sendSystemMessage(Component.literal(
                        "All matches are finished. The next bracket will be set up in 60 seconds. "
                                + "Run /stopcountdown to continue manually."));
            }
            teamPairs.clear();
            MinigameState.waitPhaseTicking = true;
            MinigameState.phase = MinigamePhase.WAITING;
            MinigameState.eventTimer = 0;
            MinigameState.clearItems(level);
            MinigameState.aliveEventPlayers = MinigameState.getAliveEventPlayers(level);
            for (ServerPlayer player : MinigameState.aliveEventPlayers) {
                player.getInventory().clearContent();
                player.removeAllEffects();
                PlayerUtils.makeInvulnerable(player);
            }
            return true;
        }
        return false;
    }

    public List<TeamPair> teamPairs() {
        return List.copyOf(teamPairs);
    }

    public void clearPairs() {
        teamPairs.clear();
    }

    public boolean addPair(PlayerTeam first, PlayerTeam second) {
        if (first == null || second == null || first == second || isPaired(first) || isPaired(second)) {
            return false;
        }
        teamPairs.add(new TeamPair(first, second));
        return true;
    }

    private void discardInvalidPairs(List<PlayerTeam> aliveTeams) {
        Set<PlayerTeam> alive = new HashSet<>(aliveTeams);
        Set<PlayerTeam> seen = new HashSet<>();
        teamPairs.removeIf(pair -> !alive.contains(pair.first)
                || pair.second == null
                || !alive.contains(pair.second)
                || !seen.add(pair.first)
                || !seen.add(pair.second));
    }

    private boolean isPaired(PlayerTeam team) {
        return teamPairs.stream().anyMatch(pair -> pair.hasTeam(team));
    }

    private void restartTiedMatch(ServerLevel level, TeamPair pair, int arenaIndex) {
        for (PlayerTeam team : List.of(pair.first, pair.second)) {
            for (ServerPlayer player : playersOnTeam(level, team)) {
                player.sendSystemMessage(Component.literal("The match was a tie. Restarting...")
                        .withStyle(ChatFormatting.YELLOW));
                player.setGameMode(getGameMode());
                player.removeAllEffects();
                giveKit(level, player);
            }
        }
        Vec3 center = Vec3.atBottomCenterOf(MinigameState.arenaCenters.get(arenaIndex));
        teleportTeamToArena(level, pair.first, center, false);
        teleportTeamToArena(level, pair.second, center, true);
    }

    private void notifyOutcome(ServerLevel level, TeamPair pair) {
        if (pair.notifiedOfMatchOutcome) {
            return;
        }
        PlayerTeam winner = alivePlayersOnTeam(level, pair.first).isEmpty() ? pair.second : pair.first;
        PlayerTeam loser = winner == pair.first ? pair.second : pair.first;
        if (loser != null) {
            for (ServerPlayer player : alivePlayersOnTeam(level, winner)) {
                player.sendSystemMessage(Component.literal("You won your match against ")
                        .append(loser.getFormattedDisplayName())
                        .append("!")
                        .withStyle(ChatFormatting.GREEN));
                PlayerUtils.makeInvulnerable(player);
            }
            for (ServerPlayer player : playersOnTeam(level, loser)) {
                player.sendSystemMessage(Component.literal("You lost your match against ")
                        .append(winner.getFormattedDisplayName())
                        .append("!")
                        .withStyle(ChatFormatting.RED));
            }
        }
        pair.notifiedOfMatchOutcome = true;
    }

    private List<ServerPlayer> alivePlayersOnTeam(ServerLevel level, PlayerTeam team) {
        if (team == null) {
            return List.of();
        }
        return level.getPlayers(player -> MinigameState.isParticipant(player.getUUID())
                && player.getTeam() == team
                && player.gameMode() == getGameMode());
    }

    private static List<ServerPlayer> playersOnTeam(ServerLevel level, PlayerTeam team) {
        if (team == null) {
            return List.of();
        }
        return level.getPlayers(player -> MinigameState.isParticipant(player.getUUID()) && player.getTeam() == team);
    }

    private void teleportTeamToArena(ServerLevel level, PlayerTeam team, Vec3 center, boolean oppositeSide) {
        Vec3 offset = oppositeSide ? new Vec3(-8.0, 4.0, 0.0) : new Vec3(8.0, 4.0, 0.0);
        float yaw = oppositeSide ? -90.0F : 90.0F;
        for (ServerPlayer player : alivePlayersOnTeam(level, team)) {
            player.teleport(new TeleportTransition(
                    level,
                    center.add(offset),
                    Vec3.ZERO,
                    yaw,
                    0.0F,
                    TeleportTransition.DO_NOTHING
            ));
        }
    }
}
