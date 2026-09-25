package io.github.brainage04.brainage_minigames.game.race;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.phys.Vec3;

/**
 * Ice boat racing: every racer drives their own boat around an ice track, through every checkpoint
 * gate in order and over the finish line, for {@link #LAPS} laps; the first to finish the last lap
 * wins. Racers cannot leave their boat: anyone out of it (or whose boat is gone) gets a new one at
 * their last checkpoint. Boats are removed when their racer leaves or the race ends.
 */
public final class IceBoatRacingGame extends RaceGame {
    public static final GameSetting LAPS = new GameSetting("laps", 3, 1, 20, "Laps in a race");

    public IceBoatRacingGame() {
        super("ice_boat_racing", "Ice Boat Racing", BrainageMinigames.id("empty"), List.of(LAPS));
    }

    @Override
    protected int laps(GameSettings settings) {
        return settings.get(LAPS);
    }

    @Override
    protected double fallMargin() {
        return Double.POSITIVE_INFINITY;
    }

    /** The {@code boat_<n>} grid slots, one per racer. */
    private static int boatSlots(MapArena arena) {
        int slots = 0;
        while (arena.point("boat_" + (slots + 1)).isPresent()) {
            slots++;
        }
        return slots;
    }

    @Override
    protected void checkCapacity(MapArena arena, TeamLayout layout) throws MatchException {
        int slots = boatSlots(arena);
        // Free-for-all fills at most one team per spawn.
        int players = layout.isFreeForAll() ? arena.teamSlots() : layout.capacity();
        if (slots < players) {
            throw new MatchException(
                    "Map %s has %d boat slots, too few for %s."
                            .formatted(arena.mapName(), slots, layout));
        }
    }

    @Override
    protected void startRacer(Match match, ServerPlayer player, Racer racer, int slot) {
        Optional<MapArena.Point> grid =
                match.arena() instanceof MapArena arena
                        ? arena.point("boat_" + slot)
                        : Optional.empty();
        if (grid.isPresent()) {
            racer.respawn = grid.get().position();
            racer.yaw = grid.get().yaw();
        }
        board(match, player, racer);
    }

    @Override
    protected boolean enforce(Match match, ServerPlayer player, Racer racer) {
        if (racer.vehicle != null
                && !racer.vehicle.isRemoved()
                && player.getVehicle() == racer.vehicle) {
            return false;
        }
        player.sendSystemMessage(
                Component.literal("You left your boat; here is a new one at your checkpoint.")
                        .withStyle(ChatFormatting.GRAY),
                true);
        board(match, player, racer);
        racer.last = player.getRootVehicle().position();
        return true;
    }

    @Override
    protected void returnToCheckpoint(Match match, ServerPlayer player, Racer racer) {
        board(match, player, racer);
    }

    /** Puts the racer in a new boat at their last checkpoint, removing their old one. */
    private static void board(Match match, ServerPlayer player, Racer racer) {
        player.stopRiding();
        discardBoat(racer);
        ServerLevel level = match.arena().level();
        Vec3 at = racer.respawn;
        Boat boat = EntityTypes.OAK_BOAT.create(level, EntitySpawnReason.EVENT);
        if (boat == null) {
            teleportToCheckpoint(match, player, racer);
            return;
        }
        boat.snapTo(at.x(), at.y(), at.z(), racer.yaw, 0.0F);
        boat.setInvulnerable(true);
        level.addFreshEntity(boat);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        PlayerUtils.teleport(player, level, at, racer.yaw);
        player.startRiding(boat);
        racer.vehicle = boat;
    }

    private static void discardBoat(Racer racer) {
        if (racer.vehicle != null) {
            racer.vehicle.ejectPassengers();
            racer.vehicle.discard();
            racer.vehicle = null;
        }
    }

    @Override
    protected void cleanUp(Racer racer) {
        discardBoat(racer);
    }
}
