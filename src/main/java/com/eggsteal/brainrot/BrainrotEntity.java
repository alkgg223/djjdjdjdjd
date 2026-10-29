package com.eggsteal.brainrot;

import com.eggsteal.Economy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A brainrot. It walks along the red carpet (ROAD) until somebody buys it; then it walks to the buyer's base (WALK),
 * where anybody with enough money can still buy it from them for double the price (the previous buyer gets their money back).
 * On a podium (PODIUM) it makes money that its owner collects on the gold pad. A thief can take it: it then rides on the
 * thief's head (CARRIED) until the thief reaches their own base, or gets hit and the brainrot goes back home.
 */
public class BrainrotEntity extends Entity {
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(BrainrotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MUT = SynchedEntityData.defineId(BrainrotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(BrainrotEntity.class, EntityDataSerializers.INT);
    public static final int ROAD = 0, WALK = 1, PODIUM = 2, CARRIED = 3;
    public static final double WALK_SPEED = 0.17, ROAD_SPEED = 0.075;

    /** PODIUM: the owner. CARRIED: who it was stolen from (it goes back there if the thief is hit). */
    public UUID owner;
    /** WALK: who bought it last, and what they paid (refunded if somebody buys it from them). */
    public UUID buyer;
    public double lastPaid, price, pending;
    public UUID stealer, thief;
    public int slot = -1;
    public boolean special;
    private int stealEnd;
    private final List<Vec3> path = new ArrayList<>();

    public BrainrotEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(VARIANT, 0);
        b.define(MUT, 0);
        b.define(STATE, ROAD);
    }

    public int variant() { return entityData.get(VARIANT); }
    public Brainrot brainrot() { return Brainrot.of(variant()); }
    public int mutation() { return entityData.get(MUT); }
    public BrMutation mut() { return BrMutation.of(mutation()); }
    public int state() { return entityData.get(STATE); }
    void setState(int s) { entityData.set(STATE, s); }
    public double value() { return BrData.value(brainrot(), mutation()); }
    public boolean beingStolen() { return stealer != null; }

    public void init(int variant, int mut) {
        entityData.set(VARIANT, variant);
        entityData.set(MUT, mut);
        price = Math.round(value());
        refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (VARIANT.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) { return EntityDimensions.scalable(0.9f, brainrot().height); }

    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isNoGravity() { return true; }
    @Override public boolean shouldBeSaved() { return state() != CARRIED && super.shouldBeSaved(); }

    // ------------------------------------------------------------------ queries

    public static List<? extends BrainrotEntity> all(ServerLevel sl) {
        return sl.getEntities(EntityTypeTest.forClass(BrainrotEntity.class), e -> true);
    }

    /** Brainrots that are, or will soon be, in this player's base (on a podium, walking there, or on their head). */
    public static int owned(ServerLevel sl, UUID id) {
        int n = 0;
        for (BrainrotEntity b : all(sl)) if (b.belongsTo(id)) n++;
        return n;
    }

    boolean belongsTo(UUID id) {
        int s = state();
        return (s == PODIUM && id.equals(owner)) || (s == WALK && id.equals(buyer)) || (s == CARRIED && id.equals(thief));
    }

    public static boolean hasRoom(ServerLevel sl, UUID id) {
        BrData.Stats st = BrData.get(sl.getServer()).map.get(id);
        return st != null && st.base >= 0 && owned(sl, id) < BrMap.capacity(st);
    }

    /** A podium slot of this base that nobody uses (or is walking to), or -1. */
    static int freeSlot(ServerLevel sl, UUID id, BrainrotEntity except) {
        BrData.Stats st = BrData.get(sl.getServer()).map.get(id);
        if (st == null || st.base < 0) return -1;
        boolean[] used = new boolean[BrMap.PODIUMS];
        for (BrainrotEntity b : all(sl)) {
            if (b == except || b.slot < 0) continue;
            if ((b.state() == PODIUM && id.equals(b.owner)) || (b.state() == WALK && id.equals(b.buyer))) used[b.slot] = true;
        }
        for (int i = 0; i < BrMap.capacity(st); i++) if (!used[i]) return i;
        return -1;
    }

    // ------------------------------------------------------------------ buying

    /** Buys the brainrot (on the carpet, or from whoever bought it last for double). Returns a reason it failed, or null. */
    public String buy(UUID who) {
        ServerLevel sl = (ServerLevel) level();
        MinecraftServer s = sl.getServer();
        BrData data = BrData.get(s);
        BrData.Stats st = data.map.get(who);
        int state = state();
        if (st == null || st.base < 0 || (state != ROAD && state != WALK)) return "§cYou can't buy this now.";
        if (who.equals(buyer)) return "§7It is already yours - it is walking to your base.";
        if (st.money < price) return "§cYou need $" + Economy.fmt(price) + " to buy this brainrot!";
        if (!hasRoom(sl, who)) return "§cYour base is full! Sneak + right-click one of your brainrots to sell it.";
        st.money -= price;
        if (buyer != null) {
            BrData.Stats prev = data.map.get(buyer);
            if (prev != null) prev.money += lastPaid;
            ServerPlayer pp = s.getPlayerList().getPlayer(buyer);
            if (pp != null) pp.sendSystemMessage(Component.literal("§c" + st.name + " bought your " + tagName() + " §cfor $" + Economy.fmt(price) + "! §7You got your $" + Economy.fmt(lastPaid) + " back."));
        }
        boolean rebuy = buyer != null;
        lastPaid = price;
        buyer = who;
        price = price * 2;
        slot = freeSlot(sl, who, this);
        setState(WALK);
        planPath();
        data.setDirty();
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 1f, 1.2f);
        if (rebuy || brainrot().rarity >= 3 || mutation() > 0 || special)
            s.getPlayerList().broadcastSystemMessage(Component.literal("§e" + st.name + " §fbought " + tagName() + " §ffor §6$" + Economy.fmt(lastPaid)
                    + "§f! Buy it from them for §c$" + Economy.fmt(price)), false);
        refreshName();
        return null;
    }

    /** Waypoints from here to the buyer's podium: out of any base through its door, along the carpet, in through the buyer's door. */
    void planPath() {
        path.clear();
        if (buyer == null || !(level() instanceof ServerLevel sl)) return;
        BrData.Stats st = BrData.get(sl.getServer()).map.get(buyer);
        if (st == null || st.base < 0) return;
        if (slot < 0) slot = freeSlot(sl, buyer, this);
        int here = BrMap.baseAt(getX(), getZ()), to = st.base;
        if (here >= 0 && here != to) {
            path.add(new Vec3(BrMap.cx(here) + 0.5, 64, getZ()));
            path.add(BrMap.gateOutside(here));
        }
        if (here != to) {
            path.add(new Vec3(BrMap.cx(to) + 0.5, 64, 0.5));
            path.add(BrMap.gateOutside(to));
        }
        if (slot >= 0) {
            Vec3 p = BrMap.podium(to, slot);
            path.add(new Vec3(BrMap.cx(to) + 0.5, 64, p.z));
            path.add(new Vec3(p.x, 64, p.z));
            path.add(p);
        } else path.add(BrMap.home(to));
    }

    private boolean stepTo(Vec3 t, double speed) {
        Vec3 d = t.subtract(position());
        double len = d.length();
        if (len > 0.01) face(d.x, d.z);
        if (len <= speed) { setPos(t); return true; }
        setPos(position().add(d.scale(speed / len)));
        return false;
    }

    void face(double dx, double dz) {
        if (Math.abs(dx) + Math.abs(dz) < 1e-4) return;
        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90f;
        setYRot(yaw);
        setYHeadRot(yaw);
    }

    // ------------------------------------------------------------------ podium

    /** Puts the brainrot on a free podium of this player's base. Returns false (and refunds a buyer) when there is no room. */
    boolean settleFor(UUID who) {
        ServerLevel sl = (ServerLevel) level();
        BrData data = BrData.get(sl.getServer());
        BrData.Stats st = data.map.get(who);
        int idx = st == null ? -1 : (slot >= 0 && slotFree(sl, who, slot) ? slot : freeSlot(sl, who, this));
        if (st == null || st.base < 0 || idx < 0) return false;
        Vec3 p = BrMap.podium(st.base, idx);
        setPos(p);
        int col = idx / 5;
        setYRot(col == 0 || col == 2 ? -90f : 90f);
        owner = who;
        slot = idx;
        buyer = null;
        thief = null;
        stealer = null;
        pending = 0;
        path.clear();
        setState(PODIUM);
        setGlowingTag(false);
        refreshName();
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y + 0.5, p.z, 12, 0.4, 0.6, 0.4, 0);
        return true;
    }

    boolean slotFree(ServerLevel sl, UUID who, int idx) {
        for (BrainrotEntity b : all(sl))
            if (b != this && b.slot == idx && ((b.state() == PODIUM && who.equals(b.owner)) || (b.state() == WALK && who.equals(b.buyer)))) return false;
        return true;
    }

    /** Places the brainrot directly on a podium (bots joining with a ready base). */
    public static BrainrotEntity spawnOnPodium(ServerLevel sl, UUID who, int variant, int mut) {
        BrainrotEntity e = com.eggsteal.EggSteal.BRAINROT.get().create(sl);
        if (e == null) return null;
        e.init(variant, mut);
        e.setPos(BrMap.home(0));
        if (!e.settleFor(who)) return null;
        sl.addFreshEntity(e);
        return e;
    }

    /** Sold by its owner for half of what it is worth. */
    public double sell() {
        BrData data = BrData.get(level().getServer());
        BrData.Stats st = data.map.get(owner);
        double got = Math.round(value() * 0.5);
        if (st != null) st.money += got;
        data.setDirty();
        if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.6, getZ(), 15, 0.4, 0.5, 0.4, 0.02);
        discard();
        return got;
    }

    // ------------------------------------------------------------------ stealing

    /** Time a thief has to stand next to a podium brainrot: rarer brainrots and richer bots take longer. */
    int stealTicks() {
        BrData.Stats os = owner == null ? null : BrData.get(level().getServer()).map.get(owner);
        return 20 + brainrot().rarity * 12 + BrData.guard(os) * 80;
    }

    /** Starts stealing. Returns a reason it can't be stolen, or null. */
    public String beginSteal(LivingEntity t) {
        if (state() != PODIUM || owner == null) return "§cYou can't steal this now.";
        if (owner.equals(t.getUUID())) return "§7That is your own brainrot.";
        if (stealer != null) return "§cSomebody is already stealing it!";
        ServerLevel sl = (ServerLevel) level();
        if (!hasRoom(sl, t.getUUID())) return "§cYour base is full - sell a brainrot first!";
        stealer = t.getUUID();
        stealEnd = tickCount + stealTicks();
        BrData data = BrData.get(sl.getServer());
        BrData.Stats os = data.map.get(owner);
        String who = nameOf(t);
        ServerPlayer op = sl.getServer().getPlayerList().getPlayer(owner);
        if (op != null) op.sendSystemMessage(Component.literal("§c⚠ " + who + " is stealing your " + tagName() + "§c! Hit them!"));
        else if (sl.getEntity(owner) instanceof BrBot b) b.aggro(t);
        if (os != null && BrData.guard(os) > 0 && t instanceof ServerPlayer sp)
            sp.sendSystemMessage(Component.literal(BrData.GUARD_NAMES[BrData.guard(os)] + " bot! §cStay next to it for " + stealTicks() / 20 + "s - it is coming for you!"));
        return null;
    }

    public void cancelSteal() {
        if (stealer == null) return;
        stealer = null;
        refreshName();
    }

    void finishSteal(LivingEntity t) {
        ServerLevel sl = (ServerLevel) level();
        stealer = null;
        thief = t.getUUID();
        slot = -1;
        setState(CARRIED);
        startRiding(t, true);
        syncRider(t);
        setGlowingTag(true);
        refreshName();
        String who = nameOf(t);
        ServerPlayer op = sl.getServer().getPlayerList().getPlayer(owner);
        if (op != null) op.sendSystemMessage(Component.literal("§c" + who + " stole your " + tagName() + "§c! Hit them before they get home!"));
        else if (sl.getEntity(owner) instanceof BrBot b) b.aggro(t);
        if (t instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal("§aYou stole " + tagName() + "§a! Run to your base - if anybody hits you, it goes back!"));
        // malicious bots love to join in
        for (BrBot b : sl.getEntities(EntityTypeTest.forClass(BrBot.class), x -> x != t && !x.getUUID().equals(owner))) {
            BrData.Stats bs = BrData.get(sl.getServer()).map.get(b.getUUID());
            if (bs != null && bs.malicious && random.nextFloat() < 0.5f && b.distanceTo(t) < 40) b.aggro(t);
        }
    }

    /** Players do not get passenger updates about themselves: tell the thief's client that it now carries (or no longer carries) this. */
    static void syncRider(Entity vehicle) {
        if (vehicle instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetPassengersPacket(sp));
    }

    /** Goes back to the base it was stolen from (or vanishes if that player left). */
    public void returnHome() {
        Entity v = getVehicle();
        if (v != null) { stopRiding(); syncRider(v); }
        thief = null;
        setGlowingTag(false);
        if (owner == null || !settleFor(owner)) discard();
    }

    String nameOf(Entity e) {
        BrData.Stats st = BrData.get(level().getServer()).map.get(e.getUUID());
        return st != null ? st.name : e.getName().getString();
    }

    public String tagName() { return mut().prefix() + brainrot().tag(); }

    // ------------------------------------------------------------------ ticking

    void refreshName() {
        if (level().isClientSide || level().getServer() == null) return;
        BrData data = BrData.get(level().getServer());
        String n;
        switch (state()) {
            case PODIUM -> {
                if (stealer != null) n = "§c⚠ Being stolen... " + Math.max(0, (stealEnd - tickCount) / 20 + 1) + "s";
                else {
                    BrData.Stats st = owner == null ? null : data.map.get(owner);
                    n = (pending >= 1 ? "§a$" + Economy.fmt(pending) + " §8| " : "") + tagName() + " §e+$" + Economy.fmt(BrData.rate(st, brainrot(), mutation())) + "/s";
                }
            }
            case CARRIED -> n = "§c§l[STOLEN] " + tagName();
            case WALK -> {
                BrData.Stats b = buyer == null ? null : data.map.get(buyer);
                n = tagName() + " §6$" + Economy.fmt(price) + (b != null ? " §7(" + b.name + "'s)" : "");
            }
            default -> n = (special ? "§d§l★ " : "") + tagName() + " §6$" + Economy.fmt(price);
        }
        setCustomName(Component.literal(n));
        setCustomNameVisible(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) { clientTick(); return; }
        ServerLevel sl = (ServerLevel) level();
        setDeltaMovement(Vec3.ZERO);
        switch (state()) {
            case ROAD -> {
                setPos(getX() + ROAD_SPEED * com.eggsteal.RandomEvents.beltSpeedMult(), 64, getZ());
                face(1, 0);
                if (getX() > BrMap.ROAD_END) {
                    if (special) sl.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§7The " + tagName() + " §7walked away unclaimed..."), false);
                    discard();
                    return;
                }
            }
            case WALK -> {
                if (path.isEmpty()) planPath();
                if (!path.isEmpty() && stepTo(path.get(0), WALK_SPEED)) path.remove(0);
                if (path.isEmpty() && buyer != null) {
                    UUID b = buyer;
                    double paid = lastPaid;
                    if (!settleFor(b)) { // no room after all: money back
                        BrData.Stats st = BrData.get(sl.getServer()).map.get(b);
                        if (st != null) st.money += paid;
                        discard();
                        return;
                    }
                }
            }
            case PODIUM -> tickPodium(sl);
            case CARRIED -> tickCarried(sl);
            default -> { }
        }
        if (tickCount % 20 == 0) refreshName();
    }

    void tickPodium(ServerLevel sl) {
        BrData data = BrData.get(sl.getServer());
        BrData.Stats st = owner == null ? null : data.map.get(owner);
        if (st == null || st.base < 0) { discard(); return; } // the owner left the game
        if (tickCount % 20 == 0) {
            double r = BrData.rate(st, brainrot(), mutation());
            if (st.bot) st.money += r; // bots collect right away
            else pending += r;
            data.setDirty();
        }
        if (tickCount % 40 == 0 && slot >= 0) setPos(BrMap.podium(st.base, slot));
        if (stealer != null) {
            Entity e = sl.getEntity(stealer);
            if (!(e instanceof LivingEntity th) || !th.isAlive() || distanceTo(th) > 4.0) cancelSteal();
            else if (tickCount >= stealEnd) finishSteal(th);
            else if (tickCount % 10 == 0) {
                refreshName();
                if (th instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("§eStealing... stay close! §7" + ((stealEnd - tickCount) / 20 + 1) + "s"), true);
            }
        }
    }

    void tickCarried(ServerLevel sl) {
        Entity v = getVehicle();
        if (!(v instanceof LivingEntity th) || !th.isAlive() || th.isRemoved()) { returnHome(); return; }
        if (tickCount % 10 == 0) {
            int slow = Math.min(3, 1 + brainrot().rarity / 2); // heavier the rarer it is
            th.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, slow - 1, false, false, true));
            th.addEffect(new MobEffectInstance(MobEffects.GLOWING, 15, 0, false, false, false));
        }
        BrData.Stats ts = BrData.get(sl.getServer()).map.get(th.getUUID());
        if (ts != null && ts.base >= 0 && BrMap.baseAt(th.getX(), th.getZ()) == ts.base) {
            UUID from = owner;
            stopRiding();
            syncRider(th);
            if (settleFor(th.getUUID())) {
                sl.getServer().getPlayerList().broadcastSystemMessage(Component.literal("§c" + ts.name + " §fstole " + tagName() + "§f!"), false);
                if (th instanceof ServerPlayer sp) sp.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.4f);
            } else { owner = from; returnHome(); }
        }
    }

    /** Sparkles, flames, hearts... of the mutation (client only). */
    void clientTick() {
        BrMutation m = mut();
        int every = state() == ROAD ? 8 : 5;
        if (m.particle == BrMutation.P_NONE || tickCount % every != 0) return;
        float h = brainrot().height;
        double x = getX() + (random.nextDouble() - 0.5) * 1.0, y = getY() + random.nextDouble() * h, z = getZ() + (random.nextDouble() - 0.5) * 1.0;
        ParticleOptions p = switch (m.particle) {
            case BrMutation.P_WAX_ON -> ParticleTypes.WAX_ON;
            case BrMutation.P_WAX_OFF -> ParticleTypes.WAX_OFF;
            case BrMutation.P_COMPOSTER -> ParticleTypes.COMPOSTER;
            case BrMutation.P_SNOW -> ParticleTypes.SNOWFLAKE;
            case BrMutation.P_SPRINKLE -> {
                int c = com.eggsteal.Mutation.hsv(random.nextFloat(), 0.7f, 1f);
                yield new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f), 0.9f);
            }
            case BrMutation.P_END_ROD -> ParticleTypes.END_ROD;
            case BrMutation.P_SLIME -> ParticleTypes.ITEM_SLIME;
            case BrMutation.P_FLAME -> ParticleTypes.FLAME;
            case BrMutation.P_LAVA -> random.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.DRIPPING_LAVA;
            case BrMutation.P_SPARK -> ParticleTypes.ELECTRIC_SPARK;
            case BrMutation.P_WATER -> ParticleTypes.DRIPPING_WATER;
            case BrMutation.P_GLOW -> ParticleTypes.GLOW;
            case BrMutation.P_SMOKE -> ParticleTypes.LARGE_SMOKE;
            case BrMutation.P_SOUL -> ParticleTypes.SOUL;
            case BrMutation.P_ENCHANT -> ParticleTypes.ENCHANT;
            case BrMutation.P_HEART -> ParticleTypes.HEART;
            case BrMutation.P_CHERRY -> ParticleTypes.CHERRY_LEAVES;
            case BrMutation.P_CRIMSON -> ParticleTypes.CRIMSON_SPORE;
            case BrMutation.P_VILLAGER -> ParticleTypes.HAPPY_VILLAGER;
            case BrMutation.P_HIT -> ParticleTypes.ENCHANTED_HIT;
            case BrMutation.P_PORTAL -> ParticleTypes.PORTAL;
            case BrMutation.P_INK -> ParticleTypes.SQUID_INK;
            case BrMutation.P_REVERSE -> ParticleTypes.REVERSE_PORTAL;
            case BrMutation.P_TOTEM -> ParticleTypes.TOTEM_OF_UNDYING;
            case BrMutation.P_SOUL_FIRE -> random.nextBoolean() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
            default -> ParticleTypes.END_ROD;
        };
        double vy = m.particle == BrMutation.P_FLAME || m.particle == BrMutation.P_SOUL_FIRE ? 0.03 : 0.01;
        level().addParticle(p, x, y, z, 0, vy, 0);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        int s = state();
        String msg = null;
        if (s == ROAD || s == WALK) msg = buy(sp.getUUID());
        else if (s == PODIUM) {
            if (sp.getUUID().equals(owner)) {
                if (sp.isShiftKeyDown()) {
                    String n = tagName();
                    double got = sell();
                    msg = "§aSold " + n + " §afor §6$" + Economy.fmt(got) + "§a!";
                    sp.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1f, 0.8f);
                } else msg = tagName() + " §7| worth §6$" + Economy.fmt(value()) + " §7| §eSneak + right-click to sell for $" + Economy.fmt(Math.round(value() * 0.5));
            } else msg = beginSteal(sp);
        }
        if (msg != null) sp.displayClientMessage(Component.literal(msg), true);
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("variant", variant());
        t.putInt("mut", mutation());
        t.putInt("state", state());
        t.putDouble("price", price);
        t.putDouble("paid", lastPaid);
        t.putDouble("pending", pending);
        t.putInt("slot", slot);
        t.putBoolean("special", special);
        if (owner != null) t.putUUID("owner", owner);
        if (buyer != null) t.putUUID("buyer", buyer);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        entityData.set(VARIANT, t.getInt("variant"));
        entityData.set(MUT, t.getInt("mut"));
        int s = t.getInt("state");
        entityData.set(STATE, s == CARRIED ? PODIUM : s);
        price = t.getDouble("price");
        lastPaid = t.getDouble("paid");
        pending = t.getDouble("pending");
        slot = t.contains("slot") ? t.getInt("slot") : -1;
        special = t.getBoolean("special");
        if (t.hasUUID("owner")) owner = t.getUUID("owner");
        if (t.hasUUID("buyer")) buyer = t.getUUID("buyer");
        refreshDimensions();
    }
}
