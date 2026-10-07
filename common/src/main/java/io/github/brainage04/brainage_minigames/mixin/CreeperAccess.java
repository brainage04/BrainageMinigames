package io.github.brainage04.brainage_minigames.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The charged flag, which vanilla only sets from a lightning strike. */
@Mixin(Creeper.class)
public interface CreeperAccess {
    @Accessor("DATA_IS_POWERED")
    static EntityDataAccessor<Boolean> brainage_minigames$powered() {
        throw new AssertionError();
    }
}
