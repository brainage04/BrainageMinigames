package io.github.brainage04.brainage_minigames.game.uhc;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.WorldClock;
import org.jspecify.annotations.Nullable;

/** Pauses only the UHC clock. The vanilla dimensions keep their own clock and timelines. */
public final class UhcClock {
    public static final ResourceKey<WorldClock> CLOCK =
            ResourceKey.create(Registries.WORLD_CLOCK, BrainageMinigames.id("uhc"));
    private static @Nullable MinecraftServer owner;
    private static @Nullable Holder<WorldClock> clock;
    private static boolean paused;

    private UhcClock() {}

    public static void tick(MinecraftServer server) {
        boolean alwaysDay = server.getGameRules().get(UhcModeRules.ALWAYS_DAY);
        if (owner != server) {
            owner = server;
            clock = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK)
                    .getOrThrow(CLOCK);
            server.overworld().clockManager().setPaused(clock, alwaysDay);
            paused = alwaysDay;
        }
        if (paused != alwaysDay) {
            server.overworld().clockManager().setPaused(clock, alwaysDay);
            paused = alwaysDay;
        }
        if (alwaysDay && server.overworld().clockManager().getTotalTicks(clock) != 6000) {
            server.overworld().clockManager().setTotalTicks(clock, 6000);
        }
    }

    public static void clear() {
        owner = null;
        clock = null;
    }
}
