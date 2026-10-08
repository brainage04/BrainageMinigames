package io.github.brainage04.brainage_minigames.game.uhc;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.Match;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;

/**
 * Server-persisted combat and loot scenarios for every UHC-style game (UHC and its variants,
 * Meetup and FinalUHC). Every scenario is off by default; see {@link UhcScenarios}.
 */
public final class UhcScenarioRules {
    /** Seconds before a dead player's loot chest explodes; 0 drops loot as usual. */
    public static final GameRule<Integer> TIME_BOMB_SECONDS = UhcModeRules.integer(0, 0, 600);
    /** Seconds a killer cannot be hurt by players after a kill; 0 disables it. */
    public static final GameRule<Integer> NO_CLEAN_SECONDS = UhcModeRules.integer(0, 0, 600);
    /** Seconds a kill's loot belongs to the killer's team; 0 disables it. */
    public static final GameRule<Integer> SAFELOOT_SECONDS = UhcModeRules.integer(0, 0, 600);
    public static final GameRule<Boolean> TEAM_BACKPACK = bool();
    public static final GameRule<Boolean> SECOND_CHANCE = bool();
    public static final GameRule<Boolean> MEETUP_ADAPTIVE_BORDER = bool();

    /** A scenario rule with the text menus and documentation show for it. */
    public record Entry(String name, GameRule<?> rule, String description) {}

    public static final List<Entry> ENTRIES = List.of(
            new Entry("uhc_time_bomb_seconds", TIME_BOMB_SECONDS,
                    "A dead player's items and a golden head go into a chest that explodes after this many seconds (0 = off)"),
            new Entry("uhc_no_clean_seconds", NO_CLEAN_SECONDS,
                    "After a kill, players cannot hurt the killer for this many seconds, until the killer attacks a player (0 = off)"),
            new Entry("uhc_safeloot_seconds", SAFELOOT_SECONDS,
                    "Only the killer's team can take a kill's loot for this many seconds (0 = off)"),
            new Entry("uhc_team_backpack", TEAM_BACKPACK,
                    "Teams share a 27-slot /backpack, dropped where the team's last member dies"),
            new Entry("uhc_second_chance", SECOND_CHANCE,
                    "A player who dies before PvP is enabled comes back once, keeping their items"),
            new Entry("meetup_adaptive_border", MEETUP_ADAPTIVE_BORDER,
                    "Meetup's border shrinks at a player count or a time, whichever comes first, then hurts survivors"));

    private UhcScenarioRules() {}

    private static GameRule<Boolean> bool() {
        return new GameRule<>(GameRuleCategory.MISC, GameRuleType.BOOL,
                BoolArgumentType.bool(), GameRuleTypeVisitor::visitBoolean,
                Codec.BOOL, value -> value ? 1 : 0, false, FeatureFlagSet.of());
    }

    public static void register(BiConsumer<Identifier, GameRule<?>> registry) {
        for (Entry entry : ENTRIES) registry.accept(BrainageMinigames.id(entry.name()), entry.rule());
    }

    /**
     * Whether the match is a UHC-style game: its arena is a UHC region or a Meetup/FinalUHC
     * terrain patch, wherever its players are now (the Nether or the deathmatch arena included).
     */
    public static boolean family(Match match) {
        return match.arena() instanceof UhcArena || match.arena() instanceof NaturalArena;
    }
}
