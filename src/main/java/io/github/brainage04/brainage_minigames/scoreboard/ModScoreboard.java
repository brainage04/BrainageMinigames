package io.github.brainage04.brainage_minigames.scoreboard;

import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public final class ModScoreboard {
    public static final String EVENTS_WON_OBJECTIVE = "brainage_events_won";

    private ModScoreboard() {
    }

    public static Objective registerEventsWon(ServerScoreboard scoreboard) {
        Objective objective = scoreboard.getObjective(EVENTS_WON_OBJECTIVE);
        if (objective != null) {
            return objective;
        }
        return scoreboard.addObjective(
                EVENTS_WON_OBJECTIVE,
                ObjectiveCriteria.DUMMY,
                Component.literal("Events Won"),
                ObjectiveCriteria.RenderType.INTEGER,
                false,
                null
        );
    }

    public static int incrementEventsWon(ServerScoreboard scoreboard, ServerPlayer player) {
        Objective objective = registerEventsWon(scoreboard);
        return scoreboard.getOrCreatePlayerScore(player, objective).increment();
    }
}
