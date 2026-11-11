package com.rpbusiness.entity;

import com.rpbusiness.block.*;
import com.rpbusiness.business.Business;
import com.rpbusiness.business.Equip;
import com.rpbusiness.business.Order;
import com.rpbusiness.core.ModItems;
import com.rpbusiness.core.ModSounds;
import com.rpbusiness.food.Dish;
import com.rpbusiness.food.Foods;
import com.rpbusiness.server.BusinessManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;
import java.util.function.Predicate;

/**
 * Work steps executed by employees. Every step uses the real equipment and the real items: nothing is
 * created from thin air and nothing is teleported. If something is missing the step fails and the plan
 * is rebuilt (or a shortage is reported).
 */
public final class Steps {
    private Steps() {}

    public static final int RUNNING = 0, DONE = 1, FAILED = -1;

    public interface Step {
        int tick(EmployeeEntity e, ServerLevel sl, Business b);
        String label();
    }

    /** Ticks between NPC think calls is variable, so steps measure time with the level clock. */
    static long now(ServerLevel sl) { return sl.getGameTime(); }

    // ============================================================ FETCH
    public static final class Fetch implements Step {
        final Predicate<ItemStack> test;
        final String what;
        BlockPos target;
        long arrived = -1;

        public Fetch(Item item) { this(s -> s.is(item), item.getDescriptionId()); }
        public Fetch(Predicate<ItemStack> test, String what) { this.test = test; this.what = what; }

        @Override public String label() { return "fetch:" + what; }

        @Override
        public int tick(EmployeeEntity e, ServerLevel sl, Business b) {
            if (target == null || !(sl.getBlockEntity(target) instanceof Container)) {
                target = BusinessManager.findStorageWith(sl, b, test, e.preferredStorage(), e.blockPosition());
                if (target == null) return FAILED;
            }
            BlockPos spot = e.standSpotFor(target);
            if (!e.walkTo(spot, 0.9, e.speed()) && !e.near(target, 1.9)) {
                if (e.isStuck()) { e.clearStuck(); target = null; return FAILED; }
                return RUNNING;
            }
            e.lookAtBlock(target);
            e.setAction(NpcEntity.ACT_REACH);
            if (arrived < 0) {
                arrived = now(sl);
                if (sl.getBlockEntity(target) instanceof AnimatedStorageBE a) a.npcOpen(30);
            }
            if (now(sl) - arrived < 14) return RUNNING;
            if (!(sl.getBlockEntity(target) instanceof Container st)) return FAILED;
            List<ItemStack> out = new ArrayList<>();
            if (ContainerUtil.extract(st, test, 1, out) == 0) { target = null; arrived = -1; return FAILED; }
            for (ItemStack s : out) e.giveItem(s);
            e.setAction(NpcEntity.ACT_NONE);
            b.stockTick = -1000; // stock changed
            return DONE;
        }
    }

    // ============================================================ STORE
    public static final class Store implements Step {
        final Item item;
        BlockPos target;
        long arrived = -1;

        public Store(Item item) { this.item = item; }
        @Override public String label() { return "store:" + item.getDescriptionId(); }

        @Override
        public int tick(EmployeeEntity e, ServerLevel sl, Business b) {
            if (e.countItem(item) == 0) return DONE;
            if (target == null || !(sl.getBlockEntity(target) instanceof Container)) {
                target = BusinessManager.findStorageWithSpace(sl, b, e.preferredStorage(), e.blockPosition());
                if (target == null) return FAILED;
            }
            if (!e.walkTo(e.standSpotFor(target), 0.9, e.speed()) && !e.near(target, 1.9)) {
                if (e.isStuck()) { e.clearStuck(); target = null; return FAILED; }
                return RUNNING;
            }
            e.lookAtBlock(target);
            e.setAction(NpcEntity.ACT_REACH);
            if (arrived < 0) {
                arrived = now(sl);
                if (sl.getBlockEntity(target) instanceof AnimatedStorageBE a) a.npcOpen(30);
            }
            if (now(sl) - arrived < 12) return RUNNING;
            if (!(sl.getBlockEntity(target) instanceof Container st)) return FAILED;
            ItemStack s = e.removeItem(x -> x.is(item), 64);
            ItemStack rem = ContainerUtil.insert(st, s);
            if (!rem.isEmpty()) { e.giveItem(rem); target = null; arrived = -1; return RUNNING; }
            e.setAction(NpcEntity.ACT_NONE);
            b.stockTick = -1000;
            return DONE;
        }
    }

    // ============================================================ CHOP
    public static final class Chop implements Step {
        final Item input;
        BlockPos board;
        boolean placed;
        long lastChop;

        public Chop(Item input) { this.input = input; }
        @Override public String label() { return "chop:" + input.getDescriptionId(); }

        @Override
        public int tick(EmployeeEntity e, ServerLevel sl, Business b) {
            if (board == null) {
                board = BusinessManager.findStation(sl, b, e, Equip.CUTTING_BOARD,
                        be -> be instanceof CuttingBoardBE c && c.isEmpty());
                if (board == null) { e.setStatus("waiting:cutting_board"); return RUNNING; }
            }
            if (!(sl.getBlockEntity(board) instanceof CuttingBoardBE cb)) { board = null; return FAILED; }
            if (!e.walkTo(e.standSpotFor(board), 0.9, e.speed()) && !e.near(board, 1.9)) {
                if (e.isStuck()) { e.clearStuck(); board = null; return FAILED; }
                return RUNNING;
            }
            e.lookAtBlock(board);
            BusinessManager.touch(sl, board);
            if (!placed) {
                if (!cb.isEmpty()) { board = null; return RUNNING; }
                ItemStack s = e.removeItem(x -> x.is(input), 1);
                if (s.isEmpty()) return FAILED;
                cb.place(s);
                placed = true;
                e.hold(new ItemStack(ModItems.KITCHEN_KNIFE.get()));
                lastChop = now(sl);
                return RUNNING;
            }
            if (cb.isEmpty()) return FAILED; // someone took it
            e.setAction(NpcEntity.ACT_CHOP);
            if (now(sl) - lastChop >= Math.max(6, (int) (16 / e.efficiency()))) {
                lastChop = now(sl);
                if (cb.isChoppable()) cb.chop();
                if (!cb.isChoppable()) {
                    e.giveItem(cb.take());
                    e.hold(ItemStack.EMPTY);
                    e.setAction(NpcEntity.ACT_NONE);
                    e.gainXp(2);
                    return DONE;
                }
            }
            return RUNNING;
        }
    }

    // ============================================================ COOK (grill / fryer / oven / pot)
    public static final class Cook implements Step {
        final Item input;
        final Foods.Cooker cooker;
        final int targetStage;
        int wantStage = -2;
        BlockPos pos;
        int slot = -1;

        public Cook(Item input, Foods.Cooker cooker, int targetStage) { this.input = input; this.cooker = cooker; this.targetStage = targetStage; }
        @Override public String label() { return "cook:" + cooker.name().toLowerCase(Locale.ROOT); }

        @Override
        public int tick(EmployeeEntity e, ServerLevel sl, Business b) {
            if (wantStage == -2) {
                // skill decides how precisely the cook takes food off the heat
                float perfect = 0.55f + 0.09f * e.skill();
                float r = e.getRandom().nextFloat();
                wantStage = r < perfect ? targetStage : (r < perfect + (1 - perfect) / 2 ? Math.max(0, targetStage - 1) : Math.min(2, targetStage + 1));
            }
            Equip eq = switch (cooker) { case GRILL -> Equip.GRILL; case FRYER -> Equip.FRYER; case OVEN -> Equip.OVEN; case POT -> Equip.POT; };
            if (pos == null) {
                pos = BusinessManager.findStation(sl, b, e, eq, be -> be instanceof CookerBE c && c.freeSlot() >= 0);
                if (pos == null) { e.setStatus("waiting:" + cooker.name().toLowerCase(Locale.ROOT)); return RUNNING; }
            }
            if (!(sl.getBlockEntity(pos) instanceof CookerBE c)) { pos = null; return FAILED; }
            if (!e.walkTo(e.standSpotFor(pos), 0.9, e.speed()) && !e.near(pos, 1.9)) {
                if (e.isStuck()) { e.clearStuck(); pos = null; return FAILED; }
                return RUNNING;
            }
            e.lookAtBlock(pos);
            BusinessManager.touch(sl, pos);
            if (slot < 0) {
                if (c.freeSlot() < 0) { pos = null; return RUNNING; }
                ItemStack s = e.removeItem(x -> x.is(input), 1);
                if (s.isEmpty()) return FAILED;
                slot = c.insert(s);
                if (slot < 0) { e.giveItem(s); pos = null; return RUNNING; }
                if (cooker == Foods.Cooker.GRILL) e.hold(new ItemStack(ModItems.SPATULA.get()));
                return RUNNING;
            }
            if (!c.occupied(slot)) return FAILED; // the player took it off
            e.setAction(NpcEntity.ACT_COOK);
            Foods.CookLine line = c.line(slot);
            if (line != null && line.needsFlip() && !c.flipped(slot) && c.ticks(slot) >= line.times()[1] * 0.5) c.flip(slot);
            if (c.stage(slot) >= wantStage) {
                e.giveItem(c.take(slot));
                e.hold(ItemStack.EMPTY);
                e.setAction(NpcEntity.ACT_NONE);
                e.gainXp(3);
                return DONE;
            }
            return RUNNING;
        }
    }

    // ============================================================ ASSEMBLE
    public static final class Assemble implements Step {
        final Foods.Assembly recipe;
        BlockPos pos;
        int added;
        long last;

        public Assemble(Foods.Assembly recipe) { this.recipe = recipe; }
        @Override public String label() { return "assemble:" + recipe.id(); }

        @Override
        public int tick(EmployeeEntity e, ServerLevel sl, Business b) {
            if (pos == null) {
                pos = BusinessManager.findStation(sl, b, e, Equip.PREP, be -> be instanceof PrepStationBE p && p.isEmpty());
                if (pos == null) { e.setStatus("waiting:prep_station"); return RUNNING; }
            }
            if (!(sl.getBlockEntity(pos) instanceof PrepStationBE p)) { pos = null; return FAILED; }
            if (!e.walkTo(e.standSpotFor(pos), 0.9, e.speed()) && !e.near(pos, 1.9)) {
                if (e.isStuck()) { e.clearStuck(); pos = null; return FAILED; }
                return RUNNING;
            }
            e.lookAtBlock(pos);
            BusinessManager.touch(sl, pos);
            if (added == 0 && !p.isEmpty()) { pos = null; return RUNNING; }
            e.setAction(NpcEntity.ACT_COOK);
            if (now(sl) - last < 8) return RUNNING;
            last = now(sl);
            if (added < recipe.parts().size()) {
                Foods.Group g = recipe.parts().get(added);
                ItemStack s = e.removeBest(g);
                if (s.isEmpty()) {
                    // give back what was already placed
                    while (!p.isEmpty()) e.giveItem(p.takeLast());
                    return FAILED;
                }
                p.add(s);
                added++;
                return RUNNING;
            }
            ItemStack r = p.assemble();
            if (r.isEmpty()) { while (!p.isEmpty()) e.giveItem(p.takeLast()); return FAILED; }
            e.giveItem(r);
            e.setAction(NpcEntity.ACT_NONE);
            e.gainXp(3);
            return DONE;
        }
    }

    // ============================================================ DRINK
    public static final class Drink implements Step {
        final int mode;
        BlockPos pos;
        boolean started;

        public Drink(int mode) { this.mode = mode; }
        @Override public String label() { return "drink"; }

        @Override
        public int tick(EmployeeEntity e, ServerLevel sl, Business b) {
            if (pos == null) {
                pos = BusinessManager.findStation(sl, b, e, Equip.DRINK, be -> be instanceof DrinkMachineBE d && d.canDispense(mode));
                if (pos == null) return FAILED;
            }
            if (!(sl.getBlockEntity(pos) instanceof DrinkMachineBE d)) { pos = null; return FAILED; }
            if (!e.walkTo(e.standSpotFor(pos), 0.9, e.speed()) && !e.near(pos, 1.9)) {
                if (e.isStuck()) { e.clearStuck(); pos = null; return FAILED; }
                return RUNNING;
            }
            e.lookAtBlock(pos);
            BusinessManager.touch(sl, pos);
            e.setAction(NpcEntity.ACT_REACH);
            if (!started) {
                if (!d.canDispense(mode)) { pos = null; return RUNNING; }
                ItemStack cup = e.removeItem(x -> x.is(ModItems.PAPER_CUP.get()), 1);
                if (cup.isEmpty()) return FAILED;
                if (!d.start(mode)) { e.giveItem(cup); pos = null; return RUNNING; }
                started = true;
                return RUNNING;
            }
            if (d.busy()) return RUNNING;
            if (d.output().isEmpty()) return FAILED;
            e.giveItem(d.take());
            e.setAction(NpcEntity.ACT_NONE);
            e.gainXp(1);
            return DONE;
        }
    }

    // ============================================================ PLACE ON SERVING COUNTER (or hand over directly)
    public static final class Serve implements Step {
        final int orderId;
        BlockPos pos;

        public Serve(int orderId) { this.orderId = orderId; }
        @Override public String label() { return "serve"; }

        @Override
        public int tick(EmployeeEntity e, ServerLevel sl, Business b) {
            Order o = b.order(orderId);
            if (o == null || !o.active()) {
                // customer is gone: keep the dish on the counter for the next customer who orders it
                o = null;
            }
            Dish dish = o != null ? o.dish : null;
            ItemStack hand = dish != null ? e.peek(dish::accepts) : e.peek(s -> Dish.servedBy(s.getItem()) != null);
            if (hand.isEmpty()) return FAILED;
            e.hold(hand);
            if (pos == null) {
                pos = BusinessManager.findStation(sl, b, e, Equip.SERVING, be -> be instanceof ServingCounterBE c && c.hasSpace());
                if (pos == null) {
                    // no serving counter: the cook brings the dish to the table personally
                    if (o == null) return FAILED;
                    Deliver d = new Deliver(orderId);
                    d.picked = true;
                    e.pushFront(d);
                    return DONE;
                }
            }
            if (!(sl.getBlockEntity(pos) instanceof ServingCounterBE c)) { pos = null; return RUNNING; }
            e.setAction(NpcEntity.ACT_CARRY);
            if (!e.walkTo(e.standSpotFor(pos), 0.9, e.speed()) && !e.near(pos, 1.9)) {
                if (e.isStuck()) { e.clearStuck(); pos = null; return FAILED; }
                return RUNNING;
            }
            e.lookAtBlock(pos);
            if (!c.hasSpace()) { pos = null; return RUNNING; }
            ItemStack dishStack = e.removeItem(s -> ItemStack.isSameItemSameComponents(s, hand), 1);
            c.insert(dishStack);
            e.hold(ItemStack.EMPTY);
            e.setAction(NpcEntity.ACT_NONE);
            if (o != null) {
                o.status = Order.Status.READY;
                o.readyAt = pos;
            }
            sl.playSound(null, pos, ModSounds.ORDER_BELL.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            e.gainXp(2);
            return DONE;
        }
    }

    // ============================================================ DELIVER TO CUSTOMER (waiter)
    public static final class Deliver implements Step {
        final int orderId;
        boolean picked;
        long start = -1;

        public Deliver(int orderId) { this.orderId = orderId; }
        @Override public String label() { return "deliver"; }

        @Override
        public int tick(EmployeeEntity e, ServerLevel sl, Business b) {
            Order o = b.order(orderId);
            if (start < 0) start = now(sl);
            if (o == null || !o.active()) {
                if (picked) e.pushFront(new ReturnDish());
                return DONE;
            }
            if (!picked) {
                BlockPos at = o.readyAt;
                if (at == null || !(sl.getBlockEntity(at) instanceof ServingCounterBE c)) { o.status = Order.Status.PENDING; o.readyAt = null; return FAILED; }
                if (!e.walkTo(e.standSpotFor(at), 0.9, e.speed()) && !e.near(at, 1.9)) {
                    if (e.isStuck()) { e.clearStuck(); return FAILED; }
                    return RUNNING;
                }
                e.lookAtBlock(at);
                int i = c.find(o.dish);
                if (i < 0) { o.status = Order.Status.PENDING; o.readyAt = null; return FAILED; }
                e.giveItem(c.take(i));
                picked = true;
                o.cook = e.getUUID();
            }
            ItemStack dish = e.peek(o.dish::accepts);
            if (dish.isEmpty()) { o.status = Order.Status.PENDING; o.readyAt = null; return FAILED; }
            e.hold(dish);
            e.setAction(NpcEntity.ACT_CARRY);
            CustomerEntity c = BusinessManager.customer(sl, o.customer);
            if (c == null) return RUNNING;
            BlockPos target = o.seat != null ? o.seat : c.blockPosition();
            if (e.distanceTo(c) > 2.3) {
                e.walkTo(e.standSpotFor(target), 0.8, e.speed());
                if (e.isStuck() && now(sl) - start > 600) { e.clearStuck(); }
                return RUNNING;
            }
            e.getLookControl().setLookAt(c);
            ItemStack one = e.removeItem(s -> ItemStack.isSameItemSameComponents(s, dish), 1);
            if (!BusinessManager.serve(sl, b, c, o, one)) { e.giveItem(one); e.hold(ItemStack.EMPTY); return DONE; }
            e.hold(ItemStack.EMPTY);
            e.setAction(NpcEntity.ACT_NONE);
            e.gainXp(2);
            return DONE;
        }
    }

    /** Puts a dish nobody wants any more back on a serving counter (or in storage). */
    public static final class ReturnDish implements Step {
        BlockPos pos;
        @Override public String label() { return "return"; }

        @Override
        public int tick(EmployeeEntity e, ServerLevel sl, Business b) {
            ItemStack d = e.peek(s -> Dish.servedBy(s.getItem()) != null);
            if (d.isEmpty()) { e.hold(ItemStack.EMPTY); return DONE; }
            if (pos == null) pos = BusinessManager.findStation(sl, b, e, Equip.SERVING, be -> be instanceof ServingCounterBE c && c.hasSpace());
            if (pos == null) {
                e.pushFront(new Store(d.getItem()));
                return DONE;
            }
            if (!(sl.getBlockEntity(pos) instanceof ServingCounterBE c)) { pos = null; return RUNNING; }
            if (!e.walkTo(e.standSpotFor(pos), 0.9, e.speed()) && !e.near(pos, 1.9)) return e.isStuck() ? FAILED : RUNNING;
            if (!c.hasSpace()) { pos = null; return RUNNING; }
            c.insert(e.removeItem(s -> ItemStack.isSameItemSameComponents(s, d), 1));
            e.hold(ItemStack.EMPTY);
            e.setAction(NpcEntity.ACT_NONE);
            BusinessManager.onDishOnCounter(sl, pos);
            return DONE;
        }
    }

    // ============================================================ TIDY (clear abandoned food from a station)
    public static final class Tidy implements Step {
        final BlockPos pos;
        long last;
        public Tidy(BlockPos pos) { this.pos = pos; }
        @Override public String label() { return "tidy"; }

        @Override
        public int tick(EmployeeEntity e, ServerLevel sl, Business b) {
            BlockEntity be = sl.getBlockEntity(pos);
            if (be == null) return DONE;
            if (!e.walkTo(e.standSpotFor(pos), 0.9, e.speed()) && !e.near(pos, 1.9)) {
                if (e.isStuck()) { e.clearStuck(); return FAILED; }
                return RUNNING;
            }
            e.lookAtBlock(pos);
            BusinessManager.touch(sl, pos);
            List<ItemStack> got = new ArrayList<>();
            if (be instanceof CookerBE c) {
                for (int i = 0; i < c.size(); i++) if (c.occupied(i) && c.stage(i) >= 1) got.add(c.take(i));
            } else if (be instanceof CuttingBoardBE cb) {
                if (cb.isChoppable()) {
                    // finish the job someone started
                    e.setAction(NpcEntity.ACT_CHOP);
                    if (now(sl) - last >= 12) { last = now(sl); cb.chop(); }
                    if (cb.isChoppable()) return RUNNING;
                }
                got.add(cb.take());
            } else if (be instanceof PrepStationBE ps) {
                if (ps.match() != null) got.add(ps.assemble());
                else while (!ps.isEmpty()) got.add(ps.takeLast());
            } else if (be instanceof DrinkMachineBE dm) {
                got.add(dm.take());
            }
            e.setAction(NpcEntity.ACT_NONE);
            for (ItemStack g : got) {
                if (g.isEmpty()) continue;
                e.giveItem(g);
                if (Dish.servedBy(g.getItem()) != null) e.pushFront(new ReturnDish());
                else e.pushFront(new Store(g.getItem()));
            }
            return DONE;
        }
    }

    // ============================================================ CLEAN TABLE
    public static final class CleanTable implements Step {
        final BlockPos table;
        long started = -1;
        public CleanTable(BlockPos table) { this.table = table; }
        @Override public String label() { return "clean"; }

        @Override
        public int tick(EmployeeEntity e, ServerLevel sl, Business b) {
            if (!BusinessManager.tableDirty(sl, b, table)) return DONE;
            e.hold(new ItemStack(ModItems.MOP.get()));
            if (!e.walkTo(e.standSpotFor(table), 0.9, e.speed()) && !e.near(table, 1.9)) {
                if (e.isStuck()) { e.clearStuck(); return FAILED; }
                return RUNNING;
            }
            e.lookAtBlock(table);
            e.setAction(NpcEntity.ACT_CLEAN);
            if (started < 0) started = now(sl);
            if (now(sl) - started < (long) (60 / e.efficiency())) return RUNNING;
            BusinessManager.cleanTable(sl, b, table);
            e.hold(ItemStack.EMPTY);
            e.setAction(NpcEntity.ACT_NONE);
            e.gainXp(1);
            return DONE;
        }
    }

    // ============================================================ PLANNER
    /**
     * Builds the list of steps needed to produce one serving of a dish from what really exists in the
     * employee's pockets and the business storage. Returns null and fills {@code missing} when an
     * ingredient is not available anywhere.
     */
    public static List<Step> plan(ServerLevel sl, Business b, EmployeeEntity e, Dish dish, Order order, Set<Item> missing) {
        Map<Item, Integer> inv = e.inventoryCounts();
        Map<Item, Integer> store = new HashMap<>(BusinessManager.storageCounts(sl, b));
        List<Step> out = new ArrayList<>();
        List<Item> wanted = new ArrayList<>();
        for (var s : dish.servedItems()) {
            Item it = s.get();
            int st = Foods.stageIndex(it);
            if (st < 0 || Foods.STAGE_QUALITY[st] >= 60) wanted.add(it);
        }
        if (!obtain(sl, b, wanted, out, inv, store, missing, 0)) return null;
        out.add(new Serve(order.id));
        return out;
    }

    private static boolean take(Map<Item, Integer> m, Item it) {
        Integer n = m.get(it);
        if (n == null || n <= 0) return false;
        m.put(it, n - 1);
        return true;
    }

    private static boolean obtain(ServerLevel sl, Business b, List<Item> acceptable, List<Step> out, Map<Item, Integer> inv,
                                  Map<Item, Integer> store, Set<Item> missing, int depth) {
        if (depth > 6) return false;
        for (Item it : acceptable) if (take(inv, it)) return true;
        for (Item it : acceptable) if (take(store, it)) { out.add(new Fetch(it)); return true; }
        Item p = acceptable.get(0);
        // cooked on the grill / fryer / oven / pot?
        Foods.CookLine line = Foods.cookLineProducing(p);
        if (line != null) {
            if (!BusinessManager.hasEquip(sl, b, cookerEquip(line.cooker()))) { missing.add(p); return false; }
            if (!obtain(sl, b, List.of(line.input().get()), out, inv, store, missing, depth + 1)) return false;
            out.add(new Cook(line.input().get(), line.cooker(), Foods.stageIndex(p)));
            return true;
        }
        Foods.Chop chop = Foods.chopProducing(p);
        if (chop != null) {
            if (!BusinessManager.hasEquip(sl, b, Equip.CUTTING_BOARD)) { missing.add(p); return false; }
            if (!obtain(sl, b, List.of(chop.in().get()), out, inv, store, missing, depth + 1)) return false;
            out.add(new Chop(chop.in().get()));
            return true;
        }
        Foods.Assembly asm = Foods.assemblyProducing(p);
        if (asm != null) {
            if (!BusinessManager.hasEquip(sl, b, Equip.PREP)) { missing.add(p); return false; }
            for (Foods.Group g : asm.parts()) {
                List<Item> acc = new ArrayList<>();
                for (var s : g.items()) {
                    int st = Foods.stageIndex(s.get());
                    if (st < 0 || Foods.STAGE_QUALITY[st] >= 60) acc.add(s.get());
                }
                if (!obtain(sl, b, acc, out, inv, store, missing, depth + 1)) return false;
            }
            out.add(new Assemble(asm));
            return true;
        }
        if (p == ModItems.COLA.get() || p == ModItems.COFFEE.get()) {
            int mode = p == ModItems.COLA.get() ? DrinkMachineBE.COLA : DrinkMachineBE.COFFEE;
            if (BusinessManager.findEquip(sl, b, Equip.DRINK, null, be -> be instanceof DrinkMachineBE d && d.charges(mode) > 0) == null) {
                missing.add(mode == DrinkMachineBE.COLA ? ModItems.COLA_SYRUP.get() : ModItems.COFFEE_BEANS.get());
                return false;
            }
            if (!obtain(sl, b, List.of(ModItems.PAPER_CUP.get()), out, inv, store, missing, depth + 1)) return false;
            out.add(new Drink(mode));
            return true;
        }
        missing.add(p);
        return false;
    }

    public static Equip cookerEquip(Foods.Cooker c) {
        return switch (c) { case GRILL -> Equip.GRILL; case FRYER -> Equip.FRYER; case OVEN -> Equip.OVEN; case POT -> Equip.POT; };
    }
}
