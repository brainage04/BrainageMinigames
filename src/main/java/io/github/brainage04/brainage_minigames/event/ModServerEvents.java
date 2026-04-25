package io.github.brainage04.brainage_minigames.event;

import io.github.brainage04.brainage_minigames.event.custom.CustomEventManager;
import io.github.brainage04.brainage_minigames.event.custom.core.MinigameState;
import io.github.brainage04.brainage_minigames.scoreboard.ModScoreboard;
import io.github.brainage04.brainage_minigames.storage.PlayerSnapshotStorage;
import io.github.brainage04.brainage_minigames.uhc.UhcManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;

public final class ModServerEvents {
    private ModServerEvents() {
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ModScoreboard.registerEventsWon(server.getScoreboard());
            MinigameState.clearEventTeams(server.getScoreboard());
            PlayerSnapshotStorage.restoreOnlinePlayers(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            CustomEventManager.stop(server);
            UhcManager.stop(server);
            PlayerSnapshotStorage.restoreOnlinePlayers(server);
        });

        ServerPlayerEvents.JOIN.register(CustomEventManager::handleConnect);
        ServerPlayerEvents.LEAVE.register(CustomEventManager::handleDisconnect);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player) || CustomEventManager.allowDamage(player));
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player) || !CustomEventManager.eliminate(player));
    }
}
