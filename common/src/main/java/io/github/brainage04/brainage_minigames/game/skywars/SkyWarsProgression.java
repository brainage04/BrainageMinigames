package io.github.brainage04.brainage_minigames.game.skywars;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.game.MatchException;
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
import org.jspecify.annotations.Nullable;

/**
 * Who owns which SkyWars kits and perks, which kit each player picked and which perks they turned
 * off or put into perk slots, saved per UUID and mode. With {@link #MAX_ALL_KITS} or {@link
 * #MAX_ALL_PERKS} on (the default) every kit or every perk, with every upgrade, counts as owned;
 * off, a player owns the mode's default kit and whatever an operator granted them, and perks have
 * their base numbers. Global perks are always owned and active.
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
        return kit.id().equals(kit.mode().defaultKit) || maxKits(server) || list(server, id, "owned").contains(key(kit));
    }

    public static boolean owns(MinecraftServer server, UUID id, SkyWarsPerk perk) {
        return perk.global() || maxPerks(server) || list(server, id, "owned").contains(key(perk));
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

    /** The saved kit choice, or the mode's default kit when none was saved or it is no longer owned. */
    public static SkyWarsKit selectedKit(MinecraftServer server, UUID id, SkyWarsMode mode) {
        SkyWarsKit kit = SkyWarsKits.find(mode, profile(server, id).getStringOr("kit_" + mode.id, mode.defaultKit));
        return kit != null && owns(server, id, kit) ? kit : SkyWarsKits.find(mode, mode.defaultKit);
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
     * drawback, which start off. For modes that toggle perks; ownership is separate, see {@link
     * #active}.
     */
    public static boolean enabled(MinecraftServer server, UUID id, SkyWarsPerk perk) {
        List<String> toggled = list(server, id, "toggled");
        return perk.enabledByDefault() != toggled.contains(key(perk));
    }

    public static void setEnabled(MinecraftServer server, UUID id, SkyWarsPerk perk, boolean enabled) {
        setListed(server, id, "toggled", key(perk), enabled != perk.enabledByDefault());
    }

    /**
     * The perk in each perk slot the menu shows ({@link SkyWarsMode#perkSlots}), null for an empty
     * slot: the saved slots, or the mode's default perks in order for a player who saved none.
     */
    public static List<@Nullable SkyWarsPerk> slots(MinecraftServer server, UUID id, SkyWarsMode mode) {
        List<@Nullable SkyWarsPerk> slots = new ArrayList<>();
        CompoundTag profile = profile(server, id);
        String key = "slots_" + mode.id;
        if (profile.contains(key)) {
            for (String perk : profile.read(key, STRINGS).orElse(List.of())) {
                slots.add(perk.isEmpty() ? null : SkyWarsPerk.find(mode, perk));
            }
        } else {
            for (SkyWarsPerk perk : SkyWarsPerk.choosable(mode)) {
                if (perk.enabledByDefault()) slots.add(perk);
            }
        }
        while (slots.size() < mode.perkSlots) slots.add(null);
        return slots.subList(0, mode.perkSlots);
    }

    /**
     * Puts {@code perk} into the zero-based {@code slot}, or empties the slot for null; a perk
     * already in another slot moves. Refuses slots the player may not use and perks they do not
     * own.
     */
    public static void setSlot(MinecraftServer server, UUID id, SkyWarsMode mode, int slot, @Nullable SkyWarsPerk perk)
            throws MatchException {
        if (mode.perkChoice != SkyWarsMode.PerkChoice.SLOTS) {
            throw new MatchException("This mode does not allow the use of perk slots!");
        }
        if (slot < 0 || slot >= mode.usableSlots(maxPerks(server))) {
            throw new MatchException("Perk slot #" + (slot + 1) + " is locked.");
        }
        if (perk != null && (perk.global() || perk.mode() != mode || !owns(server, id, perk))) {
            throw new MatchException("You have not unlocked " + perk.name() + ".");
        }
        List<@Nullable SkyWarsPerk> slots = new ArrayList<>(slots(server, id, mode));
        if (perk != null) slots.replaceAll(existing -> existing == perk ? null : existing);
        slots.set(slot, perk);
        CompoundTag profile = profile(server, id);
        profile.store("slots_" + mode.id, STRINGS, slots.stream().map(entry -> entry == null ? "" : entry.id()).toList());
        save(server, id, profile);
    }

    /**
     * Whether the perk takes effect for the player: global perks always; otherwise owned and, as
     * the mode chooses perks, toggled on or in one of the slots the player may use.
     */
    public static boolean active(MinecraftServer server, UUID id, SkyWarsPerk perk) {
        if (perk.global()) return true;
        if (!owns(server, id, perk)) return false;
        SkyWarsMode mode = perk.mode();
        if (mode.perkChoice == SkyWarsMode.PerkChoice.TOGGLE) return enabled(server, id, perk);
        List<@Nullable SkyWarsPerk> slots = slots(server, id, mode);
        return slots.subList(0, Math.min(slots.size(), mode.usableSlots(maxPerks(server)))).contains(perk);
    }
}
