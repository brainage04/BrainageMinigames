package io.github.brainage04.brainage_minigames.menu;

import io.github.brainage04.brainage_minigames.api.MatchBots;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The choices made so far while setting up a match in the menus, either a public match to open or
 * a duel to send.
 *
 * @param team the opener's team, counting from 1; 0 lets the match choose (always 0 in FFA)
 * @param bots bots per team in layout order; one entry, the total, for free-for-all
 * @param join whether the opener joins the match they open
 * @param slots a duel's other participants in layout order after the challenger
 */
record Draft(
        Purpose purpose,
        Minigame game,
        @Nullable TeamLayout layout,
        @Nullable Identifier kit,
        @Nullable Identifier map,
        int team,
        List<Integer> bots,
        boolean join,
        List<Slot> slots) {
    enum Purpose {
        OPEN,
        DUEL
    }

    /** A duel participant other than the challenger: not chosen yet, a player, or a bot. */
    record Slot(@Nullable UUID player, String name, boolean bot) {
        static final Slot EMPTY = new Slot(null, "", false);
        static final Slot BOT = new Slot(null, "Bot", true);

        static Slot player(UUID player, String name) {
            return new Slot(player, name, false);
        }

        boolean empty() {
            return player == null && !bot;
        }
    }

    Draft {
        bots = List.copyOf(bots);
        slots = List.copyOf(slots);
    }

    static Draft open(Minigame game) {
        return new Draft(Purpose.OPEN, game, null, null, null, 0, List.of(), true, List.of());
    }

    /** A duel of {@code game}, with {@code opponent} (or nobody) in the first slot. */
    static Draft duel(Minigame game, @Nullable Slot opponent) {
        return new Draft(
                Purpose.DUEL,
                game,
                null,
                null,
                null,
                0,
                List.of(),
                true,
                opponent == null ? List.of() : List.of(opponent));
    }

    boolean duel() {
        return purpose == Purpose.DUEL;
    }

    /** Whether bot options apply: a bot provider is installed and the game plays with bots. */
    boolean botsAvailable() {
        return MatchBots.available() && game.supportsBots();
    }

    /**
     * Sets the layout, keeping the opener's team when it still exists and a duel's chosen players
     * in their slots as far as they fit.
     */
    Draft withLayout(TeamLayout next) {
        int teams = next.isFreeForAll() ? 0 : next.teamSizes().size();
        List<Integer> nextBots = new ArrayList<>(Collections.nCopies(Math.max(1, teams), 0));
        int nextTeam = next.isFreeForAll() ? 0 : team >= 1 && team <= teams ? team : 1;
        List<Slot> nextSlots = new ArrayList<>(slots);
        if (!next.isFreeForAll()) {
            int others = next.capacity() - 1;
            while (nextSlots.size() > others) {
                nextSlots.removeLast();
            }
            while (nextSlots.size() < others) {
                nextSlots.add(Slot.EMPTY);
            }
        }
        return new Draft(purpose, game, next, kit, map, nextTeam, nextBots, join, nextSlots);
    }

    Draft withKit(@Nullable Identifier next) {
        return new Draft(purpose, game, layout, next, map, team, bots, join, slots);
    }

    Draft withMap(@Nullable Identifier next) {
        return new Draft(purpose, game, layout, kit, next, team, bots, join, slots);
    }

    Draft withTeam(int next) {
        return new Draft(purpose, game, layout, kit, map, next, bots, join, slots);
    }

    Draft withJoin(boolean next) {
        return new Draft(purpose, game, layout, kit, map, team, bots, next, slots);
    }

    /** {@code count} bots on team {@code index} (from 0; the only entry in FFA). */
    Draft withBots(int index, int count) {
        List<Integer> next = new ArrayList<>(bots);
        next.set(index, count);
        return new Draft(purpose, game, layout, kit, map, team, next, join, slots);
    }

    Draft withSlot(int index, Slot slot) {
        List<Slot> next = new ArrayList<>(slots);
        if (index == next.size()) {
            next.add(slot);
        } else {
            next.set(index, slot);
        }
        return new Draft(purpose, game, layout, kit, map, team, bots, join, next);
    }

    Draft withoutSlot(int index) {
        List<Slot> next = new ArrayList<>(slots);
        next.remove(index);
        return new Draft(purpose, game, layout, kit, map, team, bots, join, next);
    }

    TeamLayout requireLayout() {
        if (layout == null) {
            throw new IllegalStateException("No layout chosen yet");
        }
        return layout;
    }

    /** Bots on every team together. */
    int totalBots() {
        return bots.stream().mapToInt(Integer::intValue).sum();
    }
}
