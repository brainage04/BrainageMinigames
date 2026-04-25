package io.github.brainage04.brainage_minigames.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

public final class PlayerUtils {
    private PlayerUtils() {
    }

    public static List<ServerPlayer> getPlayers(PlayerList playerList, Predicate<ServerPlayer> predicate) {
        return playerList.getPlayers().stream().filter(predicate).toList();
    }

    public static List<ServerPlayer> getOperators(PlayerList playerList) {
        return getPlayers(playerList, player -> player.permissions().hasPermission(
                net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
    }

    public static boolean isOperatorInCreative(ServerPlayer player) {
        return player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)
                && player.gameMode() == GameType.CREATIVE;
    }

    public static void makeInvulnerable(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, Integer.MAX_VALUE, 4));
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
    }
    public static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    public static void broadcastWithSound(List<ServerPlayer> players, String message, SoundEvent sound) {
        for (ServerPlayer player : players) {
            player.sendSystemMessage(Component.literal(message));
            player.playSound(sound, 1.0F, 1.0F);
        }
    }
}
