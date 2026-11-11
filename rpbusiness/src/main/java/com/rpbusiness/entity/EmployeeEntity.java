package com.rpbusiness.entity;

import com.rpbusiness.block.TableBlock;
import com.rpbusiness.business.*;
import com.rpbusiness.core.ModItems;
import com.rpbusiness.economy.Money;
import com.rpbusiness.food.Dish;
import com.rpbusiness.food.Foods;
import com.rpbusiness.server.BusinessManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.function.Predicate;

/**
 * An NPC the player explicitly hired. Its job, salary, schedule, positions and routes live in the business'
 * {@link EmployeeRecord}; this entity carries out the job with real equipment and real ingredients.
 */
public class EmployeeEntity extends NpcEntity {
    public UUID businessId;
    private final SimpleContainer pockets = new SimpleContainer(9);
    private final Deque<Steps.Step> steps = new ArrayDeque<>();
    private int currentOrder = -1;
    private int currentJob = -1;
    private int routeIndex;
    private int failures;
    private long lastReport, quitSince = -1, lastPayTick;
    private String statusKey = "";

    public EmployeeEntity(EntityType<? extends EmployeeEntity> type, Level level) { super(type, level); }

    // ------------------------------------------------------------------ record helpers
    public Business business() { return level() instanceof ServerLevel sl ? BusinessManager.data(sl).byId(businessId) : null; }
    public EmployeeRecord record() { Business b = business(); return b == null ? null : b.employees.get(getUUID()); }

    public int skill() { EmployeeRecord r = record(); return r == null ? 1 : r.skill; }

    public float efficiency() {
        EmployeeRecord r = record();
        float e = r == null ? 1f : r.efficiency();
        Business b = business();
        if (b != null && level() instanceof ServerLevel sl && BusinessManager.managerOnDuty(sl, b)) e += 0.15f;
        if (b != null) e += (b.upgradeLevel - 1) * 0.05f; // kitchen upgrades
        return e;
    }

    public double speed() { return Math.min(1.35, 0.85 + 0.1 * efficiency()); }

    public BlockPos workCenter() {
        EmployeeRecord r = record();
        if (r != null && r.workPos != null) return r.workPos;
        return blockPosition();
    }

    public BlockPos preferredStorage() { EmployeeRecord r = record(); return r == null ? null : r.storagePos; }

    public void setStatus(String key) { statusKey = key; EmployeeRecord r = record(); if (r != null) r.status = key; }

    public void gainXp(int n) {
        EmployeeRecord r = record();
        if (r != null) { r.addXp(n); r.tasksDone++; }
    }

    public void pushFront(Steps.Step s) { steps.addFirst(s); }
    public int currentOrder() { return currentOrder; }

    // ------------------------------------------------------------------ pockets (the NPC's own inventory)
    public void giveItem(ItemStack s) {
        if (s.isEmpty()) return;
        ItemStack rem = pockets.addItem(s);
        if (!rem.isEmpty() && level() instanceof ServerLevel) spawnAtLocation(rem); // never destroyed
    }

    public ItemStack removeItem(Predicate<ItemStack> test, int n) {
        for (int i = 0; i < pockets.getContainerSize(); i++) {
            ItemStack s = pockets.getItem(i);
            if (!s.isEmpty() && test.test(s)) {
                ItemStack r = s.split(Math.min(n, s.getCount()));
                pockets.setChanged();
                return r;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Removes the highest-quality item matching an assembly slot. */
    public ItemStack removeBest(Foods.Group g) {
        int best = -1, bestQ = -1;
        for (int i = 0; i < pockets.getContainerSize(); i++) {
            ItemStack s = pockets.getItem(i);
            if (!s.isEmpty() && g.test(s.getItem()) && Foods.qualityOf(s) > bestQ) { best = i; bestQ = Foods.qualityOf(s); }
        }
        return best < 0 ? ItemStack.EMPTY : pockets.getItem(best).split(1);
    }

    public ItemStack peek(Predicate<ItemStack> test) {
        for (int i = 0; i < pockets.getContainerSize(); i++) {
            ItemStack s = pockets.getItem(i);
            if (!s.isEmpty() && test.test(s)) return s.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    public int countItem(Item it) { return pockets.countItem(it); }

    public Map<Item, Integer> inventoryCounts() {
        Map<Item, Integer> m = new HashMap<>();
        for (int i = 0; i < pockets.getContainerSize(); i++) {
            ItemStack s = pockets.getItem(i);
            if (!s.isEmpty()) m.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        return m;
    }

    // ------------------------------------------------------------------ AI
    @Override
    protected void think() {
        if (!(level() instanceof ServerLevel sl)) return;
        Business b = business();
        EmployeeRecord rec = b == null ? null : b.employees.get(getUUID());
        if (rec == null) { quit(sl, b); return; }
        setOutfit(rec.job.outfit);
        if (!npcName.equals(rec.name)) setNpcName(rec.name);

        int hour = Business.hour(sl);
        if (rec.onStrike()) { dropWork(b); setStatus("strike"); idle(sl, b, rec); label(rec); return; }
        if (!rec.onShift(hour)) { dropWork(b); setStatus("off_duty"); idle(sl, b, rec); label(rec); return; }

        if (!steps.isEmpty()) {
            Steps.Step s = steps.peekFirst();
            setStatus(s.label());
            int r = s.tick(this, sl, b);
            if (r == Steps.DONE) {
                steps.remove(s);
                failures = 0;
                if (steps.isEmpty()) { currentOrder = -1; currentJob = -1; }
            } else if (r == Steps.FAILED) {
                if (com.rpbusiness.server.SelfTest.active())
                    com.rpbusiness.RPBusiness.LOG.info("[SELFTEST] step failed: " + rec.job + " " + s.label() + " at " + blockPosition().toShortString());
                onFail(sl, b);
            }
            label(rec);
            return;
        }
        switch (rec.job) {
            case CHEF -> { if (!pickOrder(sl, b, rec, false) && !tidy(sl, b)) idle(sl, b, rec); }
            case ASSISTANT_CHEF -> {
                if (!(BusinessManager.freeChefs(sl, b) == 0 && pickOrder(sl, b, rec, true)) && !tidy(sl, b) && !prepWork(sl, b)) idle(sl, b, rec);
            }
            case WAITER -> { if (!pickDelivery(sl, b) && !(BusinessManager.countJob(b, Job.CHEF) == 0 && pickOrder(sl, b, rec, true))) patrol(sl, b, rec); }
            case CASHIER -> cashier(sl, b, rec);
            case CLEANER -> { if (!pickCleaning(sl, b)) patrol(sl, b, rec); }
            case MANAGER, DELIVERY_MANAGER -> manager(sl, b, rec);
            case SALES_ASSISTANT -> salesAssistant(sl, b, rec);
            case STOCK_MANAGER -> { if (!restock(sl, b) && !sortCrate(sl, b)) patrol(sl, b, rec); }
            case WAREHOUSE_WORKER, PACKAGE_HANDLER -> { if (!sortCrate(sl, b)) patrol(sl, b, rec); }
            case DELIVERY_DRIVER -> { if (!pickDeliveryJob(sl, b)) idle(sl, b, rec); }
        }
        label(rec);
    }

    private void label(EmployeeRecord rec) {
        String job = Component.translatable(rec.job.key()).getString();
        refreshName("[" + job + "]");
        setCustomNameVisible(false);
    }

    private void onFail(ServerLevel sl, Business b) {
        steps.clear();
        releaseJob(b);
        hold(ItemStack.EMPTY);
        setAction(ACT_NONE);
        if (currentOrder >= 0) {
            Order o = b.order(currentOrder);
            if (o != null && o.status == Order.Status.COOKING) { o.status = Order.Status.PENDING; o.cook = null; o.shortageTick = sl.getGameTime(); }
            currentOrder = -1;
        }
        failures++;
    }

    private void releaseJob(Business b) {
        if (b == null || currentJob < 0) return;
        var j = com.rpbusiness.delivery.Deliveries.job(b, currentJob);
        if (j != null && j.status != com.rpbusiness.delivery.DeliveryJob.Status.DELIVERED && getUUID().equals(j.driver)) {
            j.driver = null;
            j.status = com.rpbusiness.delivery.DeliveryJob.Status.AWAITING_PICKUP;
            j.statusTick = level().getGameTime();
        }
        currentJob = -1;
    }

    /** Abandons the current plan and frees the order for someone else. */
    private void dropWork(Business b) {
        releaseJob(b);
        if (b != null && currentOrder >= 0) {
            Order o = b.order(currentOrder);
            if (o != null && o.status == Order.Status.COOKING && getUUID().equals(o.cook)) { o.status = Order.Status.PENDING; o.cook = null; }
        }
        currentOrder = -1;
        steps.clear();
        setAction(ACT_NONE);
        hold(ItemStack.EMPTY);
    }

    private boolean pickOrder(ServerLevel sl, Business b, EmployeeRecord rec, boolean helper) {
        Order pick = null;
        for (Order o : b.orders) {
            if (o.status != Order.Status.PENDING || o.cook != null) continue;
            if (sl.getGameTime() - o.shortageTick < 200) continue;
            if (pick == null || o.created < pick.created) pick = o;
        }
        if (pick == null) return false;
        Set<Item> missing = new LinkedHashSet<>();
        List<Steps.Step> plan = Steps.plan(sl, b, this, pick.dish, pick, missing);
        if (plan == null) {
            pick.shortageTick = sl.getGameTime();
            StringBuilder sb = new StringBuilder();
            for (Item it : missing) { if (sb.length() > 0) sb.append(", "); sb.append(new ItemStack(it).getHoverName().getString()); }
            pick.shortage = sb.toString();
            setStatus("shortage");
            if (sl.getGameTime() - b.lastShortageMsg > 900) {
                b.lastShortageMsg = sl.getGameTime();
                BusinessManager.notifyOwner(sl, b, Component.translatable("msg.rpbusiness.shortage", npcName,
                        Component.translatable(pick.dish.key()), sb.toString()).withStyle(ChatFormatting.RED), false);
            }
            return false;
        }
        pick.status = Order.Status.COOKING;
        pick.cook = getUUID();
        pick.shortage = null;
        currentOrder = pick.id;
        steps.addAll(plan);
        return true;
    }

    // ------------------------------------------------------------------ shop & delivery jobs
    private void salesAssistant(ServerLevel sl, Business b, EmployeeRecord rec) {
        setStatus("helping");
        CustomerEntity near = null;
        for (CustomerEntity c : sl.getEntitiesOfClass(CustomerEntity.class, getBoundingBox().inflate(8)))
            if (b.id.equals(c.businessId) && c.state == CustomerEntity.State.SHOP) { near = c; break; }
        if (near != null) {
            getLookControl().setLookAt(near);
            if (distanceTo(near) > 2.5) walkTo(near.blockPosition(), 2.0, 0.9);
            else setAction(ACT_TALK);
            return;
        }
        setAction(ACT_NONE);
        if (!rec.route.isEmpty()) { patrol(sl, b, rec); return; }
        List<BlockPos> displays = BusinessManager.equip(sl, b, Equip.DISPLAY);
        if (!displays.isEmpty() && (getNavigation().isDone() && getRandom().nextInt(10) == 0))
            walkTo(standSpotFor(displays.get(getRandom().nextInt(displays.size()))), 1.0, 0.8);
    }

    /** Keeps every shelf stocked with the products the player put there. */
    private boolean restock(ServerLevel sl, Business b) {
        if (sl.getGameTime() % 3 != 0) return false;
        Map<Item, Integer> store = BusinessManager.storageCounts(sl, b);
        EmployeeRecord rec = record();
        List<BlockPos> displays = BusinessManager.equip(sl, b, Equip.DISPLAY);
        if (rec != null && rec.stations.stream().anyMatch(displays::contains)) displays = rec.stations.stream().filter(displays::contains).toList();
        for (BlockPos p : displays) {
            if (!(sl.getBlockEntity(p) instanceof com.rpbusiness.block.DisplayBE d)) continue;
            for (int i = 0; i < 9; i++) {
                String id = d.planogram[i];
                if (id == null) continue;
                Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id));
                ItemStack cur = d.getItem(i);
                if (!cur.isEmpty() && (!cur.is(it) || cur.getCount() >= Math.min(8, cur.getMaxStackSize()))) continue;
                if (store.getOrDefault(it, 0) <= 0) continue;
                steps.add(new Steps.Fetch(it, 16));
                steps.add(new Steps.Restock(p, it));
                return true;
            }
        }
        return false;
    }

    /** Warehouse work: unpacks delivery crates into the shelves/storage. */
    private boolean sortCrate(ServerLevel sl, Business b) {
        if (sl.getGameTime() % 4 != 0) return false;
        for (BlockPos c : BusinessManager.equip(sl, b, Equip.CRATE)) {
            if (!(sl.getBlockEntity(c) instanceof net.minecraft.world.Container cont)) continue;
            for (int i = 0; i < cont.getContainerSize(); i++) {
                ItemStack st = cont.getItem(i);
                if (st.isEmpty() || st.is(com.rpbusiness.core.ModItems.PARCEL.get())) continue;
                if (BusinessManager.findStorageWithSpace(sl, b, null, blockPosition()) == null) return false;
                Item it = st.getItem();
                Steps.Fetch f = new Steps.Fetch(x -> x.is(it), it.getDescriptionId(), 32);
                f.target = c;
                steps.add(f);
                steps.add(new Steps.Store(it));
                return true;
            }
        }
        return false;
    }

    private boolean pickDeliveryJob(ServerLevel sl, Business b) {
        if (!b.type.delivery()) return false;
        for (com.rpbusiness.delivery.DeliveryJob j : com.rpbusiness.delivery.Deliveries.openJobs(b)) {
            if (sl.getGameTime() - j.statusTick < 200 && j.statusTick > 0 && j.status == com.rpbusiness.delivery.DeliveryJob.Status.AWAITING_PICKUP && j.driver == null && failures > 0) continue;
            j.driver = getUUID();
            currentJob = j.id;
            if (j.kind == com.rpbusiness.delivery.DeliveryJob.Kind.REQUEST) {
                int id = j.id;
                steps.add(new Steps.Fetch(s -> com.rpbusiness.delivery.Deliveries.isParcelFor(s, id), "parcel", 1));
                steps.add(new Steps.DeliverParcel(id));
            } else {
                Business from = BusinessManager.data(sl).byId(j.fromBusiness);
                Business to = BusinessManager.data(sl).byId(j.toBusiness);
                if (from == null || to == null) { j.status = com.rpbusiness.delivery.DeliveryJob.Status.CANCELLED; continue; }
                Item it = com.rpbusiness.delivery.Deliveries.itemOf(j);
                Steps.Fetch f = new Steps.Fetch(it, Math.min(64, j.count));
                f.from = from;
                steps.add(f);
                steps.add(new Steps.Store(it, to, () -> com.rpbusiness.delivery.Deliveries.complete(sl, b, j)));
            }
            steps.add(new Steps.ReturnTo(b.entranceOrCenter()));
            return true;
        }
        return false;
    }

    private boolean tidy(ServerLevel sl, Business b) {
        if (sl.getGameTime() % 2 != 0) return false;
        BlockPos p = BusinessManager.findAbandoned(sl, b, blockPosition());
        if (p == null) return false;
        BusinessManager.touch(sl, p); // claim it so other cooks do not come too
        steps.add(new Steps.Tidy(p));
        return true;
    }

    /** Assistant chef: keep a small stock of chopped ingredients for the dishes on the menu. */
    private boolean prepWork(ServerLevel sl, Business b) {
        if (sl.getGameTime() % 3 != 0) return false;
        Map<Item, Integer> store = BusinessManager.storageCounts(sl, b);
        for (Item out : BusinessManager.prepNeeded(b)) {
            Foods.Chop c = Foods.chopProducing(out);
            if (c == null) continue;
            if (store.getOrDefault(out, 0) >= 4) continue;
            if (store.getOrDefault(c.in().get(), 0) <= 0) continue;
            if (BusinessManager.findStorageWithSpace(sl, b, null, blockPosition()) == null) return false;
            steps.add(new Steps.Fetch(c.in().get()));
            steps.add(new Steps.Chop(c.in().get()));
            steps.add(new Steps.Store(out));
            return true;
        }
        return false;
    }

    private boolean pickDelivery(ServerLevel sl, Business b) {
        for (Order o : b.orders) {
            if (o.status != Order.Status.READY || o.readyAt == null) continue;
            UUID claimed = BusinessManager.deliveryClaims(b).get(o.id);
            if (claimed != null && !claimed.equals(getUUID()) && BusinessManager.employee(sl, claimed) != null) continue;
            BusinessManager.deliveryClaims(b).put(o.id, getUUID());
            currentOrder = -1;
            steps.add(new Steps.Deliver(o.id));
            return true;
        }
        // leftovers in the pockets go back to the counter
        if (!peek(s -> Dish.servedBy(s.getItem()) != null).isEmpty()) { steps.add(new Steps.ReturnDish()); return true; }
        return false;
    }

    private boolean pickCleaning(ServerLevel sl, Business b) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        EmployeeRecord rec = record();
        List<BlockPos> tables = rec != null && rec.stations.stream().anyMatch(p -> BusinessManager.isTable(sl, b, p))
                ? rec.stations.stream().filter(p -> BusinessManager.isTable(sl, b, p)).toList()
                : BusinessManager.equip(sl, b, Equip.TABLE);
        for (BlockPos t : tables) {
            if (!sl.isLoaded(t)) continue;
            if (BusinessManager.tableDirty(sl, b, t) && !BusinessManager.tableInUse(sl, b, t)) {
                double d = t.distSqr(blockPosition());
                if (d < bd) { bd = d; best = t; }
            }
        }
        if (best == null) {
            for (BlockPos bed : BusinessManager.equip(sl, b, Equip.BED)) {
                var st = sl.getBlockState(bed);
                if (st.getValue(com.rpbusiness.block.HotelBedBlock.DIRTY) && !b.seatClaims.containsKey(bed)) {
                    double d = bed.distSqr(blockPosition());
                    if (d < bd) { bd = d; best = bed; }
                }
            }
        }
        if (best == null) return false;
        steps.add(new Steps.CleanTable(best));
        return true;
    }

    private void cashier(ServerLevel sl, Business b, EmployeeRecord rec) {
        BlockPos reg = BusinessManager.registerFor(sl, b, rec);
        if (reg == null) { setStatus("no_register"); idle(sl, b, rec); return; }
        BlockPos spot = rec.standPos != null ? rec.standPos : BusinessManager.behind(sl, reg, this);
        if (!walkTo(spot, 0.7, speed()) && !near(reg, 1.9)) { setStatus("walking"); return; }
        lookAtBlock(reg);
        CustomerEntity c = BusinessManager.firstInQueue(sl, b, reg);
        if (c == null) { setAction(ACT_NONE); setStatus("waiting_customers"); return; }
        setAction(ACT_CASHIER);
        setStatus("serving_customer");
        long interval = (long) (50 / efficiency());
        if (sl.getGameTime() - lastPayTick >= interval && c.distanceTo(this) < 4.5) {
            lastPayTick = sl.getGameTime();
            if (BusinessManager.processPayment(sl, b, c, getRandom().nextBoolean() ? "cash" : "card")) gainXp(2);
        }
    }

    private long lastPurchaseCheck;

    private void manager(ServerLevel sl, Business b, EmployeeRecord rec) {
        patrol(sl, b, rec);
        if (sl.getGameTime() - lastPurchaseCheck > 1000) {
            lastPurchaseCheck = sl.getGameTime();
            setStatus("purchasing");
            BusinessManager.autoPurchase(sl, b, this);
        }
        if (sl.getGameTime() - lastReport > 6000) {
            lastReport = sl.getGameTime();
            Finance.Day d = b.finance.day(Business.day(sl));
            int pending = 0;
            for (Order o : b.orders) if (o.active()) pending++;
            BusinessManager.notifyOwner(sl, b, Component.translatable("msg.rpbusiness.manager_report", npcName, b.name,
                    Money.fmt(d.income()), Money.fmt(d.expenses()), d.customers, pending, Money.fmt(b.cash)).withStyle(ChatFormatting.AQUA), false);
        }
    }

    private void patrol(ServerLevel sl, Business b, EmployeeRecord rec) {
        if (rec.route.isEmpty()) { idle(sl, b, rec); return; }
        setStatus("patrolling");
        routeIndex %= rec.route.size();
        BlockPos p = rec.route.get(routeIndex);
        if (walkTo(p, 0.9, 0.8) || isStuck()) {
            clearStuck();
            if (getRandom().nextInt(6) == 0) routeIndex = (routeIndex + 1) % rec.route.size();
        }
    }

    private void idle(ServerLevel sl, Business b, EmployeeRecord rec) {
        if (!statusKey.equals("strike") && !statusKey.equals("off_duty") && !statusKey.equals("no_register") && !statusKey.equals("shortage")) setStatus("idle");
        setAction(ACT_NONE);
        BlockPos home = rec.standPos != null ? rec.standPos : rec.servicePos != null ? rec.servicePos : null;
        if (home == null) home = idleSpot(sl, b, rec);
        if (!walkTo(home, 1.2, 0.8) && isStuck()) { clearStuck(); getNavigation().stop(); }
        if (!steps.isEmpty()) return;
        // leftovers are returned before idling
        if (!peek(s -> Dish.servedBy(s.getItem()) != null).isEmpty() && b.equipment.get(Equip.SERVING).size() > 0) steps.add(new Steps.ReturnDish());
    }

    private BlockPos idleSpotCache;
    private long idleSpotTick = -10000;

    /** Without a player-set position, staff wait next to their own kind of equipment, each at a different spot. */
    private BlockPos idleSpot(ServerLevel sl, Business b, EmployeeRecord rec) {
        if (idleSpotCache != null && sl.getGameTime() - idleSpotTick < 600) return idleSpotCache;
        idleSpotTick = sl.getGameTime();
        Equip[] pref = switch (rec.job) {
            case CHEF, ASSISTANT_CHEF -> new Equip[]{Equip.GRILL, Equip.CUTTING_BOARD, Equip.PREP, Equip.OVEN};
            case WAITER -> new Equip[]{Equip.SERVING, Equip.TABLE};
            case CASHIER -> new Equip[]{Equip.REGISTER, Equip.TERMINAL};
            case STOCK_MANAGER, WAREHOUSE_WORKER, PACKAGE_HANDLER -> new Equip[]{Equip.STORAGE, Equip.CRATE};
            case SALES_ASSISTANT -> new Equip[]{Equip.DISPLAY};
            case DELIVERY_DRIVER, DELIVERY_MANAGER -> new Equip[]{Equip.DELIVERY_TERMINAL, Equip.CRATE};
            case CLEANER -> new Equip[]{Equip.TABLE, Equip.BED, Equip.DISPLAY, Equip.REGISTER};
            default -> new Equip[]{Equip.REGISTER, Equip.COMPUTER, Equip.TABLE, Equip.DISPLAY};
        };
        int salt = Math.floorMod(getUUID().hashCode(), 97);
        BlockPos spot = null;
        if (rec.workPos != null) spot = goodSpot(b, standSpotFor(rec.workPos));
        for (Equip e : pref) {
            if (spot != null) break;
            List<BlockPos> l = BusinessManager.equip(sl, b, e);
            for (int k = 0; k < l.size() && spot == null; k++) spot = goodSpot(b, standSpotFor(l.get((salt + k) % l.size())));
        }
        if (spot == null) {
            // any free walkable spot inside, picked per employee so they do not all stand together
            for (int i = 0; i < 20 && spot == null; i++) {
                int x = b.min.getX() + 1 + Math.floorMod(salt * 31 + i * 17, Math.max(1, b.max.getX() - b.min.getX() - 1));
                int z = b.min.getZ() + 1 + Math.floorMod(salt * 13 + i * 29, Math.max(1, b.max.getZ() - b.min.getZ() - 1));
                for (int y = b.min.getY(); y <= b.min.getY() + 3; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (walkable(p)) { spot = p; break; }
                }
            }
        }
        idleSpotCache = spot != null ? spot : b.entranceOrCenter();
        return idleSpotCache;
    }

    /** A floor-level spot inside the business that an NPC can stand on. */
    private BlockPos goodSpot(Business b, BlockPos p) {
        return p != null && b.contains(p) && walkable(p) && p.getY() <= b.min.getY() + 3 ? p : null;
    }

    private void quit(ServerLevel sl, Business b) {
        setStatus("quit");
        if (quitSince < 0) quitSince = sl.getGameTime();
        // hand back everything carried
        for (int i = 0; i < pockets.getContainerSize(); i++) if (!pockets.getItem(i).isEmpty()) spawnAtLocation(pockets.removeItemNoUpdate(i));
        hold(ItemStack.EMPTY);
        BlockPos exit = b != null ? b.entranceOrCenter() : blockPosition();
        if (walkTo(exit, 1.5, 0.9) || sl.getGameTime() - quitSince > 400) discard();
    }

    // ------------------------------------------------------------------ interaction
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).is(ModItems.STAFF_PLANNER.get())) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        EmployeeRecord r = record();
        Business b = business();
        if (r == null || b == null) return InteractionResult.PASS;
        player.displayClientMessage(Component.translatable("msg.rpbusiness.employee_info", r.name, Component.translatable(r.job.key()),
                Component.translatable(EmployeeRecord.RANKS[Math.min(2, r.rank)]), r.skill, Money.fmt(r.salary),
                r.startHour, r.endHour, Component.translatable("status.rpbusiness." + statusKey.split(":")[0])), false);
        setAction(ACT_TALK);
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------ save
    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        if (businessId != null) t.putUUID("Business", businessId);
        t.put("Pockets", pockets.createTag(registryAccess()));
        t.putInt("Route", routeIndex);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        businessId = t.hasUUID("Business") ? t.getUUID("Business") : null;
        pockets.fromTag(t.getList("Pockets", Tag.TAG_COMPOUND), registryAccess());
        routeIndex = t.getInt("Route");
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel) {
            for (int i = 0; i < pockets.getContainerSize(); i++) if (!pockets.getItem(i).isEmpty()) spawnAtLocation(pockets.removeItemNoUpdate(i));
        }
        super.remove(reason);
    }
}
