package io.github.brainage04.brainage_minigames.util;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public final class LootUtils {
    private LootUtils() {}

    public static boolean exists(MinecraftServer server, Identifier id) {
        return server.reloadableRegistries()
                .lookup()
                .lookupOrThrow(Registries.LOOT_TABLE)
                .get(ResourceKey.create(Registries.LOOT_TABLE, id))
                .isPresent();
    }

    /** Rolls a chest loot table for {@code player}; unknown tables roll nothing. */
    public static List<ItemStack> roll(ServerPlayer player, Identifier id) {
        LootTable table =
                player.level()
                        .getServer()
                        .reloadableRegistries()
                        .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
        LootParams params =
                new LootParams.Builder(player.level())
                        .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                        .withParameter(LootContextParams.ORIGIN, player.position())
                        .create(LootContextParamSets.CHEST);
        return table.getRandomItems(params);
    }
}
