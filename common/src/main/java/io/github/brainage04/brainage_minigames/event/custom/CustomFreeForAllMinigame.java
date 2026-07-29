package io.github.brainage04.brainage_minigames.event.custom;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.event.custom.core.AbstractBaseMinigame;
import io.github.brainage04.brainage_minigames.event.custom.core.MinigameState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

public final class CustomFreeForAllMinigame extends AbstractBaseMinigame {
    public CustomFreeForAllMinigame(
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
        return "Custom Free-for-All";
    }


    @Override
    public Identifier getRewardsId() {
        return BrainageMinigames.id("empty");
    }

    @Override
    public void setupEvent(ServerLevel level) {
        super.setupEvent(level);
        if (MinigameState.event == this) {
            CustomEventManager.nextPhase(level.getServer());
        }
    }
}
