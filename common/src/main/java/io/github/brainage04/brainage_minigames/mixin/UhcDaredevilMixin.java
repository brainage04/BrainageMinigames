package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.uhc.UhcProgression;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class UhcDaredevilMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$bones(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Horse horse && horse.entityTags().contains("brainage_daredevil") && source.is(DamageTypeTags.IS_EXPLOSION) && horse.getOwner() instanceof ServerPlayer owner && UhcProgression.match(owner) != null) {
            level.addFreshEntity(new ItemEntity(level, horse.getX(), horse.getY(), horse.getZ(), new ItemStack(Items.BONE, 8)));
            horse.discard(); cir.setReturnValue(true);
        }
    }
}
