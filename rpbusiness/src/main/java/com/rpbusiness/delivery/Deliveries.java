package com.rpbusiness.delivery;

import com.rpbusiness.bank.Account;
import com.rpbusiness.bank.Bank;
import com.rpbusiness.business.*;
import com.rpbusiness.core.ModComponents;
import com.rpbusiness.core.ModEntities;
import com.rpbusiness.core.ModItems;
import com.rpbusiness.economy.Money;
import com.rpbusiness.entity.CourierEntity;
import com.rpbusiness.server.BusinessManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.UUID;

/** Player-owned delivery company: customer delivery requests and transfers between the player's own businesses. */
public final class Deliveries {
    private Deliveries() {}

    private static int nextId(ServerLevel sl) {
        BusinessData d = BusinessData.get(sl);
        d.nextSupplyId++;
        d.setDirty();
        return 100000 + d.nextSupplyId;
    }

    public static DeliveryJob job(Business b, int id) {
        for (DeliveryJob j : b.deliveries) if (j.id == id) return j;
        return null;
    }

    public static long standardFee(double distance) { return 12 + Math.round(distance * 0.5); }

    /** New customer requests appear every day; higher prices mean fewer requests. */
    public static void generateOffers(ServerLevel sl, Business b) {
        long day = Business.day(sl);
        if (b.requestDay == day) return;
        b.requestDay = day;
        b.deliveries.removeIf(j -> j.status == DeliveryJob.Status.OFFERED);
        b.deliveries.removeIf(j -> (j.status == DeliveryJob.Status.DELIVERED || j.status == DeliveryJob.Status.CANCELLED) && b.deliveries.size() > 30);
        RandomSource r = sl.getRandom();
        int n = Math.max(1, Math.min(6, Math.round(4f * 100f / Math.max(30, b.deliveryRate)) + r.nextInt(2)));
        BlockPos c = b.entranceOrCenter();
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            int dist = 25 + r.nextInt(50);
            int x = c.getX() + (int) (Math.cos(ang) * dist), z = c.getZ() + (int) (Math.sin(ang) * dist);
            BlockPos dest = sl.isLoaded(new BlockPos(x, 64, z)) ? sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)) : new BlockPos(x, c.getY(), z);
            DeliveryJob j = new DeliveryJob();
            j.id = nextId(sl);
            j.kind = DeliveryJob.Kind.REQUEST;
            j.client = Names.random(r);
            j.dest = dest;
            j.reward = standardFee(dist) * b.deliveryRate / 100;
            j.created = sl.getGameTime();
            b.deliveries.add(j);
        }
    }

    /** The player accepts a request: the sender walks over and drops the parcel at the office. */
    public static boolean accept(ServerPlayer p, Business b, int id) { return accept(p.serverLevel(), p, b, id); }

    public static boolean accept(ServerLevel sl, ServerPlayer p, Business b, int id) {
        DeliveryJob j = job(b, id);
        if (j == null || j.status != DeliveryJob.Status.OFFERED) return false;
        CourierEntity sender = ModEntities.COURIER.get().create(sl);
        if (sender == null) return false;
        BlockPos start = BusinessManager.reachableSpawn(sl, sender, b.entranceOrCenter(), 10, 20);
        if (start == null) start = BusinessManager.reachableSpawn(sl, sender, b.entranceOrCenter(), 4, 10);
        if (start == null) {
            if (p != null) p.displayClientMessage(Component.translatable("msg.rpbusiness.no_path_office").withStyle(ChatFormatting.RED), false);
            return false;
        }
        sender.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0, 0);
        sender.businessId = b.id;
        sender.jobId = j.id;
        sender.setOrigin(start);
        sender.setNpcName(j.client);
        sender.setSkin(sl.getRandom().nextInt(8));
        sender.cargo.addItem(parcel(j));
        sl.addFreshEntity(sender);
        j.status = DeliveryJob.Status.SENDER_COMING;
        j.statusTick = sl.getGameTime();
        BusinessData.get(sl).setDirty();
        return true;
    }

    public static ItemStack parcel(DeliveryJob j) {
        ItemStack s = new ItemStack(ModItems.PARCEL.get());
        s.set(ModComponents.PARCEL_JOB.get(), j.id);
        s.set(ModComponents.PARCEL_DEST.get(), j.dest);
        return s;
    }

    public static boolean isParcelFor(ItemStack s, int jobId) {
        return s.is(ModItems.PARCEL.get()) && Integer.valueOf(jobId).equals(s.get(ModComponents.PARCEL_JOB.get()));
    }

    /** Sender NPC left the parcel in the office storage. */
    public static void parcelDropped(ServerLevel sl, Business b, int id) {
        DeliveryJob j = job(b, id);
        if (j == null) return;
        j.status = DeliveryJob.Status.AWAITING_PICKUP;
        j.statusTick = sl.getGameTime();
        BusinessManager.notifyOwner(sl, b, Component.translatable("msg.rpbusiness.parcel_dropped", j.id, j.client).withStyle(ChatFormatting.YELLOW), false);
        BusinessData.get(sl).setDirty();
    }

    public static DeliveryJob createTransfer(ServerPlayer p, Business company, Business from, Business to, Item item, int count) {
        if (from == null || to == null || !BusinessManager.isOwner(p, from) || !BusinessManager.isOwner(p, to)) return null;
        return createTransfer(p.serverLevel(), company, from, to, item, count);
    }

    public static DeliveryJob createTransfer(ServerLevel sl, Business company, Business from, Business to, Item item, int count) {
        if (from == null || to == null || from == to || count <= 0) return null;
        DeliveryJob j = new DeliveryJob();
        j.id = nextId(sl);
        j.kind = DeliveryJob.Kind.TRANSFER;
        j.status = DeliveryJob.Status.AWAITING_PICKUP;
        j.fromBusiness = from.id;
        j.toBusiness = to.id;
        j.item = BuiltInRegistries.ITEM.getKey(item).toString();
        j.count = count;
        j.client = to.name;
        j.dest = to.entranceOrCenter();
        double dist = Math.sqrt(from.center().distSqr(to.center()));
        j.reward = standardFee(dist) * company.deliveryRate / 100;
        j.created = sl.getGameTime();
        company.deliveries.add(j);
        BusinessData.get(sl).setDirty();
        return j;
    }

    public static Item itemOf(DeliveryJob j) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(j.item)); }

    /** Job finished: the reward is paid electronically into the delivery company's bank account. */
    public static void complete(ServerLevel sl, Business company, DeliveryJob j) {
        if (j.status == DeliveryJob.Status.DELIVERED) return;
        j.status = DeliveryJob.Status.DELIVERED;
        j.statusTick = sl.getGameTime();
        Bank bank = Bank.get(sl);
        Account acc = bank.businessAccount(sl, company);
        long reward = j.reward;
        if (j.kind == DeliveryJob.Kind.TRANSFER) {
            // the receiving business pays the fee (internal between the player's own businesses)
            Business to = BusinessData.get(sl).byId(j.toBusiness);
            if (to != null && to != company) {
                if (to.cash >= reward) {
                    to.cash -= reward;
                    to.finance.record(Business.day(sl), Business.hour(sl), Finance.Cat.DELIVERY, -reward, company.name + " #" + j.id);
                } else {
                    Account ta = bank.businessAccount(sl, to);
                    if (!bank.debit(sl, ta, reward, company.name + " #" + j.id)) reward = 0;
                    else to.finance.record(Business.day(sl), Business.hour(sl), Finance.Cat.DELIVERY, -reward, company.name + " #" + j.id);
                }
            }
        }
        if (reward > 0) {
            bank.credit(sl, acc, reward, "Delivery #" + j.id + " - " + j.client);
            company.finance.record(Business.day(sl), Business.hour(sl), Finance.Cat.DELIVERY_INCOME, reward, "#" + j.id + " " + j.client);
            company.finance.day(Business.day(sl)).customers++;
        }
        company.reputation = Math.min(1000, company.reputation + 8);
        BusinessManager.notifyOwner(sl, company, Component.translatable("msg.rpbusiness.delivery_complete", j.id, Money.fmt(reward)).withStyle(ChatFormatting.GREEN), false);
        BusinessData.get(sl).setDirty();
    }

    /** The player delivers a parcel personally. */
    public static void handOverByPlayer(ServerPlayer p, ItemStack st) {
        ServerLevel sl = p.serverLevel();
        Integer id = st.get(ModComponents.PARCEL_JOB.get());
        if (id == null) return;
        for (Business b : BusinessData.get(sl).businesses.values()) {
            DeliveryJob j = job(b, id);
            if (j == null) continue;
            if (j.status == DeliveryJob.Status.DELIVERED || j.status == DeliveryJob.Status.CANCELLED) { st.shrink(1); return; }
            double d = Math.sqrt(p.blockPosition().distSqr(j.dest));
            if (d > 4.5) {
                p.displayClientMessage(Component.translatable("msg.rpbusiness.parcel_far", (int) d, j.dest.getX(), j.dest.getY(), j.dest.getZ()).withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            st.shrink(1);
            complete(sl, b, j);
            return;
        }
    }

    public static List<DeliveryJob> openJobs(Business b) {
        return b.deliveries.stream().filter(j -> j.status == DeliveryJob.Status.AWAITING_PICKUP && j.driver == null).toList();
    }

    public static void tick(ServerLevel sl, Business b) {
        if (!b.type.delivery()) return;
        generateOffers(sl, b);
        // drivers that vanished release their jobs (the goods stay in their pockets / storage, never duplicated)
        for (DeliveryJob j : b.deliveries) {
            if (j.driver != null && j.status == DeliveryJob.Status.AWAITING_PICKUP && BusinessManager.employee(sl, j.driver) == null && sl.isLoaded(b.center()))
                j.driver = null;
        }
    }

    @SuppressWarnings("unused")
    private static UUID none() { return null; }
}
