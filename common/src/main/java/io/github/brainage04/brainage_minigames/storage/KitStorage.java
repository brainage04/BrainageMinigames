package io.github.brainage04.brainage_minigames.storage;

import io.github.brainage04.brainage_minigames.BrainageMinigames;
import io.github.brainage04.brainage_minigames.util.LootUtils;
import io.github.brainage04.brainage_minigames.util.PlayerUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
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
 * world by kits edited in game.
 */
public final class KitStorage {
    public static final Identifier EMPTY_KIT = BrainageMinigames.id("empty");

    private static final Identifier STORAGE_ID = BrainageMinigames.id("kits");
    private static final String ITEMS_KEY = "items";
    private static final int EDITOR_SIZE = 6 * 9;
    private static final List<Identifier> BUILT_IN_KITS =
            List.of(
                    EMPTY_KIT,
                    BrainageMinigames.id("kits/barebones"),
                    BrainageMinigames.id("kits/battle_rush"),
                    BrainageMinigames.id("kits/bow"),
                    BrainageMinigames.id("kits/boxing"),
                    BrainageMinigames.id("kits/bridge"),
                    BrainageMinigames.id("kits/build_uhc"),
                    BrainageMinigames.id("kits/classic"),
                    BrainageMinigames.id("kits/combo"),
                    BrainageMinigames.id("kits/final_uhc"),
                    BrainageMinigames.id("kits/gapple"),
                    BrainageMinigames.id("kits/instant_crossbow"),
                    BrainageMinigames.id("kits/instant_firework_crossbow"),
                    BrainageMinigames.id("kits/meetup"),
                    BrainageMinigames.id("kits/no_debuff"),
                    BrainageMinigames.id("kits/uhc_starter"));

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

    public static List<String> suggestions(MinecraftServer server) {
        LinkedHashSet<Identifier> ids = new LinkedHashSet<>(BUILT_IN_KITS);
        ids.addAll(ids(server));
        return ids.stream().map(Identifier::toString).toList();
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

    private static void equipOrGive(ServerPlayer player, ItemStack stack) {
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
