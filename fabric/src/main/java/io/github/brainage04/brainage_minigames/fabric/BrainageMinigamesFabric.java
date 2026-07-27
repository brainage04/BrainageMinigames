package io.github.brainage04.brainage_minigames.fabric;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.command.KitCommand;
import io.github.brainage04.brainage_minigames.command.core.ModCommands;
import io.github.brainage04.brainage_minigames.event.ModServerEvents;
import io.github.brainage04.brainage_minigames.event.ModTickEvents;
import io.github.brainage04.brainage_minigames.uhc.UhcCommand;
import io.github.brainage04.brainage_minigames.uhc.UhcFeature;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

public final class BrainageMinigamesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BrainageMinigames.initialize();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> {
            ModCommands.register(dispatcher);
            KitCommand.register(dispatcher);
            UhcCommand.register(dispatcher);
        });
        ServerLifecycleEvents.SERVER_STARTING.register(UhcFeature::serverStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(ModServerEvents::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(ModServerEvents::serverStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(UhcFeature::serverStopped);
        ServerTickEvents.END_SERVER_TICK.register(ModTickEvents::tick);
        ServerPlayerEvents.JOIN.register(ModServerEvents::playerJoined);
        ServerPlayerEvents.LEAVE.register(ModServerEvents::playerLeft);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player) || ModServerEvents.allowDamage(player, source));
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player) || ModServerEvents.allowDeath(player));
    }
}
