package io.github.brainage04.brainage_minigames.dimension;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class ModDimensions {
    /** Fixed-midday, clear, mob-free void dimension for duels, map games and UHC deathmatch. */
    public static final ResourceKey<Level> MINIGAMES =
            ResourceKey.create(Registries.DIMENSION, BrainageMinigames.id("minigames"));

    /** Overworld-style dimension for UHC regions, regenerated on server restart. */
    public static final ResourceKey<Level> UHC =
            ResourceKey.create(Registries.DIMENSION, BrainageMinigames.id("uhc"));

    /** Nether-style dimension that nether portals in {@link #UHC} lead to, regenerated with it. */
    public static final ResourceKey<Level> UHC_NETHER =
            ResourceKey.create(Registries.DIMENSION, BrainageMinigames.id("uhc_nether"));

    public static final ResourceKey<Level> MEETUP =
            ResourceKey.create(Registries.DIMENSION, BrainageMinigames.id("meetup"));
    public static final ResourceKey<Level> MEETUP_NETHER =
            ResourceKey.create(Registries.DIMENSION, BrainageMinigames.id("meetup_nether"));
    public static final ResourceKey<Level> FINAL_UHC =
            ResourceKey.create(Registries.DIMENSION, BrainageMinigames.id("final_uhc"));
    public static final ResourceKey<Level> FINAL_UHC_NETHER =
            ResourceKey.create(Registries.DIMENSION, BrainageMinigames.id("final_uhc_nether"));

    public static boolean natural(ResourceKey<Level> dimension) {
        return dimension.equals(UHC) || dimension.equals(MEETUP) || dimension.equals(FINAL_UHC);
    }

    public static boolean nether(ResourceKey<Level> dimension) {
        return dimension.equals(UHC_NETHER) || dimension.equals(MEETUP_NETHER)
                || dimension.equals(FINAL_UHC_NETHER);
    }

    public static ResourceKey<Level> paired(ResourceKey<Level> dimension) {
        if (dimension.equals(UHC)) return UHC_NETHER;
        if (dimension.equals(UHC_NETHER)) return UHC;
        if (dimension.equals(MEETUP)) return MEETUP_NETHER;
        if (dimension.equals(MEETUP_NETHER)) return MEETUP;
        if (dimension.equals(FINAL_UHC)) return FINAL_UHC_NETHER;
        if (dimension.equals(FINAL_UHC_NETHER)) return FINAL_UHC;
        throw new IllegalArgumentException("Not a UHC-style dimension: " + dimension);
    }

    private ModDimensions() {}
}
