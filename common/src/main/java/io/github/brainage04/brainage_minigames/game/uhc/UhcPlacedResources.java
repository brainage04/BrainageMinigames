package io.github.brainage04.brainage_minigames.game.uhc;

import com.mojang.serialization.Codec;
import io.github.brainage04.brainage_minigames.BrainageMinigames;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Resource positions placed by anyone, retained until the UHC dimension is regenerated. */
final class UhcPlacedResources extends SavedData {
    private static final SavedDataType<UhcPlacedResources> TYPE = new SavedDataType<>(
            BrainageMinigames.id("uhc_placed_resources"),
            () -> new UhcPlacedResources(new LongOpenHashSet()),
            Codec.LONG_STREAM.xmap(
                    positions -> new UhcPlacedResources(new LongOpenHashSet(positions.toArray())),
                    data -> LongStream.of(data.positions.toLongArray())).fieldOf("positions").codec(),
            null);

    private final LongSet positions;

    private UhcPlacedResources(LongSet positions) {
        this.positions = positions;
    }

    static UhcPlacedResources get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    void add(BlockPos pos) {
        if (positions.add(pos.asLong())) setDirty();
    }

    boolean contains(BlockPos pos) {
        return positions.contains(pos.asLong());
    }
}
