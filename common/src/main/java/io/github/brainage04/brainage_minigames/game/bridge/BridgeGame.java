package io.github.brainage04.brainage_minigames.game.bridge;

import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColorCollection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.TeamColor;
import org.jspecify.annotations.Nullable;

/**
 * Bridge-style goal games on a {@link MapArena}: each team defends a goal hole on its island and
 * scores by jumping into another team's goal. A goal puts every player back into a cage at their
 * spawn and rebuilds the map; the first team to {@link #goalsToWin} goals wins. Deaths never
 * eliminate: players respawn at their base with their kit refilled. Only blocks placed during the
 * round can be broken, and only inside the map's {@code build} regions outside the goals.
 *
 * <p>Maps mark {@code spawn <team>} inside a glass cage, {@code region cage_<team>} around the cage
 * (cleared when a round starts), {@code region goal_<team>} over the goal hole and {@code region
 * build} where blocks may be placed.
 */
public final class BridgeGame implements Minigame {
    public static final GameSetting CAGE_SECONDS =
            new GameSetting(
                    "cage_seconds",
                    5,
                    0,
                    30,
                    "Seconds players wait in their cages after each goal before the next round");

    /** Hypixel gives the arrow back 3.5 seconds after it was shot. */
    private static final int ARROW_REGEN_TICKS = 70;

    private static final String GOAL_PREFIX = "goal_";
    private static final String CAGE_PREFIX = "cage_";

    /** Blocks the kit hands out in team colour. */
    private static final List<ColorCollection<Item>> COLORED_BLOCKS =
            List.of(Items.DYED_TERRACOTTA, Items.WOOL, Items.STAINED_GLASS, Items.CONCRETE);

    /** What sets the two games apart. */
    public enum Variant {
        /**
         * Hypixel's The Bridge: sword, bow with a regenerating arrow, pickaxe, clay and golden
         * apples that heal fully; a one-block bridge links the islands.
         */
        BRIDGE,
        /**
         * Minemen's Battle Rush: only wool and shears, so fights are fist knockback duels, and
         * nothing links the islands: players rush across with wool.
         */
        BATTLE_RUSH
    }

    private final String id;
    private final String displayName;
    private final Identifier kit;
    private final Variant variant;
    private final GameSetting goalsToWin;
    private final List<GameSetting> settings;
    private final Map<Match, State> states = new HashMap<>();

    public BridgeGame(
            String id,
            String displayName,
            Identifier kit,
            Variant variant,
            int defaultGoals,
            int timeLimitMinutes) {
        this.id = id;
        this.displayName = displayName;
        this.kit = kit;
        this.variant = variant;
        this.goalsToWin =
                new GameSetting("goals_to_win", defaultGoals, 1, 100, "Goals a team needs to win");
        List<GameSetting> all = new ArrayList<>(GameSetting.common(5, timeLimitMinutes, true));
        all.add(goalsToWin);
        all.add(CAGE_SECONDS);
        this.settings = List.copyOf(all);
    }

    /** Per-match state. */
    private static final class State {
        private final Map<UUID, Integer> kills = new HashMap<>();
        private final Map<UUID, Integer> goals = new HashMap<>();

        /** Ticks each player has held a bow without an arrow. */
        private final Map<UUID, Integer> arrowless = new HashMap<>();

        /** Ticks until the cages open; 0 while a round is being played. */
        private int cageTicks;

        private int round = 1;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public List<GameSetting> settings() {
        return settings;
    }

    public GameSetting goalsToWin() {
        return goalsToWin;
    }

    public Variant variant() {
        return variant;
    }

    @Override
    public Identifier defaultKit() {
        return kit;
    }

    @Override
    public GameType playerGameMode() {
        return GameType.SURVIVAL;
    }

    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings) throws MatchException {
        return MapArena.openRandom(server, id, 2);
    }

    /**
     * A map with exactly as many goals as the layout has teams when there is one, else any map with
     * room for them; free-for-all takes the map with the most goals.
     */
    @Override
    public Arena openArena(MinecraftServer server, GameSettings settings, TeamLayout layout)
            throws MatchException {
        ServerLevel level = server.getLevel(ModDimensions.MINIGAMES);
        if (level == null) {
            throw new MatchException("The minigames dimension is unavailable.");
        }
        Map<Identifier, Integer> slots = new HashMap<>();
        for (Identifier map : MapArena.maps(server, id)) {
            slots.put(map, MapArena.teamSlots(server, map));
        }
        int wanted =
                layout.isFreeForAll()
                        ? slots.values().stream().mapToInt(Integer::intValue).max().orElse(2)
                        : layout.teamSizes().size();
        List<Identifier> exact =
                slots.entrySet().stream()
                        .filter(entry -> entry.getValue() == wanted)
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList();
        if (exact.isEmpty()) {
            return MapArena.openRandom(server, id, wanted);
        }
        return MapArena.open(level, exact.get(ThreadLocalRandom.current().nextInt(exact.size())));
    }

    @Override
    public void onStart(Match match) {
        states.put(match, new State());
        openCages(match);
        for (ServerPlayer player : match.alivePlayers()) {
            dress(match, player);
        }
    }

    @Override
    public void onClose(Match match) {
        states.remove(match);
    }

    @Override
    public void tick(Match match) {
        State state = states.get(match);
        if (state == null) {
            return;
        }
        if (state.cageTicks > 0) {
            tickCages(match, state);
            return;
        }
        MapArena arena = (MapArena) match.arena();
        for (ServerPlayer player : match.alivePlayers()) {
            MatchTeam team = match.teamOf(player.getUUID()).orElse(null);
            if (team == null) {
                continue;
            }
            Optional<Integer> goal = goalAt(arena, player);
            if (goal.isPresent()) {
                Optional<MatchTeam> scoredOn = match.teamNumbered(goal.get());
                if (scoredOn.isEmpty() || scoredOn.get() == team) {
                    // Your own goal (or one nobody defends) scores nothing; it just sends you
                    // back to base.
                    match.respawn(player);
                    continue;
                }
                score(match, state, player, team, scoredOn.get());
                return;
            }
            // Nobody gets hungry; health comes back through natural regeneration.
            player.getFoodData().setFoodLevel(20);
            if (variant == Variant.BRIDGE) {
                regenerateArrow(state, player);
                healGoldenApple(player);
            }
        }
    }

    /** The team number of the goal the player is standing in. */
    private static Optional<Integer> goalAt(MapArena arena, ServerPlayer player) {
        for (MapArena.Region goal : arena.regions(GOAL_PREFIX)) {
            if (goal.contains(player.position())) {
                try {
                    return Optional.of(
                            Integer.parseInt(goal.name().substring(GOAL_PREFIX.length())));
                } catch (NumberFormatException exception) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private void score(
            Match match, State state, ServerPlayer scorer, MatchTeam team, MatchTeam scoredOn) {
        team.addScore(1);
        state.goals.merge(scorer.getUUID(), 1, Integer::sum);
        match.broadcast(
                Component.empty()
                        .append(
                                Component.literal(scorer.getScoreboardName())
                                        .withStyle(
                                                style ->
                                                        team.color()
                                                                .map(style::withColor)
                                                                .orElse(style)))
                        .append(Component.literal(" scored in ").withStyle(ChatFormatting.GRAY))
                        .append(scoredOn.displayName())
                        .append(Component.literal("'s goal! ").withStyle(ChatFormatting.GRAY))
                        .append(scoreLine(match)));
        for (ServerPlayer player : match.onlineMembers()) {
            player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
        }
        if (team.score() >= match.settings().get(goalsToWin)) {
            match.finish(List.of(team));
            return;
        }
        state.round++;
        startRound(match, state, scorer, team);
    }

    /** Rebuilds the map and puts everyone back in their cage for the next round. */
    private void startRound(Match match, State state, ServerPlayer scorer, MatchTeam team) {
        ((MapArena) match.arena()).reset();
        int ticks = match.settings().get(CAGE_SECONDS) * 20;
        Component title =
                Component.literal(scorer.getScoreboardName() + " scored!")
                        .withStyle(style -> team.color().map(style::withColor).orElse(style));
        for (ServerPlayer player : match.alivePlayers()) {
            match.respawn(player);
            match.freeze(player, ticks);
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(scoreLine(match)));
        }
        state.arrowless.clear();
        state.cageTicks = ticks;
        if (ticks == 0) {
            openCages(match);
        }
    }

    private void tickCages(Match match, State state) {
        state.cageTicks--;
        if (state.cageTicks == 0) {
            openCages(match);
            for (ServerPlayer player : match.alivePlayers()) {
                unfreeze(player);
            }
            match.broadcastActionBar(Component.literal("Go!").withStyle(ChatFormatting.GREEN));
        } else if (state.cageTicks % 20 == 0) {
            match.broadcastActionBar(
                    Component.literal("Cages open in " + state.cageTicks / 20 + "...")
                            .withStyle(ChatFormatting.GOLD));
            for (ServerPlayer player : match.alivePlayers()) {
                player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
            }
        }
    }

    /** Removes every cage, dropping the players onto their islands. */
    private static void openCages(Match match) {
        MapArena arena = (MapArena) match.arena();
        ServerLevel level = arena.level();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (MapArena.Region cage : arena.regions(CAGE_PREFIX)) {
            AABB box = cage.box();
            for (BlockPos pos :
                    BlockPos.betweenClosed(
                            (int) Math.floor(box.minX),
                            (int) Math.floor(box.minY),
                            (int) Math.floor(box.minZ),
                            (int) Math.ceil(box.maxX) - 1,
                            (int) Math.ceil(box.maxY) - 1,
                            (int) Math.ceil(box.maxZ) - 1)) {
                if (!level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Undoes {@link Match#freeze}, whose effects outlast the cage countdown. */
    private static void unfreeze(ServerPlayer player) {
        player.removeEffect(MobEffects.SLOWNESS);
        player.removeEffect(MobEffects.MINING_FATIGUE);
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.SLOW_FALLING);
    }

    /** Whether the players are waiting in their cages for the next round. */
    public boolean isCaged(Match match) {
        State state = states.get(match);
        return state != null && state.cageTicks > 0;
    }

    public int kills(Match match, UUID playerId) {
        State state = states.get(match);
        return state == null ? 0 : state.kills.getOrDefault(playerId, 0);
    }

    /** Gives back one arrow once a player with a bow has had none for 3.5 seconds. */
    private static void regenerateArrow(State state, ServerPlayer player) {
        Inventory inventory = player.getInventory();
        if (!inventory.contains(stack -> stack.is(Items.BOW))
                || inventory.contains(stack -> stack.is(ItemTags.ARROWS))) {
            state.arrowless.remove(player.getUUID());
            return;
        }
        if (state.arrowless.merge(player.getUUID(), 1, Integer::sum) >= ARROW_REGEN_TICKS) {
            state.arrowless.remove(player.getUUID());
            inventory.add(new ItemStack(Items.ARROW));
        }
    }

    /**
     * Golden apples heal fully, as on Hypixel: vanilla's Regeneration II from eating one is swapped
     * for full health, keeping its Absorption.
     */
    private static void healGoldenApple(ServerPlayer player) {
        if (player.hasEffect(MobEffects.REGENERATION)) {
            player.removeEffect(MobEffects.REGENERATION);
            player.setHealth(player.getMaxHealth());
        }
    }

    @Override
    public DeathResult onDeath(Match match, ServerPlayer victim, @Nullable ServerPlayer killer) {
        State state = states.get(match);
        if (state != null && killer != null && killer != victim) {
            state.kills.merge(killer.getUUID(), 1, Integer::sum);
        }
        return DeathResult.RESPAWN;
    }

    @Override
    public void onRespawn(Match match, ServerPlayer player) {
        dress(match, player);
        State state = states.get(match);
        if (state != null) {
            state.arrowless.remove(player.getUUID());
        }
    }

    /** Only blocks placed during the round can be broken. */
    @Override
    public boolean allowBreak(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        return !isCaged(match) && match.isPlacedBlock(pos);
    }

    /** Inside the map's build regions, never inside a goal and not while caged. */
    @Override
    public boolean allowPlace(Match match, ServerPlayer player, BlockPos pos, BlockState state) {
        if (isCaged(match) || !match.arena().canBuild(pos)) {
            return false;
        }
        return ((MapArena) match.arena())
                .regions(GOAL_PREFIX).stream().noneMatch(goal -> goal.contains(pos));
    }

    @Override
    public boolean allowDamage(Match match, ServerPlayer victim, DamageSource source) {
        return !isCaged(match);
    }

    @Override
    public void addSidebarLines(Match match, List<Component> lines) {
        lines.add(
                MatchSidebar.label(
                        "Goals to win: ", String.valueOf(match.settings().get(goalsToWin))));
        match.mapName().ifPresent(name -> lines.add(MatchSidebar.label("Map: ", name)));
        State state = states.get(match);
        if (state != null) {
            lines.add(MatchSidebar.label("Round: ", String.valueOf(state.round)));
        }
    }

    /** Goals as filled dots out of the target (numbers beyond ten), then the team's kills. */
    @Override
    public Component sidebarTeamSuffix(Match match, MatchTeam team) {
        int target = match.settings().get(goalsToWin);
        MutableComponent suffix = Component.literal(" ");
        if (target <= 10) {
            int scored = Math.min(team.score(), target);
            suffix.append(
                    Component.literal("●".repeat(scored))
                            .withStyle(style -> team.color().map(style::withColor).orElse(style)));
            suffix.append(
                    Component.literal("●".repeat(target - scored))
                            .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            suffix.append(
                    Component.literal(team.score() + "/" + target)
                            .withStyle(ChatFormatting.YELLOW));
        }
        int kills = team.members().stream().mapToInt(member -> kills(match, member)).sum();
        return suffix.append(Component.literal(" " + kills + "⚔").withStyle(ChatFormatting.GRAY));
    }

    private static Component scoreLine(Match match) {
        MutableComponent line = Component.empty();
        for (MatchTeam team : match.teams()) {
            if (!line.getSiblings().isEmpty()) {
                line.append(Component.literal(" - ").withStyle(ChatFormatting.GRAY));
            }
            line.append(
                    Component.literal(String.valueOf(team.score()))
                            .withStyle(style -> team.color().map(style::withColor).orElse(style)));
        }
        return line;
    }

    /**
     * Turns the kit's blocks (clay, wool, glass, concrete) into the team's colour and dyes its
     * leather armour to match.
     */
    private static void dress(Match match, ServerPlayer player) {
        Optional<DyeColor> dye =
                match.teamOf(player.getUUID())
                        .flatMap(team -> team.scoreboardTeam().getColor())
                        .map(BridgeGame::dyeOf);
        if (dye.isEmpty()) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ItemTags.CAULDRON_CAN_REMOVE_DYE)) {
                stack.set(
                        DataComponents.DYED_COLOR,
                        new DyedItemColor(dye.get().getTextureDiffuseColor()));
                continue;
            }
            Item recolored = recolor(stack, dye.get());
            if (recolored != null && !stack.is(recolored)) {
                inventory.setItem(slot, stack.transmuteCopy(recolored));
            }
        }
    }

    /** The team-coloured version of a coloured building block, or null for anything else. */
    private static @Nullable Item recolor(ItemStack stack, DyeColor dye) {
        if (stack.is(Items.TERRACOTTA)) {
            return Items.DYED_TERRACOTTA.pick(dye);
        }
        for (ColorCollection<Item> blocks : COLORED_BLOCKS) {
            if (blocks.asList().contains(stack.getItem())) {
                return blocks.pick(dye);
            }
        }
        return null;
    }

    /** The dye closest to a scoreboard team colour. */
    static DyeColor dyeOf(TeamColor color) {
        return switch (color) {
            case RED -> DyeColor.RED;
            case BLUE -> DyeColor.BLUE;
            case GREEN -> DyeColor.LIME;
            case YELLOW -> DyeColor.YELLOW;
            case AQUA -> DyeColor.LIGHT_BLUE;
            case LIGHT_PURPLE -> DyeColor.MAGENTA;
            case GOLD -> DyeColor.ORANGE;
            case WHITE -> DyeColor.WHITE;
            case DARK_RED -> DyeColor.BROWN;
            case DARK_BLUE -> DyeColor.BLUE;
            case DARK_GREEN -> DyeColor.GREEN;
            case DARK_AQUA -> DyeColor.CYAN;
            case DARK_PURPLE -> DyeColor.PURPLE;
            case GRAY -> DyeColor.LIGHT_GRAY;
            case DARK_GRAY -> DyeColor.GRAY;
            case BLACK -> DyeColor.BLACK;
        };
    }
}
