package io.github.brainage04.brainage_minigames.mixin;

import io.github.brainage04.brainage_minigames.game.uhc.UhcCrafting;
import io.github.brainage04.brainage_minigames.game.uhc.UhcExtraRecipes;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BrewingStandBlockEntity.class)
abstract class UhcAmbrosiaMixin {
    @Shadow private int brewTime;
    @Shadow private int fuel;
    private static boolean uhc(Level level) { return UhcExtraRecipes.brewingLevel(level); }
    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void brainage_minigames$ingredient(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot == 3 && UhcCrafting.kind(stack).equals("ambrosia")) cir.setReturnValue(uhc(((BrewingStandBlockEntity) (Object) this).getLevel()));
    }
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void brainage_minigames$brew(Level level, BlockPos pos, BlockState block, BrewingStandBlockEntity stand, CallbackInfo ci) {
        if (!UhcCrafting.kind(stand.getItem(3)).equals("ambrosia")) return;
        ci.cancel(); if (!(level instanceof ServerLevel) || !uhc(level)) return;
        UhcAmbrosiaMixin access = (UhcAmbrosiaMixin) (Object) stand;
        boolean valid = false;
        for (int slot = 0; slot < 3; slot++) if (UhcExtraRecipes.ambrosiaEligible(stand.getItem(slot))) { valid = true; break; }
        if (!valid) { access.brewTime = 0; return; }
        if (access.fuel <= 0 && stand.getItem(4).is(Items.BLAZE_POWDER)) { stand.getItem(4).shrink(1); access.fuel = 20; stand.setChanged(); }
        if (access.brewTime <= 0) {
            if (access.fuel <= 0) return;
            access.fuel--; access.brewTime = 400;
        } else if (--access.brewTime == 0) {
            for (int slot = 0; slot < 3; slot++) { ItemStack upgraded = UhcExtraRecipes.ambrosia(stand.getItem(slot)); if (!upgraded.isEmpty()) stand.setItem(slot, upgraded); }
            stand.getItem(3).shrink(1); stand.setChanged(); level.levelEvent(1035, pos, 0);
        }
    }
}
