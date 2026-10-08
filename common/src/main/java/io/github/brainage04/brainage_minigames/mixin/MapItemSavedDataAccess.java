package io.github.brainage04.brainage_minigames.mixin;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * A map centred exactly where a game wants it (vanilla snaps new maps to a 128-block grid), and
 * markers a game adds and removes itself rather than through banners, frames or items.
 */
@Mixin(MapItemSavedData.class)
public interface MapItemSavedDataAccess {
    @Invoker("<init>")
    static MapItemSavedData brainage_minigames$create(
            int centerX,
            int centerZ,
            byte scale,
            boolean trackingPosition,
            boolean unlimitedTracking,
            boolean locked,
            ResourceKey<Level> dimension) {
        throw new AssertionError();
    }

    @Invoker("addDecoration")
    void brainage_minigames$addDecoration(
            Holder<MapDecorationType> type,
            @Nullable LevelAccessor level,
            String key,
            double x,
            double z,
            double yRot,
            @Nullable Component name);

    @Invoker("removeDecoration")
    void brainage_minigames$removeDecoration(String key);
}
