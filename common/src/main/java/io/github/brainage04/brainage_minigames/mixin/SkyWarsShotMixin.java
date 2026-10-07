package io.github.brainage04.brainage_minigames.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Arrows a SkyWars participant shoots from a bow or crossbow; see {@link SkyWarsPerks#shot}. */
@Mixin(ProjectileWeaponItem.class)
abstract class SkyWarsShotMixin {
    @ModifyReturnValue(method = "createProjectile", at = @At("RETURN"))
    private Projectile brainage_minigames$skyWarsShot(Projectile projectile, Level level, LivingEntity shooter,
            ItemStack weapon, ItemStack ammo, boolean crit) {
        if (shooter instanceof ServerPlayer player) {
            SkyWarsPerks.shot(player, projectile);
        }
        return projectile;
    }
}
