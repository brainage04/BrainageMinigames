package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.CombatRules;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHook.class)
abstract class FishingHookMixin {
    @Shadow protected abstract void setHookedEntity(Entity entity);

    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$rodHit(EntityHitResult hit, CallbackInfo ci) {
        FishingHook hook = (FishingHook) (Object) this;
        if (!(hit.getEntity() instanceof ServerPlayer victim)
                || !(CombatRules.classic(hook.getOwner()) || CombatRules.classic(victim))) return;
        ci.cancel();
        if (hook.getOwner() instanceof ServerPlayer owner && owner != victim
                && MatchManager.activeMatch(owner.getUUID()) == MatchManager.activeMatch(victim.getUUID())
                && victim.hurtServer(victim.level(), hook.damageSources().thrown(hook, owner), 0)) {
            setHookedEntity(victim);
        }
    }

}
