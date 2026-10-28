package com.gunsmod.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Launch stations a remote controls (up to {@link #MAX}) and the one currently selected. */
public record RemoteLinks(List<BlockPos> stations, int selected) {
    public static final int MAX = 15;
    public static final RemoteLinks EMPTY = new RemoteLinks(List.of(), 0);
    public static final Codec<RemoteLinks> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.listOf().fieldOf("stations").forGetter(RemoteLinks::stations),
            Codec.INT.optionalFieldOf("selected", 0).forGetter(RemoteLinks::selected)
    ).apply(i, RemoteLinks::new));
    public static final StreamCodec<ByteBuf, RemoteLinks> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), RemoteLinks::stations,
            ByteBufCodecs.VAR_INT, RemoteLinks::selected,
            RemoteLinks::new);

    public RemoteLinks {
        stations = List.copyOf(stations);
        selected = stations.isEmpty() ? 0 : Math.floorMod(selected, stations.size());
    }

    public boolean isEmpty() {
        return stations.isEmpty();
    }

    public boolean isFull() {
        return stations.size() >= MAX;
    }

    public boolean contains(BlockPos pos) {
        return stations.contains(pos);
    }

    public int indexOf(BlockPos pos) {
        return stations.indexOf(pos);
    }

    /** null when nothing is linked */
    public BlockPos selectedStation() {
        return stations.isEmpty() ? null : stations.get(selected);
    }

    public RemoteLinks with(BlockPos pos) {
        if (contains(pos)) return select(indexOf(pos));
        if (isFull()) return this;
        List<BlockPos> l = new ArrayList<>(stations);
        l.add(pos.immutable());
        return new RemoteLinks(l, l.size() - 1);
    }

    public RemoteLinks without(BlockPos pos) {
        List<BlockPos> l = new ArrayList<>(stations);
        l.remove(pos);
        return new RemoteLinks(l, Math.min(selected, Math.max(0, l.size() - 1)));
    }

    public RemoteLinks select(int index) {
        return new RemoteLinks(stations, index);
    }
}
