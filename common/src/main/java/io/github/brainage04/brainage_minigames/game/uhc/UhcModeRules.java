package io.github.brainage04.brainage_minigames.game.uhc;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import java.util.function.BiConsumer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;

/** Server-persisted UHC modes; border/deathmatch choices are captured when a match opens. */
public final class UhcModeRules {
    public static final GameRule<Integer> BORDER_STYLE = new GameRule<>(
            GameRuleCategory.MISC, GameRuleType.INT, IntegerArgumentType.integer(0, 1),
            GameRuleTypeVisitor::visitInteger, Codec.intRange(0, 1), Integer::intValue,
            0, FeatureFlagSet.of());
    public static final GameRule<Boolean> DEATHMATCH = bool(true);
    public static final GameRule<Boolean> ALWAYS_DAY = bool(true);
    public static final GameRule<Boolean> DOUBLE_HEALTH = bool(true);
    /** Shared bot policy: following opponents before PvP starts is opt-in. */
    public static final GameRule<Boolean> PRE_PVP_FOLLOWING = bool(false);

    private UhcModeRules() {}

    private static GameRule<Boolean> bool(boolean defaultValue) {
        return new GameRule<>(GameRuleCategory.MISC, GameRuleType.BOOL,
                BoolArgumentType.bool(), GameRuleTypeVisitor::visitBoolean,
                Codec.BOOL, value -> value ? 1 : 0, defaultValue, FeatureFlagSet.of());
    }

    public static void register(BiConsumer<Identifier, GameRule<?>> registry) {
        registry.accept(BrainageMinigames.id("uhc_border_style"), BORDER_STYLE);
        registry.accept(BrainageMinigames.id("uhc_deathmatch"), DEATHMATCH);
        registry.accept(BrainageMinigames.id("uhc_always_day"), ALWAYS_DAY);
        registry.accept(BrainageMinigames.id("uhc_double_health"), DOUBLE_HEALTH);
        registry.accept(BrainageMinigames.id("pre_pvp_following"), PRE_PVP_FOLLOWING);
    }

    public static boolean badlion(MinecraftServer server) {
        return server.getGameRules().get(BORDER_STYLE) == 1;
    }
}
