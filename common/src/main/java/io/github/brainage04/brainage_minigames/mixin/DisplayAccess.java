package io.github.brainage04.brainage_minigames.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** How a display entity faces its viewers, which vanilla only sets from saved data. */
@Mixin(Display.class)
public interface DisplayAccess {
    @Invoker("setBillboardConstraints")
    void brainage_minigames$setBillboardConstraints(Display.BillboardConstraints constraints);
}
