package io.github.brainage04.brainage_minigames.event;

import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.event.custom.CustomEventManager;
import io.github.brainage04.brainage_minigames.event.custom.core.MinigameState;
import io.github.brainage04.brainage_minigames.uhc.UhcManager;
import net.minecraft.server.level.ServerLevel;

public final class ModTickEvents {
    private ModTickEvents() {
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (MinigameState.event != null) {
            ServerLevel level = server.getLevel(ModDimensions.MINIGAMES_LEVEL_KEY);
            if (level != null) {
                CustomEventManager.tick(level);
            } else {
                CustomEventManager.stop(server);
            }
        }
        UhcManager.tick(server);
    }
}
