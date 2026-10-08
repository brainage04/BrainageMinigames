package io.github.brainage04.brainage_minigames.mixin;

import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandAccess {
    @Accessor("brewTime")
    int brainage_minigames$brewTime();

    @Accessor("brewTime")
    void brainage_minigames$setBrewTime(int brewTime);
}
