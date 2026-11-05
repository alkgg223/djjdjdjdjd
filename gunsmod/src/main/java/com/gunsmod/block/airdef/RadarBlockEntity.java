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

    /** whether this radar is tracking the aircraft now */
    public boolean tracks(int id) {
        return tracks.containsKey(id);
    }

    /**
     * The scope picture: friendly aircraft answer the IFF interrogation and always show; enemy aircraft show only when
     * this radar (or, with the network on, another radar of the side) holds a track on them; aircraft of no side that
     * are tracked show as unknown. Our launchers within reach are marked too.
     */
    public com.gunsmod.net.Net.RadarPicture picture() {
        java.util.List<com.gunsmod.net.Net.Blip> blips = new java.util.ArrayList<>();
        Vec3 eye = antenna();
        double reach = type.reach();
        java.util.List<RadarBlockEntity> mesh = new java.util.ArrayList<>();
        if (com.gunsmod.Config.radarNetwork()) {
            for (RadarBlockEntity r : AirTracker.radarsNear(level, worldPosition, DefenseBlockEntity.NETWORK_RANGE)) {
                if (r != this && r.team == team) mesh.add(r);
            }
        }
        java.util.Set<Integer> engaged = new java.util.HashSet<>();
        java.util.List<DefenseBlockEntity> sites = AirTracker.launchersNear(level, worldPosition, reach);
        for (DefenseBlockEntity d : sites) if (d.team() == team && d.targetId() >= 0) engaged.add(d.targetId());
        for (VehicleEntity v : AirTracker.flying(level)) {
            Vec3 c = v.position();
            double dx = c.x - eye.x, dz = c.z - eye.z;
            if (dx * dx + dz * dz > reach * reach) continue;
            BotTeam side = BotTeam.of(v);
            byte iff;
            byte flags = 0;
            if (side == team) {
                iff = 0;
            } else if (tracks.containsKey(v.getId())) {
                iff = (byte) (side == null ? 2 : 1);
            } else {
                boolean seen = false;
                for (RadarBlockEntity r : mesh) {
                    if (r.tracks.containsKey(v.getId())) {
                        seen = true;
                        break;
                    }
                }
                if (!seen) continue;
                iff = (byte) (side == null ? 2 : 1);
                flags |= 1;
            }
            if (engaged.contains(v.getId())) flags |= 2;
            byte kind = v instanceof com.gunsmod.entity.JetEntity ? (byte) 0 : v instanceof com.gunsmod.entity.HelicopterEntity ? (byte) 1
                    : v instanceof com.gunsmod.entity.DroneEntity ? (byte) 2 : (byte) 3;
            Vec3 m = v.getDeltaMovement();
            float speed = (float) (Math.sqrt(m.x * m.x + m.z * m.z) * 20);
            float heading = speed > 0.5f ? (float) Math.toDegrees(Math.atan2(-m.x, m.z)) : v.getYRot();
            blips.add(new com.gunsmod.net.Net.Blip(iff, kind, flags, (float) dx, (float) dz, (float) c.y, heading, speed));
        }
        for (DefenseBlockEntity d : sites) {
            if (d.team() != team || blips.size() >= 120) continue;
            BlockPos p = d.getBlockPos();
            blips.add(new com.gunsmod.net.Net.Blip((byte) 0, (byte) 4, (byte) (d.targetId() >= 0 ? 2 : 0), (float) (p.getX() + 0.5 - eye.x),
                    (float) (p.getZ() + 0.5 - eye.z), p.getY(), 0f, 0f));
        }
        float facing = getBlockState().getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot();
        return new com.gunsmod.net.Net.RadarPicture(worldPosition, (float) reach, type.sweep, type.sector, facing, team.ordinal(), blips);
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
            if (d > type.reach()) continue;
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
