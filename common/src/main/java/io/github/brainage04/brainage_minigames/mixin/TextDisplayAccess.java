package io.github.brainage04.brainage_minigames.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The text of a text display, which vanilla only reads and sets through saved data. */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccess {
    @Invoker("getText")
    Component brainage_minigames$getText();

    @Invoker("setText")
    void brainage_minigames$setText(Component text);
}
