package io.github.brainage04.brainage_minigames.neoforge;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.command.DuelCommand;
import io.github.brainage04.brainage_minigames.command.MinigamesCommand;
import io.github.brainage04.brainage_minigames.event.ModServerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(BrainageMinigames.MOD_ID)
public final class BrainageMinigamesNeoForge {
    public BrainageMinigamesNeoForge(IEventBus modBus) {
        BrainageMinigames.initialize();
        NeoForge.EVENT_BUS.addListener(
                RegisterCommandsEvent.class,
                event -> MinigamesCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(
                RegisterCommandsEvent.class, event -> DuelCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(
                ServerStartingEvent.class,
                event -> ModServerEvents.serverStarting(event.getServer()));
        NeoForge.EVENT_BUS.addListener(
                ServerStartedEvent.class,
                event -> ModServerEvents.serverStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener(
                ServerStoppingEvent.class,
                event -> ModServerEvents.serverStopping(event.getServer()));
        NeoForge.EVENT_BUS.addListener(
                ServerStoppedEvent.class,
                event -> ModServerEvents.serverStopped(event.getServer()));
        NeoForge.EVENT_BUS.addListener(
                ServerTickEvent.Post.class, event -> ModServerEvents.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(
                PlayerEvent.PlayerLoggedInEvent.class,
                event -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        ModServerEvents.playerJoined(player);
                    }
                });
        NeoForge.EVENT_BUS.addListener(
                PlayerEvent.PlayerLoggedOutEvent.class,
                event -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        ModServerEvents.playerLeft(player);
                    }
                });
        NeoForge.EVENT_BUS.addListener(
                LivingIncomingDamageEvent.class,
                event -> {
                    if (event.getEntity() instanceof ServerPlayer player
                            && !ModServerEvents.allowDamage(player, event.getSource())) {
                        event.setCanceled(true);
                    }
                });
        NeoForge.EVENT_BUS.addListener(
                LivingDeathEvent.class,
                event -> {
                    if (event.getEntity() instanceof ServerPlayer player
                            && !ModServerEvents.allowDeath(player)) {
                        event.setCanceled(true);
                    }
                });
    }
}
