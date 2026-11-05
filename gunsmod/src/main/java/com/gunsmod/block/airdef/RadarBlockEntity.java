package com.gunsmod.block.airdef;

import com.gunsmod.Reg;
import com.gunsmod.entity.Aircraft;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.VehicleEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An air search radar. Every sweep of the antenna each enemy aircraft in range and in line of sight is a roll of the
 * dice (the type's detection, its signature, the distance); a hit puts it on the track list for a couple of sweeps.
 * Linked launchers fire at what is on the list.
 */
public class RadarBlockEntity extends BlockEntity {
    public final RadarType type;
    private BotTeam team = BotTeam.GREEN;
    /** entity id -> game time last seen */
    private final Map<Integer, Long> tracks = new HashMap<>();
    private int sweepIn, shownTracks;

    public RadarBlockEntity(BlockPos pos, BlockState state) {
        super(Reg.RADAR_BE.get(), pos, state);
        this.type = state.getBlock() instanceof AirDefenseBlock b && b.radar != null ? b.radar : RadarType.SENTINEL;
    }

    public BotTeam team() {
        return team;
    }

    public void setTeam(BotTeam t) {
        team = t;
        tracks.clear();
        sync();
    }

    /** tracks shown on the label (synced) */
    public int shownTracks() {
        return shownTracks;
    }

    /** where the antenna sits, for line of sight */
    public Vec3 antenna() {
        var g = AirDefenseGeo.GEO.get(type.id);
        double up = g == null ? 2 : g.head()[1] / 16.0 + 1.0;
        return Vec3.atBottomCenterOf(worldPosition).add(0, up, 0);
    }

    /** ids of the aircraft this radar is tracking now */
    public Iterable<Integer> tracked() {
        return tracks.keySet();
    }

    public void tick() {
        if (level == null || level.isClientSide) return;
        AirTracker.radar(this, true);
        if (--sweepIn > 0) return;
        sweepIn = type.sweep;
        long now = level.getGameTime();
        Vec3 eye = antenna();
        for (VehicleEntity v : AirTracker.flying(level)) {
            if (!v.threat() || BotTeam.of(v) == team) continue;
            Vec3 c = v.position().add(0, v.getBbHeight() * 0.5, 0);
            double d = c.distanceTo(eye);
            if (d > type.range) continue;
            double p = type.chance(AirTracker.signature(v), d);
            if (p <= 0 || level.random.nextDouble() >= p) continue;
            if (level.clip(new ClipContext(eye, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, v)).getType() != HitResult.Type.MISS) continue;
            if (!tracks.containsKey(v.getId())) level.playSound(null, worldPosition, Reg.sound("radar_contact"), SoundSource.BLOCKS, 1.2f, 1f);
            tracks.put(v.getId(), now);
            if (v instanceof Aircraft a) a.radarWarning();
        }
        long keep = type.sweep * 2L + 20;
        tracks.values().removeIf(t -> now - t > keep);
        if (tracks.size() != shownTracks) {
            shownTracks = tracks.size();
            sync();
        }
    }

    @Override
    public void setRemoved() {
        AirTracker.radar(this, false);
        super.setRemoved();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("Team", team.ordinal());
        tag.putInt("Tracks", shownTracks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        team = BotTeam.byId(tag.getInt("Team"));
        shownTracks = tag.getInt("Tracks");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag t = new CompoundTag();
        saveAdditional(t, reg);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
