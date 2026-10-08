package io.github.brainage04.brainage_minigames.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The block a block display shows, which vanilla only sets from saved data. */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayAccess {
    @Invoker("setBlockState")
    void brainage_minigames$setBlockState(BlockState state);
}
