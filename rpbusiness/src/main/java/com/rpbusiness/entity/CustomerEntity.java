package com.rpbusiness.entity;

import com.rpbusiness.block.TableBlock;
import com.rpbusiness.business.Business;
import com.rpbusiness.business.Order;
import com.rpbusiness.core.Config;
import com.rpbusiness.core.ModSounds;
import com.rpbusiness.economy.Money;
import com.rpbusiness.food.Dish;
import com.rpbusiness.food.Foods;
import com.rpbusiness.server.BusinessManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A customer with their own identity, wallet, tastes and patience. Walks in, finds a free table,
 * sits down, reads the menu, orders what they like and can afford, waits, eats, pays and leaves.
 */
public class CustomerEntity extends NpcEntity {
    public enum State { WANDER, GO_ENTRANCE, FIND_SEAT, GO_SEAT, BROWSE, WAIT_FOOD, EAT, GO_PAY, WAIT_PAY, LEAVE }

    // ---- identity & finances ----
    public int money;
    public int budget;
    public int patience;            // ticks
    public Dish.Category favorite = Dish.Category.BURGER;
    public boolean drinkLover;
    public float priceSensitivity = 1f;
    public int visitsLeft = 1;

    // ---- visit state ----
    public State state = State.WANDER;
    public UUID businessId;
    public BlockPos seat, payPoint, home;
    public final List<Integer> orderIds = new ArrayList<>();
    public int stateTicks, waitTicks, wanderTime, satisfaction = -1;
    public int bill, qualitySum, servedCount;
    private int life, retries;
    private String mood = "";

    public CustomerEntity(EntityType<? extends CustomerEntity> type, Level level) { super(type, level); }

    /** Rolls a random personality. */
    public void randomize(RandomSource r) {
        setNpcName(com.rpbusiness.business.Names.random(r));
        setSkin(r.nextInt(8));
        int wealth = r.nextInt(10);
        money = wealth < 3 ? 15 + r.nextInt(20) : wealth < 8 ? 30 + r.nextInt(50) : 80 + r.nextInt(150);
        budget = Math.max(6, (int) (money * (0.4 + r.nextFloat() * 0.5)));
        patience = (int) ((1800 + r.nextInt(3000)) * Config.PATIENCE_MULTIPLIER.get());
        favorite = Dish.Category.values()[r.nextInt(Dish.Category.values().length - 1)];
        drinkLover = r.nextFloat() < 0.55f;
        priceSensitivity = 0.5f + r.nextFloat();
        visitsLeft = 1 + (r.nextFloat() < 0.25f ? 1 : 0);
        wanderTime = 60 + r.nextInt(240);
        home = blockPosition();
    }

    public Business business() {
        return level() instanceof ServerLevel sl ? BusinessManager.data(sl).byId(businessId) : null;
    }

    private void setState(State s) { state = s; stateTicks = 0; }

    @Override
    protected void think() {
        if (!(level() instanceof ServerLevel sl)) return;
        stateTicks += thinkDelta;
        life += thinkDelta;
        Business b = business();
        if (b == null && state != State.WANDER && state != State.LEAVE) { leave(sl, null, ""); return; }
        switch (state) {
            case WANDER -> wander(sl);
            case GO_ENTRANCE -> {
                refreshName("");
                setCustomNameVisible(false);
                if (!b.isOpen(sl)) { leave(sl, b, "msg.rpbusiness.c_closed"); return; }
                if (walkTo(b.entranceOrCenter(), 1.8, 1.0) || b.contains(position())) setState(State.FIND_SEAT);
                else if (isStuck()) {
                    // try a small detour before giving up
                    clearStuck();
                    if (++retries > 4) leave(sl, b, "");
                    else {
                        net.minecraft.world.phys.Vec3 v = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosTowards(this, 8, 4,
                                net.minecraft.world.phys.Vec3.atBottomCenterOf(b.entranceOrCenter()), Math.PI / 2);
                        if (v != null) getNavigation().moveTo(v.x, v.y, v.z, 1.0);
                    }
                }
            }
            case FIND_SEAT -> {
                seat = BusinessManager.claimSeat(sl, b, getUUID());
                if (seat != null) setState(State.GO_SEAT);
                else {
                    refreshName("[waiting for a table]");
                    setCustomNameVisible(false);
                    if (stateTicks > 600) { b.customersLost++; BusinessManager.dayRecord(sl, b).lostCustomers++; leave(sl, b, "msg.rpbusiness.c_no_table"); }
                }
            }
            case GO_SEAT -> {
                if (!BusinessManager.seatValid(sl, b, seat, getUUID())) { setState(State.FIND_SEAT); return; }
                if (walkTo(seat, 1.4, 1.0)) {
                    if (SeatEntity.sit(sl, seat, this)) {
                        setAction(ACT_READ);
                        setState(State.BROWSE);
                    } else if (stateTicks > 200) { BusinessManager.releaseSeat(b, getUUID()); setState(State.FIND_SEAT); }
                } else if (isStuck() && stateTicks > 300) {
                    BusinessManager.releaseSeat(b, getUUID());
                    clearStuck();
                    setState(State.FIND_SEAT);
                }
            }
            case BROWSE -> {
                refreshName("[reading the menu]");
                setCustomNameVisible(false);
                if (stateTicks > 80 + (getId() % 5) * 12) decideOrder(sl, b);
            }
            case WAIT_FOOD -> waitFood(sl, b);
            case EAT -> eat(sl, b);
            case GO_PAY -> {
                setAction(ACT_NONE);
                if (payPoint == null) payPoint = BusinessManager.choosePayPoint(sl, b);
                if (payPoint == null) { // no register or terminal: pays at the table
                    BusinessManager.pay(sl, b, this, "table");
                    leave(sl, b, "");
                    return;
                }
                BlockPos spot = BusinessManager.queueSpot(sl, b, payPoint, getUUID());
                refreshName("[going to pay]");
                if (walkTo(spot, 1.0, 1.0) || (stateTicks > 400 && near(payPoint, 3.5))) {
                    BusinessManager.joinQueue(sl, b, payPoint, this);
                    setState(State.WAIT_PAY);
                } else if (isStuck() && stateTicks > 400) {
                    BusinessManager.joinQueue(sl, b, payPoint, this);
                    setState(State.WAIT_PAY);
                }
            }
            case WAIT_PAY -> waitPay(sl, b);
            case LEAVE -> leaving(sl, b);
        }
    }

    // ------------------------------------------------------------------ states

    private void wander(ServerLevel sl) {
        refreshName("");
        if (getNavigation().isDone() && random.nextInt(8) == 0) {
            Vec3 v = LandRandomPos.getPos(this, 10, 4);
            if (v != null) getNavigation().moveTo(v.x, v.y, v.z, 0.8);
        }
        if (stateTicks >= wanderTime) {
            Business b = BusinessManager.chooseRestaurant(sl, this);
            if (b != null) {
                businessId = b.id;
                setState(State.GO_ENTRANCE);
            } else if (stateTicks > wanderTime + 1200) {
                leave(sl, null, "");
            }
        }
    }

    private void decideOrder(ServerLevel sl, Business b) {
        List<Dish> avail = BusinessManager.availableDishes(sl, b);
        int spend = Math.min(money, (int) (budget * 1.3));
        Dish main = null, drink = null;
        double bestMain = 0, bestDrink = 0;
        boolean tooExpensive = false;
        for (Dish d : avail) {
            int price = b.menu.get(d).price;
            if (price <= 0) continue;
            double ratio = price / (double) d.basePrice;
            if (price > spend || ratio > 2.6 / priceSensitivity) { tooExpensive = true; continue; }
            double score = 1.0 + random.nextDouble();
            if (d.category == favorite) score += 2.0;
            score *= Math.exp(-Math.max(0, ratio - 0.9) * 1.8 * priceSensitivity);
            if (d.isDrink()) { if (score > bestDrink) { bestDrink = score; drink = d; } }
            else if (score > bestMain) { bestMain = score; main = d; }
        }
        List<Dish> chosen = new ArrayList<>();
        if (main != null && bestMain > 0.25) chosen.add(main);
        int left = spend - (main != null ? b.menu.get(main).price : 0);
        if (drink != null && (drinkLover || main == null || random.nextFloat() < 0.35f) && b.menu.get(drink).price <= left) chosen.add(drink);
        if (chosen.isEmpty()) {
            stopRiding();
            BusinessManager.releaseSeat(b, getUUID());
            b.customersLost++;
            BusinessManager.dayRecord(sl, b).lostCustomers++;
            b.reputation = Math.max(0, b.reputation - 2);
            leave(sl, b, tooExpensive ? "msg.rpbusiness.c_too_expensive" : "msg.rpbusiness.c_nothing");
            return;
        }
        for (Dish d : chosen) {
            Order o = BusinessManager.placeOrder(sl, b, this, d);
            orderIds.add(o.id);
        }
        setAction(ACT_NONE);
        waitTicks = 0;
        setState(State.WAIT_FOOD);
    }

    private void waitFood(ServerLevel sl, Business b) {
        waitTicks += thinkDelta;
        List<Order> mine = b.ordersOf(getUUID());
        boolean allServed = true;
        StringBuilder sb = new StringBuilder();
        for (int id : orderIds) {
            Order o = b.order(id);
            if (o == null) continue;
            if (o.status != Order.Status.SERVED) {
                allServed = false;
                if (sb.length() > 0) sb.append(", ");
                sb.append(Component.translatable(o.dish.key()).getString());
                if (o.status == Order.Status.READY) sb.append(" ✔");
            }
        }
        if (orderIds.isEmpty() || (allServed && servedCount > 0)) {
            setAction(ACT_EAT);
            setState(State.EAT);
            return;
        }
        if (mine.isEmpty() && servedCount == 0) { leave(sl, b, "msg.rpbusiness.c_order_lost"); return; }
        int secs = Math.max(0, (patience - waitTicks) / 20);
        refreshName("[" + sb + " - " + secs + "s]");
        setCustomNameVisible(false);
        if (waitTicks > patience) {
            BusinessManager.cancelOrders(sl, b, getUUID());
            b.customersLost++;
            BusinessManager.dayRecord(sl, b).lostCustomers++;
            b.reputation = Math.max(0, b.reputation - 12);
            sl.playSound(null, blockPosition(), ModSounds.UNHAPPY.get(), SoundSource.NEUTRAL, 0.8f, 1.0f);
            stopRiding();
            BusinessManager.releaseSeat(b, getUUID());
            if (servedCount > 0) { setState(State.GO_PAY); return; } // pays for what was served
            leave(sl, b, "msg.rpbusiness.c_waited_too_long");
        }
    }

    /** Called when the player, a waiter or a chef hands over a dish for one of this customer's orders. */
    public boolean receive(ServerLevel sl, Business b, Order o, ItemStack dish) {
        if (o.status == Order.Status.SERVED || state != State.WAIT_FOOD) return false;
        int q = Foods.qualityOf(dish);
        o.status = Order.Status.SERVED;
        o.quality = q;
        qualitySum += q;
        servedCount++;
        bill += o.price;
        hold(dish.copyWithCount(1));
        sl.playSound(null, blockPosition(), ModSounds.PLACE_FOOD.get(), SoundSource.NEUTRAL, 0.8f, 1.0f);
        if (q < 30) setAction(ACT_TALK);
        return true;
    }

    private void eat(ServerLevel sl, Business b) {
        refreshName("[eating]");
        setCustomNameVisible(false);
        if ((stateTicks / 24) != ((stateTicks - thinkDelta) / 24)) {
            sl.playSound(null, blockPosition(), ModSounds.EAT.get(), SoundSource.NEUTRAL, 0.5f, 0.9f + random.nextFloat() * 0.2f);
            ItemStack h = getMainHandItem();
            if (!h.isEmpty()) sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, h), getX(), getEyeY() - 0.2, getZ(), 4, 0.1, 0.05, 0.1, 0.02);
        }
        if (stateTicks >= 200) {
            hold(ItemStack.EMPTY);
            setAction(ACT_NONE);
            computeSatisfaction(sl, b);
            BlockPos table = BusinessManager.tableFor(sl, b, seat);
            if (table != null) BusinessManager.makeDirty(sl, b, table);
            stopRiding();
            BusinessManager.releaseSeat(b, getUUID());
            setState(State.GO_PAY);
        }
    }

    private void computeSatisfaction(ServerLevel sl, Business b) {
        double avgQ = servedCount > 0 ? qualitySum / (double) servedCount : 50;
        double waitFrac = Math.min(1.0, waitTicks / (double) Math.max(1, patience));
        double priceRatio = 0;
        int n = 0;
        for (int id : orderIds) {
            Order o = b.order(id);
            if (o != null && o.status == Order.Status.SERVED) { priceRatio += o.price / (double) o.dish.basePrice; n++; }
        }
        priceRatio = n > 0 ? priceRatio / n : 1.0;
        double pricePart = 15 * Math.max(0, Math.min(1, (1.6 - priceRatio) / 1.1));
        double ambience = Math.min(10, BusinessManager.ambience(b) * 2);
        satisfaction = (int) Math.max(0, Math.min(100, 0.55 * avgQ + 25 * (1 - waitFrac) + pricePart + ambience));
        mood = satisfaction >= 80 ? "☺☺" : satisfaction >= 55 ? "☺" : "☹";
        sl.playSound(null, blockPosition(), satisfaction >= 55 ? ModSounds.HAPPY.get() : ModSounds.UNHAPPY.get(), SoundSource.NEUTRAL, 0.7f, 1.0f);
        if (satisfaction >= 80) sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getEyeY() + 0.3, getZ(), 6, 0.3, 0.2, 0.3, 0);
        else if (satisfaction < 40) sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getEyeY() + 0.3, getZ(), 2, 0.2, 0.2, 0.2, 0);
    }

    /** Tip based on satisfaction. */
    public int tip() {
        if (satisfaction < 75) return 0;
        return Math.min(money - bill, Math.max(1, bill * (satisfaction - 70) / 100));
    }

    private void waitPay(ServerLevel sl, Business b) {
        setAction(ACT_PAY);
        refreshName("[waiting to pay " + Money.fmt(bill) + "]");
        setCustomNameVisible(false);
        if (payPoint != null) lookAtBlock(payPoint);
        BusinessManager.tickPaying(sl, b, this);
        if (stateTicks > 2400 + patience / 2) {
            BusinessManager.leaveQueue(b, getUUID());
            b.reputation = Math.max(0, b.reputation - 15);
            BusinessManager.notifyOwner(sl, b, Component.translatable("msg.rpbusiness.c_left_unpaid", npcName, Money.fmt(bill)).withStyle(ChatFormatting.RED), false);
            BusinessManager.finishVisit(sl, b, this, false);
            leave(sl, b, "");
        }
    }

    /** Called by the payment logic after a successful payment. */
    public void paid(ServerLevel sl, Business b) {
        setAction(ACT_NONE);
        leave(sl, b, "");
    }

    private void leave(ServerLevel sl, Business b, String reasonKey) {
        if (b != null) {
            BusinessManager.releaseSeat(b, getUUID());
            BusinessManager.leaveQueue(b, getUUID());
            if (!reasonKey.isEmpty())
                BusinessManager.notifyOwner(sl, b, Component.translatable(reasonKey, npcName).withStyle(ChatFormatting.GOLD), true);
        }
        stopRiding();
        setAction(ACT_NONE);
        hold(ItemStack.EMPTY);
        setState(State.LEAVE);
    }

    private void leaving(ServerLevel sl, Business b) {
        refreshName(mood);
        setCustomNameVisible(false);
        BlockPos exit = b != null ? b.entranceOrCenter() : home;
        if (b != null && b.contains(position())) {
            walkTo(exit, 1.5, 1.0);
            return;
        }
        if (home != null && !walkTo(home, 2.0, 0.9) && stateTicks < 1200) {
            if (isStuck()) { clearStuck(); getNavigation().stop(); }
        } else if (visitsLeft > 1 && random.nextFloat() < 0.5f) {
            visitsLeft--;
            resetVisit();
            wanderTime = 200 + random.nextInt(400);
            setState(State.WANDER);
            return;
        }
        Player p = sl.getNearestPlayer(this, 24);
        if (stateTicks > 200 && (p == null || stateTicks > 2400 || !p.hasLineOfSight(this))) discard();
    }

    private void resetVisit() {
        businessId = null; seat = null; payPoint = null; orderIds.clear();
        waitTicks = 0; bill = 0; qualitySum = 0; servedCount = 0; satisfaction = -1;
    }

    @Override
    public void checkDespawn() {
        if (com.rpbusiness.server.SelfTest.active() && state != State.LEAVE) return;
        if (state == State.WANDER || state == State.LEAVE) {
            Player p = level().getNearestPlayer(this, -1);
            if (p == null || p.distanceToSqr(this) > 128 * 128 || life > 24000) discard();
        }
    }

    // ------------------------------------------------------------------ interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level();
        Business b = business();
        ItemStack held = player.getItemInHand(hand);
        if (b != null && state == State.WAIT_FOOD && !held.isEmpty()) {
            for (int id : orderIds) {
                Order o = b.order(id);
                if (o != null && o.status != Order.Status.SERVED && o.dish.accepts(held)) {
                    ItemStack one = held.copyWithCount(1);
                    if (BusinessManager.serve(sl, b, this, o, one)) {
                        if (!player.getAbilities().instabuild) held.shrink(1);
                        player.displayClientMessage(Component.translatable("msg.rpbusiness.served", one.getHoverName(), npcName), true);
                        return InteractionResult.CONSUME;
                    }
                }
            }
            player.displayClientMessage(Component.translatable("msg.rpbusiness.wrong_dish", npcName), true);
            return InteractionResult.CONSUME;
        }
        if (b != null && state == State.WAIT_PAY && b.owner != null && b.owner.equals(player.getUUID())) {
            if (BusinessManager.processPayment(sl, b, this, "cash")) return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable("msg.rpbusiness.customer_info", npcName,
                Component.translatable("state.rpbusiness." + state.name().toLowerCase()), Money.fmt(money),
                Component.translatable("dish_category.rpbusiness." + favorite.name().toLowerCase())), false);
        setAction(ACT_TALK);
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Money", money); t.putInt("Budget", budget); t.putInt("Patience", patience);
        t.putString("Fav", favorite.name()); t.putBoolean("DrinkLover", drinkLover); t.putFloat("PriceSens", priceSensitivity);
        t.putInt("Visits", visitsLeft); t.putString("State", state.name());
        if (businessId != null) t.putUUID("Business", businessId);
        if (seat != null) t.put("Seat", NbtUtils.writeBlockPos(seat));
        if (payPoint != null) t.put("PayPoint", NbtUtils.writeBlockPos(payPoint));
        if (home != null) t.put("Home", NbtUtils.writeBlockPos(home));
        t.put("Orders", new IntArrayTag(orderIds.stream().mapToInt(Integer::intValue).toArray()));
        t.putInt("StateTicks", stateTicks); t.putInt("WaitTicks", waitTicks); t.putInt("WanderTime", wanderTime);
        t.putInt("Satisfaction", satisfaction); t.putInt("Bill", bill); t.putInt("QSum", qualitySum); t.putInt("Served", servedCount);
        t.putInt("Life", life);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        money = t.getInt("Money"); budget = t.getInt("Budget"); patience = Math.max(200, t.getInt("Patience"));
        try { favorite = Dish.Category.valueOf(t.getString("Fav")); } catch (Exception ignored) {}
        drinkLover = t.getBoolean("DrinkLover"); priceSensitivity = t.getFloat("PriceSens");
        visitsLeft = t.getInt("Visits");
        try { state = State.valueOf(t.getString("State")); } catch (Exception e) { state = State.WANDER; }
        businessId = t.hasUUID("Business") ? t.getUUID("Business") : null;
        seat = NbtUtils.readBlockPos(t, "Seat").orElse(null);
        payPoint = NbtUtils.readBlockPos(t, "PayPoint").orElse(null);
        home = NbtUtils.readBlockPos(t, "Home").orElse(blockPosition());
        orderIds.clear();
        for (int i : t.getIntArray("Orders")) orderIds.add(i);
        stateTicks = t.getInt("StateTicks"); waitTicks = t.getInt("WaitTicks"); wanderTime = t.getInt("WanderTime");
        satisfaction = t.getInt("Satisfaction"); bill = t.getInt("Bill"); qualitySum = t.getInt("QSum"); servedCount = t.getInt("Served");
        life = t.getInt("Life");
        // After a reload the seat entity may be gone: go back to finding a seat / continue gracefully.
        if (state == State.GO_SEAT || state == State.BROWSE) state = State.FIND_SEAT;
        if (state == State.WAIT_PAY) state = State.GO_PAY;
    }
}
