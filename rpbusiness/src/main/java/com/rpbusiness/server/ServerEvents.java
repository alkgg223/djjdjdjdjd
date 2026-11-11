package com.rpbusiness.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.rpbusiness.business.Business;
import com.rpbusiness.business.BusinessData;
import com.rpbusiness.core.Config;
import com.rpbusiness.core.ModEntities;
import com.rpbusiness.core.ModItems;
import com.rpbusiness.economy.Money;
import com.rpbusiness.entity.CustomerEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ServerEvents {
    private ServerEvents() {}

    private static final Map<UUID, Long> NEXT_SPAWN = new HashMap<>();

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        ServerLevel ow = server.overworld();
        long t = ow.getGameTime();
        if (t % 20 != 0) return;
        BusinessData data = BusinessData.get(server);
        if (data.businesses.isEmpty()) return;
        for (Business b : data.businesses.values()) {
            try {
                BusinessManager.tickBusiness(ow, b);
            } catch (Exception ex) {
                BusinessManager.log("Business tick failed for " + b.name + ": " + ex);
            }
        }
        data.setDirty();
        if (t % 40 == 0) spawnCustomers(ow, data, t);
        showMarkers(ow, data);
        SelfTest.tick(ow);
    }

    private static void spawnCustomers(ServerLevel ow, BusinessData data, long t) {
        if (!Config.CUSTOMERS_ENABLED.get()) return;
        int alive = ow.getEntities(ModEntities.CUSTOMER.get(), c -> true).size();
        int max = Config.MAX_CUSTOMERS.get();
        if (alive >= max) return;
        int range = Config.ACTIVE_RANGE.get();
        int hour = Business.hour(ow);
        // lunch and dinner rush hours
        double rush = (hour >= 12 && hour < 14) || (hour >= 18 && hour < 21) ? 1.8 : (hour >= 22 || hour < 7) ? 0.35 : 1.0;
        for (Business b : data.businesses.values()) {
            if (!b.type.foodService || !b.isOpen(ow) || b.enabledDishes().isEmpty()) continue;
            BlockPos c = b.entranceOrCenter();
            Player p = ow.getNearestPlayer(c.getX(), c.getY(), c.getZ(), range, false);
            if (p == null || !ow.isLoaded(c)) continue;
            long next = NEXT_SPAWN.getOrDefault(b.id, 0L);
            if (t < next) continue;
            double interval = Config.SPAWN_INTERVAL_SECONDS.get() * 20 / rush / (0.6 + b.reputation / 500.0);
            NEXT_SPAWN.put(b.id, t + (long) (interval * (0.7 + ow.getRandom().nextDouble() * 0.6)));
            if (BusinessManager.freeSeats(ow, b) == 0 || BusinessManager.availableDishes(ow, b).isEmpty()) continue;
            BusinessManager.spawnCustomerFor(ow, b);
            if (++alive >= max) return;
        }
    }

    /** While the owner holds the Layout Tool / Staff Planner, show what each placed block is designated as. */
    private static void showMarkers(ServerLevel ow, BusinessData data) {
        for (ServerPlayer p : ow.players()) {
            ItemStack held = p.getMainHandItem();
            boolean layout = held.is(ModItems.LAYOUT_TOOL.get());
            boolean staff = held.is(ModItems.STAFF_PLANNER.get());
            if (!layout && !staff) continue;
            Business b = data.at(p.blockPosition());
            if (staff) {
                UUID id = held.get(com.rpbusiness.core.ModComponents.STAFF_TARGET.get());
                Business eb = id == null ? null : data.employerOf(id);
                com.rpbusiness.business.EmployeeRecord r = eb == null ? null : eb.employees.get(id);
                if (r == null) continue;
                for (BlockPos s : r.stations) marker(ow, p, s, net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, 1.15);
                for (BlockPos s : r.route) marker(ow, p, s, net.minecraft.core.particles.ParticleTypes.END_ROD, 0.2);
                if (r.standPos != null) marker(ow, p, r.standPos, net.minecraft.core.particles.ParticleTypes.HEART, 0.3);
                if (r.storagePos != null) marker(ow, p, r.storagePos, net.minecraft.core.particles.ParticleTypes.WAX_ON, 1.15);
                continue;
            }
            if (b == null || !BusinessManager.isOwner(p, b)) continue;
            for (var e : b.designations.entrySet())
                if (e.getKey().distSqr(p.blockPosition()) < 24 * 24) marker(ow, p, e.getKey(), net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, 1.15);
            for (BlockPos x : b.excluded)
                if (x.distSqr(p.blockPosition()) < 24 * 24) marker(ow, p, x, net.minecraft.core.particles.ParticleTypes.SMOKE, 1.1);
        }
        // crumbs on player-designated tables that need cleaning
        if (ow.getGameTime() % 60 == 0)
            for (Business b : data.businesses.values())
                for (BlockPos d : b.dirty)
                    if (ow.isLoaded(d)) ow.sendParticles(net.minecraft.core.particles.ParticleTypes.WHITE_ASH, d.getX() + 0.5, d.getY() + 1.05, d.getZ() + 0.5, 6, 0.3, 0.02, 0.3, 0);
    }

    private static void marker(ServerLevel ow, ServerPlayer p, BlockPos pos, net.minecraft.core.particles.ParticleOptions type, double dy) {
        ow.sendParticles(p, type, true, pos.getX() + 0.5, pos.getY() + dy, pos.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0);
    }

    /** Sitting on blocks the owner designated as seats, and mopping designated tables. */
    @SubscribeEvent
    public static void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        BlockPos pos = e.getPos();
        Business b = BusinessData.get(sl).at(pos);
        if (b == null) return;
        Player p = e.getEntity();
        ItemStack held = p.getMainHandItem();
        if (held.is(ModItems.MOP.get()) && b.dirty.contains(pos)) {
            BusinessManager.cleanTable(sl, b, pos);
            p.displayClientMessage(Component.translatable("msg.rpbusiness.table_cleaned"), true);
            e.setCanceled(true);
            return;
        }
        if (held.isEmpty() && !p.isShiftKeyDown() && !p.isPassenger() && b.designations.get(pos) == com.rpbusiness.business.Equip.CHAIR
                && !(sl.getBlockState(pos).getBlock() instanceof com.rpbusiness.block.ChairBlock)) {
            if (!com.rpbusiness.entity.SeatEntity.occupied(sl, pos)) com.rpbusiness.entity.SeatEntity.sit(sl, pos, p);
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        Business b = BusinessData.get(sl).at(e.getPos());
        if (b != null && sl == sl.getServer().overworld()) BusinessManager.index(sl, b, e.getPos());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        Business b = BusinessData.get(sl).at(e.getPos());
        if (b != null) {
            BusinessManager.unindex(b, e.getPos());
            b.dirty.remove(e.getPos());
            b.designations.remove(e.getPos());
            b.excluded.remove(e.getPos());
            if (b.owner != null && !b.owner.equals(e.getPlayer().getUUID()) && !e.getPlayer().isCreative()) {
                // only the owner may tear down their own business
                e.setCanceled(true);
                e.getPlayer().displayClientMessage(Component.translatable("msg.rpbusiness.not_owner", b.ownerName).withStyle(ChatFormatting.RED), true);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        SelfTest.onLogin(p);
        CompoundTag pd = p.getPersistentData();
        CompoundTag mine = pd.getCompound("rpbusiness");
        if (!mine.getBoolean("starter")) {
            mine.putBoolean("starter", true);
            pd.put("rpbusiness", mine);
            Money.giveCash(p, Config.STARTING_CASH.get());
            p.getInventory().add(new ItemStack(ModItems.BUSINESS_PLANNER.get()));
            p.getInventory().add(new ItemStack(ModItems.RECIPE_BOOK.get()));
            p.displayClientMessage(Component.translatable("msg.rpbusiness.welcome", Money.fmt(Config.STARTING_CASH.get())).withStyle(ChatFormatting.GOLD), false);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        SelfTest.onStart(e.getServer());
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("rpbusiness")
                .then(Commands.literal("customer").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    CustomerEntity c = ModEntities.CUSTOMER.get().create(p.serverLevel());
                    if (c == null) return 0;
                    c.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
                    c.randomize(p.getRandom());
                    c.wanderTime = 20;
                    p.serverLevel().addFreshEntity(c);
                    ctx.getSource().sendSuccess(() -> Component.literal("Spawned customer " + c.npcName()), false);
                    return 1;
                }))
                .then(Commands.literal("cash").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000000)).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            int a = IntegerArgumentType.getInteger(ctx, "amount");
                            Money.giveCash(p, a);
                            ctx.getSource().sendSuccess(() -> Component.literal("Gave " + Money.fmt(a)), false);
                            return 1;
                        })))
                .then(Commands.literal("time").executes(ctx -> {
                    ServerLevel sl = ctx.getSource().getLevel();
                    ctx.getSource().sendSuccess(() -> Component.literal("Day " + Business.day(sl) + ", " + Business.hour(sl) + ":00"), false);
                    return 1;
                })));
    }
}
