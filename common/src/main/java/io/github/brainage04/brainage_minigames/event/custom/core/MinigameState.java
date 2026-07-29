package io.github.brainage04.brainage_minigames.event.custom.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class MinigameState {
    public static final int WAIT_PHASE_TICKS = 1_200;
    public static final int COUNTDOWN_TICKS = 100;
    public static final int POST_WIN_PHASE_TICKS = 1_200;
    public static final String EVENT_TEAM_PREFIX = "event_";

    public static IBaseMinigame event;
    public static MinigamePhase phase = MinigamePhase.WAITING;
    public static int eventTimer;
    public static boolean waitPhaseTicking = true;
    public static boolean postWinPhaseTicking = true;
    public static boolean acceptingParticipants = true;
    public static List<ServerPlayer> aliveEventPlayers = new ArrayList<>();
    public static List<PlayerTeam> aliveEventTeams = new ArrayList<>();
    public static List<BlockPos> arenaCenters = new ArrayList<>();

    private static final Set<UUID> PARTICIPANTS = new HashSet<>();
    private static final List<BoundingBox> ARENAS = new ArrayList<>();

    private MinigameState() {
    }

    public static boolean isParticipant(UUID playerId) {
        return PARTICIPANTS.contains(playerId);
    }

    public static void addParticipant(ServerPlayer player) {
        PARTICIPANTS.add(player.getUUID());
    }

    public static void removeParticipant(ServerPlayer player) {
        PARTICIPANTS.remove(player.getUUID());
    }

    public static Collection<UUID> participantIds() {
        return Set.copyOf(PARTICIPANTS);
    }

    public static List<ServerPlayer> getParticipatingPlayers(ServerLevel level) {
        return level.getPlayers(player -> isParticipant(player.getUUID()) && player.gameMode() == GameType.ADVENTURE);
    }

    public static List<ServerPlayer> getAliveEventPlayers(ServerLevel level) {
        if (event == null) {
            return List.of();
        }
        return level.getPlayers(player -> isParticipant(player.getUUID()) && player.gameMode() == event.getGameMode());
    }

    public static List<ServerPlayer> getDeadEventPlayers(ServerLevel level) {
        if (event == null) {
            return List.of();
        }
        return level.getPlayers(player -> isParticipant(player.getUUID()) && player.gameMode() != event.getGameMode());
    }

    public static List<PlayerTeam> getAliveEventTeams(List<ServerPlayer> alivePlayers) {
        List<PlayerTeam> teams = new ArrayList<>();
        for (ServerPlayer player : alivePlayers) {
            PlayerTeam team = player.getTeam();
            if (team != null && team.getName().startsWith(EVENT_TEAM_PREFIX) && !teams.contains(team)) {
                teams.add(team);
            }
        }
        return teams;
    }

    public static void clearEventTeams(ServerScoreboard scoreboard) {
        for (PlayerTeam team : List.copyOf(scoreboard.getPlayerTeams())) {
            if (team.getName().startsWith(EVENT_TEAM_PREFIX)) {
                scoreboard.removePlayerTeam(team);
            }
        }
    }

    public static void clearItems(ServerLevel level) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity) {
                entity.discard();
            }
        }
    }

    public static void cleanUp(ServerLevel level) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof FireworkRocketEntity || entity instanceof ItemEntity) {
                entity.discard();
            }
        }
        clearArenas(level);
        clearEventTeams(level.getScoreboard());
    }

    public static List<BlockPos> setupTournament(ServerLevel level, int arenaCount) {
        clearArenas(level);
        if (arenaCount <= 0) {
            arenaCenters = List.of();
            return arenaCenters;
        }

        int sideLength = (int) Math.ceil(Math.sqrt(arenaCount));
        List<BlockPos> centers = new ArrayList<>(arenaCount);
        for (int index = 0; index < arenaCount; index++) {
            int xIndex = index / sideLength;
            int zIndex = index % sideLength;
            BlockPos center = new BlockPos(xIndex * 22, 200, zIndex * 22);
            BoundingBox box = new BoundingBox(
                    center.getX() - 10,
                    center.getY(),
                    center.getZ() - 10,
                    center.getX() + 10,
                    center.getY() + 10,
                    center.getZ() + 10
            );
            fillHollow(level, box);
            ARENAS.add(box);
            centers.add(center);
        }
        arenaCenters = List.copyOf(centers);
        return arenaCenters;
    }

    private static void fillHollow(ServerLevel level, BoundingBox box) {
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (x == box.minX() || x == box.maxX()
                            || y == box.minY() || y == box.maxY()
                            || z == box.minZ() || z == box.maxZ()) {
                        level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.GLASS.defaultBlockState());
                    }
                }
            }
        }
    }

    private static void clearArenas(ServerLevel level) {
        for (BoundingBox box : ARENAS) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        if (x == box.minX() || x == box.maxX()
                                || y == box.minY() || y == box.maxY()
                                || z == box.minZ() || z == box.maxZ()) {
                            level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
        }
        ARENAS.clear();
        arenaCenters = new ArrayList<>();
    }

    public static void reset() {
        event = null;
        phase = MinigamePhase.WAITING;
        eventTimer = 0;
        waitPhaseTicking = true;
        postWinPhaseTicking = true;
        acceptingParticipants = true;
        aliveEventPlayers = new ArrayList<>();
        aliveEventTeams = new ArrayList<>();
        arenaCenters = new ArrayList<>();
        PARTICIPANTS.clear();
        ARENAS.clear();
    }

    public static void tick(ServerLevel level) {
        IBaseMinigame activeEvent = event;
        if (activeEvent == null) {
            return;
        }
        switch (phase) {
            case WAITING -> activeEvent.waitingPhase(level);
            case SETUP_EVENT -> activeEvent.setupEvent(level);
            case COUNTDOWN -> activeEvent.countdownPhase(level);
            case PVP -> activeEvent.pvpPhase(level);
            case WIN_EVENT -> activeEvent.winEvent(List.copyOf(aliveEventPlayers), level);
            case POST_WIN -> activeEvent.postWinPhase(level);
        }
        if (event == activeEvent) {
            eventTimer++;
        }
    }
}
