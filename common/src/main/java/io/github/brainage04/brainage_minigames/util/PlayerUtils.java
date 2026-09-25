package io.github.brainage04.brainage_minigames.util;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public final class PlayerUtils {
    private PlayerUtils() {}

    public static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    public static void heal(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        FoodData food = player.getFoodData();
        food.setFoodLevel(20);
        food.setSaturation(20.0F);
    }

    /** Clears everything a minigame should not inherit from the player's previous state. */
    public static void reset(ServerPlayer player, GameType gameType) {
        player.setGameMode(gameType);
        player.getInventory().clearContent();
        player.removeAllEffects();
        heal(player);
    }

    public static ServerPlayer teleport(
            ServerPlayer player, ServerLevel level, Vec3 position, float yaw) {
        return player.teleport(
                new TeleportTransition(
                        level, position, Vec3.ZERO, yaw, 0.0F, TeleportTransition.DO_NOTHING));
    }

    /**
     * Sends the block at {@code pos} as the server has it, undoing whatever the client predicted
     * for an action the server refused.
     */
    public static void resyncBlock(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            Packet<ClientGamePacketListener> packet = blockEntity.getUpdatePacket();
            if (packet != null) {
                player.connection.send(packet);
            }
        }
    }

    /** Sends the player's whole inventory, undoing items the client predicted were used up. */
    public static void resyncInventory(ServerPlayer player) {
        player.containerMenu.sendAllDataToRemote();
        player.inventoryMenu.sendAllDataToRemote();
    }
}
