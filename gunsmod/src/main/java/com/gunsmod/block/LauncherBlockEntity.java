package com.gunsmod.block;

import com.gunsmod.Reg;
import com.gunsmod.entity.MissileEntity;
import com.gunsmod.item.MissileItem;
import com.gunsmod.net.Net;
import java.util.Comparator;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Missile launch station: holds one missile and a target, raises the erector, ignites and launches. */
public class LauncherBlockEntity extends BlockEntity {
    public static final int IDLE = 0, ARMED = 1, ERECTING = 2, IGNITION = 3, LOWERING = 4;
    /** keeps a station's chunk loaded while it is used from far away or busy launching */
    public static final TicketType<ChunkPos> STATION_TICKET = TicketType.create("gunsmod_station", Comparator.comparingLong(ChunkPos::toLong), 100);

    private int missile = -1;
    private boolean hasTarget;
    private int tx, ty, tz;
    private int state = IDLE;
    private int timer;
    @Nullable private UUID launcher;
    /** client animation clock */
    public int clientTimer;

    public LauncherBlockEntity(BlockPos pos, BlockState blockState) {
        this(Reg.LAUNCHER_BE.get(), pos, blockState);
    }

    protected LauncherBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    // ------------------------------------------------------------------------------------ kind of station
    public boolean isHeavy() {
        return false;
    }

    public boolean accepts(int kind) {
        return MissileItem.isHeavy(kind) == isHeavy();
    }

    public int erectTime() {
        return 50;
    }

    public int igniteTime() {
        return 30;
    }

    public int lowerTime() {
        return 50;
    }

    // ------------------------------------------------------------------------------------ state
    public boolean hasMissile() {
        return missile >= 0;
    }

    public int missileKind() {
        return missile;
    }

    public int getState() {
        return state;
    }

    public boolean isBusy() {
        return state >= ERECTING;
    }

    public boolean hasTarget() {
        return hasTarget;
    }

    public BlockPos targetPos() {
        return new BlockPos(tx, ty, tz);
    }

    public ItemStack missileStack() {
        return new ItemStack(MissileItem.byKind(missile));
    }

    /** load a missile; false when one is already loaded, the station is busy or the missile does not fit */
    public boolean loadMissile(int kind) {
        if (hasMissile() || isBusy() || !accepts(kind)) return false;
        missile = kind;
        sync();
        return true;
    }

    public boolean setTarget(int x, int y, int z) {
        if (isBusy()) return false;
        hasTarget = true;
        tx = x;
        ty = y;
        tz = z;
        sync();
        return true;
    }

    /** @return null when armed, otherwise the translation key of the reason */
    @Nullable
    public String arm() {
        if (state != IDLE) return isBusy() ? "gunsmod.msg.station_busy" : null;
        if (!hasMissile()) return "gunsmod.msg.no_missile";
        if (!hasTarget) return "gunsmod.msg.no_target";
        state = ARMED;
        level.playSound(null, worldPosition, Reg.sound("missile_alarm"), SoundSource.BLOCKS, 3f, 1f);
        sync();
        return null;
    }

    public void disarm() {
        if (state == ARMED) {
            state = IDLE;
            sync();
        }
    }

    /** start the launch sequence of an armed station */
    public boolean launch(@Nullable Entity by) {
        if (state != ARMED) return false;
        begin(by);
        return true;
    }

    /** one-step launch used by soldiers: set the target and fire straight away */
    public boolean fireAt(Entity by, BlockPos target) {
        if (state != IDLE || !hasMissile()) return false;
        hasTarget = true;
        tx = target.getX();
        ty = target.getY();
        tz = target.getZ();
        begin(by);
        return true;
    }

    private void begin(@Nullable Entity by) {
        state = ERECTING;
        timer = 0;
        launcher = by != null ? by.getUUID() : null;
        level.playSound(null, worldPosition, Reg.sound("missile_alarm"), SoundSource.BLOCKS, isHeavy() ? 6f : 4f, 0.9f);
        keepLoaded();
        sync();
    }

    public void keepLoaded() {
        if (level instanceof ServerLevel sl) {
            ChunkPos cp = new ChunkPos(worldPosition);
            sl.getChunkSource().addRegionTicket(STATION_TICKET, cp, 2, cp);
        }
    }

    // ------------------------------------------------------------------------------------ geometry
    public Direction facing() {
        BlockState st = getBlockState();
        return st.hasProperty(FacingBlock.FACING) ? st.getValue(FacingBlock.FACING) : Direction.NORTH;
    }

    /** erector angle in degrees: 0 = lying forward, 90 = vertical */
    public float erectAngle(float partial) {
        float t = clientTimer + partial;
        return switch (state) {
            case ERECTING -> Math.min(90f, 90f * t / erectTime());
            case IGNITION -> 90f;
            case LOWERING -> Math.max(0f, 90f * (1f - t / lowerTime()));
            default -> 0f;
        };
    }

    /** where the missile entity starts (its render centre) */
    public Vec3 launchPoint() {
        Direction f = facing();
        // the hinge sits 0.375 blocks behind the centre (opposite of facing)
        return Vec3.atBottomCenterOf(worldPosition).add(-f.getStepX() * 0.375, 2.1, -f.getStepZ() * 0.375);
    }

    /** bottom of the standing missile, where the exhaust hits the pad */
    public Vec3 exhaustPoint() {
        return launchPoint().add(0, -1.3, 0);
    }

    // ------------------------------------------------------------------------------------ tick
    public void tick() {
        if (level == null) return;
        if (level.isClientSide) {
            clientTimer++;
            clientEffects();
            return;
        }
        if (!isBusy()) return;
        timer++;
        if (timer % 40 == 0) keepLoaded();
        if (state == ERECTING && timer >= erectTime()) {
            state = IGNITION;
            timer = 0;
            level.playSound(null, worldPosition, Reg.sound(isHeavy() ? "heavy_launch" : "missile_launch"), SoundSource.BLOCKS, isHeavy() ? 10f : 6f, 1f);
            sync();
        } else if (state == IGNITION && timer >= igniteTime()) {
            Entity owner = launcher != null && level instanceof ServerLevel sl ? sl.getEntity(launcher) : null;
            MissileEntity m = new MissileEntity(level, owner, launchPoint(), new BlockPos(tx, ty, tz), missile);
            level.addFreshEntity(m);
            missile = -1;
            state = LOWERING;
            timer = 0;
            sync();
        } else if (state == LOWERING && timer >= lowerTime()) {
            state = IDLE;
            timer = 0;
            sync();
        }
    }

    private void clientEffects() {
        boolean ignition = state == IGNITION;
        if (!(ignition || state == ERECTING && clientTimer > erectTime() - 6)) return;
        RandomSource r = level.random;
        Vec3 base = exhaustPoint();
        boolean heavy = isHeavy();
        int smoke = ignition ? (heavy ? 6 : 3) : 1;
        for (int i = 0; i < smoke; i++) {
            double a = r.nextDouble() * Math.PI * 2, sp = (heavy ? 0.35 : 0.22) * (0.5 + r.nextDouble());
            level.addParticle(Reg.DUST_CLOUD.get(), base.x + Math.cos(a) * 0.6, base.y + 0.2, base.z + Math.sin(a) * 0.6,
                    Math.cos(a) * sp, 0.02 + r.nextDouble() * 0.04, Math.sin(a) * sp);
        }
        if (ignition) {
            for (int i = 0; i < (heavy ? 4 : 2); i++) {
                level.addParticle(Reg.FIREBALL.get(), base.x + (r.nextDouble() - 0.5) * 0.6, base.y + 0.4, base.z + (r.nextDouble() - 0.5) * 0.6,
                        (r.nextDouble() - 0.5) * 0.25, -0.05, (r.nextDouble() - 0.5) * 0.25);
            }
            level.addParticle(Reg.FLAME.get(), base.x, base.y + 0.6, base.z, 0, -0.15, 0);
            if (clientTimer % 3 == 0) level.addParticle(Reg.EMBER.get(), base.x, base.y + 0.5, base.z, (r.nextDouble() - 0.5) * 0.6, 0.3, (r.nextDouble() - 0.5) * 0.6);
        }
    }

    // ------------------------------------------------------------------------------------ status / sync
    public Net.StationInfo info() {
        return new Net.StationInfo(worldPosition, true, isHeavy(), missile, hasTarget, tx, ty, tz, state);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("Missile", missile);
        tag.putBoolean("HasTarget", hasTarget);
        tag.putInt("TX", tx);
        tag.putInt("TY", ty);
        tag.putInt("TZ", tz);
        tag.putInt("State", state == ARMED ? IDLE : state);
        tag.putInt("Timer", timer);
        if (launcher != null) tag.putUUID("Launcher", launcher);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        missile = tag.getInt("Missile");
        hasTarget = tag.getBoolean("HasTarget");
        tx = tag.getInt("TX");
        ty = tag.getInt("TY");
        tz = tag.getInt("TZ");
        int newState = tag.getInt("State");
        if (newState != state) clientTimer = 0;
        state = newState;
        timer = tag.getInt("Timer");
        launcher = tag.hasUUID("Launcher") ? tag.getUUID("Launcher") : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag t = new CompoundTag();
        saveAdditional(t, reg);
        t.putInt("State", state);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** a station block entity at the position, loading its chunk if needed */
    @Nullable
    public static LauncherBlockEntity at(Level level, BlockPos pos) {
        if (level instanceof ServerLevel sl) sl.getChunkAt(pos);
        return level.getBlockEntity(pos) instanceof LauncherBlockEntity be ? be : null;
    }
}
