package com.eggsteal.brainrot;

import com.eggsteal.BatItem;
import com.eggsteal.CoilItem;
import com.eggsteal.EggSteal;
import com.eggsteal.Economy;
import com.eggsteal.RandomEvents;
import java.util.ArrayList;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game events of the Brainrot Heist mode. Every handler does nothing in Egg Steal worlds. */
@EventBusSubscriber(modid = EggSteal.MODID)
public class BrEvents {
    static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath(EggSteal.MODID, "brainrot_speed");

    static boolean on(MinecraftServer s) { return BrMode.on(s); }

    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        BrMode.reset();
        BrMap.Lock.resetAll();
        BrRoad.reset();
        BrBotLife.reset();
        BrHud.reset();
        if (on(e.getServer()) && BrData.get(e.getServer()).built) BrMap.rules(e.getServer());
    }

    /** Stolen brainrots on somebody's head are not saved with the world: put them back home before it stops. */
    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        if (!on(e.getServer())) return;
        for (BrainrotEntity b : new ArrayList<>(BrainrotEntity.all(e.getServer().overworld())))
            if (b.state() == BrainrotEntity.CARRIED) b.returnHome();
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !on(p.server)) return;
        MinecraftServer s = p.server;
        ServerLevel sl = s.overworld();
        BrData data = BrData.get(s);
        if (!data.built || data.layout < BrMap.LAYOUT) BrMap.build(sl);
        BrData.Stats st = data.stats(p.getUUID());
        st.name = p.getGameProfile().getName();
        st.bot = false;
        if (st.base < 0 && data.claim(p.getUUID()) < 0) {
            BrBotLife.kickOne(s, sl, data);
            data.claim(p.getUUID());
        }
        if (st.base >= 0) BrMap.buildBase(sl, st.base, st); // in my skin
        boolean first = !st.kit;
        if (first) {
            st.kit = true;
            st.money = 100;
            p.getInventory().add(new ItemStack(EggSteal.BAT.get()));
            p.getInventory().add(new ItemStack(EggSteal.BR_GUIDE_BOOK.get()));
        }
        data.setDirty();
        if (st.base >= 0 && (first || !BrMap.inMap(p.getX(), p.getZ()) || p.getY() < 55)) {
            Vec3 h = BrMap.home(st.base);
            p.teleportTo(sl, h.x, h.y + 0.5, h.z, BrMap.dir(st.base) > 0 ? 0f : 180f, 0f);
        }
        p.sendSystemMessage(Component.literal("§e§lWelcome to Brainrot Heist! §7Right-click a brainrot on the red carpet to buy it, "
                + "collect money on the gold pads, steal from other bases and lock yours. §bRight-click the guide book (or /brainrot guide) to learn everything."));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !on(p.server)) return;
        for (Entity x : new ArrayList<>(p.getPassengers())) if (x instanceof BrainrotEntity b) b.returnHome();
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !on(p.server)) return;
        BrData.Stats st = BrData.get(p.server).stats(p.getUUID());
        if (st.base >= 0) {
            Vec3 h = BrMap.home(st.base);
            p.teleportTo(p.server.overworld(), h.x, h.y + 0.5, h.z, 0f, 0f);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        MinecraftServer s = e.getServer();
        if (!on(s)) return;
        BrData data = BrData.get(s);
        if (!data.built) return;
        ServerLevel sl = s.overworld();
        if (data.layout < BrMap.LAYOUT) BrMap.build(sl); // a world from an older version: move the bases
        int t = s.getTickCount();
        BrMap.Lock.tick(sl, data);
        BrRoad.tick(s, data);
        RandomEvents.tick(s);
        if (t % 5 == 0) collect(s, sl, data);
        if (t % 10 == 0) {
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                if (p.getMainHandItem().getItem() instanceof CoilItem ci)
                    p.addEffect(new MobEffectInstance(ci.jump ? MobEffects.JUMP : MobEffects.MOVEMENT_SPEED, 30, ci.jump ? 3 : 2, false, false, false));
            }
        }
        if (t % 20 == 0) {
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                BrData.Stats st = data.map.get(p.getUUID());
                AttributeInstance a = p.getAttribute(Attributes.MOVEMENT_SPEED);
                if (st != null && a != null) a.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, 0.06 * st.speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            BrHud.actionbar(s, data);
            BrBotLife.tick(s, data);
            com.eggsteal.Hud.setText(s, "es_event", RandomEvents.status(s));
        }
        if (t % 100 == 0) BrHud.boards(s, data);
    }

    /** Standing on the gold pad in front of a podium collects that brainrot's money. */
    static void collect(MinecraftServer s, ServerLevel sl, BrData data) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            BrData.Stats st = data.map.get(p.getUUID());
            if (st == null || st.base < 0 || BrMap.baseAt(p.getX(), p.getZ()) != st.base) continue;
            for (BrainrotEntity b : BrainrotEntity.all(sl)) {
                if (b.state() != BrainrotEntity.PODIUM || !p.getUUID().equals(b.owner) || b.slot < 0 || b.pending < 1) continue;
                Vec3 pad = BrMap.pad(st.base, b.slot);
                if (Math.abs(p.getX() - pad.x) < 1.0 && Math.abs(p.getZ() - pad.z) < 1.0 && Math.abs(p.getY() - pad.y) < 1.5) {
                    st.money += b.pending;
                    p.displayClientMessage(Component.literal("§a+$" + Economy.fmt(b.pending) + " §7from " + b.tagName()), true);
                    p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.3f);
                    b.pending = 0;
                    b.refreshName();
                    data.setDirty();
                }
            }
        }
    }

    @SubscribeEvent
    public static void clickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p) || !on(p.server)) return;
        BrData.Stats st = BrData.get(p.server).stats(p.getUUID());
        BlockPos bp = e.getPos();
        if (st.base >= 0 && bp.equals(BrMap.lockButton(st.base))) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            if (BrMap.Lock.press((ServerLevel) e.getLevel(), st.base)) p.displayClientMessage(Component.literal("§c§lBASE LOCKED §7for 60 seconds - nobody else can enter!"), true);
            else p.displayClientMessage(Component.literal("§7Lock: " + BrMap.Lock.seconds(p.server, st.base) + "s"), true);
        }
    }

    @SubscribeEvent
    public static void hurt(LivingIncomingDamageEvent e) {
        LivingEntity target = e.getEntity();
        if (target.level().isClientSide || !on(target.getServer())) return;
        if (!(e.getSource().getEntity() instanceof LivingEntity attacker) || attacker == target) return;
        ServerLevel sl = (ServerLevel) target.level();
        // any hit stops a theft in progress, and makes a carried brainrot go back home
        for (BrainrotEntity b : BrainrotEntity.all(sl)) if (target.getUUID().equals(b.stealer)) b.cancelSteal();
        for (Entity x : new ArrayList<>(target.getPassengers())) {
            if (x instanceof BrainrotEntity b) {
                String name = b.tagName();
                b.returnHome();
                String who = b.nameOf(attacker);
                sl.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§a" + who + " §fhit the thief: " + name + " §fwent back home!"), false);
            }
        }
        // a bat (or a bot) freezes players and bots for 3 seconds, rich bots hit harder
        boolean bat = attacker.getMainHandItem().getItem() instanceof BatItem || attacker instanceof BrBot;
        if (bat && (target instanceof Player || target instanceof BrBot)) {
            int g = attacker instanceof BrBot ? BrData.guard(BrData.get(sl.getServer()).map.get(attacker.getUUID())) : 0;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60 + 20 * g, 7, false, true, true));
        }
        if (target instanceof BrBot bot && bot.getTarget() == null) bot.aggro(attacker); // bots hit back
    }

    @SubscribeEvent
    public static void fall(LivingFallEvent e) {
        if (!e.getEntity().level().isClientSide && on(e.getEntity().getServer())) e.setDamageMultiplier(0f);
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p && on(p.server) && !p.isCreative()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void placeBlock(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && on(p.server) && !p.isCreative()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("brainrot")
                .then(Commands.literal("guide").executes(c -> { BrShop.open(c.getSource().getPlayerOrException(), true, -1); return 1; }))
                .then(Commands.literal("shop").executes(c -> { BrShop.open(c.getSource().getPlayerOrException(), false, -1); return 1; }))
                .then(Commands.literal("home").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    BrData.Stats st = BrData.get(p.server).stats(p.getUUID());
                    if (st.base >= 0 && p.getPassengers().isEmpty()) { Vec3 h = BrMap.home(st.base); p.teleportTo(p.server.overworld(), h.x, h.y + 0.5, h.z, 0f, 0f); }
                    return 1;
                }))
                .then(Commands.literal("top").executes(c -> { BrHud.boards(c.getSource().getServer(), BrData.get(c.getSource().getServer())); return 1; }))
                .then(Commands.literal("map").requires(src -> src.hasPermission(2)).executes(c -> { BrMap.build(c.getSource().getServer().overworld()); return 1; }))
                .then(Commands.literal("rare").requires(src -> src.hasPermission(2)).executes(c -> { BrRoad.spawnSpecial(c.getSource().getServer(), true); return 1; })));
    }
}
