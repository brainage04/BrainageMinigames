package io.github.brainage04.brainage_minigames.menu;

import net.minecraft.world.inventory.ContainerInput;
import org.jspecify.annotations.Nullable;

/** The clicks a menu button reacts to; every other container input is refused. */
public enum Click {
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT;

    public boolean right() {
        return this == RIGHT || this == SHIFT_RIGHT;
    }

    public boolean shift() {
        return this == SHIFT_LEFT || this == SHIFT_RIGHT;
    }

    /** The click a container input means on a button, or null for drags, swaps, throws and so on. */
    static @Nullable Click of(ContainerInput input, int button) {
        return switch (input) {
            case PICKUP -> button == 0 ? LEFT : button == 1 ? RIGHT : null;
            case QUICK_MOVE -> button == 0 ? SHIFT_LEFT : button == 1 ? SHIFT_RIGHT : null;
            default -> null;
        };
    }
}
