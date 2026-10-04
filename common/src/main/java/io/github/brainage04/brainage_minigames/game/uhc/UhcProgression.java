package io.github.brainage04.brainage_minigames.game.uhc;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.dimension.ModDimensions;
import io.github.brainage04.brainage_minigames.game.Match;
import io.github.brainage04.brainage_minigames.game.MatchManager;
import io.github.brainage04.brainage_minigames.game.MatchTeam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jspecify.annotations.Nullable;

/** Independent, simultaneous profession trees; purchases never modify a player's saved inventory. */
public final class UhcProgression {
    public static final Identifier STORAGE = BrainageMinigames.id("uhc_progression");
    public static final GameRule<Boolean> MAX_ALL = booleanRule(false);
    public static final GameRule<Boolean> MAX_ALL_KITS = booleanRule(false);
    public static final GameRule<Boolean> CHOOSE_PRESTIGE = booleanRule(false);
    public static final GameRule<Integer> COIN_MULTIPLIER = UhcModeRules.integer(100, 0, 100_000);
    public static final GameRule<Boolean> UNLIMITED_CRAFTS = booleanRule(true);
    public static final GameRule<Boolean> NO_DUPLICATE_CRAFTS = booleanRule(true);

    private static GameRule<Boolean> booleanRule(boolean defaultValue) {
        return new GameRule<>(GameRuleCategory.PLAYER, GameRuleType.BOOL, BoolArgumentType.bool(),
                GameRuleTypeVisitor::visitBoolean, Codec.BOOL, value -> value ? 1 : 0,
                defaultValue, FeatureFlagSet.of());
    }

    public static void register(java.util.function.BiConsumer<Identifier, GameRule<?>> registrar) {
        registrar.accept(BrainageMinigames.id("uhc_max_all_perks"), MAX_ALL);
        registrar.accept(BrainageMinigames.id("uhc_max_all_kits"), MAX_ALL_KITS);
        registrar.accept(BrainageMinigames.id("uhc_choose_prestige_bonus"), CHOOSE_PRESTIGE);
        registrar.accept(BrainageMinigames.id("uhc_coin_multiplier"), COIN_MULTIPLIER);
        registrar.accept(BrainageMinigames.id("uhc_unlimited_crafts"), UNLIMITED_CRAFTS);
        registrar.accept(BrainageMinigames.id("uhc_no_duplicate_crafts"), NO_DUPLICATE_CRAFTS);
    }
    private static final Codec<Map<String, Integer>> PURCHASES = Codec.unboundedMap(Codec.STRING, Codec.INT);
    private static final Map<Match, State> MATCHES = new HashMap<>();
    private static final Map<Tree, List<Node>> NODES = new java.util.EnumMap<>(Tree.class);

    static {
        for (Tree tree : Tree.values()) NODES.put(tree, buildNodes(tree));
    }

    public enum Tree {
        WEAPONSMITH("weaponsmith", 250), ARMORSMITH("armorsmith", 250),
        ALCHEMY("alchemy", 190), SURVIVALISM("survivalism", 125),
        ENGINEERING("engineering", 125), COOKING("cooking", 250),
        ENCHANTING("enchanting", 190), HUNTER("hunter", 500), BLOODCRAFT("bloodcraft", 500),
        TOOLSMITH("toolsmith", 250), APPRENTICE("apprentice", 125),
        INVENTION("invention", 25), STRATEGIST("strategist", 125), EXTRAS("extras", 0);
        public final String id;
        public final int baseCost;
        Tree(String id, int baseCost) { this.id = id; this.baseCost = baseCost; }
        public static @Nullable Tree find(String id) {
            for (Tree tree : values()) if (tree.id.equals(id)) return tree;
            return null;
        }
    }

    public record Node(String id, int cost, List<String> requires, boolean perk) {}

    private static final class State {
        final Map<UUID, Map<String, Integer>> crafts = new HashMap<>();
        final Map<UUID, int[]> levels = new HashMap<>();
        final Set<UUID> nether = new HashSet<>();
        int survivalPeriod;
    }

    private UhcProgression() {}

    /** Historical shop schedule with the released 2017 first-eight-node discount. Unpublished prices remain local policy. */
    public static List<Node> nodes(Tree tree) { return NODES.get(tree); }

    private static List<Node> buildNodes(Tree tree) {
        if (tree == Tree.EXTRAS) return UhcExtraRecipes.nodes();
        int unit = tree.baseCost;
        if (tree == Tree.TOOLSMITH || tree == Tree.APPRENTICE) return List.of(
                new Node("recipe1", unit, List.of(), false),
                new Node("recipe2", unit * 20, List.of("recipe1"), false),
                new Node("recipe3", unit * 20, List.of("recipe1"), false),
                new Node("recipe4", unit * 100, List.of("recipe2", "recipe3"), false),
                new Node("prestige", 50_000, List.of("recipe4"), false));
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node("recipe1", unit / 2, List.of(), false));
        nodes.add(new Node("perk1", cost(tree, 2) / 2, List.of("recipe1"), true));
        for (String branch : List.of("a", "b")) {
            nodes.add(new Node("perk_" + branch + "2", cost(tree, 4) / 2, List.of("perk1"), true));
            nodes.add(new Node("perk_" + branch + "3", cost(tree, 10) / 2, List.of("perk_" + branch + "2"), true));
            String recipe = branch.equals("a") ? "recipe2" : "recipe3";
            nodes.add(new Node(recipe, cost(tree, 20) / 2, List.of("perk_" + branch + "3"), false));
            nodes.add(new Node("perk_" + branch + "4", cost(tree, 40), List.of(recipe), true));
            nodes.add(new Node("perk_" + branch + "5", cost(tree, 60), List.of("perk_" + branch + "4"), true));
        }
        nodes.add(new Node("perk6", cost(tree, 80), List.of("perk_a5", "perk_b5"), true));
        nodes.add(new Node("recipe4", cost(tree, 100), List.of("perk6"), false));
        nodes.add(new Node("prestige", unit == 500 ? 100_000 : unit == 250 ? 50_000 : 37_500,
                List.of("recipe4"), false));
        // Strategist's second-prestige effect is confirmed; its price is a local, unconfirmed policy.
        if (tree == Tree.STRATEGIST) nodes.add(new Node("prestige2", 100_000, List.of("prestige"), false));
        return List.copyOf(nodes);
    }

    private static int cost(Tree tree, int multiplier) {
        return tree.baseCost == 190 ? 250 * multiplier * 3 / 4 : tree.baseCost * multiplier;
    }

    private static CompoundTag profile(MinecraftServer server, UUID id) {
        return server.getCommandStorage().get(STORAGE).getCompound(id.toString()).orElseGet(CompoundTag::new);
    }

    private static void save(MinecraftServer server, UUID id, CompoundTag profile) {
        CompoundTag root = server.getCommandStorage().get(STORAGE);
        root.put(id.toString(), profile);
        server.getCommandStorage().set(STORAGE, root);
    }

    public static long coins(MinecraftServer server, UUID id) { return profile(server, id).getLongOr("coins", 0); }

    public static void award(MinecraftServer server, UUID id, int amount) {
        long scaled = (long) amount * server.getGameRules().get(COIN_MULTIPLIER) / 100;
        if (scaled <= 0) return;
        CompoundTag profile = profile(server, id);
        long coins = profile.getLongOr("coins", 0);
        profile.putLong("coins", coins > Long.MAX_VALUE - scaled ? Long.MAX_VALUE : coins + scaled);
        save(server, id, profile);
    }

    /** All match coin actions pass through the same multiplier. */
    public enum CoinAction {
        SURVIVAL(10), KILL(50), NETHER_ENTRY(15), WIN(150);
        final int coins;
        CoinAction(int coins) { this.coins = coins; }
    }

    public static void award(Match match, UUID id, CoinAction action) {
        award(match.server(), id, action.coins);
    }

    public static Map<String, Integer> purchases(MinecraftServer server, UUID id) {
        return profile(server, id).read("purchases", PURCHASES).orElse(Map.of());
    }

    public static boolean bought(MinecraftServer server, UUID id, Tree tree, String node) {
        return purchases(server, id).containsKey(tree.id + "/" + node);
    }

    /** Checks prerequisites and balance before either mutation; repeated purchases cannot spend coins. */
    public static @Nullable String unlock(MinecraftServer server, UUID id, Tree tree, String nodeId) {
        return purchase(server, id, tree.id, nodes(tree).stream().filter(n -> n.id.equals(nodeId)).findFirst().orElse(null));
    }

    public static @Nullable String purchase(MinecraftServer server, UUID id, String section, @Nullable Node node) {
        if (node == null) return "Unknown shop node.";
        CompoundTag profile = profile(server, id);
        Map<String, Integer> owned = new HashMap<>(profile.read("purchases", PURCHASES).orElse(Map.of()));
        String key = section + "/" + node.id;
        if (owned.containsKey(key)) return "Already unlocked.";
        for (String prerequisite : node.requires) {
            String required = prerequisite.contains("/") ? prerequisite : section + "/" + prerequisite;
            if (!owned.containsKey(required)) return "Unlock " + prerequisite + " first.";
        }
        long coins = profile.getLongOr("coins", 0);
        if (coins < node.cost) return "Not enough coins: need " + node.cost + ", have " + coins + ".";
        owned.put(key, 1);
        profile.store("purchases", PURCHASES, owned);
        profile.putLong("coins", coins - node.cost);
        save(server, id, profile);
        Match match = MatchManager.matchOf(id).orElse(null);
        if (match != null && MATCHES.containsKey(match)) MATCHES.get(match).levels.remove(id);
        return null;
    }

    public static String selectedKit(MinecraftServer server, UUID id) {
        String kit = profile(server, id).getStringOr("kit", "stone");
        return kit.isEmpty() ? "stone" : kit;
    }
    public static void selectKit(MinecraftServer server, UUID id, String kit) {
        CompoundTag profile = profile(server, id); profile.putString("kit", kit); save(server, id, profile);
    }

    public static int prestigeChoice(MinecraftServer server, UUID id, UhcKits.Kit kit) {
        return profile(server, id).getIntOr("prestige_" + kit.id, 0);
    }

    public static void selectPrestige(MinecraftServer server, UUID id, UhcKits.Kit kit, int choice) {
        CompoundTag profile = profile(server, id);
        profile.putInt("prestige_" + kit.id, choice);
        save(server, id, profile);
    }

    public static @Nullable Match match(ServerPlayer player) {
        Match match = MatchManager.matchOf(player.getUUID()).orElse(null);
        return match != null && match.game() instanceof UhcGame && match.isActiveParticipant(player.getUUID())
                ? match : null;
    }

    public static boolean maxed(ServerPlayer player) {
        return match(player) != null && player.level().getGameRules().get(MAX_ALL);
    }

    public static int level(ServerPlayer player, Tree tree) {
        Match match = match(player);
        if (match == null || tree == Tree.TOOLSMITH || tree == Tree.APPRENTICE) return 0;
        if (player.level().getGameRules().get(MAX_ALL)) return 10;
        int[] levels = state(match).levels.computeIfAbsent(player.getUUID(), id -> {
            Map<String, Integer> owned = purchases(player.level().getServer(), id);
            int[] counts = new int[NODES.size()];
            for (var entry : NODES.entrySet()) for (Node node : entry.getValue()) if (node.perk && owned.containsKey(entry.getKey().id + "/" + node.id)) counts[entry.getKey().ordinal()]++;
            return counts;
        });
        return levels[tree.ordinal()];
    }

    public static boolean canCraft(ServerPlayer player, Tree tree, int recipe, String id) {
        Match match = match(player);
        if (match == null) return false;
        if (tree == Tree.STRATEGIST && !UhcAdvancedRecipes.strategistEligible(player, recipe)) return false;
        MinecraftServer server = player.level().getServer();
        if (!maxed(player) && !bought(server, player.getUUID(), tree, tree == Tree.EXTRAS ? id : "recipe" + recipe)) return false;
        if (player.level().getGameRules().get(UNLIMITED_CRAFTS)) return true;
        boolean prestige = maxed(player) || bought(server, player.getUUID(), tree, "prestige");
        int limit = tree == Tree.EXTRAS ? 1 : (recipe == 4 ? 1 : 3) + (prestige ? 1 : 0);
        return state(match).crafts.getOrDefault(player.getUUID(), Map.of()).getOrDefault(id, 0) < limit;
    }

    public static void crafted(ServerPlayer player, String id) {
        Match match = match(player);
        if (match != null) state(match).crafts.computeIfAbsent(player.getUUID(), key -> new HashMap<>()).merge(id, 1, Integer::sum);
    }

    private static State state(Match match) { return MATCHES.computeIfAbsent(match, key -> new State()); }

    public static void start(Match match) {
        MATCHES.put(match, new State());
        UhcExtraRecipes.start(match);
        for (ServerPlayer player : match.alivePlayers()) {
            int vitamins = level(player, Tree.COOKING);
            if (vitamins > 0) player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, vitamins * 60 * 20, 1));
        }
    }

    public static void tick(Match match) {
        State state = state(match);
        int period = match.activeTicks() / (5 * 60 * 20);
        if (period > state.survivalPeriod) {
            state.survivalPeriod = period;
            for (ServerPlayer player : match.alivePlayers()) award(match, player.getUUID(), CoinAction.SURVIVAL);
        }
        for (ServerPlayer player : match.alivePlayers()) {
            if (player.level().dimension().equals(ModDimensions.UHC_NETHER) && state.nether.add(player.getUUID())) {
                award(match, player.getUUID(), CoinAction.NETHER_ENTRY);
            }
        }
        UhcAdvancedRecipes.tick(match);
        UhcExtraRecipes.tick(match);
        UhcCraftPrompts.tick(match);
    }

    public static void killed(Match match, ServerPlayer victim, @Nullable ServerPlayer killer) {
        ItemStack head = UhcCrafting.playerHead(victim);
        victim.level().addFreshEntity(new ItemEntity(victim.level(), victim.getX(), victim.getY(), victim.getZ(), head));
        if (killer == null || killer == victim || !match.isActiveParticipant(killer.getUUID())) return;
        MatchTeam team = match.teamOf(killer.getUUID()).orElseThrow();
        if (match.teamOf(victim.getUUID()).orElse(null) == team) return;
        team.addScore(1);
        int nuggets = 0;
        for (ServerPlayer teammate : match.alivePlayers()) {
            if (!team.members().contains(teammate.getUUID())) continue;
            nuggets += level(teammate, Tree.HUNTER);
            if (teammate == killer || teammate.level() == killer.level() && teammate.distanceToSqr(killer) <= 200 * 200) {
                award(match, teammate.getUUID(), CoinAction.KILL);
            }
        }
        if (nuggets > 0) victim.level().addFreshEntity(new ItemEntity(victim.level(), victim.getX(), victim.getY(), victim.getZ(), new ItemStack(Items.GOLD_NUGGET, nuggets)));
        int berserk = level(killer, Tree.WEAPONSMITH);
        int tenacity = level(killer, Tree.ARMORSMITH);
        if (berserk > 0) killer.addEffect(new MobEffectInstance(MobEffects.STRENGTH, berserk * 10, 0));
        if (tenacity > 0) killer.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, tenacity * 20, 0));
        UhcExtraRecipes.killed(killer);
    }

    public static void won(Match match, List<MatchTeam> winners) {
        if (winners.size() == 1) for (UUID id : winners.getFirst().members()) {
            if (match.involves(id)) award(match, id, CoinAction.WIN);
        }
    }

    public static void close(Match match) {
        UhcCraftPrompts.close(match);
        MATCHES.remove(match);
        UhcAdvancedRecipes.close(match);
        UhcExtraRecipes.close(match);
    }
}
