package io.github.brainage04.brainage_minigames.uhc;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;

public final class UhcFeature {
    private UhcFeature() {
    }

    public static void initialize() {
        UhcCommand.initialize();
        UhcWorldCleanup.initialize();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player) || UhcManager.allowDamage(player, source));
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player) || !UhcManager.eliminate(player));
        ServerPlayerEvents.JOIN.register(UhcManager::handleConnect);
        ServerPlayerEvents.LEAVE.register(UhcManager::handleDisconnect);
    }
}
