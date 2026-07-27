package io.github.brainage04.brainage_minigames.neoforge;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.command.KitCommand;
import io.github.brainage04.brainage_minigames.command.core.ModCommands;
import io.github.brainage04.brainage_minigames.event.ModServerEvents;
import io.github.brainage04.brainage_minigames.event.ModTickEvents;
import io.github.brainage04.brainage_minigames.uhc.UhcCommand;
import io.github.brainage04.brainage_minigames.uhc.UhcFeature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerPlayer;

@Mod(BrainageMinigames.MOD_ID)
public final class BrainageMinigamesNeoForge {
    public BrainageMinigamesNeoForge(IEventBus modBus) {
        BrainageMinigames.initialize();
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::serverStarting);
        NeoForge.EVENT_BUS.addListener(this::serverStarted);
        NeoForge.EVENT_BUS.addListener(this::serverStopping);
        NeoForge.EVENT_BUS.addListener(this::serverStopped);
        NeoForge.EVENT_BUS.addListener(this::serverTick);
        NeoForge.EVENT_BUS.addListener(this::playerLogin);
        NeoForge.EVENT_BUS.addListener(this::playerLogout);
        NeoForge.EVENT_BUS.addListener(this::livingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(this::livingDeath);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
        KitCommand.register(event.getDispatcher());
        UhcCommand.register(event.getDispatcher());
    }
    private void serverStarting(ServerStartingEvent event) { UhcFeature.serverStarting(event.getServer()); }
    private void serverStarted(ServerStartedEvent event) { ModServerEvents.serverStarted(event.getServer()); }
    private void serverStopping(ServerStoppingEvent event) { ModServerEvents.serverStopping(event.getServer()); }
    private void serverStopped(ServerStoppedEvent event) { UhcFeature.serverStopped(event.getServer()); }
    private void serverTick(ServerTickEvent.Post event) { ModTickEvents.tick(event.getServer()); }
    private void playerLogin(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer player) ModServerEvents.playerJoined(player); }
    private void playerLogout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer player) ModServerEvents.playerLeft(player); }
    private void livingIncomingDamage(LivingIncomingDamageEvent event) { if (event.getEntity() instanceof ServerPlayer player && !ModServerEvents.allowDamage(player, event.getSource())) event.setCanceled(true); }
    private void livingDeath(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer player && !ModServerEvents.allowDeath(player)) event.setCanceled(true); }
}
