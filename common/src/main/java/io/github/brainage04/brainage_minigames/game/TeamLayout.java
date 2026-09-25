package io.github.brainage04.brainage_minigames.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The teams a match is played with: either a fixed list of team sizes ({@code 1v1}, {@code 2v3v4})
 * or free-for-all, where every participant is their own team.
 */
public record TeamLayout(List<Integer> teamSizes) {
    public static final TeamLayout FREE_FOR_ALL = new TeamLayout(List.of());
    public static final int MAX_TEAM_SIZE = 100;
    public static final int MAX_TEAMS = 100;
    private static final String FREE_FOR_ALL_NAME = "ffa";

    public TeamLayout {
        teamSizes = List.copyOf(teamSizes);
    }

    /** Parses {@code ffa} or two or more positive team sizes separated by {@code v}. */
    public static Optional<TeamLayout> parse(String text) {
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals(FREE_FOR_ALL_NAME)) {
            return Optional.of(FREE_FOR_ALL);
        }
        String[] parts = normalized.split("v", -1);
        if (parts.length < 2 || parts.length > MAX_TEAMS) {
            return Optional.empty();
        }
        List<Integer> sizes = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                return Optional.empty();
            }
            int size = Integer.parseInt(part);
            if (size < 1 || size > MAX_TEAM_SIZE) {
                return Optional.empty();
            }
            sizes.add(size);
        }
        return Optional.of(new TeamLayout(sizes));
    }

    public boolean isFreeForAll() {
        return teamSizes.isEmpty();
    }

    /** The most players the layout can hold; unbounded for free-for-all. */
    public int capacity() {
        return isFreeForAll()
                ? Integer.MAX_VALUE
                : teamSizes.stream().mapToInt(Integer::intValue).sum();
    }

    /** Fixed layouts start only when every slot is filled; free-for-all needs two players. */
    public int requiredPlayers() {
        return isFreeForAll() ? 2 : capacity();
    }

    @Override
    public String toString() {
        return isFreeForAll()
                ? FREE_FOR_ALL_NAME
                : teamSizes.stream().map(String::valueOf).collect(Collectors.joining("v"));
    }
}
