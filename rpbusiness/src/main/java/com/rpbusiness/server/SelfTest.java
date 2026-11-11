package com.rpbusiness.server;

import com.rpbusiness.RPBusiness;
import com.rpbusiness.block.ChairBlock;
import com.rpbusiness.block.StorageBE;
import com.rpbusiness.block.TableBlock;
import com.rpbusiness.business.*;
import com.rpbusiness.core.ModBlocks;
import com.rpbusiness.core.ModEntities;
import com.rpbusiness.core.ModItems;
import com.rpbusiness.entity.CustomerEntity;
import com.rpbusiness.entity.EmployeeEntity;
import com.rpbusiness.food.Dish;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.UUID;

/**
 * Developer self-test, only active when the JVM is started with -Drpbusiness.selftest=build|verify.
 * It builds a small restaurant, hires staff, stocks the fridge, lets customers come and checks that orders
 * are cooked, served and paid for with real equipment. Never runs in normal play.
 */
public final class SelfTest {
    private SelfTest() {}

    private static final String MODE = System.getProperty("rpbusiness.selftest");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000beef");
    private static long startTick = -1;
    private static int spawned;
    private static BlockPos origin;

    public static boolean active() { return MODE != null; }

    private static void log(String s) { RPBusiness.LOG.info("[SELFTEST] " + s); }

    public static void onStart(MinecraftServer server) {
        if (!active()) return;
        ServerLevel sl = server.overworld();
        BusinessData data = BusinessData.get(sl);
        Business existing = null;
        for (Business b : data.businesses.values()) if (b.name.equals("SelfTest Diner")) existing = b;
        if (MODE.equals("verify")) {
            if (existing == null) { log("VERIFY FAIL: business not found after reload"); return; }
            log("VERIFY business=" + existing.name + " cash=" + existing.cash + " served=" + existing.customersServed
                    + " employees=" + existing.employees.size() + " rep=" + existing.reputation + " orders=" + existing.orders.size()
                    + " history=" + existing.history.size() + " supply=" + existing.supplyOrders.size()
                    + " txlog=" + existing.finance.log.size() + " equipment=" + existing.equipment.get(Equip.CHAIR).size() + " chairs");
            origin = existing.min;
            for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 2; dz++)
                sl.setChunkForced((origin.getX() >> 4) + dx, (origin.getZ() >> 4) + dz, true);
            startTick = sl.getGameTime();
            return;
        }
        if (existing != null) { log("already built"); return; }
        BlockPos spawn = sl.getSharedSpawnPos();
        int ox = spawn.getX() + 30, oz = spawn.getZ() + 30;
        for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 2; dz++) sl.setChunkForced((ox >> 4) + dx, (oz >> 4) + dz, true);
        int y = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(ox + 6, 0, oz + 6)).getY();
        origin = new BlockPos(ox, y, oz);
        build(sl, origin);
        Business b = new Business();
        b.id = UUID.randomUUID();
        b.name = "SelfTest Diner";
        b.owner = OWNER;
        b.ownerName = "Tester";
        b.min = origin.offset(0, -1, 0);
        b.max = origin.offset(12, 5, 12);
        b.openHour = 0;
        b.closeHour = 0;
        b.cash = 1000;
        for (Dish d : Dish.values()) b.menu.get(d).enabled = true;
        data.businesses.put(b.id, b);
        b.entrance = BusinessManager.findEntrance(sl, b);
        BusinessManager.scan(sl, b);
        log("entrance=" + b.entrance + " equipment=" + b.equipment);
        BlockPos inside = origin.offset(6, 0, 6);
        BusinessManager.spawnEmployee(sl, b, new Candidate("Chef Karim", 4, 70, Job.CHEF, 0), Job.CHEF, inside, 0);
        BusinessManager.spawnEmployee(sl, b, new Candidate("Lina Prep", 2, 40, Job.ASSISTANT_CHEF, 1), Job.ASSISTANT_CHEF, inside, 0);
        BusinessManager.spawnEmployee(sl, b, new Candidate("Omar Waiter", 3, 35, Job.WAITER, 2), Job.WAITER, inside, 0);
        BusinessManager.spawnEmployee(sl, b, new Candidate("Sara Cash", 3, 35, Job.CASHIER, 3), Job.CASHIER, inside, 0);
        BusinessManager.spawnEmployee(sl, b, new Candidate("Ali Clean", 2, 25, Job.CLEANER, 0), Job.CLEANER, inside, 0);
        // supplier order: arrives by courier
        BusinessManager.createSupplyOrder(sl, b, List.of(new ItemStack(ModItems.TOMATO.get(), 16), new ItemStack(ModItems.POTATO.get(), 8)),
                "Green Valley Farms", 22, 10, true);
        data.setDirty();
        startTick = sl.getGameTime();
        log("built at " + origin);
    }

    private static void set(ServerLevel sl, BlockPos p, BlockState s) { sl.setBlock(p, s, 3); }

    private static BlockState facing(Block b, Direction d) {
        return b.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, d);
    }

    private static void build(ServerLevel sl, BlockPos o) {
        // clear & floor 13x13, walls 4 high, roof
        for (int x = 0; x <= 12; x++) for (int z = 0; z <= 12; z++) {
            for (int y = 0; y <= 5; y++) set(sl, o.offset(x, y, z), Blocks.AIR.defaultBlockState());
            set(sl, o.offset(x, -1, z), ModBlocks.CHECKERED_FLOOR.get().defaultBlockState());
            set(sl, o.offset(x, 5, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
            boolean wall = x == 0 || z == 0 || x == 12 || z == 12;
            if (wall) for (int y = 0; y < 5; y++) set(sl, o.offset(x, y, z), Blocks.BRICKS.defaultBlockState());
        }
        // ground in front of the door
        for (int x = 4; x <= 8; x++) for (int z = 13; z <= 16; z++) {
            set(sl, o.offset(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState());
            for (int y = 0; y < 3; y++) set(sl, o.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        // door on the south wall
        BlockPos door = o.offset(6, 0, 12);
        set(sl, door, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(sl, door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // kitchen along the north wall (z=1), facing south
        Direction s = Direction.SOUTH;
        set(sl, o.offset(1, 0, 1), facing(ModBlocks.FRIDGE.get(), s));
        set(sl, o.offset(1, 1, 1), facing(ModBlocks.FRIDGE.get(), s).setValue(com.rpbusiness.block.FridgeBlock.HALF, DoubleBlockHalf.UPPER));
        set(sl, o.offset(2, 0, 1), facing(ModBlocks.KITCHEN_COUNTER.get(), s));
        set(sl, o.offset(2, 1, 1), facing(ModBlocks.CUTTING_BOARD.get(), s));
        set(sl, o.offset(3, 0, 1), facing(ModBlocks.GRILL.get(), s));
        set(sl, o.offset(4, 0, 1), facing(ModBlocks.FRYER.get(), s));
        set(sl, o.offset(5, 0, 1), facing(ModBlocks.OVEN.get(), s));
        set(sl, o.offset(6, 0, 1), facing(ModBlocks.KITCHEN_COUNTER.get(), s));
        set(sl, o.offset(6, 1, 1), facing(ModBlocks.COOKING_POT.get(), s));
        set(sl, o.offset(7, 0, 1), facing(ModBlocks.PREP_STATION.get(), s));
        set(sl, o.offset(8, 0, 1), facing(ModBlocks.DRINK_MACHINE.get(), s));
        set(sl, o.offset(9, 0, 1), facing(ModBlocks.KITCHEN_COUNTER.get(), s));
        set(sl, o.offset(9, 1, 1), facing(ModBlocks.CUTTING_BOARD.get(), s));
        set(sl, o.offset(10, 0, 1), facing(ModBlocks.FREEZER.get(), s));
        set(sl, o.offset(11, 0, 1), facing(ModBlocks.DELIVERY_CRATE.get(), s));
        // serving counter + register row at z=4 (with a gap to walk through)
        set(sl, o.offset(2, 0, 4), facing(ModBlocks.SERVING_COUNTER.get(), s));
        set(sl, o.offset(3, 0, 4), facing(ModBlocks.SERVING_COUNTER.get(), s));
        set(sl, o.offset(9, 0, 4), facing(ModBlocks.KITCHEN_COUNTER.get(), s));
        set(sl, o.offset(9, 1, 4), facing(ModBlocks.CASH_REGISTER.get(), s));
        set(sl, o.offset(11, 0, 7), facing(ModBlocks.PAYMENT_TERMINAL.get(), Direction.WEST));
        // dining: 3 tables with 2 chairs each
        for (int i = 0; i < 3; i++) {
            BlockPos t = o.offset(2 + i * 3, 0, 8);
            set(sl, t, ModBlocks.DINING_TABLE.get().defaultBlockState());
            set(sl, t.west(), facing(ModBlocks.DINING_CHAIR.get(), Direction.EAST));
            set(sl, t.east(), facing(ModBlocks.DINING_CHAIR.get(), Direction.WEST));
        }
        set(sl, o.offset(6, 4, 6), ModBlocks.PENDANT_LAMP.get().defaultBlockState());
        set(sl, o.offset(1, 0, 11), ModBlocks.POTTED_PLANT.get().defaultBlockState());
        set(sl, o.offset(11, 0, 11), ModBlocks.POTTED_PLANT.get().defaultBlockState());
        set(sl, o.offset(10, 0, 9), facing(ModBlocks.MENU_BOARD.get(), Direction.WEST));
        // stock the fridge & freezer with real ingredients
        Item[] stock = {ModItems.TOMATO.get(), ModItems.ONION.get(), ModItems.LETTUCE.get(), ModItems.POTATO.get(), ModItems.RAW_BEEF.get(),
                ModItems.BURGER_BUN.get(), ModItems.CHEESE_BLOCK.get(), ModItems.BREAD_LOAF.get(), ModItems.PIZZA_DOUGH.get(), ModItems.PAPER_CUP.get()};
        if (sl.getBlockEntity(o.offset(1, 0, 1)) instanceof StorageBE f) for (Item it : stock) f.insert(new ItemStack(it, 12));
        if (sl.getBlockEntity(o.offset(10, 0, 1)) instanceof StorageBE f) f.insert(new ItemStack(ModItems.RAW_BEEF.get(), 12));
        if (sl.getBlockEntity(o.offset(8, 0, 1)) instanceof com.rpbusiness.block.DrinkMachineBE dm) {
            for (int i = 0; i < 3; i++) { dm.addRefill(ModItems.COLA_SYRUP.get()); dm.addRefill(ModItems.COFFEE_BEANS.get()); }
        }
    }

    /** Dev-only: -Drpbusiness.camera=dx,dy,dz,yaw,pitch places the player relative to the test restaurant for screenshots. */
    public static void onLogin(net.minecraft.server.level.ServerPlayer p) {
        String cam = System.getProperty("rpbusiness.camera");
        if (cam == null) return;
        Business b = null;
        for (Business x : BusinessData.get(p.serverLevel()).businesses.values()) if (x.name.equals("SelfTest Diner")) b = x;
        if (b == null) return;
        String[] v = cam.split(",");
        BlockPos o = b.min.above();
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.serverLevel().setDayTime(6000);
        p.teleportTo(p.serverLevel(), o.getX() + Double.parseDouble(v[0]), o.getY() + Double.parseDouble(v[1]), o.getZ() + Double.parseDouble(v[2]),
                Float.parseFloat(v[3]), Float.parseFloat(v[4]));
        for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 2; dz++) p.serverLevel().setChunkForced((o.getX() >> 4) + dx, (o.getZ() >> 4) + dz, true);
        if (System.getProperty("rpbusiness.guitour") != null) {
            b.owner = p.getUUID();
            b.ownerName = p.getGameProfile().getName();
            BusinessManager.refreshCandidates(p.serverLevel(), b, true);
            com.rpbusiness.network.Net.openBusiness(p, b, "overview");
        }
    }

    public static void tick(ServerLevel sl) {
        if (!active() || startTick < 0) return;
        long t = sl.getGameTime() - startTick;
        Business b = null;
        for (Business x : BusinessData.get(sl).businesses.values()) if (x.name.equals("SelfTest Diner")) b = x;
        if (b == null) return;
        if (MODE.equals("build") && t % 160 == 0 && spawned < Integer.getInteger("rpbusiness.selftest.customers", 14) && t > 40) {
            if (spawned % 2 == 1) {
                BusinessManager.spawnCustomerFor(sl, b);
                spawned++;
                log("spawned customer via normal spawning");
            } else {
            CustomerEntity c = ModEntities.CUSTOMER.get().create(sl);
            if (c != null) {
                BlockPos at = b.entranceOrCenter().south(6);
                at = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
                c.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                c.randomize(sl.getRandom());
                c.money = 200;
                c.budget = 120;
                c.patience = 20 * 60 * 6;
                c.wanderTime = 10;
                c.home = at;
                sl.addFreshEntity(c);
                spawned++;
                log("spawned customer " + c.npcName() + " favorite=" + c.favorite);
            }
            }
        }
        if (t % 200 == 0) {
            StringBuilder sb = new StringBuilder("t=" + t + " cash=" + b.cash + " served=" + b.customersServed + " lost=" + b.customersLost + " rep=" + b.reputation);
            sb.append(" orders=[");
            for (Order o : b.orders) sb.append(o.id).append(':').append(o.dish.id()).append('/').append(o.status).append(o.shortage != null ? "!" + o.shortage : "").append(' ');
            sb.append("] staff=[");
            for (EmployeeRecord r : b.employees.values()) {
                EmployeeEntity e = BusinessManager.employee(sl, r.id);
                sb.append(r.job).append('=').append(r.status).append(e == null ? "(missing)" : "@" + e.blockPosition().toShortString()).append(' ');
            }
            sb.append("] customers=[");
            for (CustomerEntity c : sl.getEntities(ModEntities.CUSTOMER.get(), c -> true))
                sb.append(c.npcName()).append('=').append(c.state).append('$').append(c.money).append(' ');
            sb.append("] couriers=[");
            for (com.rpbusiness.entity.CourierEntity c : sl.getEntities(ModEntities.COURIER.get(), c -> true))
                sb.append(c.blockPosition().toShortString()).append(c.isStuck() ? "(stuck)" : "").append(' ');
            sb.append("] supply=[");
            for (SupplyOrder so : b.supplyOrders) sb.append(so.id).append(':').append(so.status).append(' ');
            sb.append("] dirtyTables=");
            int dirty = 0;
            for (BlockPos p : b.equipment.get(Equip.TABLE)) if (sl.getBlockState(p).getBlock() instanceof TableBlock && sl.getBlockState(p).getValue(TableBlock.DIRTY)) dirty++;
            sb.append(dirty);
            log(sb.toString());
        }
        long limit = Long.getLong("rpbusiness.selftest.ticks", 7200);
        if (t == limit) {
            log("RESULT served=" + b.customersServed + " lost=" + b.customersLost + " cash=" + b.cash + " income=" + b.finance.totalIncome
                    + " history=" + b.history.size() + " supply=" + b.supplyOrders.stream().map(x -> x.status.name()).toList());
            for (CompoundLike h : CompoundLike.of(b)) log("  history " + h.s);
            sl.getServer().halt(false);
        }
    }

    private record CompoundLike(String s) {
        static List<CompoundLike> of(Business b) {
            return b.history.stream().limit(20).map(t -> new CompoundLike(t.getString("cname") + " " + t.getString("dish") + " "
                    + t.getString("status") + " q=" + t.getInt("quality") + " $" + t.getInt("price"))).toList();
        }
    }

    @SuppressWarnings("unused")
    private static boolean isChair(BlockState s) { return s.getBlock() instanceof ChairBlock; }
}
