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
            var bank = com.rpbusiness.bank.Bank.get(sl);
            log("VERIFY bank accounts=" + bank.accounts.size() + " loans=" + bank.loans.stream().map(l -> l.id + ":" + l.remaining + "/" + l.status).toList()
                    + " credit=" + bank.creditScore(OWNER) + " balances=" + bank.accounts.values().stream().map(a -> a.id + "=" + a.balance).toList());
            for (Business x : data.businesses.values())
                log("VERIFY " + x.name + " type=" + x.type + " served=" + x.customersServed + " cash=" + x.cash + " staff=" + x.employees.size()
                        + " jobs=" + x.deliveries.stream().map(j -> j.id + "/" + j.status).toList() + " prices=" + x.prices.size());
            origin = existing.min;
            for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 2; dz++)
                sl.setChunkForced((origin.getX() >> 4) + dx, (origin.getZ() >> 4) + dz, true);
            startTick = sl.getGameTime();
            return;
        }
        if (existing != null) { log("already built"); return; }
        BlockPos spawn = sl.getSharedSpawnPos();
        int ox = spawn.getX() + 30, oz = spawn.getZ() + 30;
        for (int dx = -2; dx <= 3; dx++) for (int dz = -1; dz <= 4; dz++) sl.setChunkForced((ox >> 4) + dx, (oz >> 4) + dz, true);
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
        buildStage2(sl, data, b);
        data.setDirty();
        startTick = sl.getGameTime();
        log("built at " + origin);
    }

    private static Business shop, office, kitRestaurant, kitHotel;

    private static void room(ServerLevel sl, BlockPos o, int size) {
        for (int x = 0; x <= size; x++) for (int z = 0; z <= size; z++) {
            for (int y = 0; y <= 5; y++) set(sl, o.offset(x, y, z), Blocks.AIR.defaultBlockState());
            set(sl, o.offset(x, -1, z), ModBlocks.KITCHEN_TILES.get().defaultBlockState());
            set(sl, o.offset(x, 4, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
            if (x == 0 || z == 0 || x == size || z == size) for (int y = 0; y < 4; y++) set(sl, o.offset(x, y, z), Blocks.STONE_BRICKS.defaultBlockState());
        }
        for (int x = size / 2 - 1; x <= size / 2 + 1; x++) for (int z = size + 1; z <= size + 4; z++) {
            set(sl, o.offset(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState());
            for (int y = 0; y < 3; y++) set(sl, o.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        BlockPos door = o.offset(size / 2, 0, size);
        set(sl, door, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(sl, door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static Business register(ServerLevel sl, BusinessData data, String name, BusinessType type, BlockPos min, BlockPos max) {
        Business b = new Business();
        b.id = UUID.randomUUID();
        b.name = name;
        b.type = type;
        b.owner = OWNER;
        b.ownerName = "Tester";
        b.min = min;
        b.max = max;
        b.openHour = 0;
        b.closeHour = 0;
        b.cash = 500;
        data.businesses.put(b.id, b);
        b.entrance = BusinessManager.findEntrance(sl, b);
        BusinessManager.scan(sl, b);
        com.rpbusiness.bank.Bank.get(sl).businessAccount(sl, b);
        return b;
    }

    private static void buildStage2(ServerLevel sl, BusinessData data, Business diner) {
        // ---- grocery store (east of the diner)
        BlockPos so = origin.offset(16, 0, 0);
        room(sl, so, 10);
        Direction s = Direction.SOUTH;
        Item[][] products = {{net.minecraft.world.item.Items.BREAD, net.minecraft.world.item.Items.APPLE, net.minecraft.world.item.Items.COOKIE},
                {ModItems.TOMATO.get(), ModItems.CHEESE_BLOCK.get(), ModItems.BURGER_BUN.get()},
                {net.minecraft.world.item.Items.CARROT, net.minecraft.world.item.Items.PUMPKIN_PIE, net.minecraft.world.item.Items.MELON_SLICE},
                {ModItems.COLA.get(), ModItems.COFFEE.get(), net.minecraft.world.item.Items.HONEY_BOTTLE}};
        for (int i = 0; i < 4; i++) {
            BlockPos d = so.offset(2 + i * 2, 0, 1);
            set(sl, d, facing(i % 2 == 0 ? ModBlocks.DISPLAY_SHELF.get() : ModBlocks.FRIDGE_DISPLAY.get(), s));
            if (sl.getBlockEntity(d) instanceof com.rpbusiness.block.DisplayBE de)
                for (int k = 0; k < 9; k++) de.setItem(k, new ItemStack(products[i][k % 3], 6));
        }
        set(sl, so.offset(1, 0, 4), facing(ModBlocks.PANTRY_SHELF.get(), Direction.EAST));
        if (sl.getBlockEntity(so.offset(1, 0, 4)) instanceof StorageBE st) {
            for (Item[] row : products) for (Item it : row) st.insert(new ItemStack(it, 16));
            st.insert(new ItemStack(ModItems.POTATO.get(), 32)); // warehouse stock for the transfer test
        }
        set(sl, so.offset(8, 0, 6), facing(ModBlocks.KITCHEN_COUNTER.get(), s));
        set(sl, so.offset(8, 1, 6), facing(ModBlocks.CASH_REGISTER.get(), s));
        set(sl, so.offset(3, 0, 7), facing(ModBlocks.PAYMENT_TERMINAL.get(), s));
        set(sl, so.offset(9, 0, 1), facing(ModBlocks.DELIVERY_CRATE.get(), s));
        shop = register(sl, data, "SelfTest Grocery", BusinessType.GROCERY, so.offset(0, -1, 0), so.offset(10, 4, 10));
        BlockPos in = so.offset(5, 0, 5);
        BusinessManager.spawnEmployee(sl, shop, new Candidate("Rana Cash", 3, 35, Job.CASHIER, 1), Job.CASHIER, in, 0);
        BusinessManager.spawnEmployee(sl, shop, new Candidate("Sami Stock", 3, 36, Job.STOCK_MANAGER, 2), Job.STOCK_MANAGER, in, 0);
        BusinessManager.spawnEmployee(sl, shop, new Candidate("Dana Help", 2, 32, Job.SALES_ASSISTANT, 3), Job.SALES_ASSISTANT, in, 0);
        // ---- delivery office (west of the diner)
        BlockPos oo = origin.offset(-12, 0, 0);
        room(sl, oo, 8);
        set(sl, oo.offset(2, 0, 1), facing(ModBlocks.DELIVERY_CRATE.get(), s));
        set(sl, oo.offset(4, 0, 1), facing(ModBlocks.DELIVERY_TERMINAL.get(), s));
        set(sl, oo.offset(6, 0, 1), facing(ModBlocks.PANTRY_SHELF.get(), s));
        // a road connecting the three businesses (players build their own roads)
        for (int x = -14; x <= 30; x++) for (int z = 13; z <= 15; z++) {
            set(sl, origin.offset(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState());
            for (int y = 0; y < 4; y++) set(sl, origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        office = register(sl, data, "SelfTest Delivery", BusinessType.DELIVERY, oo.offset(0, -1, 0), oo.offset(8, 4, 8));
        BusinessManager.spawnEmployee(sl, office, new Candidate("Driver Zaid", 3, 45, Job.DELIVERY_DRIVER, 0), Job.DELIVERY_DRIVER, oo.offset(4, 0, 4), 0);
        var tr = com.rpbusiness.delivery.Deliveries.createTransfer(sl, office, shop, diner, ModItems.POTATO.get(), 8);
        log("transfer job " + (tr == null ? "FAILED" : tr.id + " fee " + tr.reward));
        com.rpbusiness.delivery.Deliveries.generateOffers(sl, office);

        // ---- ready-made kits (placed like a player would: clicking the ground, looking north)
        BlockPos kc = sl.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.offset(6, 0, 44)).below();
        kitRestaurant = Kits.place(sl, OWNER, "Tester", "restaurant", kc, Direction.NORTH, m -> log("kit: " + m.getString()));
        BlockPos hc = sl.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.offset(32, 0, 44)).below();
        kitHotel = Kits.place(sl, OWNER, "Tester", "hotel", hc, Direction.NORTH, m -> log("kit: " + m.getString()));
        if (kitRestaurant != null) {
            kitRestaurant.openHour = 0; kitRestaurant.closeHour = 0; kitRestaurant.cash = 300;
            BlockPos kin = kitRestaurant.entrance;
            BusinessManager.spawnEmployee(sl, kitRestaurant, new Candidate("Kit Chef", 3, 60, Job.CHEF, 0), Job.CHEF, kin, 0);
            BusinessManager.spawnEmployee(sl, kitRestaurant, new Candidate("Kit Waiter", 3, 35, Job.WAITER, 1), Job.WAITER, kin, 0);
            BusinessManager.spawnEmployee(sl, kitRestaurant, new Candidate("Kit Cashier", 3, 35, Job.CASHIER, 2), Job.CASHIER, kin, 0);
            BusinessManager.spawnEmployee(sl, kitRestaurant, new Candidate("Kit Manager", 3, 80, Job.MANAGER, 3), Job.MANAGER, kin, 0);
            log("KIT restaurant " + kitRestaurant.min + " -> " + kitRestaurant.max + " entrance " + kitRestaurant.entrance + " equip " + kitRestaurant.equipment.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue().size()).toList());
        }
        if (kitHotel != null) {
            kitHotel.openHour = 0; kitHotel.closeHour = 0;
            BusinessManager.spawnEmployee(sl, kitHotel, new Candidate("Kit Reception", 3, 35, Job.CASHIER, 2), Job.CASHIER, kitHotel.entrance, 0);
            BusinessManager.spawnEmployee(sl, kitHotel, new Candidate("Kit Maid", 3, 25, Job.CLEANER, 1), Job.CLEANER, kitHotel.entrance, 0);
            log("KIT hotel beds=" + kitHotel.equipment.get(Equip.BED).size() + " entrance " + kitHotel.entrance);
            BlockPos e = kitHotel.entrance;
            StringBuilder sb = new StringBuilder("ENTRANCE column:");
            for (int dz = -3; dz <= 8; dz++) {
                BlockPos q = e.offset(0, 0, dz);
                int hgt = sl.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, q).getY();
                sb.append(" z").append(dz).append(":h").append(hgt).append('/')
                        .append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(sl.getBlockState(q.below()).getBlock()).getPath()).append('|')
                        .append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(sl.getBlockState(q).getBlock()).getPath());
            }
            log(sb.toString());
        }
        // the diner's manager buys supplies when stock gets low
        BusinessManager.spawnEmployee(sl, diner, new Candidate("Diner Manager", 3, 80, Job.MANAGER, 1), Job.MANAGER, origin.offset(6, 0, 6), 0);
        // ---- bank
        var bank = com.rpbusiness.bank.Bank.get(sl);
        var acc = bank.open(sl, OWNER, "Tester", com.rpbusiness.bank.Account.Type.CHECKING, "Checking", null);
        bank.credit(sl, acc, 1000, "Test deposit");
        boolean t1 = bank.transfer(sl, acc, bank.businessAccount(sl, diner), 250, "Capital");
        boolean t2 = bank.transfer(sl, acc, bank.businessAccount(sl, diner), 5000, "Too much");
        var loan = bank.takeLoan(sl, OWNER, acc.id, 2000, 0);
        log("BANK acc=" + acc.id + " bal=" + acc.balance + " transferOk=" + t1 + " overdraftBlocked=" + !t2 + " diner=" + bank.businessAccount(sl, diner).balance
                + " loan=" + (loan == null ? "denied" : loan.id + " total " + loan.totalDue + " inst " + loan.installment + " rate " + loan.interestPercent)
                + " max=" + bank.maxLoan(OWNER) + " credit=" + bank.creditScore(OWNER));
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
            Business[] targets = {kitRestaurant, kitHotel, shop, kitHotel};
            Business tgt = targets[spawned % targets.length];
            if (spawned % 2 == 1 && tgt != null) {
                CustomerEntity c = ModEntities.CUSTOMER.get().create(sl);
                BlockPos at = c == null ? null : BusinessManager.reachableSpawn(sl, c, tgt.entranceOrCenter(), 6, 14);
                if (at != null) {
                    c.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                    c.randomize(sl.getRandom());
                    c.money = 200; c.budget = 120; c.home = at;
                    c.businessId = tgt.id;
                    c.state = CustomerEntity.State.GO_ENTRANCE;
                    sl.addFreshEntity(c);
                    log("spawned customer for " + tgt.name);
                }
                spawned++;
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
            if (shop != null) {
                StringBuilder s2 = new StringBuilder("SHOP served=" + shop.customersServed + " lost=" + shop.customersLost + " cash=" + shop.cash
                        + " bank=" + com.rpbusiness.bank.Bank.get(sl).businessAccount(sl, shop).balance + " staff=[");
                for (EmployeeRecord r : shop.employees.values()) s2.append(r.job).append('=').append(r.status).append(' ');
                s2.append("] shoppers=").append(BusinessManager.shoppers(sl, shop));
                s2.append(" OFFICE jobs=[");
                for (var j : office.deliveries) s2.append(j.id).append(':').append(j.kind).append('/').append(j.status).append(' ');
                s2.append("] bank=").append(com.rpbusiness.bank.Bank.get(sl).businessAccount(sl, office).balance);
                if (kitRestaurant != null) s2.append(" KITREST served=").append(kitRestaurant.customersServed).append(" lost=").append(kitRestaurant.customersLost)
                        .append(" orders=").append(kitRestaurant.orders.size()).append(" supply=").append(kitRestaurant.supplyOrders.size());
                if (kitHotel != null) s2.append(" HOTEL served=").append(kitHotel.customersServed).append(" free=").append(BusinessManager.freeBeds(sl, kitHotel));
                s2.append(" DINER supply=").append(b.supplyOrders.stream().map(x -> x.supplier + ":" + x.status).toList());
                for (EmployeeRecord r : office.employees.values()) {
                    EmployeeEntity e = BusinessManager.employee(sl, r.id);
                    s2.append(" driver=").append(r.status).append(e == null ? "(missing)" : "@" + e.blockPosition().toShortString());
                }
                log(s2.toString());
            }
        }
        if (MODE.equals("build") && t == 200 && office != null) {
            for (var j : office.deliveries) if (j.status == com.rpbusiness.delivery.DeliveryJob.Status.OFFERED) {
                j.dest = origin.offset(26, 0, 14); // a house at the end of the road
                boolean ok = com.rpbusiness.delivery.Deliveries.accept(sl, null, office, j.id);
                log("accepted request " + j.id + " -> " + ok + " reward " + j.reward);
                break;
            }
        }
        if (MODE.equals("build") && t == 3000) {
            // advance one in-game day: salaries and the loan installment must be processed
            var bank = com.rpbusiness.bank.Bank.get(sl);
            long before = bank.accountsOf(OWNER).stream().filter(a -> a.type == com.rpbusiness.bank.Account.Type.CHECKING).mapToLong(a -> a.balance).sum();
            sl.setDayTime(sl.getDayTime() + 24000);
            bank.tickDay(sl);
            for (Business x : BusinessData.get(sl).businesses.values()) BusinessManager.tickBusiness(sl, x);
            long after = bank.accountsOf(OWNER).stream().filter(a -> a.type == com.rpbusiness.bank.Account.Type.CHECKING).mapToLong(a -> a.balance).sum();
            String loans = bank.loans.stream().map(l -> l.id + ":" + l.remaining + "/" + l.status + " paid=" + l.paidInstallments).toList().toString();
            log("DAY ADVANCE checking " + before + " -> " + after + " loans " + loans + " credit " + bank.creditScore(OWNER));
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
