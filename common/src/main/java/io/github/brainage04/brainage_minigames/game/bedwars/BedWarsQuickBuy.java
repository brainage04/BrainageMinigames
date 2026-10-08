package io.github.brainage04.brainage_minigames.game.bedwars;

import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/**
 * Each player's Bed Wars Quick Buy: 21 slots of shop items (empty ones as {@code ""}), saved per UUID
 * in the world's command storage. A player who never changed it has {@link BedWarsShop#DEFAULT_QUICK_BUY}.
 */
public final class BedWarsQuickBuy {
    public static final Identifier STORAGE = BrainageMinigames.id("bedwars_quick_buy");
    private static final Codec<List<String>> STRINGS = Codec.STRING.listOf();

    private BedWarsQuickBuy() {}

    public static List<String> get(MinecraftServer server, UUID player) {
        List<String> saved = server.getCommandStorage().get(STORAGE).read(player.toString(), STRINGS).orElse(List.of());
        if (saved.size() != BedWarsShop.QUICK_BUY_SLOTS) return BedWarsShop.DEFAULT_QUICK_BUY;
        List<String> slots = new ArrayList<>(saved);
        slots.replaceAll(id -> id.isEmpty() || BedWarsShop.find(id).isPresent() ? id : "");
        return List.copyOf(slots);
    }

    /** Puts {@code item} (or {@code ""} to empty it) into slot {@code slot}, from 0. */
    public static void set(MinecraftServer server, UUID player, int slot, String item) {
        List<String> slots = new ArrayList<>(get(server, player));
        slots.set(slot, item);
        CompoundTag root = server.getCommandStorage().get(STORAGE);
        root.store(player.toString(), STRINGS, slots);
        server.getCommandStorage().set(STORAGE, root);
    }

    /** Back to {@link BedWarsShop#DEFAULT_QUICK_BUY}. */
    public static void reset(MinecraftServer server, UUID player) {
        CompoundTag root = server.getCommandStorage().get(STORAGE);
        root.remove(player.toString());
        server.getCommandStorage().set(STORAGE, root);
    }
}
