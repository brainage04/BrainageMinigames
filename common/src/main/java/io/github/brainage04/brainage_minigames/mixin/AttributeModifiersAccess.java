package io.github.brainage04.brainage_minigames.mixin;

import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AttributeInstance.class)
public interface AttributeModifiersAccess {
    @Accessor("modifierById")
    Map<Identifier, AttributeModifier> brainage_minigames$modifiers();
}
