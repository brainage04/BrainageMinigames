package io.github.brainage04.brainage_minigames.game.skywars;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;

/**
 * Who owns which SkyWars kits and perks, which kit each player picked and which perks they turned
 * off, saved per UUID and mode. With {@link #MAX_ALL_KITS} or {@link #MAX_ALL_PERKS} on (the
 * default) every kit or every perk, with every upgrade, counts as owned; off, a player owns the
 * Default kit and whatever an operator granted them, and perks have their base numbers.
 */
public final class SkyWarsProgression {
    public static final Identifier STORAGE = BrainageMinigames.id("skywars_progression");
    public static final GameRule<Boolean> MAX_ALL_KITS = booleanRule(true);
    public static final GameRule<Boolean> MAX_ALL_PERKS = booleanRule(true);

    private static final Codec<List<String>> STRINGS = Codec.STRING.listOf();

    private SkyWarsProgression() {}

    private static GameRule<Boolean> booleanRule(boolean defaultValue) {
        return new GameRule<>(GameRuleCategory.PLAYER, GameRuleType.BOOL, BoolArgumentType.bool(),
                GameRuleTypeVisitor::visitBoolean, Codec.BOOL, value -> value ? 1 : 0,
                defaultValue, FeatureFlagSet.of());
    }

    public static void register(BiConsumer<Identifier, GameRule<?>> registrar) {
        registrar.accept(BrainageMinigames.id("skywars_max_all_kits"), MAX_ALL_KITS);
        registrar.accept(BrainageMinigames.id("skywars_max_all_perks"), MAX_ALL_PERKS);
    }

    public static boolean maxKits(MinecraftServer server) {
        return server.getGameRules().get(MAX_ALL_KITS);
    }

    public static boolean maxPerks(MinecraftServer server) {
        return server.getGameRules().get(MAX_ALL_PERKS);
    }

    private static CompoundTag profile(MinecraftServer server, UUID id) {
        return server.getCommandStorage().get(STORAGE).getCompound(id.toString()).orElseGet(CompoundTag::new);
    }

    private static void save(MinecraftServer server, UUID id, CompoundTag profile) {
        CompoundTag root = server.getCommandStorage().get(STORAGE);
        root.put(id.toString(), profile);
        server.getCommandStorage().set(STORAGE, root);
    }

    private static List<String> list(MinecraftServer server, UUID id, String key) {
        return profile(server, id).read(key, STRINGS).orElse(List.of());
    }

    private static void setListed(MinecraftServer server, UUID id, String key, String entry, boolean listed) {
        CompoundTag profile = profile(server, id);
        List<String> entries = new ArrayList<>(profile.read(key, STRINGS).orElse(List.of()));
        entries.remove(entry);
        if (listed) {
            entries.add(entry);
        }
        profile.store(key, STRINGS, entries);
        save(server, id, profile);
    }

    private static String key(SkyWarsKit kit) {
        return kit.mode().id + "/kit/" + kit.id();
    }

    private static String key(SkyWarsPerk perk) {
        return perk.mode().id + "/perk/" + perk.id();
    }

    public static boolean owns(MinecraftServer server, UUID id, SkyWarsKit kit) {
        return kit.id().equals(SkyWarsKits.DEFAULT) || maxKits(server) || list(server, id, "owned").contains(key(kit));
    }

    public static boolean owns(MinecraftServer server, UUID id, SkyWarsPerk perk) {
        return maxPerks(server) || list(server, id, "owned").contains(key(perk));
    }

    /** Grants or takes away a kit or perk independently of the max rules; operators use this. */
    public static void setOwned(MinecraftServer server, UUID id, String entry, boolean owned) {
        setListed(server, id, "owned", entry, owned);
    }

    public static String entry(SkyWarsKit kit) {
        return key(kit);
    }

    public static String entry(SkyWarsPerk perk) {
        return key(perk);
    }

    /** The saved kit choice, or Default when none was saved or it is no longer owned. */
    public static SkyWarsKit selectedKit(MinecraftServer server, UUID id, SkyWarsMode mode) {
        SkyWarsKit kit = SkyWarsKits.find(mode, profile(server, id).getStringOr("kit_" + mode.id, SkyWarsKits.DEFAULT));
        return kit != null && owns(server, id, kit) ? kit : SkyWarsKits.find(mode, SkyWarsKits.DEFAULT);
    }

    public static boolean hasSelection(MinecraftServer server, UUID id, SkyWarsMode mode) {
        return profile(server, id).contains("kit_" + mode.id);
    }

    public static void selectKit(MinecraftServer server, UUID id, SkyWarsKit kit) {
        CompoundTag profile = profile(server, id);
        profile.putString("kit_" + kit.mode().id, kit.id());
        save(server, id, profile);
    }

    /**
     * Whether the player's toggle for the perk is on: perks start on, except those with a
     * drawback, which start off. Ownership is separate; see {@link #active}.
     */
    public static boolean enabled(MinecraftServer server, UUID id, SkyWarsPerk perk) {
        List<String> toggled = list(server, id, "toggled");
        return perk.enabledByDefault() != toggled.contains(key(perk));
    }

    public static void setEnabled(MinecraftServer server, UUID id, SkyWarsPerk perk, boolean enabled) {
        setListed(server, id, "toggled", key(perk), enabled != perk.enabledByDefault());
    }

    /** Owned and toggled on. */
    public static boolean active(MinecraftServer server, UUID id, SkyWarsPerk perk) {
        return owns(server, id, perk) && enabled(server, id, perk);
    }
}
