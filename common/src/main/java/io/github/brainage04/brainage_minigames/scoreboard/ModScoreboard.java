package io.github.brainage04.brainage_minigames.scoreboard;

import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public final class ModScoreboard {
    public static final String GAMES_WON_OBJECTIVE = "brainage_games_won";

    private ModScoreboard() {}

    public static Objective registerGamesWon(ServerScoreboard scoreboard) {
        Objective objective = scoreboard.getObjective(GAMES_WON_OBJECTIVE);
        if (objective != null) {
            return objective;
        }
        return scoreboard.addObjective(
                GAMES_WON_OBJECTIVE,
                ObjectiveCriteria.DUMMY,
                Component.literal("Games Won"),
                ObjectiveCriteria.RenderType.INTEGER,
                false,
                null);
    }

    public static int incrementGamesWon(ServerScoreboard scoreboard, ServerPlayer player) {
        Objective objective = registerGamesWon(scoreboard);
        return scoreboard.getOrCreatePlayerScore(player, objective).increment();
    }
}
