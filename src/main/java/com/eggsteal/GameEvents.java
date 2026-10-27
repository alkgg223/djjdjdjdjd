package com.eggsteal;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = EggSteal.MODID)
public class GameEvents {

    static boolean inArena(double x, double z) { return Math.abs(x) <= 80 && Math.abs(z) <= 60; }

    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        if (Economy.get(e.getServer()).arenaBuilt) Arena.rules(e.getServer());
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        MinecraftServer s = p.server;
        ServerLevel sl = s.overworld();
        Economy eco = Economy.get(s);
        if (!eco.arenaBuilt) Arena.build(sl);
        Economy.Stats st = eco.stats(p.getUUID());
        st.name = p.getGameProfile().getName();
        st.bot = false;
        if (st.base < 0 && eco.claim(p.getUUID()) < 0) {
            kickBot(sl, eco);
            eco.claim(p.getUUID());
        }
        boolean first = !st.kit;
        if (first) {
            st.kit = true;
            st.money = 150;
            p.getInventory().add(new ItemStack(EggSteal.BAT.get()));
        }
        eco.setDirty();
        if (st.base >= 0 && (first || !inArena(p.getX(), p.getZ()) || p.getY() < 55)) {
            Vec3 h = Arena.home(st.base);
            p.teleportTo(sl, h.x, h.y + 1, h.z, 0f, 0f);
        }
        p.sendSystemMessage(Component.literal("§6Welcome to Egg Steal! §7Buy eggs by right-clicking your conveyor belt next to an egg, right-click an egg on your base to hatch it, steal from others, and upgrade at the Shop in the center (or /eggsteal shop). Press the red button in your base for a 30s laser door!"));
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Economy.Stats st = Economy.get(p.server).stats(p.getUUID());
        if (st.base >= 0) {
            Vec3 h = Arena.home(st.base);
            p.teleportTo(p.server.overworld(), h.x, h.y + 1, h.z, 0f, 0f);
        }
    }

    static void kickBot(ServerLevel sl, Economy eco) {
        for (Map.Entry<UUID, Economy.Stats> en : new ArrayList<>(eco.map.entrySet())) {
            if (!en.getValue().bot || en.getValue().base < 0) continue;
            UUID id = en.getKey();
            Entity b = sl.getEntity(id);
            if (b != null) b.discard();
            for (CreatureEntity c : sl.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(CreatureEntity.class), x -> id.equals(x.owner()))) c.discard();
            Laser.reset(sl, en.getValue().base);
            Arena.setFloor2(sl, en.getValue().base, false);
            Arena.paintBase(sl, en.getValue().base, 0);
            BotLife.leaveAt.remove(id);
            eco.release(id);
            return;
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        MinecraftServer s = e.getServer();
        Economy eco = Economy.get(s);
        if (!eco.arenaBuilt) return;
        int t = s.getTickCount();
        ServerLevel sl = s.overworld();
        Belt.tick(sl, eco);
        Laser.tick(sl);
        if (t % 10 == 0) {
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                if (p.getMainHandItem().getItem() instanceof CoilItem ci)
                    p.addEffect(new MobEffectInstance(ci.jump ? MobEffects.JUMP : MobEffects.MOVEMENT_SPEED, 30, ci.jump ? 3 : 2, false, false, false));
            }
        }
        if (t % 20 == 0) {
            Hud.actionbar(s);
            BotLife.tick(s, eco);
            Auction.tick(s);
        }
        if (t % 100 == 0) Hud.leaderboard(s);
    }

    @SubscribeEvent
    public static void fall(LivingFallEvent e) {
        if (inArena(e.getEntity().getX(), e.getEntity().getZ())) e.setDamageMultiplier(0f);
    }

    public static void spawnBot(ServerLevel sl, UUID id, Economy.Stats st) {
        BotEntity b = EggSteal.BOT.get().create(sl);
        if (b == null) return;
        b.setUUID(id);
        Vec3 h = Arena.home(st.base);
        b.moveTo(h.x, h.y, h.z, 0f, 0f);
        sl.addFreshEntity(b);
    }

    /** Right-click your own conveyor belt (the block next to an egg) to buy that egg. */
    @SubscribeEvent
    public static void beltClick(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p)) return;
        Economy eco = Economy.get(p.server);
        Economy.Stats st = eco.stats(p.getUUID());
        if (st.base < 0) return;
        BlockPos bp = e.getPos();
        if (bp.equals(Arena.buttonPos(st.base))) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            if (Laser.press((ServerLevel) e.getLevel(), st.base)) p.displayClientMessage(Component.literal("§c§lLASER DOOR ON §7for 30 seconds - nobody can enter!"), true);
            else p.displayClientMessage(Component.literal("§7Laser is recharging: " + Laser.coolSeconds(p.server, st.base) + "s"), true);
            return;
        }
        if (bp.getY() != 63 || Math.abs(bp.getX() - Arena.cx(st.base)) > 10 || Math.abs(bp.getZ() - Arena.beltZ(st.base)) > 1) return;
        ServerLevel sl = (ServerLevel) e.getLevel();
        ItemEntity best = null;
        double bd = 3.0;
        for (ItemEntity ie : sl.getEntitiesOfClass(ItemEntity.class, Belt.box(st.base), it -> it.getPersistentData().contains("es_belt"))) {
            double d = Math.abs(ie.getX() - (bp.getX() + 0.5));
            if (d < bd) { bd = d; best = ie; }
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (best == null) { p.displayClientMessage(Component.literal("§7No egg here - click the belt right next to an egg."), true); return; }
        Creature c = Creature.VALUES[best.getPersistentData().getInt("es_belt")];
        if (st.money < c.price) {
            p.displayClientMessage(Component.literal("§cNot enough money! Need $" + Economy.fmt(c.price)), true);
            return;
        }
        st.money -= c.price;
        eco.setDirty();
        best.discard();
        ItemStack egg = Steal.makeEgg(c.ordinal(), null);
        if (!p.getInventory().add(egg)) p.drop(egg, false);
        p.displayClientMessage(Component.literal("§aBought " + c.tag() + " §aegg! Right-click it on your base to hatch."), true);
    }

    @SubscribeEvent
    public static void hurt(LivingIncomingDamageEvent e) {
        if (e.getEntity().level().isClientSide) return;
        if (e.getSource().getEntity() instanceof LivingEntity attacker) {
            LivingEntity target = e.getEntity();
            Steal.onHit(target, attacker);
            // a hit from a bat (or from any bot) freezes players and bots for 3 seconds
            boolean bat = attacker.getMainHandItem().getItem() instanceof BatItem || attacker instanceof BotEntity;
            if (bat && (target instanceof Player || target instanceof BotEntity))
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 7, false, true, true));
        }
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent e) {
        if (!e.getPlayer().isCreative() && inArena(e.getPos().getX(), e.getPos().getZ())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void placeBlock(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof Player p && !p.isCreative() && inArena(e.getPos().getX(), e.getPos().getZ())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("eggsteal")
                .then(Commands.literal("shop").executes(c -> { Shop.open(c.getSource().getPlayerOrException(), false); return 1; }))
                .then(Commands.literal("home").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Economy.Stats st = Economy.get(p.server).stats(p.getUUID());
                    if (st.base >= 0) { Vec3 h = Arena.home(st.base); p.teleportTo(p.server.overworld(), h.x, h.y + 1, h.z, 0f, 0f); }
                    return 1;
                }))
                .then(Commands.literal("top").executes(c -> { Hud.leaderboard(c.getSource().getServer()); return 1; }))
                .then(Commands.literal("arena").requires(s -> s.hasPermission(2)).executes(c -> {
                    Arena.build(c.getSource().getServer().overworld());
                    return 1;
                })));
    }
}
