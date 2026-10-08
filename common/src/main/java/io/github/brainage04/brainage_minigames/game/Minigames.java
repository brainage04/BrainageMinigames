package io.github.brainage04.brainage_minigames.game;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsGame;
import io.github.brainage04.brainage_minigames.game.bedwars.BedWarsMode;
import io.github.brainage04.brainage_minigames.game.bridge.BridgeGame;
import io.github.brainage04.brainage_minigames.game.ctw.CaptureTheWoolGame;
import io.github.brainage04.brainage_minigames.game.duel.DuelGame;
import io.github.brainage04.brainage_minigames.game.duel.DuelGame.Mechanic;
import io.github.brainage04.brainage_minigames.game.pearlfight.PearlFightGame;
import io.github.brainage04.brainage_minigames.game.quake.QuakeGame;
import io.github.brainage04.brainage_minigames.game.race.IceBoatRacingGame;
import io.github.brainage04.brainage_minigames.game.race.ParkourGame;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsGame;
import io.github.brainage04.brainage_minigames.game.skywars.SkyWarsMode;
import io.github.brainage04.brainage_minigames.game.spleef.BowSpleefGame;
import io.github.brainage04.brainage_minigames.game.spleef.SpleefGame;
import io.github.brainage04.brainage_minigames.game.sumo.SumoGame;
import io.github.brainage04.brainage_minigames.game.uhc.FinalUhcGame;
import io.github.brainage04.brainage_minigames.game.uhc.MeetupGame;
import io.github.brainage04.brainage_minigames.game.uhc.UhcGame;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.block.Blocks;

public final class Minigames {
    public static final Minigame UHC = new UhcGame(UhcGame.Variant.UHC);
    public static final Minigame SPEED_UHC = new UhcGame(UhcGame.Variant.SPEED);
    public static final Minigame MINI_UHC = new UhcGame(UhcGame.Variant.MINI);
    public static final Minigame BUILD_UHC =
            new DuelGame(
                    "build_uhc",
                    "BuildUHC",
                    BrainageMinigames.id("kits/build_uhc"),
                    61,
                    Blocks.GRASS_BLOCK,
                    false,
                    Mechanic.BUILD);
    public static final Minigame CLASSIC =
            new DuelGame(
                    "classic",
                    "Classic",
                    BrainageMinigames.id("kits/classic"),
                    41,
                    Blocks.SMOOTH_STONE,
                    true,
                    Mechanic.STANDARD);
    public static final Minigame NO_DEBUFF =
            new DuelGame(
                    "no_debuff",
                    "No Debuff",
                    BrainageMinigames.id("kits/no_debuff"),
                    41,
                    Blocks.SMOOTH_STONE,
                    true,
                    Mechanic.STANDARD);
    public static final Minigame GAPPLE =
            new DuelGame(
                    "gapple",
                    "Gapple",
                    BrainageMinigames.id("kits/gapple"),
                    41,
                    Blocks.SMOOTH_STONE,
                    true,
                    Mechanic.STANDARD);
    public static final Minigame BOXING =
            new DuelGame(
                    "boxing",
                    "Boxing",
                    BrainageMinigames.id("kits/boxing"),
                    31,
                    Blocks.SMOOTH_STONE,
                    true,
                    Mechanic.BOXING);
    public static final Minigame COMBO =
            new DuelGame(
                    "combo",
                    "Combo",
                    BrainageMinigames.id("kits/combo"),
                    41,
                    Blocks.SMOOTH_STONE,
                    true,
                    Mechanic.COMBO);
    public static final Minigame BOW =
            new DuelGame(
                    "bow",
                    "Bow",
                    BrainageMinigames.id("kits/bow"),
                    41,
                    Blocks.SMOOTH_STONE,
                    true,
                    Mechanic.BOW);
    public static final SumoGame SUMO = new SumoGame();

    public static final SkyWarsGame SKYWARS =
            new SkyWarsGame(
                    "skywars",
                    "SkyWars",
                    SkyWarsMode.INSANE,
                    "skywars",
                    false,
                    BrainageMinigames.id("kits/skywars"),
                    Minigame.COMMON_LAYOUTS,
                    9);
    public static final SkyWarsGame SKYWARS_MINI =
            new SkyWarsGame(
                    "skywars_mini",
                    "Mini SkyWars",
                    SkyWarsMode.MINI,
                    "skywars_mini",
                    false,
                    BrainageMinigames.id("kits/skywars_mini"),
                    List.of("1v1v1v1", "1v1", "1v1v1", "2v2", "ffa"),
                    9);
    public static final SkyWarsGame SKYWARS_MEGA =
            new SkyWarsGame(
                    "skywars_mega",
                    "Mega SkyWars",
                    SkyWarsMode.MEGA,
                    "skywars_mega",
                    false,
                    BrainageMinigames.id("kits/skywars_mega"),
                    List.of(TeamLayout.teamsOf(2, 12).toString(), TeamLayout.teamsOf(2, 8).toString(), "2v2v2v2", "2v2",
                            "ffa"),
                    15);
    public static final SkyWarsGame SKYWARS_LUCKY =
            new SkyWarsGame(
                    "skywars_lucky",
                    "Lucky Block SkyWars",
                    SkyWarsMode.INSANE,
                    "skywars",
                    true,
                    BrainageMinigames.id("kits/skywars"),
                    Minigame.COMMON_LAYOUTS,
                    9);
    public static final Minigame MEETUP = new MeetupGame();
    public static final Minigame FINAL_UHC = new FinalUhcGame();
    public static final Minigame SPLEEF = new SpleefGame();
    public static final Minigame BOW_SPLEEF = new BowSpleefGame();
    public static final QuakeGame QUAKE = new QuakeGame();
    public static final PearlFightGame PEARL_FIGHT = new PearlFightGame();
    public static final BridgeGame BRIDGE =
            new BridgeGame(
                    "bridge",
                    "Bridge",
                    BrainageMinigames.id("kits/bridge"),
                    BridgeGame.Variant.BRIDGE,
                    5,
                    15);
    public static final BridgeGame BATTLE_RUSH =
            new BridgeGame(
                    "battle_rush",
                    "Battle Rush",
                    BrainageMinigames.id("kits/battle_rush"),
                    BridgeGame.Variant.BATTLE_RUSH,
                    3,
                    10);
    public static final CaptureTheWoolGame CAPTURE_THE_WOOL = new CaptureTheWoolGame();
    /** Solo (8 teams of 1), Doubles (8 of 2), 3v3v3v3, 4v4v4v4 and 4v4, as Hypixel's queues. */
    public static final List<String> BED_WARS_LAYOUTS = List.of(TeamLayout.teamsOf(1, 8).toString(),
            TeamLayout.teamsOf(2, 8).toString(), "3v3v3v3", "4v4v4v4", "4v4");
    public static final BedWarsGame BED_WARS = new BedWarsGame("bedwars", "Bed Wars", BedWarsMode.CORE, BED_WARS_LAYOUTS);
    /** Doubles and 4v4v4v4, the Dream modes' usual queues. */
    private static final List<String> DREAM_LAYOUTS = List.of(TeamLayout.teamsOf(2, 8).toString(), "4v4v4v4");
    public static final BedWarsGame BED_WARS_CASTLE = new BedWarsGame("bedwars_castle", "Bed Wars Castle", BedWarsMode.CASTLE,
            List.of("40v40", "20v20", "8v8"));
    public static final BedWarsGame BED_WARS_RUSH = new BedWarsGame("bedwars_rush", "Bed Wars Rush", BedWarsMode.RUSH,
            List.of(TeamLayout.teamsOf(1, 8).toString(), TeamLayout.teamsOf(2, 8).toString(), "4v4v4v4"));
    public static final BedWarsGame BED_WARS_ULTIMATE = new BedWarsGame("bedwars_ultimate", "Bed Wars Ultimate", BedWarsMode.ULTIMATE,
            DREAM_LAYOUTS);
    public static final BedWarsGame BED_WARS_ARMED = new BedWarsGame("bedwars_armed", "Bed Wars Armed", BedWarsMode.ARMED, DREAM_LAYOUTS);
    public static final BedWarsGame BED_WARS_LUCKY = new BedWarsGame("bedwars_lucky", "Bed Wars Lucky Blocks", BedWarsMode.LUCKY_BLOCKS,
            DREAM_LAYOUTS);
    public static final BedWarsGame BED_WARS_VOIDLESS = new BedWarsGame("bedwars_voidless", "Bed Wars Voidless", BedWarsMode.VOIDLESS,
            DREAM_LAYOUTS);
    public static final BedWarsGame BED_WARS_SWAPPAGE = new BedWarsGame("bedwars_swappage", "Bed Wars Swappage", BedWarsMode.SWAPPAGE,
            DREAM_LAYOUTS);
    public static final BedWarsGame BED_WARS_ONE_BLOCK = new BedWarsGame("bedwars_one_block", "Bed Wars One Block", BedWarsMode.ONE_BLOCK,
            List.of(TeamLayout.teamsOf(1, 8).toString(), TeamLayout.teamsOf(2, 8).toString()));
    public static final ParkourGame PARKOUR = new ParkourGame();
    public static final IceBoatRacingGame ICE_BOAT_RACING = new IceBoatRacingGame();

    public static final List<Minigame> ALL =
            List.of(
                    UHC,
                    SPEED_UHC,
                    MINI_UHC,
                    BUILD_UHC,
                    CLASSIC,
                    NO_DEBUFF,
                    GAPPLE,
                    BOXING,
                    COMBO,
                    BOW,
                    SUMO,
                    SKYWARS,
                    SKYWARS_MINI,
                    SKYWARS_MEGA,
                    SKYWARS_LUCKY,
                    MEETUP,
                    FINAL_UHC,
                    SPLEEF,
                    BOW_SPLEEF,
                    QUAKE,
                    PEARL_FIGHT,
                    BRIDGE,
                    BATTLE_RUSH,
                    CAPTURE_THE_WOOL,
                    BED_WARS,
                    BED_WARS_CASTLE,
                    BED_WARS_RUSH,
                    BED_WARS_ULTIMATE,
                    BED_WARS_ARMED,
                    BED_WARS_LUCKY,
                    BED_WARS_VOIDLESS,
                    BED_WARS_SWAPPAGE,
                    BED_WARS_ONE_BLOCK,
                    PARKOUR,
                    ICE_BOAT_RACING);

    private Minigames() {}

    public static Optional<Minigame> byId(String id) {
        return ALL.stream().filter(game -> game.id().equals(id)).findFirst();
    }
}
