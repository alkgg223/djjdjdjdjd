package com.rpbusiness.entity;

import com.rpbusiness.block.StorageBE;
import com.rpbusiness.business.Business;
import com.rpbusiness.business.Equip;
import com.rpbusiness.business.SupplyOrder;
import com.rpbusiness.core.ModBlocks;
import com.rpbusiness.core.ModItems;
import com.rpbusiness.server.BusinessManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Supplier courier: physically carries purchased goods from outside to the business' delivery crate.
 * Goods only enter the business inventory when the courier arrives.
 */
public class CourierEntity extends NpcEntity {
    public UUID businessId;
    public int supplyId;
    /** >= 0: this NPC is a customer bringing a parcel to the player's delivery company. */
    public int jobId = -1;
    public final SimpleContainer cargo = new SimpleContainer(27);
    private boolean delivered;
    private BlockPos origin;
    private int ticks;

    public CourierEntity(EntityType<? extends CourierEntity> type, Level level) { super(type, level); }

    public void setOrigin(BlockPos p) { origin = p; }

    @Override
    protected void think() {
        if (!(level() instanceof ServerLevel sl)) return;
        ticks += thinkDelta;
        setOutfit(jobId >= 0 ? 0 : 6);
        Business b = BusinessManager.data(sl).byId(businessId);
        if (b == null) { discard(); return; }
        if (delivered) {
            setAction(ACT_NONE);
            hold(ItemStack.EMPTY);
            refreshName("[" + Component.translatable("job.rpbusiness.courier").getString() + "]");
            Player p = sl.getNearestPlayer(this, 20);
            if ((origin == null || walkTo(origin, 2, 1.0) || ticks > 1200) && (p == null || !p.hasLineOfSight(this))) discard();
            if (ticks > 2400) discard();
            return;
        }
        hold(new ItemStack(jobId >= 0 ? com.rpbusiness.core.ModItems.PARCEL.get() : ModBlocks.PACKAGE.get().asItem()));
        setAction(ACT_CARRY);
        BlockPos crate = BusinessManager.findEquip(sl, b, Equip.CRATE, b.entranceOrCenter(),
                be -> be instanceof net.minecraft.world.Container c && com.rpbusiness.block.ContainerUtil.hasSpace(c));
        if (crate == null && jobId >= 0)
            crate = BusinessManager.findStorageWithSpace(sl, b, null, b.entranceOrCenter());
        BlockPos target = crate != null ? standSpotFor(crate) : b.entranceOrCenter();
        boolean arrived = walkTo(target, 1.2, 1.0) || (crate != null && near(crate, 2.0));
        if (!arrived && ticks > 3000 && !b.contains(position()) && blockPosition().distSqr(b.entranceOrCenter()) > 36) {
            // lost on the way: send the order out again with a new courier (cargo stays with the order, no duplication)
            if (jobId >= 0) {
                var j = com.rpbusiness.delivery.Deliveries.job(b, jobId);
                if (j != null) j.status = com.rpbusiness.delivery.DeliveryJob.Status.OFFERED;
            }
            for (SupplyOrder o : b.supplyOrders) if (o.id == supplyId && o.status == SupplyOrder.Status.IN_TRANSIT) {
                o.status = SupplyOrder.Status.ORDERED;
                o.eta = sl.getGameTime() + 200;
            }
            cargo.clearContent();
            delivered = true;
            discard();
            return;
        }
        if (crate != null && arrived) {
            unload(sl, b, crate);
            if (cargo.isEmpty()) { finish(sl, b, false); return; }
        }
        if (crate == null || arrived || ticks > 2400) {
            // No (free) delivery crate: wait at the entrance and hand the goods to the owner in person.
            refreshName("[" + Component.translatable("msg.rpbusiness.courier_waiting").getString() + "]");
            setCustomNameVisible(true);
            if (ticks - lastNotice > 1200 || lastNotice == 0) {
                lastNotice = Math.max(1, ticks);
                BusinessManager.notifyOwner(sl, b, Component.translatable("msg.rpbusiness.courier_wait_notice", b.name).withStyle(ChatFormatting.YELLOW), false);
            }
        } else refreshName("[" + Component.translatable("msg.rpbusiness.courier_tag").getString() + "]");
    }

    private int lastNotice;

    private void unload(ServerLevel sl, Business b, BlockPos crate) {
        if (!(sl.getBlockEntity(crate) instanceof net.minecraft.world.Container st)) return;
        if (sl.getBlockEntity(crate) instanceof com.rpbusiness.block.AnimatedStorageBE a) a.npcOpen(30);
        for (int i = 0; i < cargo.getContainerSize(); i++) {
            ItemStack s = cargo.getItem(i);
            if (!s.isEmpty()) cargo.setItem(i, com.rpbusiness.block.ContainerUtil.insert(st, s));
        }
        b.stockTick = -1000;
    }

    private void finish(ServerLevel sl, Business b, boolean byHand) {
        if (jobId >= 0) {
            com.rpbusiness.delivery.Deliveries.parcelDropped(sl, b, jobId);
            delivered = true;
            ticks = 0;
            setCustomNameVisible(false);
            return;
        }
        BusinessManager.notifyOwner(sl, b, Component.translatable(byHand ? "msg.rpbusiness.delivery_hand" : "msg.rpbusiness.delivery_done", b.name).withStyle(ChatFormatting.GREEN), false);
        for (SupplyOrder o : b.supplyOrders) if (o.id == supplyId) o.status = SupplyOrder.Status.DELIVERED;
        BusinessManager.data(sl).setDirty();
        delivered = true;
        ticks = 0;
        setCustomNameVisible(false);
    }

    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (level().isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level();
        Business b = BusinessManager.data(sl).byId(businessId);
        if (b == null || delivered || !BusinessManager.isOwner(player, b)) return net.minecraft.world.InteractionResult.PASS;
        for (int i = 0; i < cargo.getContainerSize(); i++) {
            ItemStack s = cargo.removeItemNoUpdate(i);
            if (!s.isEmpty() && !player.getInventory().add(s)) player.drop(s, false);
        }
        finish(sl, b, true);
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        if (businessId != null) t.putUUID("Business", businessId);
        t.putInt("Supply", supplyId);
        t.putInt("Job", jobId);
        t.put("Cargo", cargo.createTag(registryAccess()));
        t.putBoolean("Delivered", delivered);
        if (origin != null) t.put("Origin", NbtUtils.writeBlockPos(origin));
        t.putInt("Ticks", ticks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        businessId = t.hasUUID("Business") ? t.getUUID("Business") : null;
        supplyId = t.getInt("Supply");
        jobId = t.contains("Job") ? t.getInt("Job") : -1;
        cargo.fromTag(t.getList("Cargo", Tag.TAG_COMPOUND), registryAccess());
        delivered = t.getBoolean("Delivered");
        origin = NbtUtils.readBlockPos(t, "Origin").orElse(null);
        ticks = t.getInt("Ticks");
    }

    @Override
    public void remove(RemovalReason reason) {
        // never lose purchased goods
        if (reason.shouldDestroy() && !delivered && level() instanceof ServerLevel)
            for (int i = 0; i < cargo.getContainerSize(); i++) if (!cargo.getItem(i).isEmpty()) spawnAtLocation(cargo.removeItemNoUpdate(i));
        super.remove(reason);
    }
}
