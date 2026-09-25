package io.github.brainage04.brainage_minigames.game.arena;

import java.util.BitSet;

/**
 * Hands out space in the minigames dimension: slots are {@link #SPACING} blocks apart along the X
 * axis, centred on Z = 0, so arenas in different slots never overlap.
 */
final class ArenaSlots {
    static final int SPACING = 512;

    private static final BitSet USED = new BitSet();

    private ArenaSlots() {}

    /** Reserves {@code count} adjacent free slots and returns the first. */
    static synchronized int allocate(int count) {
        int first = USED.nextClearBit(0);
        while (USED.nextSetBit(first) != -1 && USED.nextSetBit(first) < first + count) {
            first = USED.nextClearBit(USED.nextSetBit(first));
        }
        USED.set(first, first + count);
        return first;
    }

    static synchronized void release(int first, int count) {
        USED.clear(first, first + count);
    }

    /** Slots needed for something {@code width} blocks wide along X, with room to spare. */
    static int slotsFor(int width) {
        return Math.max(1, (width + 64 + SPACING - 1) / SPACING);
    }

    /** The X coordinate at the centre of {@code count} slots starting at {@code first}. */
    static int centerX(int first, int count) {
        return first * SPACING + (count - 1) * SPACING / 2;
    }
}
