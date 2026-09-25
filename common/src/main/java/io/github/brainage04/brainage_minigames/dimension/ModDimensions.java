package io.github.brainage04.brainage_minigames.dimension;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class ModDimensions {
    /** Void dimension that holds the duel arenas. */
    public static final ResourceKey<Level> MINIGAMES =
            ResourceKey.create(Registries.DIMENSION, BrainageMinigames.id("minigames"));

    /** Overworld-style dimension regenerated after every UHC. */
    public static final ResourceKey<Level> UHC =
            ResourceKey.create(Registries.DIMENSION, BrainageMinigames.id("uhc"));

    private ModDimensions() {}
}
