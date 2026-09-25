package io.github.brainage04.brainage_minigames.game.pearlfight;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.GameSetting;
import io.github.brainage04.brainage_minigames.game.GameSettings;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchException;
import io.github.brainage04.brainage_minigames.game.MatchSidebar;
import io.github.brainage04.brainage_minigames.game.MatchTeam;
import io.github.brainage04.brainage_minigames.game.Minigame;
import io.github.brainage04.brainage_minigames.game.TeamLayout;
import io.github.brainage04.brainage_minigames.game.arena.Arena;
import io.github.brainage04.brainage_minigames.game.arena.MapArena;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Minemen Club's Pearl Fight: players on floating platforms over the void with a knockback stick,
 * ender pearls, wool and shears. Nobody takes damage; knocking an opponent into the void scores a
 * point, and the first team to the target wins. In a two-team match every point starts a new round:
 * the map is reset and everyone goes back to their spawn with a fresh kit.
 */
public final class PearlFightGame implements Minigame {
    public static final String ID = "pearl_fight";

    public static final GameSetting POINTS_TO_WIN =
            new GameSetting("points_to_win", 3, 1, 100, "Void kills a team needs to win");
    public static final GameSetting ROUND_FREEZE_TICKS =
            new GameSetting(
                    "round_freeze_ticks",
                    60,
                    0,
                    200,
                    "Ticks players are frozen at their spawns after each point in a two-team match");

    private static final List<GameSetting> SETTINGS;

    static {
        List<GameSetting> all = new ArrayList<>(GameSetting.common(3, 10, false));
        all.add(POINTS_TO_WIN);
        all.add(ROUND_FREEZE_TICKS);
        SETTINGS = List.copyOf(all);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Pearl Fight";
    }

    @Override
    public List<GameSetting> settings() {
        return SETTINGS;
    }

    @Override
    public Identifier defaultKit() {
        return BrainageMinigames.id("kits/pearl_fight");
    }

    @Override
    public GameType playerGameMode() {
        return GameType.SURVIVAL;
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings) throws MatchException {
        return MapArena.openRandom(server, ID);
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings, TeamLayout layout)
            throws MatchException {
        return MapArena.openRandom(
                server, ID, layout.isFreeForAll() ? 2 : layout.teamSizes().size());
    }

    @Override
    public void onStart(Match match) {
        for (ServerPlayer player : match.alivePlayers()) {
            equip(match, player);
        }
    }

    @Override
    public void onRespawn(Match match, ServerPlayer player) {
        equip(match, player);
        if (isRounds(match)) {
            match.freeze(player, match.settings().get(ROUND_FREEZE_TICKS));
        }
    }

    /**
     * Resistance V cancels every hit's damage but keeps its knockback; wool takes the team colour.
     */
    private static void equip(Match match, ServerPlayer player) {
        player.addEffect(
                new MobEffectInstance(
                        MobEffects.RESISTANCE,
                        MobEffectInstance.INFINITE_DURATION,
                        4,
                        false,
                        false));
        player.addEffect(
                new MobEffectInstance(
                        MobEffects.SATURATION,
                        MobEffectInstance.INFINITE_DURATION,
                        0,
                        false,
                        false));
        Item white = Items.WOOL.white();
        Item wool = match.teamOf(player.getUUID()).map(PearlFightGame::woolOf).orElse(white);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(white) && wool != white) {
                inventory.setItem(slot, new ItemStack(wool, stack.getCount()));
            }
        }
    }

    /** The wool closest to the team's colour. */
    public static Item woolOf(MatchTeam team) {
        DyeColor dye =
                team.color()
                        .map(
                                color ->
                                        switch (color.serialize()) {
                                            case "red", "dark_red" -> DyeColor.RED;
                                            case "blue", "dark_blue" -> DyeColor.BLUE;
                                            case "green" -> DyeColor.LIME;
                                            case "dark_green" -> DyeColor.GREEN;
                                            case "yellow" -> DyeColor.YELLOW;
                                            case "aqua" -> DyeColor.LIGHT_BLUE;
                                            case "dark_aqua" -> DyeColor.CYAN;
                                            case "light_purple" -> DyeColor.MAGENTA;
                                            case "dark_purple" -> DyeColor.PURPLE;
                                            case "gold" -> DyeColor.ORANGE;
                                            case "gray" -> DyeColor.LIGHT_GRAY;
                                            case "dark_gray" -> DyeColor.GRAY;
                                            case "black" -> DyeColor.BLACK;
                                            default -> DyeColor.WHITE;
                                        })
                        .orElse(DyeColor.WHITE);
        return Items.WOOL.pick(dye);
    }

    /** Two-team matches are played in rounds; bigger ones only respawn whoever fell. */
    private static boolean isRounds(Match match) {
        return match.teams().size() == 2;
    }

    /** Hits land (for their knockback and the kill credit) but fall damage never does. */
    @Override
    public boolean allowDamage(Match match, ServerPlayer victim, DamageSource source) {
        return source.getEntity() instanceof ServerPlayer;
    }

    /** Only wool placed during the match can be broken. */
    @Override
    public boolean allowBreak(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        return match.isPlacedBlock(pos);
    }

    @Override
    public boolean allowPlace(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        return state.is(BlockTags.WOOL) && match.arena().canBuild(pos);
    }

    @Override
    public DeathResult onDeath(Match match, ServerPlayer victim, @Nullable ServerPlayer killer) {
        Optional<MatchTeam> victimTeam = match.teamOf(victim.getUUID());
        MatchTeam scorer = null;
        if (killer != null && killer != victim) {
            Optional<MatchTeam> killerTeam = match.teamOf(killer.getUUID());
            if (killerTeam.isPresent() && !killerTeam.equals(victimTeam)) {
                scorer = killerTeam.get();
            }
        }
        if (scorer == null && isRounds(match)) {
            // Falling off on your own still gives the other team the point.
            scorer =
                    match.teams().stream()
                            .filter(team -> victimTeam.map(own -> own != team).orElse(true))
                            .findFirst()
                            .orElse(null);
        }
        if (scorer == null) {
            return DeathResult.RESPAWN;
        }
        scorer.addScore(1);
        MutableComponent message =
                Component.empty()
                        .append(scorer.displayName())
                        .append(" scored! ")
                        .withStyle(ChatFormatting.GOLD);
        message.append(scores(match));
        match.broadcast(message);

        if (isRounds(match) && scorer.score() < match.settings().get(POINTS_TO_WIN)) {
            newRound(match, victim);
        }
        return DeathResult.RESPAWN;
    }

    /**
     * Resets the map and sends everyone but {@code victim} back to their spawn; the match respawns
     * the victim itself once this returns.
     */
    private static void newRound(Match match, ServerPlayer victim) {
        if (match.arena() instanceof MapArena map) {
            map.reset();
        }
        for (ServerPlayer player : match.alivePlayers()) {
            if (player != victim) {
                match.respawn(player);
            }
        }
    }

    private static Component scores(Match match) {
        MutableComponent scores = Component.empty();
        for (MatchTeam team : match.teams()) {
            if (!scores.getSiblings().isEmpty()) {
                scores.append(Component.literal(" - ").withStyle(ChatFormatting.GRAY));
            }
            scores.append(team.displayName()).append(" " + team.score());
        }
        return scores;
    }

    @Override
    public void tick(Match match) {
        int target = match.settings().get(POINTS_TO_WIN);
        match.teams().stream()
                .filter(team -> team.score() >= target)
                .findFirst()
                .ifPresent(team -> match.finish(List.of(team)));
    }

    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        lines.add(
                MatchSidebar.label("First to: ", match.settings().get(POINTS_TO_WIN) + " points"));
    }

    @Override
    public Component sidebarTeamSuffix(Match match, MatchTeam team) {
        return Component.literal(" " + team.score() + "/" + match.settings().get(POINTS_TO_WIN))
                .withStyle(ChatFormatting.YELLOW);
    }
}
