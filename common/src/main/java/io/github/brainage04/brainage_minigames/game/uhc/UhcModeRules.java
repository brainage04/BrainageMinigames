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
    public static final GameRule<Integer> DEATHMATCH_BORDER_START = integer(113, 16, 20_000);
    public static final GameRule<Integer> DEATHMATCH_BORDER_FINAL = integer(56, 1, 20_000);
    public static final GameRule<Integer> NETHER_BORDER_SCALE = integer(8, 1, 100);
    /** Zero uses the per-match deathmatch_minutes setting. Otherwise minutes after grace. */
    public static final GameRule<Integer> DEATHMATCH_AFTER_GRACE = integer(0, 0, 600);
    public static final GameRule<Integer> DEATHMATCH_SKIP_PLAYERS = integer(0, 0, 10_000);
    public static final GameRule<Integer> DEATHMATCH_SKIP_MINUTES = integer(10, 1, 600);
    /** Zero uses the per-match deathmatch_duration_minutes setting. */
    public static final GameRule<Integer> DEATHMATCH_DURATION = integer(0, 0, 600);
    public static final GameRule<Boolean> TIMEOUT_MOST_KILLS = bool(false);
    public static final GameRule<Boolean> COMBAT_LOGGER = bool(true);
    /** Shared bot policy: following opponents before PvP starts is opt-in. */
    public static final GameRule<Boolean> PRE_PVP_FOLLOWING = bool(false);

    private UhcModeRules() {}

    static GameRule<Boolean> bool(boolean defaultValue) {
        return new GameRule<>(GameRuleCategory.MISC, GameRuleType.BOOL,
                BoolArgumentType.bool(), GameRuleTypeVisitor::visitBoolean,
                Codec.BOOL, value -> value ? 1 : 0, defaultValue, FeatureFlagSet.of());
    }

    static GameRule<Integer> integer(int value, int minimum, int maximum) {
        return new GameRule<>(GameRuleCategory.MISC, GameRuleType.INT,
                IntegerArgumentType.integer(minimum, maximum), GameRuleTypeVisitor::visitInteger,
                Codec.intRange(minimum, maximum), Integer::intValue, value, FeatureFlagSet.of());
    }

    public static void register(BiConsumer<Identifier, GameRule<?>> registry) {
        registry.accept(BrainageMinigames.id("uhc_border_style"), BORDER_STYLE);
        registry.accept(BrainageMinigames.id("uhc_deathmatch"), DEATHMATCH);
        registry.accept(BrainageMinigames.id("uhc_always_day"), ALWAYS_DAY);
        registry.accept(BrainageMinigames.id("uhc_double_health"), DOUBLE_HEALTH);
        registry.accept(BrainageMinigames.id("uhc_deathmatch_border_start"), DEATHMATCH_BORDER_START);
        registry.accept(BrainageMinigames.id("uhc_deathmatch_border_final"), DEATHMATCH_BORDER_FINAL);
        registry.accept(BrainageMinigames.id("uhc_nether_border_scale"), NETHER_BORDER_SCALE);
        registry.accept(BrainageMinigames.id("uhc_deathmatch_after_grace_minutes"), DEATHMATCH_AFTER_GRACE);
        registry.accept(BrainageMinigames.id("uhc_deathmatch_skip_players"), DEATHMATCH_SKIP_PLAYERS);
        registry.accept(BrainageMinigames.id("uhc_deathmatch_skip_minutes"), DEATHMATCH_SKIP_MINUTES);
        registry.accept(BrainageMinigames.id("uhc_deathmatch_duration_minutes"), DEATHMATCH_DURATION);
        registry.accept(BrainageMinigames.id("uhc_timeout_most_kills"), TIMEOUT_MOST_KILLS);
        registry.accept(BrainageMinigames.id("uhc_combat_logger"), COMBAT_LOGGER);
        registry.accept(BrainageMinigames.id("pre_pvp_following"), PRE_PVP_FOLLOWING);
    }

    public static boolean badlion(MinecraftServer server) {
        return server.getGameRules().get(BORDER_STYLE) == 1;
    }
}
