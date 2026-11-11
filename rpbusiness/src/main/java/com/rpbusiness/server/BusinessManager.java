package com.rpbusiness.server;

import com.rpbusiness.RPBusiness;
import com.rpbusiness.block.*;
import com.rpbusiness.business.*;
import com.rpbusiness.core.*;
import com.rpbusiness.economy.Money;
import com.rpbusiness.entity.*;
import com.rpbusiness.food.Dish;
import com.rpbusiness.food.Foods;
import com.rpbusiness.network.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;
import java.util.function.Predicate;

/** Server-side logic hub: equipment index, seating, ordering, payments, staff, salaries, supply deliveries. */
public final class BusinessManager {
    private BusinessManager() {}

    private static final Map<UUID, Map<Integer, UUID>> DELIVERY_CLAIMS = new HashMap<>();
    private static final Map<UUID, Long> AVAIL_TICK = new HashMap<>();
    private static final Map<UUID, List<Dish>> AVAIL = new HashMap<>();
    private static final Map<Integer, Integer> ORDER_MISSING = new HashMap<>();

    public static BusinessData data(ServerLevel sl) { return BusinessData.get(sl); }

    /** Last game tick anyone (player or employee) worked at a station; used to detect abandoned food. */
    private static final Map<BlockPos, Long> STATION_TOUCH = new HashMap<>();

    public static void touch(Level l, BlockPos p) { if (!l.isClientSide) STATION_TOUCH.put(p.immutable(), l.getGameTime()); }

    public static long idleFor(Level l, BlockPos p) {
        Long t = STATION_TOUCH.get(p);
        return t == null ? Long.MAX_VALUE / 2 : l.getGameTime() - t;
    }

    /** A station holding food nobody has touched for a while (e.g. a cook's plan was interrupted). */
    public static BlockPos findAbandoned(ServerLevel sl, Business b, BlockPos near) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (Equip e : new Equip[]{Equip.GRILL, Equip.FRYER, Equip.OVEN, Equip.POT, Equip.CUTTING_BOARD, Equip.PREP, Equip.DRINK}) {
            for (BlockPos p : equip(sl, b, e)) {
                BlockEntity be = sl.getBlockEntity(p);
                long idle = idleFor(sl, p);
                boolean abandoned = false;
                if (be instanceof CookerBE c) {
                    for (int i = 0; i < c.size(); i++) if (c.occupied(i) && c.stage(i) >= 1 && idle > 100) abandoned = true;
                } else if (be instanceof CuttingBoardBE cb) {
                    abandoned = !cb.isEmpty() && idle > (cb.isChoppable() ? 600 : 200);
                } else if (be instanceof PrepStationBE ps) {
                    abandoned = !ps.isEmpty() && idle > 600;
                } else if (be instanceof DrinkMachineBE dm) {
                    abandoned = !dm.output().isEmpty() && idle > 200;
                }
                if (!abandoned) continue;
                double d = p.distSqr(near);
                if (d < bd) { bd = d; best = p; }
            }
        }
        return best;
    }

    public static Finance.Day dayRecord(ServerLevel sl, Business b) { return b.finance.day(Business.day(sl)); }

    public static Map<Integer, UUID> deliveryClaims(Business b) { return DELIVERY_CLAIMS.computeIfAbsent(b.id, k -> new HashMap<>()); }

    public static void notifyOwner(ServerLevel sl, Business b, Component msg, boolean actionBar) {
        if (b.owner == null) return;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(b.owner);
        if (p != null) p.displayClientMessage(actionBar ? msg : Component.literal("[" + b.name + "] ").withStyle(ChatFormatting.DARK_AQUA).append(msg), actionBar);
    }

    public static boolean isOwner(Player p, Business b) { return b != null && b.owner != null && b.owner.equals(p.getUUID()); }

    // =================================================================== equipment index

    /** Business-aware classification: player designations and exclusions win over the block's default role. */
    public static Equip classify(ServerLevel sl, Business b, BlockPos p) {
        if (b != null) {
            if (b.excluded.contains(p)) return null;
            Equip d = b.designations.get(p);
            if (d != null && designationValid(sl, p, d)) return d;
        }
        return classify(sl.getBlockState(p), sl.getBlockEntity(p));
    }

    public static boolean designationValid(ServerLevel sl, BlockPos p, Equip e) {
        BlockState s = sl.getBlockState(p);
        if (s.isAir()) return false;
        return switch (e) {
            case STORAGE, CRATE -> sl.getBlockEntity(p) instanceof Container;
            case SERVING -> sl.getBlockEntity(p) instanceof ServingCounterBE;
            case CHAIR, TABLE, REGISTER, TERMINAL, DECOR -> true;
            default -> classify(s, sl.getBlockEntity(p)) == e;
        };
    }

    public static Equip classify(BlockState s, BlockEntity be) {
        var blk = s.getBlock();
        if (blk instanceof DisplayBlock) return Equip.DISPLAY;
        if (blk instanceof HotelBedBlock) return Equip.BED;
        if (blk instanceof SafeBlock) return null;
        if (blk instanceof ChairBlock) return Equip.CHAIR;
        if (blk instanceof TableBlock) return Equip.TABLE;
        if (blk instanceof CuttingBoardBlock) return Equip.CUTTING_BOARD;
        if (blk instanceof CookerBlock cb) return Steps.cookerEquip(cb.cooker);
        if (blk instanceof PrepStationBlock) return Equip.PREP;
        if (blk instanceof DrinkMachineBlock) return Equip.DRINK;
        if (blk instanceof ServingCounterBlock) return Equip.SERVING;
        if (blk instanceof ShapedBlock sb) {
            return switch (sb.kind) {
                case REGISTER -> Equip.REGISTER;
                case TERMINAL -> Equip.TERMINAL;
                case COMPUTER -> Equip.COMPUTER;
                case SUPPLIER -> Equip.SUPPLIER;
                case LAMP, PLANT, MENU_BOARD -> Equip.DECOR;
                case BANK, BANK_COMPUTER -> Equip.BANK;
                case ATM -> Equip.ATM;
                case DELIVERY -> Equip.DELIVERY_TERMINAL;
                default -> null;
            };
        }
        if (blk instanceof FridgeBlock && s.getValue(FridgeBlock.HALF) == DoubleBlockHalf.UPPER) return null;
        if (blk == ModBlocks.DELIVERY_CRATE.get()) return Equip.CRATE;
        if (blk == ModBlocks.PACKAGE.get()) return null;
        if (be instanceof BaseContainerBlockEntity && be instanceof Container) return Equip.STORAGE;
        return null;
    }

    public static void index(ServerLevel sl, Business b, BlockPos pos) {
        Equip e = classify(sl, b, pos);
        if (e != null) b.equipment.get(e).add(pos.immutable());
        if (e == Equip.CRATE) b.equipment.get(Equip.STORAGE).add(pos.immutable());
        b.stockTick = -1000;
    }

    public static void unindex(Business b, BlockPos pos) {
        for (var set : b.equipment.values()) set.remove(pos);
        b.stockTick = -1000;
    }

    public static void scan(ServerLevel sl, Business b) {
        for (var set : b.equipment.values()) set.clear();
        for (BlockPos p : BlockPos.betweenClosed(b.min, b.max)) {
            if (!sl.isLoaded(p)) continue;
            BlockState s = sl.getBlockState(p);
            if (s.isAir()) continue;
            index(sl, b, p);
        }
        b.designations.keySet().removeIf(p -> !b.contains(p));
        data(sl).setDirty();
    }

    /** Valid positions of a type, dropping stale entries lazily. */
    public static List<BlockPos> equip(ServerLevel sl, Business b, Equip e) {
        List<BlockPos> out = new ArrayList<>();
        Iterator<BlockPos> it = b.equipment.get(e).iterator();
        while (it.hasNext()) {
            BlockPos p = it.next();
            if (!sl.isLoaded(p)) continue;
            Equip now = classify(sl, b, p);
            if (now != e && !(e == Equip.STORAGE && now == Equip.CRATE)) { it.remove(); continue; }
            out.add(p);
        }
        return out;
    }

    public static boolean hasEquip(ServerLevel sl, Business b, Equip e) { return !equip(sl, b, e).isEmpty(); }

    public static BlockPos findEquip(ServerLevel sl, Business b, Equip e, BlockPos near, Predicate<BlockEntity> test) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : equip(sl, b, e)) {
            BlockEntity be = sl.getBlockEntity(p);
            if (test != null && !test.test(be)) continue;
            double d = near == null ? 0 : p.distSqr(near);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    /**
     * Equipment an employee should use: if the player assigned this employee to specific stations of this type,
     * only those are used (the employee waits for them); otherwise the nearest free one.
     */
    public static BlockPos findStation(ServerLevel sl, Business b, EmployeeEntity e, Equip type, Predicate<BlockEntity> test) {
        EmployeeRecord r = e.record();
        if (r != null && !r.stations.isEmpty()) {
            boolean hasType = false;
            BlockPos best = null;
            double bd = Double.MAX_VALUE;
            for (BlockPos p : r.stations) {
                if (!sl.isLoaded(p) || classify(sl, b, p) != type) continue;
                hasType = true;
                if (test != null && !test.test(sl.getBlockEntity(p))) continue;
                double d = p.distSqr(e.blockPosition());
                if (d < bd) { bd = d; best = p; }
            }
            if (hasType) return best;
        }
        return findEquip(sl, b, type, e.workCenter(), test);
    }

    public static BlockPos findStorageWith(ServerLevel sl, Business b, Predicate<ItemStack> test, BlockPos preferred, BlockPos near) {
        if (preferred != null && sl.getBlockEntity(preferred) instanceof Container c && contains(c, test)) return preferred;
        return findEquip(sl, b, Equip.STORAGE, near, be -> be instanceof Container c && contains(c, test));
    }

    public static BlockPos findStorageWithSpace(ServerLevel sl, Business b, BlockPos preferred, BlockPos near) {
        if (preferred != null && sl.getBlockEntity(preferred) instanceof Container c && ContainerUtil.hasSpace(c)) return preferred;
        BlockPos fridge = findEquip(sl, b, Equip.STORAGE, near, be -> be instanceof AnimatedStorageBE c && ContainerUtil.hasSpace(c));
        if (fridge != null) return fridge;
        return findEquip(sl, b, Equip.STORAGE, near, be -> be instanceof Container c && ContainerUtil.hasSpace(c)
                && !(sl.getBlockState(be.getBlockPos()).is(ModBlocks.DELIVERY_CRATE.get())));
    }

    private static boolean contains(Container c, Predicate<ItemStack> test) {
        for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty() && test.test(c.getItem(i))) return true;
        return false;
    }

    /** Item counts in all storage of the business (cached for 5 seconds). */
    public static Map<Item, Integer> storageCounts(ServerLevel sl, Business b) {
        if (sl.getGameTime() - b.stockTick < 100) return b.stock;
        Map<Item, Integer> m = new HashMap<>();
        for (BlockPos p : equip(sl, b, Equip.STORAGE)) {
            if (sl.getBlockEntity(p) instanceof Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack s = c.getItem(i);
                    if (!s.isEmpty()) m.merge(s.getItem(), s.getCount(), Integer::sum);
                }
            }
        }
        b.stock = m;
        b.stockTick = sl.getGameTime();
        return m;
    }

    public static int ambience(Business b) { return b.equipment.get(Equip.DECOR).size(); }

    // =================================================================== menu availability

    /** Dishes that are on the menu and can really be produced (or are already waiting on a counter). */
    public static List<Dish> availableDishes(ServerLevel sl, Business b) {
        Long t = AVAIL_TICK.get(b.id);
        if (t != null && sl.getGameTime() - t < 40 && AVAIL.containsKey(b.id)) return AVAIL.get(b.id);
        List<Dish> out = new ArrayList<>();
        Map<Item, Integer> store = new HashMap<>(storageCounts(sl, b));
        // the owner's own inventory counts while they are working inside
        ServerPlayer owner = b.owner == null ? null : sl.getServer().getPlayerList().getPlayer(b.owner);
        if (owner != null && owner.level() == sl && b.contains(owner.position())) {
            for (ItemStack s : owner.getInventory().items) if (!s.isEmpty()) store.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        for (var e : equip(sl, b, Equip.SERVING)) {
            if (sl.getBlockEntity(e) instanceof ServingCounterBE c)
                for (int i = 0; i < ServingCounterBE.SIZE; i++) if (!c.get(i).isEmpty()) store.merge(c.get(i).getItem(), 1, Integer::sum);
        }
        for (Dish d : b.enabledDishes()) {
            if (canProduce(sl, b, d.servedItems().stream().map(s -> (Item) s.get()).filter(i -> {
                int st = Foods.stageIndex(i);
                return st < 0 || Foods.STAGE_QUALITY[st] >= 40;
            }).toList(), new HashMap<>(store), 0)) out.add(d);
        }
        AVAIL.put(b.id, out);
        AVAIL_TICK.put(b.id, sl.getGameTime());
        return out;
    }

    private static boolean canProduce(ServerLevel sl, Business b, List<Item> acceptable, Map<Item, Integer> store, int depth) {
        if (depth > 6 || acceptable.isEmpty()) return false;
        for (Item it : acceptable) {
            Integer n = store.get(it);
            if (n != null && n > 0) { store.put(it, n - 1); return true; }
        }
        Item p = acceptable.get(0);
        Foods.CookLine line = Foods.cookLineProducing(p);
        if (line != null) return hasEquip(sl, b, Steps.cookerEquip(line.cooker())) && canProduce(sl, b, List.of(line.input().get()), store, depth + 1);
        Foods.Chop chop = Foods.chopProducing(p);
        if (chop != null) return hasEquip(sl, b, Equip.CUTTING_BOARD) && canProduce(sl, b, List.of(chop.in().get()), store, depth + 1);
        Foods.Assembly asm = Foods.assemblyProducing(p);
        if (asm != null) {
            if (!hasEquip(sl, b, Equip.PREP)) return false;
            for (Foods.Group g : asm.parts()) {
                List<Item> acc = new ArrayList<>();
                for (var s : g.items()) acc.add(s.get());
                if (!canProduce(sl, b, acc, store, depth + 1)) return false;
            }
            return true;
        }
        if (p == ModItems.COLA.get() || p == ModItems.COFFEE.get()) {
            int mode = p == ModItems.COLA.get() ? DrinkMachineBE.COLA : DrinkMachineBE.COFFEE;
            return findEquip(sl, b, Equip.DRINK, null, be -> be instanceof DrinkMachineBE d && d.charges(mode) > 0) != null
                    && canProduce(sl, b, List.of(ModItems.PAPER_CUP.get()), store, depth + 1);
        }
        return false;
    }

    /** Chopped ingredients the menu needs (for the assistant chef's prep work). */
    public static List<Item> prepNeeded(Business b) {
        LinkedHashSet<Item> out = new LinkedHashSet<>();
        for (Dish d : b.enabledDishes()) collectChops(d.target(), out, 0);
        return new ArrayList<>(out);
    }

    private static void collectChops(Item it, Set<Item> out, int depth) {
        if (depth > 6) return;
        if (Foods.chopProducing(it) != null) out.add(it);
        Foods.CookLine l = Foods.cookLineProducing(it);
        if (l != null) collectChops(l.input().get(), out, depth + 1);
        Foods.Assembly a = Foods.assemblyProducing(it);
        if (a != null) for (Foods.Group g : a.parts()) collectChops(g.primary(), out, depth + 1);
    }

    // =================================================================== customers: choosing, seating

    public static Business chooseRestaurant(ServerLevel sl, CustomerEntity c) {
        Business best = null;
        double bs = -1e9;
        for (Business b : data(sl).businesses.values()) {
            if (!(b.type.foodService || b.type.retail || b.type == BusinessType.HOTEL) || !b.isOpen(sl)) continue;
            double dist = Math.sqrt(b.center().distSqr(c.blockPosition()));
            if (dist > 96) continue;
            double score = b.reputation / 200.0 + c.getRandom().nextDouble() * 1.5 - dist / 48.0
                    + (100 - b.priceMultiplier) / 40.0 + b.discount / 15.0;
            if (b.type.foodService) {
                List<Dish> avail = availableDishes(sl, b);
                if (avail.isEmpty() || freeSeats(sl, b) == 0) continue;
                for (Dish d : avail) if (d.category == c.favorite) { score += 1.5; break; }
            } else if (b.type == BusinessType.HOTEL) {
                if (freeBeds(sl, b) == 0) continue;
                score += 0.5;
            } else {
                if (!b.type.retail || !hasProducts(sl, b) || shoppers(sl, b) >= 6 + equip(sl, b, Equip.DISPLAY).size() / 2) continue;
            }
            if (score > bs) { bs = score; best = b; }
        }
        return best;
    }

    public static BlockPos tableFor(Level l, BlockPos chair) {
        if (chair == null) return null;
        Business b = l instanceof ServerLevel sl ? data(sl).at(chair) : null;
        return tableFor(l, b, chair);
    }

    public static boolean isTable(Level l, Business b, BlockPos p) {
        if (b != null && b.excluded.contains(p)) return false;
        if (b != null && b.designations.get(p) == Equip.TABLE) return true;
        return l.getBlockState(p).getBlock() instanceof TableBlock;
    }

    /** Direction a person sitting on this block faces (chairs and sofas: their facing; stairs: away from the back). */
    public static Direction seatFacing(Level l, Business b, BlockPos seat) {
        BlockState s = l.getBlockState(seat);
        if (s.getBlock() instanceof ChairBlock) return s.getValue(HorizontalDirectionalBlock.FACING);
        if (s.getBlock() instanceof net.minecraft.world.level.block.StairBlock) return s.getValue(net.minecraft.world.level.block.StairBlock.FACING).getOpposite();
        for (Direction d : Direction.Plane.HORIZONTAL) if (isTable(l, b, seat.relative(d))) return d;
        return s.hasProperty(HorizontalDirectionalBlock.FACING) ? s.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    public static BlockPos tableFor(Level l, Business b, BlockPos chair) {
        if (chair == null) return null;
        BlockPos f = chair.relative(seatFacing(l, b, chair));
        if (isTable(l, b, f)) return f;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = chair.relative(d);
            if (isTable(l, b, p)) return p;
        }
        return null;
    }

    /** True if this block may be sat on: a mod chair/sofa, or any block the owner designated as a seat. */
    public static boolean isSeat(Level l, BlockPos p) {
        if (l.getBlockState(p).getBlock() instanceof ChairBlock) {
            Business b = l instanceof ServerLevel sl ? data(sl).at(p) : null;
            return b == null || !b.excluded.contains(p);
        }
        if (!(l instanceof ServerLevel sl)) return false;
        Business b = data(sl).at(p);
        return b != null && b.designations.get(p) == Equip.CHAIR && !l.getBlockState(p).isAir();
    }

    public static boolean tableDirty(Level l, Business b, BlockPos t) {
        BlockState s = l.getBlockState(t);
        if (s.getBlock() instanceof TableBlock) return s.getValue(TableBlock.DIRTY);
        return b != null && b.dirty.contains(t);
    }

    public static void makeDirty(ServerLevel sl, Business b, BlockPos t) {
        if (sl.getBlockState(t).getBlock() instanceof TableBlock) TableBlock.dirty(sl, t);
        else if (b != null) b.dirty.add(t.immutable());
    }

    public static void cleanTable(ServerLevel sl, Business b, BlockPos t) {
        if (sl.getBlockState(t).getBlock() instanceof TableBlock) { TableBlock.clean(sl, t); return; }
        if (b != null && b.dirty.remove(t)) {
            sl.playSound(null, t, ModSounds.CLEAN.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
            sl.sendParticles(ParticleTypes.BUBBLE_POP, t.getX() + 0.5, t.getY() + 1.05, t.getZ() + 0.5, 12, 0.35, 0.05, 0.35, 0.02);
        }
    }

    private static void cleanClaims(ServerLevel sl, Business b) {
        b.seatClaims.entrySet().removeIf(e -> !(sl.getEntity(e.getValue()) instanceof CustomerEntity));
    }

    private static boolean seatUsable(ServerLevel sl, Business b, BlockPos chair) {
        if (!isSeat(sl, chair)) return false;
        BlockPos t = tableFor(sl, b, chair);
        if (t == null || tableDirty(sl, b, t)) return false;
        return !SeatEntity.occupied(sl, chair);
    }

    public static int freeSeats(ServerLevel sl, Business b) {
        cleanClaims(sl, b);
        int n = 0;
        for (BlockPos p : equip(sl, b, Equip.CHAIR)) if (!b.seatClaims.containsKey(p) && seatUsable(sl, b, p)) n++;
        return n;
    }

    public static BlockPos claimSeat(ServerLevel sl, Business b, UUID who) {
        cleanClaims(sl, b);
        for (var e : b.seatClaims.entrySet()) if (e.getValue().equals(who)) return e.getKey();
        List<BlockPos> chairs = equip(sl, b, Equip.CHAIR);
        Collections.shuffle(chairs, new Random(sl.getGameTime() ^ who.hashCode()));
        for (BlockPos p : chairs) {
            if (b.seatClaims.containsKey(p) || !seatUsable(sl, b, p)) continue;
            b.seatClaims.put(p.immutable(), who);
            return p;
        }
        return null;
    }

    public static boolean seatValid(ServerLevel sl, Business b, BlockPos seat, UUID who) {
        return seat != null && who.equals(b.seatClaims.get(seat)) && isSeat(sl, seat);
    }

    public static void releaseSeat(Business b, UUID who) { b.seatClaims.values().removeIf(u -> u.equals(who)); }

    public static boolean tableInUse(ServerLevel sl, Business b, BlockPos table) {
        for (var e : b.seatClaims.entrySet()) {
            if (table.equals(tableFor(sl, b, e.getKey())) && sl.getEntity(e.getValue()) instanceof CustomerEntity c
                    && (c.state == CustomerEntity.State.EAT || c.state == CustomerEntity.State.WAIT_FOOD)) return true;
        }
        return false;
    }

    // =================================================================== manager purchasing

    private static void rawFor(Item it, Set<Item> out, int depth) {
        if (depth > 6) return;
        if (com.rpbusiness.economy.Suppliers.find(it) != null) { out.add(it); return; }
        Foods.Chop c = Foods.chopProducing(it);
        if (c != null) { rawFor(c.in().get(), out, depth + 1); return; }
        Foods.CookLine l = Foods.cookLineProducing(it);
        if (l != null) { rawFor(l.input().get(), out, depth + 1); return; }
        Foods.Assembly a = Foods.assemblyProducing(it);
        if (a != null) for (Foods.Group g : a.parts()) rawFor(g.primary(), out, depth + 1);
    }

    /**
     * The manager checks stock and orders what is running low from the suppliers, paying from the business
     * cash (or its bank account). Goods arrive by courier like any other order.
     */
    public static void autoPurchase(ServerLevel sl, Business b, EmployeeEntity manager) {
        if (!b.autoBuy) return;
        for (SupplyOrder o : b.supplyOrders) if (o.status == SupplyOrder.Status.ORDERED || o.status == SupplyOrder.Status.IN_TRANSIT) return;
        Set<Item> wanted = new LinkedHashSet<>();
        int target;
        if (b.type.foodService) {
            for (Dish d : b.enabledDishes()) for (var s : d.servedItems()) { rawFor(s.get(), wanted, 0); break; }
            if (b.menu.get(Dish.COLA).enabled || b.menu.get(Dish.COFFEE).enabled) wanted.add(ModItems.PAPER_CUP.get());
            for (BlockPos p : equip(sl, b, Equip.DRINK)) if (sl.getBlockEntity(p) instanceof DrinkMachineBE dm) {
                if (b.menu.get(Dish.COLA).enabled && dm.charges(DrinkMachineBE.COLA) < 8) wanted.add(ModItems.COLA_SYRUP.get());
                if (b.menu.get(Dish.COFFEE).enabled && dm.charges(DrinkMachineBE.COFFEE) < 8) wanted.add(ModItems.COFFEE_BEANS.get());
            }
            wanted.remove(ModItems.COLA.get());
            wanted.remove(ModItems.COFFEE.get());
            target = 24;
        } else if (b.type.retail) {
            for (BlockPos p : equip(sl, b, Equip.DISPLAY)) if (sl.getBlockEntity(p) instanceof DisplayBE d)
                for (String id : d.planogram) if (id != null) {
                    Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id));
                    if (com.rpbusiness.economy.Suppliers.find(it) != null) wanted.add(it);
                }
            target = 32;
        } else return;
        if (wanted.isEmpty()) return;
        Map<Item, Integer> have = new HashMap<>(storageCounts(sl, b));
        for (BlockPos p : equip(sl, b, Equip.DISPLAY)) if (sl.getBlockEntity(p) instanceof DisplayBE d)
            for (int i = 0; i < d.getContainerSize(); i++) if (!d.getItem(i).isEmpty()) have.merge(d.getItem(i).getItem(), d.getItem(i).getCount(), Integer::sum);
        List<ItemStack> goods = new ArrayList<>();
        long cost = 0;
        StringBuilder what = new StringBuilder();
        for (Item it : wanted) {
            int h = have.getOrDefault(it, 0);
            boolean refill = it == ModItems.COLA_SYRUP.get() || it == ModItems.COFFEE_BEANS.get();
            if (!refill && h >= target / 3) continue;
            int[] ref = com.rpbusiness.economy.Suppliers.find(it);
            var pr = com.rpbusiness.economy.Suppliers.ALL.get(ref[0]).products().get(ref[1]);
            int packs = Math.max(1, (int) Math.ceil((target - h) / (double) pr.pack()));
            if (refill) packs = 1;
            if (cost + (long) packs * pr.price() > b.autoBuyBudget) packs = (int) ((b.autoBuyBudget - cost) / pr.price());
            if (packs <= 0) continue;
            cost += (long) packs * pr.price();
            int total = packs * pr.pack();
            while (total > 0) { int c = Math.min(64, total); goods.add(new ItemStack(it, c)); total -= c; }
            if (what.length() > 0) what.append(", ");
            what.append(packs * pr.pack()).append("x ").append(new ItemStack(it).getHoverName().getString());
        }
        if (goods.isEmpty() || goods.size() > 27) return;
        long fee = 8 + goods.size() * 2L;
        long total = cost + fee;
        com.rpbusiness.bank.Bank bank = com.rpbusiness.bank.Bank.get(sl);
        String payer;
        if (b.cash >= total) { b.cash -= total; payer = "cash"; }
        else if (bank.debit(sl, bank.businessAccount(sl, b), total, "Auto purchase by " + manager.npcName())) payer = "bank";
        else {
            notifyOwner(sl, b, Component.translatable("msg.rpbusiness.auto_no_money", manager.npcName(), Money.fmt(total)).withStyle(ChatFormatting.RED), false);
            return;
        }
        SupplyOrder o = createSupplyOrder(sl, b, goods, "Manager order", 0, 0, false);
        long day = Business.day(sl);
        b.finance.record(day, Business.hour(sl), Finance.Cat.AUTO_PURCHASE, -total, manager.npcName() + " (" + payer + ")");
        o.cost = cost;
        o.fee = fee;
        notifyOwner(sl, b, Component.translatable("msg.rpbusiness.auto_ordered", manager.npcName(), what.toString(), Money.fmt(total)).withStyle(ChatFormatting.AQUA), false);
    }

    // =================================================================== hotel
    public static int freeBeds(ServerLevel sl, Business b) {
        cleanClaims(sl, b);
        int n = 0;
        for (BlockPos p : equip(sl, b, Equip.BED))
            if (!b.seatClaims.containsKey(p) && !sl.getBlockState(p).getValue(HotelBedBlock.DIRTY) && !SeatEntity.occupied(sl, p)) n++;
        return n;
    }

    public static BlockPos claimBed(ServerLevel sl, Business b, UUID who) {
        cleanClaims(sl, b);
        for (var e : b.seatClaims.entrySet()) if (e.getValue().equals(who)) return e.getKey();
        for (BlockPos p : equip(sl, b, Equip.BED)) {
            if (b.seatClaims.containsKey(p) || sl.getBlockState(p).getValue(HotelBedBlock.DIRTY) || SeatEntity.occupied(sl, p)) continue;
            b.seatClaims.put(p.immutable(), who);
            return p;
        }
        return null;
    }

    // =================================================================== shops

    public static boolean hasProducts(ServerLevel sl, Business b) {
        for (BlockPos p : equip(sl, b, Equip.DISPLAY)) if (sl.getBlockEntity(p) instanceof DisplayBE d && d.hasProducts()) return true;
        return false;
    }

    public static int shoppers(ServerLevel sl, Business b) {
        int n = 0;
        for (CustomerEntity c : sl.getEntities(ModEntities.CUSTOMER.get(), x -> b.id.equals(x.businessId) && x.state != CustomerEntity.State.LEAVE)) n++;
        return n;
    }

    public static int priceOf(Business b, Item item) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
        return b.effective(b.prices.getOrDefault(id, com.rpbusiness.economy.Values.base(item)));
    }

    public static boolean assistantOnDuty(ServerLevel sl, Business b) {
        int h = Business.hour(sl);
        for (EmployeeRecord r : b.employees.values())
            if (r.job == Job.SALES_ASSISTANT && !r.onStrike() && r.onShift(h) && employee(sl, r.id) != null) return true;
        return false;
    }

    public static BlockPos pickDisplay(ServerLevel sl, Business b, List<BlockPos> visited, CustomerEntity c) {
        List<BlockPos> opts = new ArrayList<>();
        for (BlockPos p : equip(sl, b, Equip.DISPLAY))
            if (!visited.contains(p) && sl.getBlockEntity(p) instanceof DisplayBE d && d.hasProducts()) opts.add(p);
        if (opts.isEmpty()) return null;
        return opts.get(c.getRandom().nextInt(opts.size()));
    }

    /** The customer looks at the products on one shelf and takes what they like and can afford. */
    public static void shopAtDisplay(ServerLevel sl, Business b, CustomerEntity c, BlockPos pos) {
        if (!(sl.getBlockEntity(pos) instanceof DisplayBE d)) return;
        boolean helper = assistantOnDuty(sl, b);
        int spend = Math.min(c.money, (int) (c.budget * 1.3)) - c.bill;
        boolean tooExpensive = false;
        for (int i = 0; i < d.getContainerSize(); i++) {
            ItemStack st = d.getItem(i);
            if (!com.rpbusiness.economy.Values.sellable(st)) continue;
            int price = priceOf(b, st.getItem());
            double ratio = price / (double) Math.max(1, com.rpbusiness.economy.Values.base(st.getItem()));
            double pWant = 0.45 * Math.exp(-Math.max(0, ratio - 0.9) * 2.0 * c.priceSensitivity) * (helper ? 1.35 : 1.0);
            if (c.getRandom().nextDouble() > pWant) { if (ratio > 1.6) tooExpensive = true; continue; }
            int qty = 1 + c.getRandom().nextInt(3);
            qty = Math.min(qty, st.getCount());
            while (qty > 0 && price * qty > spend) qty--;
            if (qty <= 0) { tooExpensive = true; continue; }
            ItemStack taken = d.removeItem(i, qty);
            ItemStack rem = c.basket.addItem(taken);
            if (!rem.isEmpty()) { d.insert(rem); qty -= rem.getCount(); }
            c.bill += price * qty;
            spend -= price * qty;
        }
        if (c.countBasket() == 0 && tooExpensive) c.servedCount = -1; // remembers "too expensive" for the leave message
        sl.playSound(null, pos, ModSounds.PLACE_FOOD.get(), SoundSource.BLOCKS, 0.4f, 1.3f);
        b.stockTick = -1000;
    }

    public static int shopSatisfaction(ServerLevel sl, Business b, CustomerEntity c) {
        int items = c.countBasket();
        double ratioSum = 0;
        int n = 0;
        for (int i = 0; i < c.basket.getContainerSize(); i++) {
            ItemStack s = c.basket.getItem(i);
            if (s.isEmpty()) continue;
            ratioSum += priceOf(b, s.getItem()) / (double) Math.max(1, com.rpbusiness.economy.Values.base(s.getItem()));
            n++;
        }
        double ratio = n > 0 ? ratioSum / n : 1;
        double v = 50 + Math.min(20, items * 4) + 20 * Math.max(0, Math.min(1, (1.6 - ratio) / 1.1)) + Math.min(10, ambience(b) * 2)
                + (assistantOnDuty(sl, b) ? 10 : 0);
        return (int) Math.max(0, Math.min(100, v));
    }

    /** Puts a customer's unpaid products back on the shelves (or into storage). Nothing is lost or duplicated. */
    public static void returnBasket(ServerLevel sl, Business b, CustomerEntity c) {
        for (int i = 0; i < c.basket.getContainerSize(); i++) {
            ItemStack s = c.basket.removeItemNoUpdate(i);
            if (s.isEmpty()) continue;
            for (BlockPos p : equip(sl, b, Equip.DISPLAY)) {
                if (s.isEmpty()) break;
                if (sl.getBlockEntity(p) instanceof DisplayBE d) s = d.insert(s);
            }
            if (!s.isEmpty()) {
                BlockPos st = findStorageWithSpace(sl, b, null, c.blockPosition());
                if (st != null && sl.getBlockEntity(st) instanceof Container cont) s = ContainerUtil.insert(cont, s);
            }
            if (!s.isEmpty()) c.spawnAtLocation(s);
        }
        c.bill = 0;
    }

    // =================================================================== orders

    public static Order placeOrder(ServerLevel sl, Business b, CustomerEntity c, Dish d) {
        BusinessData data = data(sl);
        Order o = new Order();
        o.id = data.nextOrderId++;
        o.customer = c.getUUID();
        o.customerName = c.npcName();
        o.dish = d;
        o.price = b.effective(b.menu.get(d).price);
        o.seat = c.seat;
        o.created = sl.getGameTime();
        b.orders.add(o);
        data.setDirty();
        notifyOwner(sl, b, Component.translatable("msg.rpbusiness.new_order", c.npcName(), Component.translatable(d.key()), Money.fmt(o.price))
                .withStyle(ChatFormatting.YELLOW), false);
        ServerPlayer owner = b.owner == null ? null : sl.getServer().getPlayerList().getPlayer(b.owner);
        if (owner != null && owner.level() == sl && owner.distanceToSqr(c) < 64 * 64)
            sl.playSound(null, owner.blockPosition(), ModSounds.ORDER_BELL.get(), SoundSource.PLAYERS, 0.6f, 1.2f);
        // a matching dish may already be waiting on a counter
        matchCounters(sl, b);
        return o;
    }

    public static void cancelOrders(ServerLevel sl, Business b, UUID customer) {
        for (Order o : new ArrayList<>(b.orders)) {
            if (o.customer.equals(customer) && o.status != Order.Status.SERVED) {
                o.status = Order.Status.CANCELLED;
                b.archive(o, sl.getGameTime());
            }
        }
        data(sl).setDirty();
    }

    /** Marks pending orders READY when a matching finished dish is already on a serving counter. */
    public static void matchCounters(ServerLevel sl, Business b) {
        Map<BlockPos, Map<Dish, Integer>> reserved = new HashMap<>();
        for (Order o : b.orders) if (o.status == Order.Status.READY && o.readyAt != null)
            reserved.computeIfAbsent(o.readyAt, k -> new EnumMap<>(Dish.class)).merge(o.dish, 1, Integer::sum);
        for (Order o : b.orders) {
            if (o.status != Order.Status.PENDING || o.cook != null) continue;
            for (BlockPos p : equip(sl, b, Equip.SERVING)) {
                if (!(sl.getBlockEntity(p) instanceof ServingCounterBE c)) continue;
                int have = c.count(o.dish);
                int res = reserved.getOrDefault(p, Map.of()).getOrDefault(o.dish, 0);
                if (have > res) {
                    o.status = Order.Status.READY;
                    o.readyAt = p;
                    reserved.computeIfAbsent(p, k -> new EnumMap<>(Dish.class)).merge(o.dish, 1, Integer::sum);
                    break;
                }
            }
        }
    }

    public static void onDishOnCounter(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel sl)) return;
        Business b = data(sl).at(pos);
        if (b != null) matchCounters(sl, b);
    }

    public static boolean serve(ServerLevel sl, Business b, CustomerEntity c, Order o, ItemStack dish) {
        if (!o.dish.accepts(dish)) return false;
        if (!c.receive(sl, b, o, dish)) return false;
        deliveryClaims(b).remove(o.id);
        data(sl).setDirty();
        return true;
    }

    public static CustomerEntity customer(ServerLevel sl, UUID id) { return sl.getEntity(id) instanceof CustomerEntity c ? c : null; }
    public static EmployeeEntity employee(ServerLevel sl, UUID id) { return sl.getEntity(id) instanceof EmployeeEntity e ? e : null; }

    // =================================================================== payment

    public static BlockPos choosePayPoint(ServerLevel sl, Business b) {
        BlockPos best = null;
        for (BlockPos r : equip(sl, b, Equip.REGISTER)) if (cashierAt(sl, b, r)) return r;
        BlockPos t = findEquip(sl, b, Equip.TERMINAL, null, null);
        if (t != null) return t;
        List<BlockPos> regs = equip(sl, b, Equip.REGISTER);
        if (!regs.isEmpty()) best = regs.get(0);
        return best;
    }

    private static Direction facing(ServerLevel sl, BlockPos p) {
        BlockState s = sl.getBlockState(p);
        return s.hasProperty(HorizontalDirectionalBlock.FACING) ? s.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    private static BlockPos ground(ServerLevel sl, BlockPos p, NpcEntity npc) {
        if (npc.walkable(p)) return p;
        if (npc.walkable(p.below())) return p.below();
        if (npc.walkable(p.above())) return p.above();
        return p;
    }

    /** Where a paying customer stands: in front of the register/terminal, one block per person in the queue. */
    public static BlockPos queueSpot(ServerLevel sl, Business b, BlockPos pay, UUID who) {
        List<UUID> q = b.payQueues.getOrDefault(pay, List.of());
        int idx = q.indexOf(who);
        if (idx < 0) idx = q.size();
        Direction f = facing(sl, pay);
        CustomerEntity c = customer(sl, who);
        BlockPos p = pay.relative(f, 1 + idx);
        return c == null ? p : ground(sl, p, c);
    }

    public static BlockPos behind(ServerLevel sl, BlockPos reg, NpcEntity npc) {
        return ground(sl, reg.relative(facing(sl, reg).getOpposite()), npc);
    }

    public static void joinQueue(ServerLevel sl, Business b, BlockPos pay, CustomerEntity c) {
        List<UUID> q = b.payQueues.computeIfAbsent(pay.immutable(), k -> new ArrayList<>());
        if (!q.contains(c.getUUID())) q.add(c.getUUID());
        if (!cashierAt(sl, b, pay) && classify(sl, b, pay) == Equip.REGISTER && sl.getGameTime() - b.lastQueueMsg > 400) {
            b.lastQueueMsg = sl.getGameTime();
            notifyOwner(sl, b, Component.translatable("msg.rpbusiness.waiting_register", c.npcName(), Money.fmt(c.bill)).withStyle(ChatFormatting.GOLD), false);
        }
    }

    public static void leaveQueue(Business b, UUID who) {
        for (List<UUID> q : b.payQueues.values()) q.remove(who);
    }

    public static CustomerEntity firstInQueue(ServerLevel sl, Business b, BlockPos pay) {
        List<UUID> q = b.payQueues.get(pay);
        if (q == null) return null;
        q.removeIf(u -> !(customer(sl, u) instanceof CustomerEntity c) || c.state != CustomerEntity.State.WAIT_PAY);
        return q.isEmpty() ? null : customer(sl, q.get(0));
    }

    /** Self-service: customers pay by card at a payment terminal. */
    public static void tickPaying(ServerLevel sl, Business b, CustomerEntity c) {
        if (c.payPoint == null) return;
        if (classify(sl, b, c.payPoint) != Equip.TERMINAL) return;
        CustomerEntity first = firstInQueue(sl, b, c.payPoint);
        if (first == c && c.stateTicks > 60 && c.near(c.payPoint, 2.6)) processPayment(sl, b, c, "card");
    }

    public static boolean cashierAt(ServerLevel sl, Business b, BlockPos reg) {
        int h = Business.hour(sl);
        for (EmployeeRecord r : b.employees.values()) {
            if (r.job != Job.CASHIER || r.onStrike() || !r.onShift(h)) continue;
            EmployeeEntity e = employee(sl, r.id);
            if (e != null && e.distanceToSqr(reg.getX() + 0.5, reg.getY(), reg.getZ() + 0.5) < 16) return true;
        }
        return false;
    }

    public static BlockPos registerFor(ServerLevel sl, Business b, EmployeeRecord rec) {
        for (BlockPos st : rec.stations) if (sl.isLoaded(st) && classify(sl, b, st) == Equip.REGISTER) return st;
        if (rec.workPos != null && classify(sl, b, rec.workPos) == Equip.REGISTER) return rec.workPos;
        // pick the register no other cashier is using
        Set<BlockPos> taken = new HashSet<>();
        for (EmployeeRecord r : b.employees.values())
            if (r != rec && r.job == Job.CASHIER && r.workPos != null) taken.add(r.workPos);
        BlockPos near = rec.standPos != null ? rec.standPos : b.center();
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : equip(sl, b, Equip.REGISTER)) {
            double d = p.distSqr(near) + (taken.contains(p) ? 10000 : 0);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    /** Takes the customer's money (only what they have) and books it into the business. */
    public static boolean processPayment(ServerLevel sl, Business b, CustomerEntity c, String method) {
        if (c.state != CustomerEntity.State.WAIT_PAY && !method.equals("table")) return false;
        boolean shop = !b.type.foodService;
        int tip = shop ? 0 : Math.max(0, c.tip());
        int amount = Math.min(c.money, c.bill + tip);
        tip = Math.max(0, amount - c.bill);
        c.money -= amount;
        boolean electronic = method.equals("card");
        if (electronic) {
            c.hold(new ItemStack(ModItems.BANK_CARD.get()));
            com.rpbusiness.bank.Bank bank = com.rpbusiness.bank.Bank.get(sl);
            bank.credit(sl, bank.businessAccount(sl, b), amount, "Card payment - " + c.npcName());
        } else b.cash += amount;
        long day = Business.day(sl);
        int hour = Business.hour(sl);
        int food = 0, drink = 0;
        for (Order o : b.orders) {
            if (!o.customer.equals(c.getUUID()) || o.status != Order.Status.SERVED) continue;
            if (o.dish.isDrink()) drink += o.price; else food += o.price;
            b.menu.get(o.dish).sold++;
        }
        int scale = food + drink;
        if (scale > amount - tip && scale > 0) { // customer could not pay everything (should not happen)
            food = food * (amount - tip) / scale;
            drink = amount - tip - food;
        }
        String who = c.npcName();
        if (shop && amount > 0) b.finance.record(day, hour, b.type == BusinessType.HOTEL ? Finance.Cat.ROOM_SALES : Finance.Cat.PRODUCT_SALES,
                amount, who + " (" + method + ")");
        if (food > 0) b.finance.record(day, hour, Finance.Cat.FOOD_SALES, food, who + " (" + method + ")");
        if (drink > 0) b.finance.record(day, hour, Finance.Cat.DRINK_SALES, drink, who + " (" + method + ")");
        if (tip > 0) b.finance.record(day, hour, Finance.Cat.TIPS, tip, who);
        Finance.Day d = b.finance.day(day);
        d.customers++;
        b.customersServed++;
        int s = Math.max(0, c.satisfaction);
        b.totalSatisfaction += s;
        b.reputation = Math.max(0, Math.min(1000, b.reputation + (s - 55) / 3));
        leaveQueue(b, c.getUUID());
        finishVisit(sl, b, c, true);
        sl.playSound(null, c.blockPosition(), method.equals("card") ? ModSounds.CARD_BEEP.get() : ModSounds.CASH_REGISTER.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.getX(), c.getY() + 1.2, c.getZ(), 4, 0.3, 0.3, 0.3, 0);
        notifyOwner(sl, b, Component.translatable("msg.rpbusiness.payment", Money.fmt(amount), who, Money.fmt(tip)).withStyle(ChatFormatting.GREEN), true);
        c.paid(sl, b);
        data(sl).setDirty();
        return true;
    }

    public static void pay(ServerLevel sl, Business b, CustomerEntity c, String method) { processPayment(sl, b, c, method); }

    public static void finishVisit(ServerLevel sl, Business b, CustomerEntity c, boolean paid) {
        for (Order o : new ArrayList<>(b.orders)) {
            if (!o.customer.equals(c.getUUID())) continue;
            if (o.status != Order.Status.SERVED) o.status = Order.Status.CANCELLED;
            b.archive(o, sl.getGameTime());
        }
    }

    // =================================================================== staff

    public static int countJob(Business b, Job j) {
        int n = 0;
        for (EmployeeRecord r : b.employees.values()) if (r.job == j) n++;
        return n;
    }

    public static int freeChefs(ServerLevel sl, Business b) {
        int n = 0, h = Business.hour(sl);
        for (EmployeeRecord r : b.employees.values()) {
            if (r.job != Job.CHEF || r.onStrike() || !r.onShift(h)) continue;
            EmployeeEntity e = employee(sl, r.id);
            if (e != null && e.currentOrder() < 0) n++;
        }
        return n;
    }

    public static boolean managerOnDuty(ServerLevel sl, Business b) {
        int h = Business.hour(sl);
        for (EmployeeRecord r : b.employees.values())
            if (r.job == Job.MANAGER && !r.onStrike() && r.onShift(h) && employee(sl, r.id) != null) return true;
        return false;
    }

    public static void refreshCandidates(ServerLevel sl, Business b, boolean force) {
        long day = Business.day(sl);
        if (!force && b.candidateDay == day && !b.candidates.isEmpty()) return;
        b.candidateDay = day;
        b.candidates.clear();
        RandomSource r = sl.getRandom();
        for (int i = 0; i < 6; i++) {
            int skill = 1 + r.nextInt(3) + (r.nextInt(6) == 0 ? 2 : 0);
            Job[] jobs = Job.jobsFor(b.type);
            Job job = jobs[r.nextInt(jobs.length)];
            int salary = (int) Math.round(job.baseSalary * (0.75 + 0.15 * skill) * (0.9 + r.nextDouble() * 0.2));
            b.candidates.add(new Candidate(Names.random(r), Math.min(5, skill), salary, job, r.nextInt(4)));
        }
    }

    public static int salaryFor(Job job, int skill, int rank) {
        return (int) Math.round(job.baseSalary * (0.75 + 0.15 * skill) * (1 + 0.2 * rank));
    }

    public static boolean hire(ServerPlayer p, Business b, int index, Job job) {
        if (index < 0 || index >= b.candidates.size()) return false;
        ServerLevel sl = p.serverLevel();
        Candidate c = b.candidates.remove(index);
        BlockPos spawn = b.entranceOrCenter();
        BlockPos at = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
        if (b.contains(p.position()) || Math.abs(at.getY() - spawn.getY()) > 4) at = p.blockPosition();
        EmployeeRecord r = spawnEmployee(sl, b, c, job, at, p.getYRot() + 180);
        if (r == null) return false;
        p.displayClientMessage(Component.translatable("msg.rpbusiness.hired", r.name, Component.translatable(job.key()), Money.fmt(r.salary)).withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    public static EmployeeRecord spawnEmployee(ServerLevel sl, Business b, Candidate c, Job job, BlockPos at, float yaw) {
        EmployeeEntity e = ModEntities.EMPLOYEE.get().create(sl);
        if (e == null) return null;
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        e.businessId = b.id;
        e.setSkin(c.skin());
        e.setOutfit(job.outfit);
        e.setNpcName(c.name());
        EmployeeRecord r = new EmployeeRecord();
        r.id = e.getUUID();
        r.name = c.name();
        r.job = job;
        r.skill = c.skill();
        r.salary = job == c.preferred() ? c.salary() : salaryFor(job, c.skill(), 0);
        r.skin = c.skin();
        r.startHour = b.openHour;
        r.endHour = b.closeHour;
        r.hiredDay = Business.day(sl);
        b.employees.put(r.id, r);
        sl.addFreshEntity(e);
        b.finance.record(Business.day(sl), Business.hour(sl), Finance.Cat.HIRING, 0, "Hired " + r.name);
        data(sl).setDirty();
        return r;
    }

    public static void fire(ServerLevel sl, Business b, UUID id) {
        EmployeeRecord r = b.employees.remove(id);
        if (r == null) return;
        for (Order o : b.orders) if (id.equals(o.cook) && o.status == Order.Status.COOKING) { o.status = Order.Status.PENDING; o.cook = null; }
        deliveryClaims(b).values().removeIf(u -> u.equals(id));
        notifyOwner(sl, b, Component.translatable("msg.rpbusiness.fired", r.name), false);
        data(sl).setDirty();
    }

    /** Pays one day's wages from the business' cash. Unpaid staff go on strike until paid. */
    private static void paySalaries(ServerLevel sl, Business b, long day) {
        for (EmployeeRecord r : b.employees.values()) {
            if (r.hiredDay == day) continue;
            com.rpbusiness.bank.Bank bank = com.rpbusiness.bank.Bank.get(sl);
            if (b.cash >= r.salary) {
                b.cash -= r.salary;
                b.finance.record(day, 0, Finance.Cat.SALARIES, -r.salary, r.name);
                if (r.unpaidDays > 0) r.unpaidDays = 0;
            } else if (bank.debit(sl, bank.businessAccount(sl, b), r.salary, "Salary " + r.name)) {
                b.finance.record(day, 0, Finance.Cat.SALARIES, -r.salary, r.name + " (bank)");
                if (r.unpaidDays > 0) r.unpaidDays = 0;
            } else {
                r.unpaidDays++;
                notifyOwner(sl, b, Component.translatable("msg.rpbusiness.cant_pay", r.name, Money.fmt(r.salary)).withStyle(ChatFormatting.RED), false);
            }
        }
    }

    public static boolean payOverdue(ServerLevel sl, Business b, UUID id) {
        EmployeeRecord r = b.employees.get(id);
        if (r == null || r.unpaidDays <= 0) return false;
        long owed = (long) r.salary * r.unpaidDays;
        if (b.cash < owed) return false;
        b.cash -= owed;
        b.finance.record(Business.day(sl), Business.hour(sl), Finance.Cat.SALARIES, -owed, r.name + " (overdue)");
        r.unpaidDays = 0;
        data(sl).setDirty();
        return true;
    }

    // =================================================================== registration

    public static Business register(ServerPlayer p, BlockPos a, BlockPos c, String name, BusinessType type) {
        ServerLevel sl = p.serverLevel();
        if (sl != sl.getServer().overworld()) { p.displayClientMessage(Component.translatable("msg.rpbusiness.overworld_only").withStyle(ChatFormatting.RED), false); return null; }
        BlockPos min = new BlockPos(Math.min(a.getX(), c.getX()), Math.min(a.getY(), c.getY()), Math.min(a.getZ(), c.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), c.getX()), Math.max(a.getY(), c.getY()), Math.max(a.getZ(), c.getZ()));
        int maxSize = Config.MAX_BUSINESS_SIZE.get();
        if (max.getX() - min.getX() + 1 > maxSize || max.getZ() - min.getZ() + 1 > maxSize || max.getY() - min.getY() + 1 > 48) {
            p.displayClientMessage(Component.translatable("msg.rpbusiness.too_big", maxSize).withStyle(ChatFormatting.RED), false);
            return null;
        }
        if (max.getX() - min.getX() < 2 || max.getZ() - min.getZ() < 2 || max.getY() - min.getY() < 1) {
            p.displayClientMessage(Component.translatable("msg.rpbusiness.too_small").withStyle(ChatFormatting.RED), false);
            return null;
        }
        BusinessData d = data(sl);
        if (d.overlaps(min, max, null)) {
            p.displayClientMessage(Component.translatable("msg.rpbusiness.overlap").withStyle(ChatFormatting.RED), false);
            return null;
        }
        Business b = new Business();
        b.id = UUID.randomUUID();
        b.name = name.isBlank() ? p.getGameProfile().getName() + "'s " + Component.translatable(type.key()).getString() : name.trim();
        if (b.name.length() > 32) b.name = b.name.substring(0, 32);
        b.type = type;
        b.owner = p.getUUID();
        b.ownerName = p.getGameProfile().getName();
        b.min = min;
        b.max = max;
        b.entrance = findEntrance(sl, b);
        if (type.foodService) for (Dish dish : new Dish[]{Dish.BURGER, Dish.FRIES, Dish.COLA}) b.menu.get(dish).enabled = true;
        d.businesses.put(b.id, b);
        com.rpbusiness.bank.Bank.get(sl).businessAccount(sl, b);
        scan(sl, b);
        refreshCandidates(sl, b, true);
        b.lastSalaryDay = Business.day(sl);
        d.setDirty();
        p.displayClientMessage(Component.translatable("msg.rpbusiness.registered", b.name, Component.translatable(type.key())).withStyle(ChatFormatting.GREEN), false);
        return b;
    }

    /**
     * Finds how customers get in: any door inside (or right next to) the area, otherwise an open doorway in the
     * walls. Returns a walkable spot just outside it.
     */
    public static BlockPos findEntrance(ServerLevel sl, Business b) {
        BlockPos c = b.center();
        BlockPos best = null;
        double bestScore = -1e9;
        for (BlockPos p : BlockPos.betweenClosed(b.min.offset(-1, 0, -1), b.max.offset(1, 0, 1))) {
            if (!sl.isLoaded(p)) continue;
            BlockState s = sl.getBlockState(p);
            if (!(s.getBlock() instanceof DoorBlock || s.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock)) continue;
            if (s.hasProperty(DoorBlock.HALF) && s.getValue(DoorBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) continue;
            Direction f = s.getValue(HorizontalDirectionalBlock.FACING);
            for (Direction out : new Direction[]{f, f.getOpposite()}) {
                BlockPos o1 = p.relative(out), o2 = p.relative(out, 2);
                double away = Math.sqrt(o1.distSqr(c)) - Math.sqrt(p.distSqr(c));
                double score = away * 10 + (b.contains(o1) ? 0 : 50) + (b.contains(o2) ? 0 : 50) + (isOpenSpot(sl, o2) ? 20 : 0)
                        - Math.abs(p.getY() - b.min.getY()) * 2;
                if (score > bestScore) { bestScore = score; best = isOpenSpot(sl, o2) ? o2.immutable() : o1.immutable(); }
            }
        }
        if (best != null) return best;
        // no door: look for a 2-high gap in the outer wall
        int fy = b.min.getY();
        for (int y = fy; y <= Math.min(b.max.getY(), fy + 3); y++) {
            for (BlockPos p : BlockPos.betweenClosed(new BlockPos(b.min.getX(), y, b.min.getZ()), new BlockPos(b.max.getX(), y, b.max.getZ()))) {
                boolean edge = p.getX() == b.min.getX() || p.getX() == b.max.getX() || p.getZ() == b.min.getZ() || p.getZ() == b.max.getZ();
                if (!edge || !isOpenSpot(sl, p)) continue;
                Direction out = Direction.getNearest(p.getX() - c.getX(), 0, p.getZ() - c.getZ());
                if (out.getAxis().isVertical()) continue;
                BlockPos o = p.relative(out);
                if (isOpenSpot(sl, o) && isOpenSpot(sl, p.relative(out.getOpposite()))) return o.relative(out).immutable();
            }
        }
        return null;
    }

    private static boolean isOpenSpot(ServerLevel sl, BlockPos p) {
        return sl.getBlockState(p).getCollisionShape(sl, p).isEmpty() && sl.getBlockState(p.above()).getCollisionShape(sl, p.above()).isEmpty()
                && !sl.getBlockState(p.below()).getCollisionShape(sl, p.below()).isEmpty();
    }

    public static void deleteBusiness(ServerLevel sl, Business b) {
        for (UUID id : new ArrayList<>(b.employees.keySet())) fire(sl, b, id);
        data(sl).businesses.remove(b.id);
        data(sl).setDirty();
    }

    // =================================================================== supplier orders

    public static boolean placeSupplyOrder(ServerPlayer p, Business b, List<ItemStack> goods, String supplier, long cost, boolean payCash) {
        return placeSupplyOrder(p, b, goods, supplier, cost, payCash ? 1 : 0);
    }

    /** payMode: 0 business cash drawer, 1 the player's own cash, 2 the business bank account. */
    public static boolean placeSupplyOrder(ServerPlayer p, Business b, List<ItemStack> goods, String supplier, long cost, int payMode) {
        ServerLevel sl = p.serverLevel();
        if (goods.isEmpty()) return false;
        long fee = 8 + goods.size() * 2L;
        long total = cost + fee;
        boolean payCash = payMode == 1;
        if (payMode == 2) {
            com.rpbusiness.bank.Bank bank = com.rpbusiness.bank.Bank.get(sl);
            if (!bank.debit(sl, bank.businessAccount(sl, b), total, "Supplier: " + supplier)) {
                p.displayClientMessage(Component.translatable("msg.rpbusiness.bank_insufficient").withStyle(ChatFormatting.RED), false);
                return false;
            }
        } else if (payCash) {
            if (!Money.takeCash(p, total)) { p.displayClientMessage(Component.translatable("msg.rpbusiness.not_enough_cash", Money.fmt(total)).withStyle(ChatFormatting.RED), false); return false; }
        } else {
            if (b.cash < total) { p.displayClientMessage(Component.translatable("msg.rpbusiness.not_enough_business_cash", Money.fmt(total)).withStyle(ChatFormatting.RED), false); return false; }
            b.cash -= total;
        }
        SupplyOrder o = createSupplyOrder(sl, b, goods, supplier, cost, fee, !payCash);
        p.displayClientMessage(Component.translatable("msg.rpbusiness.supply_ordered", o.id, supplier, Money.fmt(total)).withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    public static SupplyOrder createSupplyOrder(ServerLevel sl, Business b, List<ItemStack> goods, String supplier, long cost, long fee, boolean booked) {
        BusinessData d = data(sl);
        SupplyOrder o = new SupplyOrder();
        o.id = d.nextSupplyId++;
        o.supplier = supplier;
        o.goods.addAll(goods);
        o.cost = cost;
        o.fee = fee;
        o.placed = sl.getGameTime();
        o.eta = sl.getGameTime() + 600 + sl.getRandom().nextInt(600); // 30-60 seconds (0.5-1 in-game hour)
        b.supplyOrders.add(o);
        while (b.supplyOrders.size() > 30) {
            SupplyOrder old = b.supplyOrders.stream().filter(x -> x.status == SupplyOrder.Status.DELIVERED).findFirst().orElse(null);
            if (old == null) break;
            b.supplyOrders.remove(old);
        }
        long day = Business.day(sl);
        int h = Business.hour(sl);
        if (booked) {
            b.finance.record(day, h, Finance.Cat.SUPPLIES, -cost, supplier);
            b.finance.record(day, h, Finance.Cat.DELIVERY, -fee, "Delivery #" + o.id);
        }
        d.setDirty();
        return o;
    }

    private static void tickSupply(ServerLevel sl, Business b) {
        for (SupplyOrder o : b.supplyOrders) {
            if (o.status != SupplyOrder.Status.ORDERED || sl.getGameTime() < o.eta) continue;
            BlockPos dest = b.entranceOrCenter();
            if (!sl.isLoaded(dest)) continue; // the courier only drives while the area is loaded
            CourierEntity c = ModEntities.COURIER.get().create(sl);
            if (c == null) continue;
            BlockPos start = reachableSpawn(sl, c, dest, 16, 26);
            if (start == null) start = reachableSpawn(sl, c, dest, 6, 14);
            if (start == null) { o.eta = sl.getGameTime() + 200; continue; } // try again shortly
            c.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0, 0);
            c.businessId = b.id;
            c.supplyId = o.id;
            c.setOrigin(start);
            c.setNpcName(Names.random(sl.getRandom()));
            c.setSkin(sl.getRandom().nextInt(4));
            for (ItemStack s : o.goods) c.cargo.addItem(s.copy());
            sl.addFreshEntity(c);
            o.status = SupplyOrder.Status.IN_TRANSIT;
            o.courier = c.getUUID();
            o.courierSpawned = sl.getGameTime();
            notifyOwner(sl, b, Component.translatable("msg.rpbusiness.courier_coming", o.id).withStyle(ChatFormatting.YELLOW), true);
        }
    }

    // =================================================================== spawning helpers

    public static BlockPos spawnPointNear(ServerLevel sl, BlockPos center, int minD, int maxD) {
        RandomSource r = sl.getRandom();
        for (int i = 0; i < 20; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            int d = minD + r.nextInt(Math.max(1, maxD - minD));
            int x = center.getX() + (int) (Math.cos(ang) * d), z = center.getZ() + (int) (Math.sin(ang) * d);
            if (!sl.isLoaded(new BlockPos(x, center.getY(), z))) continue;
            BlockPos top = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (Math.abs(top.getY() - center.getY()) > 10) continue;
            BlockState below = sl.getBlockState(top.below());
            if (!below.getFluidState().isEmpty() || below.isAir()) continue;
            if (data(sl).at(top) != null || data(sl).at(top.below()) != null || data(sl).at(top.below(2)) != null) continue; // not on roofs
            return top;
        }
        return null;
    }

    /** A spawn point from which this NPC can really walk to the destination (no teleporting later). */
    public static BlockPos reachableSpawn(ServerLevel sl, NpcEntity npc, BlockPos dest, int minD, int maxD) {
        BlockPos fallback = null;
        for (int i = 0; i < 16; i++) {
            BlockPos at = spawnPointNear(sl, dest, minD, maxD);
            if (at == null) continue;
            if (fallback == null) fallback = at;
            npc.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            npc.setOnGround(true); // navigation refuses to plan for entities in the air
            net.minecraft.world.level.pathfinder.Path path = npc.getNavigation().createPath(dest, 1);
            if (path != null && path.canReach()) return at;
            if (path != null && path.getEndNode() != null && path.getEndNode().asBlockPos().distSqr(dest) <= 4) return at;
        }
        return null;
    }

    public static void spawnCustomerFor(ServerLevel sl, Business b) {
        CustomerEntity c = ModEntities.CUSTOMER.get().create(sl);
        if (c == null) return;
        BlockPos at = reachableSpawn(sl, c, b.entranceOrCenter(), Math.max(6, Config.SPAWN_DISTANCE.get() - 8), Config.SPAWN_DISTANCE.get() + 6);
        if (at == null) return;
        c.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, sl.getRandom().nextFloat() * 360, 0);
        c.randomize(sl.getRandom());
        c.home = at;
        sl.addFreshEntity(c);
    }

    // =================================================================== periodic business upkeep

    public static void tickBusiness(ServerLevel sl, Business b) {
        long day = Business.day(sl);
        if (b.lastSalaryDay < 0) b.lastSalaryDay = day;
        if (day > b.lastSalaryDay) {
            for (long d = b.lastSalaryDay + 1; d <= day; d++) paySalaries(sl, b, d);
            b.lastSalaryDay = day;
            refreshCandidates(sl, b, true);
            notifyOwner(sl, b, Component.translatable("msg.rpbusiness.new_day", b.name, Money.fmt(b.cash)).withStyle(ChatFormatting.AQUA), false);
        }
        if (b.bankAccount == null && b.owner != null) com.rpbusiness.bank.Bank.get(sl).businessAccount(sl, b);
        com.rpbusiness.delivery.Deliveries.tick(sl, b);
        if (!sl.isLoaded(b.center())) return;
        if (b.entrance == null && sl.getGameTime() % 200 == 0) b.entrance = findEntrance(sl, b);
        cleanClaims(sl, b);
        matchCounters(sl, b);
        for (Order o : new ArrayList<>(b.orders)) {
            // orders whose cook vanished go back to the queue
            if (o.status == Order.Status.COOKING) {
                EmployeeEntity e = o.cook == null ? null : employee(sl, o.cook);
                if (e == null || e.currentOrder() != o.id) { o.status = Order.Status.PENDING; o.cook = null; }
            }
            if (o.status == Order.Status.READY && o.readyAt != null && !(sl.getBlockEntity(o.readyAt) instanceof ServingCounterBE)) {
                o.status = Order.Status.PENDING; o.readyAt = null;
            }
            if (customer(sl, o.customer) == null) {
                int m = ORDER_MISSING.merge(o.id, 20, Integer::sum);
                if (m > 1200) { o.status = Order.Status.CANCELLED; b.archive(o, sl.getGameTime()); ORDER_MISSING.remove(o.id); }
            } else ORDER_MISSING.remove(o.id);
        }
        tickSupply(sl, b);
    }

    // =================================================================== block interaction

    public static void useFunctionalBlock(ServerPlayer p, BlockPos pos, ShapedBlock.Kind kind) {
        ServerLevel sl = p.serverLevel();
        Business b = data(sl).at(pos);
        switch (kind) {
            case COMPUTER -> {
                if (b == null) Net.openRegistration(p, pos);
                else if (!isOwner(p, b)) p.displayClientMessage(Component.translatable("msg.rpbusiness.not_owner", b.ownerName).withStyle(ChatFormatting.RED), true);
                else { if (!b.equipment.get(Equip.COMPUTER).contains(pos)) index(sl, b, pos); Net.openBusiness(p, b, "overview"); }
            }
            case SUPPLIER -> {
                if (b == null) p.displayClientMessage(Component.translatable("msg.rpbusiness.need_business").withStyle(ChatFormatting.RED), true);
                else if (!isOwner(p, b)) p.displayClientMessage(Component.translatable("msg.rpbusiness.not_owner", b.ownerName).withStyle(ChatFormatting.RED), true);
                else Net.openSupplier(p, b);
            }
            case REGISTER -> {
                if (b == null) { p.displayClientMessage(Component.translatable("msg.rpbusiness.need_business").withStyle(ChatFormatting.RED), true); return; }
                CustomerEntity c = firstInQueue(sl, b, pos);
                if (c == null) {
                    Finance.Day d = dayRecord(sl, b);
                    p.displayClientMessage(Component.translatable("msg.rpbusiness.register_idle", Money.fmt(d.income()), Money.fmt(b.cash)), true);
                } else if (isOwner(p, b)) {
                    processPayment(sl, b, c, "cash");
                }
            }
            case TERMINAL -> {
                if (b == null) { p.displayClientMessage(Component.translatable("msg.rpbusiness.need_business").withStyle(ChatFormatting.RED), true); return; }
                p.displayClientMessage(Component.translatable("msg.rpbusiness.terminal_info", b.name), true);
            }
            case BANK, BANK_COMPUTER -> Net.openBank(p, null, kind == ShapedBlock.Kind.BANK_COMPUTER);
            case ATM -> {
                ItemStack card = findCard(p);
                if (card == null) p.displayClientMessage(Component.translatable("msg.rpbusiness.need_card").withStyle(ChatFormatting.RED), true);
                else Net.openAtm(p, card.get(ModComponents.CARD_ACCOUNT.get()));
            }
            case DELIVERY -> {
                if (b == null || !isOwner(p, b)) p.displayClientMessage(Component.translatable("msg.rpbusiness.need_business").withStyle(ChatFormatting.RED), true);
                else Net.openBusiness(p, b, b.type.delivery() ? "deliveries" : "overview");
            }
            case MENU_BOARD -> {
                if (b == null) { p.displayClientMessage(Component.translatable("msg.rpbusiness.need_business").withStyle(ChatFormatting.RED), true); return; }
                p.displayClientMessage(Component.literal("══ " + b.name + " ══").withStyle(ChatFormatting.GOLD), false);
                List<Dish> avail = availableDishes(sl, b);
                for (Dish d : b.enabledDishes()) {
                    boolean ok = avail.contains(d);
                    p.displayClientMessage(Component.literal(" • ").append(Component.translatable(d.key())).append(" ... " + Money.fmt(b.menu.get(d).price))
                            .withStyle(ok ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY), false);
                }
            }
            default -> {}
        }
    }

    public static ItemStack findCard(ServerPlayer p) {
        ItemStack main = p.getMainHandItem();
        if (main.is(ModItems.BANK_CARD.get()) && main.has(ModComponents.CARD_ACCOUNT.get())) return main;
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.BANK_CARD.get()) && s.has(ModComponents.CARD_ACCOUNT.get())) return s;
        return null;
    }

    public static void log(String s) { RPBusiness.LOG.info(s); }
}
