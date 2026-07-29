package io.github.brainage04.brainage_minigames.event.custom.core;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.event.custom.CustomEventManager;
import io.github.brainage04.brainage_minigames.scoreboard.ModScoreboard;
import io.github.brainage04.brainage_minigames.storage.KitStorage;
import io.github.brainage04.brainage_minigames.storage.PlayerSnapshotStorage;
import io.github.brainage04.brainage_minigames.util.ChatUtils;
import io.github.brainage04.brainage_minigames.util.ItemStackUtils;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import io.github.brainage04.brainage_minigames.util.StringUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

public interface IBaseMinigame {
    String getName();

    int getMinTeams();

    int getMaxTeams();

    int getMinPlayersPerTeam();

    int getMaxPlayersPerTeam();

    boolean isReadyToStart(ServerLevel eventsDimension);

    String getRequirementsToStart();

    default GameType getGameMode() {
        return GameType.SURVIVAL;
    }

    Identifier getKitId();

    Identifier getRewardsId();

    default void waitingPhase(ServerLevel eventsDimension) {
        if (!MinigameState.waitPhaseTicking) {
            return;
        }
        if (MinigameState.eventTimer < MinigameState.WAIT_PHASE_TICKS) {
            if (MinigameState.eventTimer >= 200 && MinigameState.eventTimer % 200 == 0) {
                ChatUtils.sendCountdown(
                        PlayerUtils.getOperators(eventsDimension.getServer().getPlayerList()),
                        "The event will start automatically in ",
                        (MinigameState.WAIT_PHASE_TICKS - MinigameState.eventTimer) / 20,
                        "."
                );
            }
            return;
        }

        if (!isReadyToStart(eventsDimension)) {
            String message = getRequirementsToStart();
            BrainageMinigames.LOGGER.error(message);
            for (ServerPlayer player : PlayerUtils.getOperators(eventsDimension.getServer().getPlayerList())) {
                player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
            }
            CustomEventManager.stop(eventsDimension.getServer());
        } else {
            CustomEventManager.nextPhase(eventsDimension.getServer());
        }
    }

    void setupEvent(ServerLevel eventsDimension);

    default void countdownPhase(ServerLevel eventsDimension) {
        if (MinigameState.eventTimer < MinigameState.COUNTDOWN_TICKS) {
            if (MinigameState.eventTimer % 20 == 0) {
                ChatUtils.sendCountdown(
                        MinigameState.getParticipatingPlayers(eventsDimension),
                        "Event starts in ",
                        (MinigameState.COUNTDOWN_TICKS - MinigameState.eventTimer) / 20,
                        "!"
                );
            }
            return;
        }

        for (ServerPlayer player : MinigameState.getParticipatingPlayers(eventsDimension)) {
            player.sendSystemMessage(Component.literal("Event has started!"));
            player.playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.0F, 1.0F);
            player.setGameMode(getGameMode());
            player.removeAllEffects();
            giveKit(eventsDimension, player);
        }
        CustomEventManager.nextPhase(eventsDimension.getServer());
    }

    boolean pvpPhase(ServerLevel eventsDimension);

    default void winEvent(List<ServerPlayer> winners, ServerLevel eventsDimension) {
        if (winners.isEmpty()) {
            CustomEventManager.stop(eventsDimension.getServer());
            return;
        }

        MinecraftServer server = eventsDimension.getServer();
        Component title = Component.literal(
                StringUtils.getWinnersTitle(winners.stream().map(ServerPlayer::getScoreboardName).toList())
        ).withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD);
        Component subtitle = Component.literal(winners.size() > 1 ? "have won the event!" : "has won the event!")
                .withStyle(ChatFormatting.GREEN);
        ChatUtils.broadcastServerEvent(server, title, subtitle, true, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);

        for (ServerPlayer player : winners) {
            PlayerUtils.makeInvulnerable(player);
            giveRewards(eventsDimension, player);
            ModScoreboard.incrementEventsWon(server.getScoreboard(), player);
        }
        for (ServerPlayer operator : PlayerUtils.getOperators(server.getPlayerList())) {
            ChatUtils.sendStopCountdownUsage(operator, false);
            ChatUtils.sendStopEventUsage(operator);
        }
        CustomEventManager.nextPhase(server);
    }

    default void postWinPhase(ServerLevel eventsDimension) {
        if (MinigameState.eventTimer % 20 == 0) {
            for (ServerPlayer player : MinigameState.aliveEventPlayers) {
                eventsDimension.addFreshEntity(new FireworkRocketEntity(
                        eventsDimension,
                        player.getX(),
                        player.getY() + 3.0,
                        player.getZ(),
                        ItemStackUtils.fireworkRocket()
                ));
            }
        }
        if (!MinigameState.postWinPhaseTicking) {
            return;
        }
        if (MinigameState.eventTimer < MinigameState.POST_WIN_PHASE_TICKS) {
            if (MinigameState.eventTimer >= 200 && MinigameState.eventTimer % 200 == 0) {
                ChatUtils.sendCountdown(
                        PlayerUtils.getOperators(eventsDimension.getServer().getPlayerList()),
                        "The event will end automatically in ",
                        (MinigameState.POST_WIN_PHASE_TICKS - MinigameState.eventTimer) / 20,
                        "."
                );
            }
        } else {
            CustomEventManager.stop(eventsDimension.getServer());
        }
    }

    default LootTable getRewards(MinecraftServer server) {
        return getLootTable(server, getRewardsId());
    }


    private static LootTable getLootTable(MinecraftServer server, Identifier id) {
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, id);
        return server.reloadableRegistries().getLootTable(key);
    }

    default void giveRewards(ServerLevel level, ServerPlayer player) {
        LootTable lootTable = getRewards(level.getServer());
        LootParams params = new LootParams.Builder(level)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .create(LootContextParamSets.CHEST);
        PlayerSnapshotStorage.addRewards(player, lootTable.getRandomItems(params));
    }

    default void giveKit(ServerLevel level, ServerPlayer player) {
        KitStorage.give(level.getServer(), getKitId(), List.of(player));
    }
}
