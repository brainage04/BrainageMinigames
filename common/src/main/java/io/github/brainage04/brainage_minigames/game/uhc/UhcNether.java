package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Pairs the UHC dimension with the UHC nether: nether portals in either lead to the other, found or
 * built the way vanilla does between the Overworld and the Nether. Every other level keeps vanilla
 * portals. The mixins on the portal and fire blocks call in here.
 */
public final class UhcNether {
    /**
     * Ticks a spectator who followed a player through a portal waits for the player to be on their
     * client before watching them again, however their chunks are going.
     */
    private static final int FOLLOW_TIMEOUT_TICKS = 5 * 20;

    /**
     * Spectators to put back on the player they were watching once that player is on their client.
     */
    private static final Map<UUID, Follow> FOLLOWS = new HashMap<>();

    private record Follow(UUID target, int deadline, boolean ready) {}

    private UhcNether() {}

    /** Whether nether portals in the level are linked by this class rather than by vanilla. */
    public static boolean isLinked(Level level) {
        return level.dimension() == ModDimensions.UHC
                || level.dimension() == ModDimensions.UHC_NETHER;
    }

    /**
     * The level a nether portal in {@code from}, a {@linkplain #isLinked linked} level, sends the
     * entity to, or null when it does nothing. Portals always lead out of the UHC nether. Into it,
     * a match's player may only go while their UHC's nether is open, which rules out every other
     * match in the UHC dimension; anything else may while no UHC has closed it.
     */
    public static @Nullable ServerLevel destination(ServerLevel from, Entity entity) {
        MinecraftServer server = from.getServer();
        if (from.dimension() == ModDimensions.UHC_NETHER) {
            return server.getLevel(ModDimensions.UHC);
        }
        ServerLevel nether = server.getLevel(ModDimensions.UHC_NETHER);
        if (nether == null) {
            return null;
        }
        if (entity instanceof ServerPlayer player) {
            Optional<Match> match = MatchManager.matchOf(player.getUUID());
            if (match.isPresent()) {
                return match.get().arena() instanceof UhcArena arena
                        ? arena.openNether().orElse(null)
                        : null;
            }
        }
        Optional<UhcArena> uhc = UhcArena.active();
        return uhc.isEmpty() ? nether : uhc.get().openNether().orElse(null);
    }

    /**
     * Called after vanilla moved a spectator along with the player they were watching into another
     * level and stopped them watching, as the entity they watched may no longer exist there. A
     * player does, so between the linked levels the spectator watches them again once the player is
     * on their client: a camera packet for an entity the client does not have yet is ignored.
     */
    public static void followAfterTravel(
            ServerPlayer spectator, ServerPlayer target, ServerLevel from) {
        if (isLinked(from) && isLinked(target.level()) && spectator.isSpectator()) {
            FOLLOWS.put(
                    spectator.getUUID(),
                    new Follow(
                            target.getUUID(),
                            target.level().getServer().getTickCount() + FOLLOW_TIMEOUT_TICKS,
                            false));
        }
    }

    public static void tick(MinecraftServer server) {
        if (FOLLOWS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Follow>> iterator = FOLLOWS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Follow> entry = iterator.next();
            ServerPlayer spectator = server.getPlayerList().getPlayer(entry.getKey());
            Follow follow = entry.getValue();
            ServerPlayer target = server.getPlayerList().getPlayer(follow.target());
            if (spectator == null
                    || target == null
                    || !spectator.isSpectator()
                    || spectator.getCamera() != spectator
                    || spectator.level() != target.level()) {
                iterator.remove();
                continue;
            }
            // The chunk being tracked is vanilla's own test for sending its entities; they are
            // sent the tick it becomes true, so the camera follows a tick later.
            if (follow.ready() || server.getTickCount() >= follow.deadline()) {
                spectator.setCamera(target);
                iterator.remove();
            } else if (target.level()
                    .getChunkSource()
                    .chunkMap
                    .isChunkTracked(
                            spectator, target.chunkPosition().x(), target.chunkPosition().z())) {
                entry.setValue(new Follow(follow.target(), follow.deadline(), true));
            }
        }
    }

    public static void clear() {
        FOLLOWS.clear();
    }
}
