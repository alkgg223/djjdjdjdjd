package com.rpbusiness.entity;

import com.rpbusiness.block.TableBlock;
import com.rpbusiness.business.Business;
import com.rpbusiness.business.Equip;
import com.rpbusiness.business.HotelRoom;
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
    public enum State { WANDER, GO_ENTRANCE, FIND_SEAT, GO_SEAT, BROWSE, WAIT_FOOD, EAT, GO_PAY, WAIT_PAY, LEAVE, SHOP, CHECK_IN, GO_ROOM, STAY, WAIT_GROUP, CALL_CLEANER }

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
    // ---- shopping ----
    public final net.minecraft.world.SimpleContainer basket = new net.minecraft.world.SimpleContainer(18);
    public final List<BlockPos> visited = new ArrayList<>();
    private BlockPos shelf;
    public int shelvesToVisit = 2;
    public boolean paid, stayed;
    public int stayTicks = 2400;
    private String mood = "";
    // ---- groups (friends, couples, families) ----
    /** Leader of this customer's group, null when this customer is the leader or alone. */
    public UUID leaderId;
    public final List<UUID> members = new ArrayList<>();
    public int groupBill, groupSat, groupSatN;
    // ---- hotel stay ----
    public int roomId = -1, bedSlot, nights = 1;
    public long checkOut;
    private int activity = -1;
    private long activityUntil;
    private BlockPos activitySpot, tvPos;

    public boolean isFollower() { return leaderId != null; }
    public int groupSize() { return 1 + members.size(); }

    public CustomerEntity leader(ServerLevel sl) { return leaderId == null ? this : BusinessManager.customer(sl, leaderId); }

    /** Leader first, then the other members that still exist. */
    public List<CustomerEntity> group(ServerLevel sl) {
        CustomerEntity l = leader(sl);
        List<CustomerEntity> out = new ArrayList<>();
        if (l == null) { out.add(this); return out; }
        out.add(l);
        for (UUID id : l.members) { CustomerEntity m = BusinessManager.customer(sl, id); if (m != null) out.add(m); }
        return out;
    }

    public List<UUID> groupIds() {
        List<UUID> l = new ArrayList<>();
        l.add(getUUID());
        l.addAll(members);
        return l;
    }

    private void follow(CustomerEntity l) {
        if (distanceTo(l) > 2.6) walkTo(l.blockPosition(), 2.0, 1.05);
        else if (getNavigation().isInProgress()) getNavigation().stop();
    }

    void standUp(Business b) {
        if (isPassenger()) stopRiding();
        if (b != null) BusinessManager.releaseSeat(b, getUUID());
        setAction(ACT_NONE);
        hold(ItemStack.EMPTY);
    }

    /** Members follow their leader; the leader takes the decisions for the whole group. Returns true when handled. */
    private boolean followerThink(ServerLevel sl) {
        CustomerEntity l = BusinessManager.customer(sl, leaderId);
        if (l == null) {
            leaderId = null;
            if (state == State.WANDER || state == State.GO_ENTRANCE || state == State.SHOP || state == State.CHECK_IN || state == State.FIND_SEAT
                    || state == State.GO_PAY || state == State.WAIT_PAY || state == State.WAIT_GROUP) {
                Business b = business();
                standUp(b);
                leave(sl, b, "");
                return true;
            }
            return false;
        }
        if (l.businessId != null) businessId = l.businessId;
        switch (state) {
            case WANDER, GO_ENTRANCE, SHOP, FIND_SEAT, CHECK_IN, GO_PAY, WAIT_PAY -> {
                if (l.state == State.LEAVE) { setState(State.LEAVE); return true; }
                setAction(ACT_NONE);
                refreshName("");
                follow(l);
                return true;
            }
            case WAIT_GROUP -> {
                refreshName("[waiting for the group]");
                if (l.state != State.WAIT_GROUP && l.state != State.EAT && l.state != State.WAIT_FOOD && l.state != State.BROWSE && l.state != State.GO_SEAT) {
                    standUp(business());
                    setState(State.LEAVE);
                }
                return true;
            }
            case LEAVE -> {
                if (l.state == State.LEAVE || l.state == State.GO_PAY || l.state == State.WAIT_PAY) {
                    Business b = business();
                    if (b != null && b.contains(position()) && l.state == State.LEAVE) return false; // walk out on our own
                    follow(l);
                    if (stateTicks > 3000) discard();
                    return true;
                }
                return false;
            }
            default -> { return false; }
        }
    }

    /** Someone in the group lost patience: everybody stops, and the leader pays for what was served. */
    private void groupAbort(ServerLevel sl, Business b) {
        CustomerEntity l = leader(sl);
        if (l == null) l = this;
        for (CustomerEntity m : group(sl)) {
            BusinessManager.cancelOrders(sl, b, m.getUUID());
            m.standUp(b);
            if (m != l) {
                l.groupBill += m.bill;
                m.bill = 0;
                m.setState(State.LEAVE);
            }
        }
        l.bill += l.groupBill;
        l.groupBill = 0;
        b.customersLost++;
        BusinessManager.dayRecord(sl, b).lostCustomers++;
        b.reputation = Math.max(0, b.reputation - 12);
        sl.playSound(null, l.blockPosition(), ModSounds.UNHAPPY.get(), SoundSource.NEUTRAL, 0.8f, 1.0f);
        if (l.bill > 0) l.setState(State.GO_PAY);
        else l.leave(sl, b, "msg.rpbusiness.c_waited_too_long");
    }

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
        shelvesToVisit = 1 + r.nextInt(3);
        stayTicks = 1800 + r.nextInt(2400);
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
        if (isFollower() && followerThink(sl)) return;
        Business b = business();
        if (b == null && state != State.WANDER && state != State.LEAVE) { leave(sl, null, ""); return; }
        switch (state) {
            case WANDER -> wander(sl);
            case GO_ENTRANCE -> {
                refreshName("");
                setCustomNameVisible(false);
                if (!b.isOpen(sl)) { leave(sl, b, "msg.rpbusiness.c_closed"); return; }
                if (stateTicks > 3000) { leave(sl, b, ""); return; }
                if (walkTo(b.entranceOrCenter(), 1.8, 1.0) || b.contains(position()))
                    setState(b.type.foodService ? State.FIND_SEAT : b.type == com.rpbusiness.business.BusinessType.HOTEL ? State.CHECK_IN : State.SHOP);
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
                List<BlockPos> seats = BusinessManager.claimTable(sl, b, groupIds());
                if (seats != null) {
                    seat = seats.get(0);
                    int i = 1;
                    for (UUID id : members) {
                        CustomerEntity m = BusinessManager.customer(sl, id);
                        if (m == null || i >= seats.size()) continue;
                        m.seat = seats.get(i++);
                        m.businessId = b.id;
                        m.setState(State.GO_SEAT);
                    }
                    setState(State.GO_SEAT);
                }
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
            case SHOP -> shop(sl, b);
            case WAIT_GROUP -> waitGroup(sl, b);
            case CALL_CLEANER -> callCleaner(sl, b);
            case CHECK_IN -> {
                HotelRoom r = BusinessManager.claimRoom(sl, b, getUUID(), groupSize());
                if (r == null) { leave(sl, b, "msg.rpbusiness.c_no_room"); return; }
                int per = b.effective(b.roomPrice);
                while (nights > 1 && per * nights > money) nights--;
                if (per * nights > money) { r.guest = null; leave(sl, b, "msg.rpbusiness.c_too_expensive"); return; }
                roomId = r.id;
                r.nights = nights;
                bill = per * nights;
                satisfaction = (int) Math.max(30, Math.min(95, 85 - (per - 40) * 0.8 + BusinessManager.ambience(b) * 2));
                payPoint = null;
                refreshName("[checking in: " + nights + " night" + (nights > 1 ? "s" : "") + "]");
                setState(State.GO_PAY);
            }
            case GO_ROOM -> {
                refreshName("[going to the room]");
                if (seat == null) { leave(sl, b, ""); return; }
                if (walkTo(seat, 1.6, 0.9) || near(seat, 2.2)) setState(State.STAY);
                else if (isStuck() && stateTicks > 600) { clearStuck(); setState(State.STAY); }
            }
            case STAY -> hotelStay(sl, b);
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

    // ------------------------------------------------------------------ shopping
    private void shop(ServerLevel sl, Business b) {
        if (!b.isOpen(sl)) { BusinessManager.returnBasket(sl, b, this); leave(sl, b, "msg.rpbusiness.c_closed"); return; }
        hold(new ItemStack(basket.getContainerSize() > 0 && countBasket() > 5 ? com.rpbusiness.core.ModItems.SHOPPING_CART.get() : com.rpbusiness.core.ModItems.SHOPPING_BASKET.get()));
        refreshName("[shopping " + Money.fmt(bill) + "]");
        if (shelf == null) {
            if (visited.size() >= shelvesToVisit) { finishShopping(sl, b); return; }
            shelf = BusinessManager.pickDisplay(sl, b, visited, this);
            if (shelf == null) { finishShopping(sl, b); return; }
            stateTicks = 0;
        }
        if (!walkTo(standSpotFor(shelf), 0.9, 0.9) && !near(shelf, 1.9)) {
            if (isStuck() || stateTicks > 600) { clearStuck(); visited.add(shelf); shelf = null; }
            return;
        }
        lookAtBlock(shelf);
        setAction(ACT_REACH);
        if (stateTicks < 50) return;
        BusinessManager.shopAtDisplay(sl, b, this, shelf);
        visited.add(shelf);
        shelf = null;
        setAction(ACT_NONE);
        stateTicks = 0;
    }

    public int countBasket() {
        int n = 0;
        for (int i = 0; i < basket.getContainerSize(); i++) n += basket.getItem(i).getCount();
        return n;
    }

    private void finishShopping(ServerLevel sl, Business b) {
        if (countBasket() == 0) {
            b.customersLost++;
            BusinessManager.dayRecord(sl, b).lostCustomers++;
            leave(sl, b, servedCount < 0 ? "msg.rpbusiness.c_too_expensive" : "msg.rpbusiness.c_nothing_shop");
            return;
        }
        satisfaction = BusinessManager.shopSatisfaction(sl, b, this);
        payPoint = null;
        setState(State.GO_PAY);
    }

    private void decideOrder(ServerLevel sl, Business b) {
        List<Dish> avail = BusinessManager.availableDishes(sl, b);
        int spend = Math.min(money, (int) (budget * 1.3));
        Dish main = null, drink = null;
        double bestMain = 0, bestDrink = 0;
        boolean tooExpensive = false;
        for (Dish d : avail) {
            int price = b.effective(b.menu.get(d).price);
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
        int left = spend - (main != null ? b.effective(b.menu.get(main).price) : 0);
        if (drink != null && (drinkLover || main == null || random.nextFloat() < 0.35f) && b.effective(b.menu.get(drink).price) <= left) chosen.add(drink);
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
        if (waitTicks > patience && (isFollower() || !members.isEmpty())) { groupAbort(sl, b); return; }
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
            if (isFollower() || !members.isEmpty()) {
                // the group leaves together; the leader pays for everybody
                CustomerEntity l = leader(sl);
                if (isFollower() && l != null) { l.groupBill += bill; l.groupSat += satisfaction; l.groupSatN++; bill = 0; }
                setState(State.WAIT_GROUP);
                return;
            }
            stopRiding();
            BusinessManager.releaseSeat(b, getUUID());
            setState(State.GO_PAY);
        }
    }

    private void waitGroup(ServerLevel sl, Business b) {
        refreshName("[waiting for the group]");
        boolean allDone = true;
        for (UUID id : members) {
            CustomerEntity m = BusinessManager.customer(sl, id);
            if (m != null && m.state != State.WAIT_GROUP && m.state != State.LEAVE) allDone = false;
        }
        if (allDone || stateTicks > 2400) {
            bill += groupBill;
            groupBill = 0;
            if (groupSatN > 0) satisfaction = (satisfaction + groupSat) / (1 + groupSatN);
            groupSat = 0; groupSatN = 0;
            standUp(b);
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
            BusinessManager.returnBasket(sl, b, this);
            BusinessManager.finishVisit(sl, b, this, false);
            leave(sl, b, "");
        }
    }

    /** Called by the payment logic after a successful payment. */
    public void paid(ServerLevel sl, Business b) {
        setAction(ACT_NONE);
        paid = true;
        if (b.type == com.rpbusiness.business.BusinessType.HOTEL && !stayed) { checkIn(sl, b); return; }
        leave(sl, b, "");
    }

    // ------------------------------------------------------------------ hotel stay
    private void checkIn(ServerLevel sl, Business b) {
        HotelRoom r = b.room(roomId);
        if (r == null) { leave(sl, b, ""); return; }
        long out = sl.getDayTime() + nights * (com.rpbusiness.server.SelfTest.active() ? 1800L : 24000L);
        r.checkOutTick = out;
        r.lastMessDay = Business.day(sl);
        List<Object[]> slots = BusinessManager.bedSlots(sl, b, r);
        List<CustomerEntity> g = group(sl);
        for (int i = 0; i < g.size(); i++) {
            CustomerEntity m = g.get(i);
            m.roomId = r.id;
            m.checkOut = out;
            m.nights = nights;
            m.businessId = b.id;
            if (!slots.isEmpty()) {
                Object[] sl0 = slots.get(i % slots.size());
                m.seat = (BlockPos) sl0[0];
                m.bedSlot = (Integer) sl0[1];
            }
            m.setState(State.GO_ROOM);
        }
        BusinessManager.notifyOwner(sl, b, Component.translatable("msg.rpbusiness.checked_in", npcName, g.size(), r.name, nights).withStyle(ChatFormatting.AQUA), false);
    }

    private BlockPos findInRoom(ServerLevel sl, HotelRoom r, java.util.function.Predicate<BlockPos> test) {
        List<BlockPos> l = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(r.min, r.max)) if (sl.isLoaded(p) && test.test(p)) l.add(p.immutable());
        return l.isEmpty() ? null : l.get(random.nextInt(l.size()));
    }

    private void endActivity(ServerLevel sl, HotelRoom r) {
        if (isPassenger()) stopRiding();
        setAction(ACT_NONE);
        if (tvPos != null) {
            boolean others = false;
            for (CustomerEntity m : group(sl)) if (m != this && tvPos.equals(m.tvPos)) others = true;
            if (!others) com.rpbusiness.block.TvBlock.setOn(sl, tvPos, false);
            tvPos = null;
        }
        activity = -1;
    }

    /** Daily life in the room: sleeping at night, TV on the sofa, naps, walks through the hotel. */
    private void hotelStay(ServerLevel sl, Business b) {
        HotelRoom r = b.room(roomId);
        if (r == null) { endActivity(sl, null); leave(sl, b, ""); return; }
        if (sl.getDayTime() >= checkOut) {
            if (!isFollower()) checkOutGroup(sl, b, r);
            else { refreshName("[packing]"); endActivity(sl, r); }
            return;
        }
        if (!isFollower() && !r.cleanRequested && BusinessManager.roomDirtyAt(sl, r) != null && random.nextInt(20) == 0) {
            endActivity(sl, r);
            setState(State.CALL_CLEANER);
            return;
        }
        int hour = Business.hour(sl);
        boolean night = hour >= 22 || hour < 7;
        if (night) {
            if (activity != 1) { endActivity(sl, r); activity = 1; }
            sleepInBed(sl);
            refreshName("[sleeping]");
            return;
        }
        if (sl.getGameTime() > activityUntil || activity < 0) {
            endActivity(sl, r);
            activityUntil = sl.getGameTime() + 800 + random.nextInt(1400);
            int roll = random.nextInt(10);
            BlockPos tv = findInRoom(sl, r, p -> sl.getBlockState(p).getBlock() instanceof com.rpbusiness.block.TvBlock);
            BlockPos seatSpot = findInRoom(sl, r, p -> sl.getBlockState(p).getBlock() instanceof com.rpbusiness.block.ChairBlock cb
                    && !(cb instanceof com.rpbusiness.block.HotelBedBlock) && !SeatEntity.occupied(sl, p));
            if (roll < 5 && tv != null && seatSpot != null) { activity = 0; activitySpot = seatSpot; tvPos = tv; }
            else if (roll < 7) { activity = 1; }
            else if (roll < 9 && !b.rooms.isEmpty()) {
                activity = 2;
                BlockPos spot = null;
                for (int i = 0; i < 12 && spot == null; i++) {
                    BlockPos p = new BlockPos(b.min.getX() + random.nextInt(Math.max(1, b.max.getX() - b.min.getX())), b.min.getY() + 1,
                            b.min.getZ() + random.nextInt(Math.max(1, b.max.getZ() - b.min.getZ())));
                    if (walkable(p) && b.roomAt(p) == null) spot = p;
                }
                activitySpot = spot != null ? spot : seat;
            } else { activity = 3; activitySpot = findInRoom(sl, r, this::walkable); }
        }
        switch (activity) {
            case 0 -> {
                refreshName("[watching TV]");
                if (!isPassenger()) {
                    if (walkTo(activitySpot, 1.4, 0.9) || near(activitySpot, 1.6)) {
                        if (!SeatEntity.sit(sl, activitySpot, this)) activityUntil = 0;
                    }
                } else {
                    if (tvPos != null) { com.rpbusiness.block.TvBlock.setOn(sl, tvPos, true); lookAtBlock(tvPos); }
                }
            }
            case 1 -> { refreshName("[resting]"); sleepInBed(sl); }
            default -> {
                refreshName(activity == 2 ? "[walking around]" : "[relaxing]");
                if (activitySpot != null && !walkTo(activitySpot, 1.5, 0.8) && isStuck()) { clearStuck(); activityUntil = 0; }
            }
        }
    }

    private void sleepInBed(ServerLevel sl) {
        if (seat == null) return;
        if (isPassenger()) { setAction(ACT_SLEEP); return; }
        if (walkTo(seat, 1.5, 0.9) || near(seat, 2.0)) {
            if (SeatEntity.sit(sl, seat, this, bedSlot)) setAction(ACT_SLEEP);
        }
    }

    /** The guest walks to the reception and asks for a cleaner. */
    private void callCleaner(ServerLevel sl, Business b) {
        HotelRoom r = b.room(roomId);
        if (r == null) { setState(State.STAY); return; }
        refreshName("[asking for cleaning]");
        BlockPos desk = BusinessManager.findEquip(sl, b, Equip.REGISTER, blockPosition(), null);
        if (desk == null) desk = BusinessManager.findEquip(sl, b, Equip.COMPUTER, blockPosition(), null);
        if (desk == null || walkTo(BusinessManager.queueSpot(sl, b, desk, getUUID()), 1.2, 1.0) || near(desk, 2.8) || stateTicks > 1200) {
            setAction(ACT_TALK);
            if (desk != null) lookAtBlock(desk);
            if (stateTicks < 60 && desk != null && !near(desk, 2.8) && stateTicks <= 1200) return;
            r.cleanRequested = true;
            r.requestTick = sl.getGameTime();
            sl.playSound(null, blockPosition(), ModSounds.RECEPTION_BELL.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
            BusinessManager.notifyOwner(sl, b, Component.translatable("msg.rpbusiness.clean_request", npcName, r.name).withStyle(ChatFormatting.YELLOW), false);
            setAction(ACT_NONE);
            activityUntil = 0;
            setState(State.STAY);
        }
    }

    private void checkOutGroup(ServerLevel sl, Business b, HotelRoom r) {
        int sat = Math.max(0, satisfaction);
        if (r.cleanRequested && sl.getGameTime() - r.requestTick > 2400) sat -= 20;
        if (BusinessManager.roomDirtyAt(sl, r) == null) sat += 5;
        for (CustomerEntity m : group(sl)) {
            m.endActivity(sl, r);
            if (m.seat != null && sl.getBlockState(m.seat).getBlock() instanceof com.rpbusiness.block.HotelBedBlock) com.rpbusiness.block.HotelBedBlock.dirty(sl, m.seat);
            m.stayed = true;
            m.standUp(b);
            if (m != this) m.setState(State.LEAVE);
        }
        r.guest = null;
        r.cleanRequested = false;
        b.reputation = Math.max(0, Math.min(1000, b.reputation + (Math.max(0, Math.min(100, sat)) - 55) / 3));
        sl.playSound(null, blockPosition(), ModSounds.HAPPY.get(), SoundSource.NEUTRAL, 0.7f, 1.0f);
        BusinessManager.notifyOwner(sl, b, Component.translatable("msg.rpbusiness.checked_out", npcName, r.name).withStyle(ChatFormatting.AQUA), true);
        BusinessManager.data(sl).setDirty();
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
        Player seen = sl.getNearestPlayer(this, 32);
        boolean watched = seen != null && seen.hasLineOfSight(this);
        if (b != null && b.contains(position())) {
            // walk out through the door (or toward the outside spot they came from), never to the middle of the shop
            BlockPos exit = b.entrance != null ? b.entrance : home;
            if (exit == null || walkTo(exit, 1.5, 1.0) || isStuck()) {
                if (isStuck()) { clearStuck(); getNavigation().stop(); }
            }
            if ((stateTicks > 900 && !watched) || stateTicks > 2400) discard(); // could not find a way out
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
        visited.clear(); shelf = null;
        if (paid) basket.clearContent(); // bought items were taken home
        paid = false;
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
        t.put("Basket", basket.createTag(registryAccess()));
        t.putInt("Shelves", shelvesToVisit);
        t.putBoolean("Paid", paid);
        t.putBoolean("Stayed", stayed);
        if (leaderId != null) t.putUUID("Leader", leaderId);
        net.minecraft.nbt.ListTag ml = new net.minecraft.nbt.ListTag();
        for (UUID m : members) ml.add(net.minecraft.nbt.NbtUtils.createUUID(m));
        t.put("Members", ml);
        t.putInt("GroupBill", groupBill);
        t.putInt("Room", roomId); t.putInt("BedSlot", bedSlot); t.putInt("Nights", nights); t.putLong("CheckOut", checkOut);
        t.putInt("StayTicks", stayTicks);
        net.minecraft.nbt.ListTag vl = new net.minecraft.nbt.ListTag();
        for (BlockPos v : visited) vl.add(NbtUtils.writeBlockPos(v));
        t.put("Visited", vl);
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
        basket.fromTag(t.getList("Basket", net.minecraft.nbt.Tag.TAG_COMPOUND), registryAccess());
        shelvesToVisit = Math.max(1, t.getInt("Shelves"));
        paid = t.getBoolean("Paid");
        stayed = t.getBoolean("Stayed");
        leaderId = t.hasUUID("Leader") ? t.getUUID("Leader") : null;
        members.clear();
        for (net.minecraft.nbt.Tag x : t.getList("Members", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) members.add(net.minecraft.nbt.NbtUtils.loadUUID(x));
        groupBill = t.getInt("GroupBill");
        roomId = t.contains("Room") ? t.getInt("Room") : -1; bedSlot = t.getInt("BedSlot"); nights = Math.max(1, t.getInt("Nights")); checkOut = t.getLong("CheckOut");
        stayTicks = Math.max(600, t.getInt("StayTicks"));
        visited.clear();
        for (net.minecraft.nbt.Tag x : t.getList("Visited", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            int[] a = ((IntArrayTag) x).getAsIntArray();
            if (a.length == 3) visited.add(new BlockPos(a[0], a[1], a[2]));
        }
        // After a reload the seat entity may be gone: go back to finding a seat / continue gracefully.
        if (state == State.GO_SEAT || state == State.BROWSE) state = State.FIND_SEAT;
        if (state == State.WAIT_PAY) state = State.GO_PAY;
    }
}
