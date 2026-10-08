package io.github.brainage04.brainage_minigames.storage;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.util.LootUtils;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Kits are loot tables bundled with the mod (or added by datapacks), optionally overridden per
 * world by kits edited in game. Bundled kits have a display name and a one-line description of
 * what they give.
 */
public final class KitStorage {
    public static final Identifier EMPTY_KIT = BrainageMinigames.id("empty");

    private static final Identifier STORAGE_ID = BrainageMinigames.id("kits");
    private static final String ITEMS_KEY = "items";
    private static final int EDITOR_SIZE = 6 * 9;

    /**
     * A bundled kit's name and contents; {@code general} kits are offered for every game, the others
     * only for the game they belong to.
     */
    private record Bundled(String name, String description, boolean general) {}

    private static final Map<Identifier, Bundled> BUNDLED = bundled();

    private static Map<Identifier, Bundled> bundled() {
        Map<Identifier, Bundled> kits = new LinkedHashMap<>();
        kits.put(EMPTY_KIT, new Bundled("Empty", "Nothing: everyone starts empty-handed.", true));
        general(kits, "barebones", "Barebones",
                "Iron armour, iron sword and axe, a shield, golden apples and steak.");
        general(kits, "battle_rush", "Battle Rush", "A stack of white wool and shears.");
        general(kits, "bow", "Bow", "An Infinity bow, leather armour, golden apples and steak.");
        general(kits, "boxing", "Boxing", "A Sharpness I diamond sword.");
        general(kits, "bridge", "Bridge",
                "Iron sword, bow, diamond pickaxe, 128 terracotta, 8 golden apples and leather armour.");
        general(kits, "build_uhc", "BuildUHC",
                "Enchanted diamond armour, sword and pickaxe, a Power bow, rod, golden apples, cobblestone, water and lava.");
        general(kits, "classic", "Classic",
                "Iron armour, iron sword and axe, a bow, rod, shield, golden apples and steak.");
        general(kits, "combo", "Combo",
                "Protection II diamond armour, a Sharpness II diamond sword and 8 golden apples.");
        general(kits, "final_uhc", "FinalUHC",
                "Protection II diamond armour, a Sharpness III sword, 16 golden apples, buckets, blocks and diamond tools.");
        general(kits, "gapple", "Gapple",
                "Protection IV diamond armour, a Sharpness III sword, 64 golden apples, Strength and Speed potions.");
        general(kits, "instant_crossbow", "Instant Crossbow",
                "A Quick Charge Multishot crossbow, 512 arrows, iron armour and golden apples.");
        general(kits, "instant_firework_crossbow", "Instant Firework Crossbow",
                "A Quick Charge Multishot crossbow, 512 fireworks, iron armour and golden apples.");
        general(kits, "meetup", "Meetup",
                "A random diamond, iron or mixed loadout with a rod, buckets, blocks and diamond tools.");
        general(kits, "no_debuff", "No Debuff",
                "Protection II diamond armour, a Sharpness III Fire Aspect sword, 30 healing splashes, pearls and potions.");
        general(kits, "uhc_starter", "UHC Starter",
                "Each player's chosen UHC kit (/minigames uhc kit); stone tools by default.");
        own(kits, "bedwars", "Bed Wars", "A wooden sword; armour and tools follow what each player bought.");
        own(kits, "bow_spleef", "Bow Spleef", "An unbreakable Flame Infinity bow.");
        own(kits, "capture_the_wool", "Capture the Wool",
                "Unbreakable stone sword, iron pickaxe, bow and iron axe, 192 oak planks, a golden apple, 8 arrows and leather armour.");
        own(kits, "parkour", "Parkour", "A boost feather and a back-to-checkpoint plate.");
        own(kits, "pearl_fight", "Pearl Fight", "A Knockback stick, 8 ender pearls, wool and shears.");
        own(kits, "quake", "Quake", "A railgun hoe and a dash feather.");
        own(kits, "skywars", "SkyWars", "Each player's chosen SkyWars kit (/minigames skywars kit); the Default kit's iron tools, sword and chestplate otherwise.");
        own(kits, "skywars_mega", "Mega SkyWars", "Each player's chosen Mega kit (/minigames skywars mega kit); the Default kit's iron tools and sword and leather armour otherwise.");
        own(kits, "skywars_mini", "Mini SkyWars", "Each player's chosen Mini kit (/minigames skywars mini kit); Champion's Sharpness II diamond sword, iron tools and armour, anvil and books otherwise.");
        own(kits, "speed_uhc", "Speed UHC", "Each player's chosen Speed UHC kit (/minigames speed_uhc kit); the Default kit's six oak planks and iron chestplate otherwise.");
        own(kits, "spleef", "Spleef", "An unbreakable Efficiency V diamond shovel.");
        return Collections.unmodifiableMap(kits);
    }

    private static void general(Map<Identifier, Bundled> kits, String path, String name, String description) {
        kits.put(BrainageMinigames.id("kits/" + path), new Bundled(name, description, true));
    }

    private static void own(Map<Identifier, Bundled> kits, String path, String name, String description) {
        kits.put(BrainageMinigames.id("kits/" + path), new Bundled(name, description, false));
    }

    /**
     * The kit's name for players, such as "No Debuff" or "UHC Starter"; other kits read like the
     * last part of their id, so {@code example:kits/sky_duel} reads "Sky Duel".
     */
    public static String displayName(Identifier kitId) {
        Bundled bundled = BUNDLED.get(kitId);
        if (bundled != null) return bundled.name();
        String path = kitId.getPath();
        StringBuilder name = new StringBuilder();
        for (String word : path.substring(path.lastIndexOf('/') + 1).split("_")) {
            if (word.isEmpty()) continue;
            if (!name.isEmpty()) name.append(' ');
            name.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return name.toString();
    }

    /** One line on what the kit gives; a kit edited on this server, or from a datapack, says so. */
    public static String description(MinecraftServer server, Identifier kitId) {
        if (root(server).contains(kitId.toString())) return "A kit edited on this server.";
        Bundled bundled = BUNDLED.get(kitId);
        return bundled != null ? bundled.description() : "A kit added by a datapack.";
    }

    private KitStorage() {}

    public static Optional<List<ItemStack>> get(MinecraftServer server, Identifier kitId) {
        CompoundTag encoded = root(server).getCompound(kitId.toString()).orElse(null);
        if (encoded == null) {
            return Optional.empty();
        }
        var input =
                TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), encoded);
        List<ItemStack> items = input.read(ITEMS_KEY, ItemStack.CODEC.listOf()).orElse(List.of());
        return Optional.of(items.stream().map(ItemStack::copy).toList());
    }

    public static boolean exists(MinecraftServer server, Identifier kitId) {
        return root(server).contains(kitId.toString()) || LootUtils.exists(server, kitId);
    }

    public static void save(MinecraftServer server, Identifier kitId, Collection<ItemStack> items) {
        List<ItemStack> copies =
                items.stream()
                        .filter(stack -> !stack.isEmpty())
                        .limit(EDITOR_SIZE)
                        .map(ItemStack::copy)
                        .toList();
        TagValueOutput output =
                TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, server.registryAccess());
        output.store(ITEMS_KEY, ItemStack.CODEC.listOf(), copies);

        CompoundTag root = root(server);
        root.put(kitId.toString(), output.buildResult());
        server.getCommandStorage().set(STORAGE_ID, root);
    }

    public static boolean delete(MinecraftServer server, Identifier kitId) {
        CompoundTag root = root(server);
        if (root.remove(kitId.toString()) == null) {
            return false;
        }
        server.getCommandStorage().set(STORAGE_ID, root);
        return true;
    }

    public static List<Identifier> ids(MinecraftServer server) {
        return root(server).keySet().stream()
                .map(Identifier::tryParse)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
    }

    /** Every kit for any game: the general bundled kits, then those edited on this server. */
    public static List<Identifier> kits(MinecraftServer server) {
        LinkedHashSet<Identifier> ids = new LinkedHashSet<>();
        BUNDLED.forEach((id, bundled) -> {
            if (bundled.general()) ids.add(id);
        });
        ids.addAll(ids(server));
        return List.copyOf(ids);
    }

    public static List<String> suggestions(MinecraftServer server) {
        return kits(server).stream().map(Identifier::toString).toList();
    }

    /** Gives the kit to each player, wearing any armour that fits an empty armour slot. */
    public static boolean give(
            MinecraftServer server, Identifier kitId, Collection<ServerPlayer> players) {
        Optional<List<ItemStack>> storedKit = get(server, kitId);
        if (storedKit.isEmpty() && !LootUtils.exists(server, kitId)) {
            return false;
        }
        for (ServerPlayer player : players) {
            List<ItemStack> kit = storedKit.orElseGet(() -> LootUtils.roll(player, kitId));
            for (ItemStack stack : kit) {
                equipOrGive(player, stack.copy());
            }
        }
        return true;
    }

    public static void equipOrGive(ServerPlayer player, ItemStack stack) {
        EquipmentSlot slot = player.getEquipmentSlotForItem(stack);
        if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                && player.getItemBySlot(slot).isEmpty()) {
            player.setItemSlot(slot, stack);
        } else {
            PlayerUtils.giveOrDrop(player, stack);
        }
    }

    public static void openEditor(ServerPlayer player, Identifier kitId) {
        MinecraftServer server = player.level().getServer();
        EditableKitContainer container = new EditableKitContainer(server, kitId);
        List<ItemStack> existing =
                get(server, kitId)
                        .orElseGet(
                                () ->
                                        LootUtils.exists(server, kitId)
                                                ? LootUtils.roll(player, kitId)
                                                : List.of());
        for (int slot = 0; slot < Math.min(existing.size(), container.getContainerSize()); slot++) {
            container.setItem(slot, existing.get(slot));
        }
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) ->
                                ChestMenu.sixRows(containerId, inventory, container),
                        Component.literal("Edit kit: " + kitId)));
    }

    private static CompoundTag root(MinecraftServer server) {
        return server.getCommandStorage().get(STORAGE_ID);
    }

    private static final class EditableKitContainer extends SimpleContainer {
        private final MinecraftServer server;
        private final Identifier kitId;

        private EditableKitContainer(MinecraftServer server, Identifier kitId) {
            super(EDITOR_SIZE);
            this.server = server;
            this.kitId = kitId;
        }

        @Override
        public void stopOpen(ContainerUser user) {
            super.stopOpen(user);
            List<ItemStack> items = new ArrayList<>(getContainerSize());
            for (int slot = 0; slot < getContainerSize(); slot++) {
                items.add(getItem(slot));
            }
            save(server, kitId, items);
        }
    }
}
