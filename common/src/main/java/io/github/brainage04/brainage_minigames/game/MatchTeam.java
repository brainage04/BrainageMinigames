package io.github.brainage04.brainage_minigames.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

public final class MatchTeam {
    private final int number;
    private final int size;
    private final PlayerTeam scoreboardTeam;
    private final List<UUID> members = new ArrayList<>();
    private int score;

    MatchTeam(int number, int size, PlayerTeam scoreboardTeam) {
        this.number = number;
        this.size = size;
        this.scoreboardTeam = scoreboardTeam;
    }

    /** One-based, matching the team numbers accepted by {@code /minigames join}. */
    public int number() {
        return number;
    }

    public int size() {
        return size;
    }

    public List<UUID> members() {
        return List.copyOf(members);
    }

    void add(UUID member) {
        members.add(member);
    }

    boolean isFull() {
        return members.size() >= size;
    }

    public PlayerTeam scoreboardTeam() {
        return scoreboardTeam;
    }

    public Optional<TextColor> color() {
        return scoreboardTeam.getColor().map(TeamColor::textColor);
    }

    public Component displayName() {
        return scoreboardTeam.getFormattedDisplayName();
    }

    /** Game-defined points; the highest score wins a match that reaches its time limit. */
    public int score() {
        return score;
    }

    public void addScore(int points) {
        score += points;
    }
}
