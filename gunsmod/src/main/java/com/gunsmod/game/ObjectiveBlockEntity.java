package com.gunsmod.game;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotTeam;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The state of an objective. A capture point holds a balance from -1 (red owns it) to +1 (green owns it): the soldiers
 * of a side standing round it push it their way, and it changes hands at the ends (and goes neutral at 0). A flag stand
 * knows whether its flag is home; a target has its strength and, in Search and Destroy, a planted charge.
 */
public class ObjectiveBlockEntity extends BlockEntity {
    public static final float TARGET_HP = 100f;
    public static final double RADIUS = 7;

    @Nullable private BotTeam owner;
    private float balance;
    private char label = 'A';
    private float hp = TARGET_HP;
    private boolean flagHome = true;
    /** Search and Destroy: ticks until the planted charge goes off (-1 none); planting / defusing progress 0..1 */
    private int charge = -1;
    private float plant, defuse;
    private int syncIn;

    public ObjectiveBlockEntity(BlockPos pos, BlockState state) {
        super(Reg.OBJECTIVE_BE.get(), pos, state);
    }

    public ObjectiveBlock.Kind kind() {
        return getBlockState().getBlock() instanceof ObjectiveBlock b ? b.kind : ObjectiveBlock.Kind.POINT;
    }

    @Nullable
    public BotTeam owner() {
        return owner;
    }

    public void setOwner(@Nullable BotTeam t) {
        owner = t;
        balance = t == null ? 0 : t == BotTeam.GREEN ? 1 : -1;
        sync();
    }

    public float balance() {
        return balance;
    }

    public char label() {
        return label;
    }

    public void setLabel(char c) {
        label = c;
        sync();
    }

    public float hp() {
        return hp;
    }

    public boolean destroyed() {
        return kind() == ObjectiveBlock.Kind.TARGET && hp <= 0;
    }

    public boolean flagHome() {
        return flagHome;
    }

    public void setFlagHome(boolean b) {
        if (flagHome != b) {
            flagHome = b;
            sync();
        }
    }

    public int charge() {
        return charge;
    }

    public float plant() {
        return plant;
    }

    public float defuse() {
        return defuse;
    }

    public Vec3 center() {
        return Vec3.atBottomCenterOf(worldPosition).add(0, 0.5, 0);
    }

    /** a battle starts: everything back to its starting state */
    public void reset(boolean keepOwner) {
        hp = TARGET_HP;
        flagHome = true;
        charge = -1;
        plant = defuse = 0;
        if (!keepOwner) setOwner(null);
        sync();
    }

    /** a capture point is pushed by the soldiers round it (+ green, - red) */
    public void push(float amount) {
        float was = balance;
        balance = Math.max(-1f, Math.min(1f, balance + amount));
        if (balance >= 1f && owner != BotTeam.GREEN) owner = BotTeam.GREEN;
        else if (balance <= -1f && owner != BotTeam.RED) owner = BotTeam.RED;
        else if (owner == BotTeam.GREEN && balance <= 0 || owner == BotTeam.RED && balance >= 0) owner = null;
        if (was != balance) dirty();
    }

    public void damage(float amount) {
        if (hp <= 0) return;
        hp = Math.max(0, hp - amount);
        sync();
    }

    public void setCharge(int ticks) {
        charge = ticks;
        sync();
    }

    public void setPlant(float p) {
        plant = p;
        dirty();
    }

    public void setDefuse(float d) {
        defuse = d;
        dirty();
    }

    public Component status() {
        Component side = owner == null ? Component.translatable("gunsmod.obj.neutral") : Component.translatable("gunsmod.team." + owner.name().toLowerCase());
        return switch (kind()) {
            case POINT -> Component.translatable("gunsmod.obj.status.point", String.valueOf(label), side, Math.round(Math.abs(balance) * 100));
            case FLAG -> Component.translatable("gunsmod.obj.status.flag", side,
                    Component.translatable(flagHome ? "gunsmod.obj.flag_home" : "gunsmod.obj.flag_gone"));
            case TARGET -> Component.translatable("gunsmod.obj.status.target", side, Math.round(hp));
            case DESTINATION -> Component.translatable("gunsmod.obj.status.destination", side);
            case CONTROL -> Component.translatable("block.gunsmod.mission_control");
        };
    }

    public void tick() {
        if (level == null || level.isClientSide) return;
        Objectives.track(this, true);
        if (syncIn > 0 && --syncIn == 0) sync();
    }

    /** a change that the clients should see soon (not at once: capture points change every few ticks) */
    private void dirty() {
        setChanged();
        if (syncIn <= 0) syncIn = 10;
    }

    @Override
    public void setRemoved() {
        Objectives.track(this, false);
        super.setRemoved();
    }

    private void sync() {
        setChanged();
        syncIn = 0;
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("Owner", owner == null ? -1 : owner.ordinal());
        tag.putFloat("Balance", balance);
        tag.putString("Label", String.valueOf(label));
        tag.putFloat("Hp", hp);
        tag.putBoolean("FlagHome", flagHome);
        tag.putInt("Charge", charge);
        tag.putFloat("Plant", plant);
        tag.putFloat("Defuse", defuse);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        int o = tag.contains("Owner") ? tag.getInt("Owner") : -1;
        owner = o < 0 ? null : BotTeam.byId(o);
        balance = tag.getFloat("Balance");
        String l = tag.getString("Label");
        label = l.isEmpty() ? 'A' : l.charAt(0);
        hp = tag.contains("Hp") ? tag.getFloat("Hp") : TARGET_HP;
        flagHome = !tag.contains("FlagHome") || tag.getBoolean("FlagHome");
        charge = tag.contains("Charge") ? tag.getInt("Charge") : -1;
        plant = tag.getFloat("Plant");
        defuse = tag.getFloat("Defuse");
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
