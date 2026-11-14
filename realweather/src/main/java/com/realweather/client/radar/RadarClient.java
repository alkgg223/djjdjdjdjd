package com.realweather.client.radar;

import com.realweather.device.MonitorBlock;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/** Client side of the radar: which radar towers are loaded, the monitors' screens, and opening the display. */
public final class RadarClient {
    /** How far a monitor can be from its radar. */
    public static final int LINK_RANGE = 64;
    private static final Set<BlockPos> RADARS = new HashSet<>();
    private static final Long2ObjectOpenHashMap<RadarCanvas> SCREENS = new Long2ObjectOpenHashMap<>();
    public static final float[] RANGES = {128F, 256F, 512F, 1024F};

    private RadarClient() {}

    public static void radarLoaded(BlockPos pos, boolean loaded) {
        if (loaded) {
            RADARS.add(pos.immutable());
        } else {
            RADARS.remove(pos);
        }
    }

    public static BlockPos nearestRadar(BlockPos from) {
        BlockPos best = null;
        double bd = (double) LINK_RANGE * LINK_RANGE;
        for (BlockPos p : RADARS) {
            double d = p.distSqr(from);
            if (d <= bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    public static void openRadar(BlockPos pos) {
        Minecraft.getInstance().setScreen(new RadarScreen(pos, pos, RadarProducts.REF));
    }

    public static void openMonitor(BlockPos pos, int mode) {
        Minecraft.getInstance().setScreen(new RadarScreen(nearestRadar(pos), pos, mode));
    }

    public static void monitorRemoved(BlockPos pos) {
        RadarCanvas c = SCREENS.remove(pos.asLong());
        if (c != null) {
            c.close();
        }
    }

    /** The picture on a monitor's screen (made when it is first drawn). */
    public static RadarCanvas screenFor(ClientLevel level, BlockPos pos, BlockState state) {
        RadarCanvas c = SCREENS.get(pos.asLong());
        int mode = state.hasProperty(MonitorBlock.MODE) ? state.getValue(MonitorBlock.MODE) : 0;
        BlockPos radar = nearestRadar(pos);
        boolean fresh = false;
        if (c == null) {
            c = new RadarCanvas(128);
            c.range = 256F;
            SCREENS.put(pos.asLong(), c);
            fresh = true;
        }
        boolean signal = radar != null;
        double ox = signal ? radar.getX() + 0.5 : pos.getX() + 0.5;
        double oz = signal ? radar.getZ() + 0.5 : pos.getZ() + 0.5;
        if (fresh || c.product != mode || c.signal != signal || c.ox != ox || c.oz != oz) {
            c.product = mode;
            c.signal = signal;
            c.ox = ox;
            c.oz = oz;
            c.redrawAll(level);
        }
        c.lastUsed = level.getGameTime();
        return c;
    }

    /** Client tick: monitors that were drawn recently keep sweeping; forgotten ones are freed. */
    public static void tick(Minecraft mc, ClientLevel level) {
        if (level == null) {
            if (!SCREENS.isEmpty()) {
                SCREENS.values().forEach(RadarCanvas::close);
                SCREENS.clear();
            }
            RADARS.clear();
            return;
        }
        long now = level.getGameTime();
        var it = SCREENS.long2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            RadarCanvas c = it.next().getValue();
            if (now - c.lastUsed > 200 || now < c.lastUsed) {
                c.close();
                it.remove();
            } else {
                c.sweep(level, 1);
                c.upload();
            }
        }
    }
}
