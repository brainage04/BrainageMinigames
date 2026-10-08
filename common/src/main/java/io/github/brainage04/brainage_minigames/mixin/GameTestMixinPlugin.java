package io.github.brainage04.brainage_minigames.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Adds the GameTest-only mixins when the separate GameTest source set is on the runtime classpath,
 * on both loaders and in development and production runs alike.
 */
public final class GameTestMixinPlugin implements IMixinConfigPlugin {
    @Override
    public List<String> getMixins() {
        return getClass().getClassLoader().getResource(
                        "io/github/brainage04/brainage_minigames/mixin/GameTestWritesMixin.class")
                == null ? List.of() : List.of("GameTestWritesMixin", "GameTestRegionFileMixin");
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
