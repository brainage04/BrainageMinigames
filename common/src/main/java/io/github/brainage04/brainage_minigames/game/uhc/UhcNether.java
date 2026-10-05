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
import net.minecraft.world.level.border.WorldBorder;
import org.jspecify.annotations.Nullable;

/**
 * Pairs each UHC-style dimension with its own Nether. Portal exits use the participant's local
 * border; other levels keep vanilla portals. The portal and fire block mixins call in here.
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
        return ModDimensions.natural(level.dimension()) || ModDimensions.nether(level.dimension());
    }

    /**
     * Resolves the paired level. Match participants can enter only their own arena's open Nether;
     * Meetup and FinalUHC portals stay disabled. Travel back from a paired Nether is always allowed.
     */
    public static @Nullable ServerLevel destination(ServerLevel from, Entity entity) {
        MinecraftServer server = from.getServer();
        if (ModDimensions.nether(from.dimension())) {
            return server.getLevel(ModDimensions.paired(from.dimension()));
        }
        if (entity instanceof ServerPlayer player) {
            Optional<Match> match = MatchManager.matchOf(player.getUUID());
            if (match.isPresent()) {
                return match.get().arena() instanceof UhcArena arena && from == arena.level()
                        ? arena.openNether().orElse(null) : null;
            }
        }
        return server.getLevel(ModDimensions.paired(from.dimension()));
    }

    /** The participant's destination border, or the level border for unowned entities. */
    public static WorldBorder destinationBorder(ServerLevel destination, Entity entity) {
        Match match = entity instanceof ServerPlayer player
                ? MatchManager.matchOf(player.getUUID()).orElse(null)
                : io.github.brainage04.brainage_minigames.game.UhcCombatLogger.match(entity);
        if (match != null && match.arena() instanceof UhcArena arena) {
            WorldBorder border = arena.border(destination);
            if (border != null) return border;
        }
        return destination.getWorldBorder();
    }

    /**
     * Called after vanilla moved a spectator along with the player they were watching into another
     * level and stopped them watching, as the entity they watched may no longer exist there. A
     * player does, so between the linked levels the spectator watches them again once the player is
     * on their client: a camera packet for an entity the client does not have yet is ignored.
     */
    public static void followAfterTravel(
            ServerPlayer spectator, ServerPlayer target, ServerLevel from) {
        boolean deathmatch = target.level().dimension() == ModDimensions.MINIGAMES
                && MatchManager.matchOf(target.getUUID())
                        .filter(match -> match.arena() instanceof UhcArena arena && arena.inDeathmatch())
                        .filter(match -> match.involves(spectator.getUUID())).isPresent();
        if (isLinked(from) && (isLinked(target.level()) || deathmatch) && spectator.isSpectator()) {
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
