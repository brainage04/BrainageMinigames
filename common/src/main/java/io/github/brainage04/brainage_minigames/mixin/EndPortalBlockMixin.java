package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.UhcCombatLogger;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndPortalBlock.class)
abstract class EndPortalBlockMixin {
    private static final Map<ServerPlayer, Integer> brainage_minigames$lastMessage = new WeakHashMap<>();

    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$keepMatchParticipants(ServerLevel level, Entity entity, BlockPos pos,
            CallbackInfoReturnable<TeleportTransition> cir) {
        Entity participant = brainage_minigames$participant(entity);
        if (participant == null) return;
        if (participant instanceof ServerPlayer player) {
            int tick = level.getServer().getTickCount();
            Integer previous = brainage_minigames$lastMessage.get(player);
            if (previous == null || tick - previous >= 100) {
                player.sendSystemMessage(Component.literal("End portals are disabled during this match."));
                brainage_minigames$lastMessage.put(player, tick);
            }
        }
        cir.setReturnValue(null);
    }

    private static Entity brainage_minigames$participant(Entity entity) {
        if (entity instanceof ServerPlayer player && MatchManager.matchOf(player.getUUID()).isPresent()) return player;
        if (UhcCombatLogger.participant(entity) != null) return entity;
        for (Entity passenger : entity.getPassengers()) {
            Entity participant = brainage_minigames$participant(passenger);
            if (participant != null) return participant;
        }
        return null;
    }
}
