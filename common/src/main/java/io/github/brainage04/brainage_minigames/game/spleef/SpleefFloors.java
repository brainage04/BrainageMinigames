package io.github.brainage04.brainage_minigames.game.spleef;

import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The {@code region floor_<n>} layers of a Spleef or Bow Spleef map. */
final class SpleefFloors {
    static final String PREFIX = "floor_";

    private SpleefFloors() {}

    static MapArena open(MinecraftServer server, String gameId, TeamLayout layout)
            throws MatchException {
        int teams = layout.isFreeForAll() ? 2 : layout.teamSizes().size();
        return MapArena.openRandom(server, gameId, teams);
    }

    /** Whether {@code pos} lies in one of the map's floor layers. */
    static boolean contains(Match match, BlockPos pos) {
        return match.arena() instanceof MapArena map
                && map.regions(PREFIX).stream().anyMatch(region -> region.contains(pos));
    }

    static int count(Match match) {
        return match.arena() instanceof MapArena map ? map.regions(PREFIX).size() : 0;
    }

    /** Neither game uses hunger, so it is kept full. */
    static void feed(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(5.0F);
        }
    }
}
