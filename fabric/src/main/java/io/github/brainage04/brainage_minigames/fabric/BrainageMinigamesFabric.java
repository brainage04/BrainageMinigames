package io.github.brainage04.brainage_minigames.fabric;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.command.DuelCommand;
import io.github.brainage04.brainage_minigames.command.MinigamesCommand;
import io.github.brainage04.brainage_minigames.event.ModServerEvents;
import io.github.brainage04.brainage_minigames.game.CombatRules;
import io.github.brainage04.brainage_minigames.game.AntiJanitor;
import io.github.brainage04.brainage_minigames.game.uhc.UhcResourceRules;
import io.github.brainage04.brainage_minigames.game.uhc.UhcModeRules;
import io.github.brainage04.brainage_minigames.scoreboard.EloRatings;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

public final class BrainageMinigamesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BrainageMinigames.initialize();
        UhcResourceRules.register((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        io.github.brainage04.brainage_minigames.game.uhc.UhcSpawnRules.register((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        io.github.brainage04.brainage_minigames.game.uhc.UhcMobDrops.register((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        UhcModeRules.register((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        io.github.brainage04.brainage_minigames.game.uhc.UhcResourceScenarios.register((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        EloRatings.register((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        CombatRules.register((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        AntiJanitor.register((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        io.github.brainage04.brainage_minigames.game.ContainerProtection.register((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        io.github.brainage04.brainage_minigames.game.uhc.UhcProgression.register((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        io.github.brainage04.brainage_minigames.game.skywars.SkyWarsProgression.register((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        io.github.brainage04.brainage_minigames.game.uhc.SpeedUhcProgression.register((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        io.github.brainage04.brainage_minigames.game.MatchService.register((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        io.github.brainage04.brainage_minigames.feedback.FeedbackReminders.register((id, rule) -> Registry.register(BuiltInRegistries.GAME_RULE, id, rule));
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, context, environment) -> MinigamesCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, context, environment) -> DuelCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, context, environment) -> io.github.brainage04.brainage_minigames.feedback.FeedbackCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, context, environment) -> io.github.brainage04.brainage_minigames.hub.HubCommand.register(dispatcher));
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, context, environment) -> io.github.brainage04.brainage_minigames.command.ShoutCommand.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(ModServerEvents::serverStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(ModServerEvents::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(ModServerEvents::serverStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(ModServerEvents::serverStopped);
        ServerTickEvents.END_SERVER_TICK.register(ModServerEvents::tick);
        ServerPlayerEvents.JOIN.register(ModServerEvents::playerJoined);
        ServerPlayerEvents.LEAVE.register(ModServerEvents::playerLeft);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(
                (entity, source, amount) ->
                        !(entity instanceof ServerPlayer player)
                                || ModServerEvents.allowDamage(player, source));
        ServerLivingEntityEvents.ALLOW_DEATH.register(
                (entity, source, amount) ->
                        !(entity instanceof ServerPlayer player)
                                || ModServerEvents.allowDeath(player));
    }
}
