package io.github.brainage04.brainage_minigames.game.uhc;

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
 * Who owns which Speed UHC kits, perks and Masteries, the kit and Mastery each player picked and
 * the perks they turned off, saved per UUID. With a max-all rule on (the default) every kit, every
 * perk at its top tier, or every Mastery counts as owned; off, a player owns Default and Wild
 * Specialist plus whatever an operator granted, and granted perks are at tier I.
 */
public final class SpeedUhcProgression {
    public static final Identifier STORAGE = BrainageMinigames.id("speed_uhc_progression");
    public static final GameRule<Boolean> MAX_ALL_KITS = booleanRule(true);
    public static final GameRule<Boolean> MAX_ALL_PERKS = booleanRule(true);
    public static final GameRule<Boolean> MAX_ALL_MASTERIES = booleanRule(true);

    private static final Codec<List<String>> STRINGS = Codec.STRING.listOf();

    private SpeedUhcProgression() {}

    private static GameRule<Boolean> booleanRule(boolean defaultValue) {
        return new GameRule<>(GameRuleCategory.PLAYER, GameRuleType.BOOL, BoolArgumentType.bool(),
                GameRuleTypeVisitor::visitBoolean, Codec.BOOL, value -> value ? 1 : 0,
                defaultValue, FeatureFlagSet.of());
    }

    public static void register(BiConsumer<Identifier, GameRule<?>> registrar) {
        registrar.accept(BrainageMinigames.id("speed_uhc_max_all_kits"), MAX_ALL_KITS);
        registrar.accept(BrainageMinigames.id("speed_uhc_max_all_perks"), MAX_ALL_PERKS);
        registrar.accept(BrainageMinigames.id("speed_uhc_max_all_masteries"), MAX_ALL_MASTERIES);
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
        if (listed) entries.add(entry);
        profile.store(key, STRINGS, entries);
        save(server, id, profile);
    }

    /** The grant entry of a kit, perk or Mastery: {@code kit/<id>}, {@code perk/<id>} or {@code mastery/<id>}. */
    public static String entry(SpeedUhcKit kit) {
        return "kit/" + kit.id();
    }

    public static String entry(SpeedUhcPerk perk) {
        return "perk/" + perk.id;
    }

    public static String entry(SpeedUhcMastery mastery) {
        return "mastery/" + mastery.id;
    }

    public static boolean owns(MinecraftServer server, UUID id, SpeedUhcKit kit) {
        return kit.isDefault() || server.getGameRules().get(MAX_ALL_KITS) || list(server, id, "owned").contains(entry(kit));
    }

    public static boolean owns(MinecraftServer server, UUID id, SpeedUhcPerk perk) {
        return maxPerks(server) || list(server, id, "owned").contains(entry(perk));
    }

    public static boolean owns(MinecraftServer server, UUID id, SpeedUhcMastery mastery) {
        return mastery == SpeedUhcMastery.WILD_SPECIALIST || server.getGameRules().get(MAX_ALL_MASTERIES)
                || list(server, id, "owned").contains(entry(mastery));
    }

    /** Grants or takes away a kit, perk or Mastery independently of the max rules; operators use this. */
    public static void setOwned(MinecraftServer server, UUID id, String entry, boolean owned) {
        setListed(server, id, "owned", entry, owned);
    }

    public static boolean hasSelection(MinecraftServer server, UUID id) {
        return profile(server, id).contains("kit");
    }

    /** The saved kit, or Default when none was saved or it is no longer owned. */
    public static SpeedUhcKit selectedKit(MinecraftServer server, UUID id) {
        SpeedUhcKit kit = SpeedUhcKit.find(profile(server, id).getStringOr("kit", SpeedUhcKit.DEFAULT));
        return kit != null && owns(server, id, kit) ? kit : SpeedUhcKit.ALL.getFirst();
    }

    public static void selectKit(MinecraftServer server, UUID id, SpeedUhcKit kit) {
        CompoundTag profile = profile(server, id);
        profile.putString("kit", kit.id());
        save(server, id, profile);
    }

    public static boolean hasMastery(MinecraftServer server, UUID id) {
        return profile(server, id).contains("mastery");
    }

    /** The one active Mastery: the saved one while owned, otherwise Wild Specialist. */
    public static SpeedUhcMastery mastery(MinecraftServer server, UUID id) {
        SpeedUhcMastery mastery = SpeedUhcMastery.find(profile(server, id).getStringOr("mastery", ""));
        return mastery != null && owns(server, id, mastery) ? mastery : SpeedUhcMastery.WILD_SPECIALIST;
    }

    public static void selectMastery(MinecraftServer server, UUID id, SpeedUhcMastery mastery) {
        CompoundTag profile = profile(server, id);
        profile.putString("mastery", mastery.id);
        save(server, id, profile);
    }

    /** Whether the player's toggle for the perk is on; every perk starts on. */
    public static boolean enabled(MinecraftServer server, UUID id, SpeedUhcPerk perk) {
        return !list(server, id, "disabled").contains(perk.id);
    }

    public static void setEnabled(MinecraftServer server, UUID id, SpeedUhcPerk perk, boolean enabled) {
        setListed(server, id, "disabled", perk.id, !enabled);
    }

    /** Owned and toggled on: the perks the player takes into a match. */
    public static boolean active(MinecraftServer server, UUID id, SpeedUhcPerk perk) {
        return owns(server, id, perk) && enabled(server, id, perk);
    }
}
