package io.github.brainage04.brainage_minigames.event.custom.core;

import net.minecraft.world.scores.PlayerTeam;

import java.util.Objects;

public final class TeamPair {
    public final PlayerTeam first;
    public final PlayerTeam second;
    public boolean notifiedOfMatchOutcome;

    public TeamPair(PlayerTeam first, PlayerTeam second) {
        this.first = Objects.requireNonNull(first, "first");
        this.second = second;
    }

    public boolean hasTeam(PlayerTeam team) {
        return team != null && (sameTeam(team, first) || sameTeam(team, second));
    }

    private static boolean sameTeam(PlayerTeam left, PlayerTeam right) {
        return right != null && Objects.equals(left.getName(), right.getName());
    }
}
