package com.gunsmod.block;

import com.gunsmod.Reg;
import com.gunsmod.entity.MissileEntity;
import com.gunsmod.net.Net;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class LauncherBlockEntity extends BlockEntity {
    public static final int IDLE = 0, ARMED = 1, ERECTING = 2, IGNITION = 3, LOWERING = 4;
    public static final int ERECT_TIME = 50, IGNITE_TIME = 30, LOWER_TIME = 50;

    private int missile = -1;
    private boolean hasTarget;
    private int tx, ty, tz;
    private int state = IDLE;
    private int timer;
    @Nullable private UUID launcher;
    /** client animation clock */
    public int clientTimer;

    public LauncherBlockEntity(BlockPos pos, BlockState blockState) {
        super(Reg.LAUNCHER_BE.get(), pos, blockState);
    }

    public boolean hasMissile() {
        return missile >= 0;
    }

    public int missileKind() {
        return missile;
    }

    public int getState() {
        return state;
    }

    public ItemStack missileStack() {
        return new ItemStack(switch (missile) {
            case 1 -> Reg.MISSILE_CLUSTER.get();
            case 2 -> Reg.MISSILE_INCENDIARY.get();
            default -> Reg.MISSILE_HE.get();
        });
    }

    public void loadMissile(int kind) {
        missile = kind;
        sync();
    }

    public void setTarget(int x, int y, int z, Player p) {
        if (state >= ERECTING) return;
        hasTarget = true;
        tx = x;
        ty = y;
        tz = z;
        p.displayClientMessage(Component.translatable("gunsmod.msg.target_set", x, y, z), true);
        sync();
    }

    public void arm(Player p) {
        if (state != IDLE) return;
        if (!hasMissile()) {
            p.displayClientMessage(Component.translatable("gunsmod.msg.no_missile"), true);
            return;
        }
        if (!hasTarget) {
            p.displayClientMessage(Component.translatable("gunsmod.msg.no_target"), true);
            return;
        }
        state = ARMED;
        level.playSound(null, worldPosition, Reg.sound("missile_alarm"), SoundSource.BLOCKS, 3f, 1f);
        sync();
    }

    public void disarm() {
        if (state == ARMED) {
            state = IDLE;
            sync();
        }
    }

    public void launch(Player p) {
        if (state != ARMED) return;
        state = ERECTING;
        timer = 0;
        launcher = p.getUUID();
        level.playSound(null, worldPosition, Reg.sound("missile_alarm"), SoundSource.BLOCKS, 4f, 0.9f);
        p.displayClientMessage(Component.translatable("gunsmod.msg.launching"), true);
        sync();
    }

    public Direction facing() {
        BlockState st = getBlockState();
        return st.hasProperty(FacingBlock.FACING) ? st.getValue(FacingBlock.FACING) : Direction.NORTH;
    }

    /** erector angle in degrees: 0 = lying forward, 90 = vertical */
    public float erectAngle(float partial) {
        float t = clientTimer + partial;
        return switch (state) {
            case ERECTING -> Math.min(90f, 90f * t / ERECT_TIME);
            case IGNITION -> 90f;
            case LOWERING -> Math.max(0f, 90f * (1f - t / LOWER_TIME));
            default -> 0f;
        };
    }

    /** world position of the rail top when vertical */
    public Vec3 railTop() {
        Direction f = facing();
        // the hinge sits 0.375 blocks behind the centre (opposite of facing)
        return Vec3.atBottomCenterOf(worldPosition).add(-f.getStepX() * 0.375, 0.9 + 2.2, -f.getStepZ() * 0.375);
    }

    public void tick() {
        if (level == null) return;
        if (level.isClientSide) {
            clientTimer++;
            if (state == IGNITION || state == ERECTING && clientTimer > ERECT_TIME - 5) {
                Vec3 base = railTop().add(0, -2.4, 0);
                for (int i = 0; i < (state == IGNITION ? 10 : 2); i++) {
                    level.addParticle(Reg.SMOKE.get(), base.x + (level.random.nextDouble() - 0.5) * 1.6, base.y, base.z + (level.random.nextDouble() - 0.5) * 1.6,
                            (level.random.nextDouble() - 0.5) * 0.3, 0.02, (level.random.nextDouble() - 0.5) * 0.3);
                }
                if (state == IGNITION) level.addParticle(Reg.FLAME.get(), base.x, base.y + 0.3, base.z, 0, -0.1, 0);
            }
            return;
        }
        if (state == ERECTING || state == IGNITION || state == LOWERING) {
            timer++;
            if (state == ERECTING && timer >= ERECT_TIME) {
                state = IGNITION;
                timer = 0;
                level.playSound(null, worldPosition, Reg.sound("missile_launch"), SoundSource.BLOCKS, 6f, 1f);
                sync();
            } else if (state == IGNITION && timer >= IGNITE_TIME) {
                LivingEntity owner = launcher != null ? level.getPlayerByUUID(launcher) : null;
                MissileEntity m = new MissileEntity(level, owner, railTop().add(0, -1.0, 0), new BlockPos(tx, ty, tz), missile);
                level.addFreshEntity(m);
                missile = -1;
                state = LOWERING;
                timer = 0;
                sync();
            } else if (state == LOWERING && timer >= LOWER_TIME) {
                state = IDLE;
                timer = 0;
                sync();
            }
        }
    }

    public void sendStatus(ServerPlayer sp, boolean open) {
        PacketDistributor.sendToPlayer(sp, new Net.LaunchStatus(worldPosition, true, missile, hasTarget, tx, ty, tz, state, open));
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
        if (tag.hasUUID("Launcher")) launcher = tag.getUUID("Launcher");
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
}
