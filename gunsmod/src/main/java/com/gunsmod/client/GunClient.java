package com.gunsmod.client;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.VehicleEntity;
import com.gunsmod.gun.Attachment;
import com.gunsmod.gun.GunAttachments;
import com.gunsmod.gun.GunLogic;
import com.gunsmod.gun.GunType;
import com.gunsmod.item.GunItem;
import com.gunsmod.net.Net;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import com.gunsmod.client.screen.TeamScreen;
import net.minecraft.network.chat.Component;

/** Client-side gun state: input, aiming, recoil, animation timers and local shot prediction. */
public final class GunClient {
    private GunClient() {}

    public static final String CATEGORY = "key.categories.gunsmod";
    public static final KeyMapping RELOAD = new KeyMapping("key.gunsmod.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping SEAT = new KeyMapping("key.gunsmod.seat", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping HORN = new KeyMapping("key.gunsmod.horn", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping FLARES = new KeyMapping("key.gunsmod.flares", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping TEAM = new KeyMapping("key.gunsmod.team", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    // aiming
    public static boolean aiming;
    public static float aim, prevAim;
    private static boolean aimToggle;
    private static boolean useWasDown;
    private static int useDownTicks;
    // shooting
    private static boolean trigWas;
    private static int cooldown;
    private static int burstLeft;
    private static int pendingShots;
    private static int lastAmmoSeen = -1;
    private static ItemStack lastStack = ItemStack.EMPTY;
    public static float spin, spinSpeed;
    // animation
    public static float kick, prevKick;
    public static int flashTicks;
    public static int sinceShot = 100;
    public static float partialTick;
    public static int hitMarker;
    public static boolean hitKill;
    public static int ticks;
    private static boolean turretWas;
    private static boolean vehAttackWas, vehUseWas;
    private static final RandomSource RNG = RandomSource.create();

    public static float aim(float pt) {
        return Mth.lerp(pt, prevAim, aim);
    }

    public static float kick(float pt) {
        return Mth.lerp(pt, prevKick, kick);
    }

    public static boolean holdingGun(LocalPlayer p) {
        return p != null && p.getMainHandItem().getItem() instanceof GunItem;
    }

    /** sniper-scope overlay active */
    public static boolean scoped() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !holdingGun(mc.player) || aim < 0.85f) return false;
        Attachment s = GunItem.attachments(mc.player.getMainHandItem()).get(Attachment.Slot.SCOPE);
        return s != null && s.overlayScope();
    }

    public static float zoom() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !holdingGun(mc.player)) return 1f;
        ItemStack st = mc.player.getMainHandItem();
        GunType t = GunItem.typeOf(st);
        float z = GunItem.attachments(st).zoom(t);
        return Math.max(1.15f, z);
    }

    // ------------------------------------------------------------------------------------ tick
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ticks++;
        prevAim = aim;
        prevKick = kick;
        kick *= 0.55f;
        if (flashTicks > 0) flashTicks--;
        if (hitMarker > 0) hitMarker--;
        sinceShot++;
        if (cooldown > 0) cooldown--;
        if (p == null) return;
        boolean inScreen = mc.screen != null;
        while (TEAM.consumeClick()) {
            if (BattleHud.field()) mc.setScreen(new TeamScreen());
            else p.displayClientMessage(Component.translatable("gunsmod.msg.no_battlefield"), true);
        }

        // ---- vehicle controls
        if (p.getVehicle() instanceof JeepEntity jeep) {
            boolean gunner = jeep.isGunner(p);
            boolean fire = gunner && !inScreen && mc.options.keyAttack.isDown();
            if (fire != turretWas) {
                PacketDistributor.sendToServer(new Net.Turret(fire));
                turretWas = fire;
            }
            while (SEAT.consumeClick()) PacketDistributor.sendToServer(new Net.VehicleAction(0, 0, 0, 0));
            while (HORN.consumeClick()) PacketDistributor.sendToServer(new Net.VehicleAction(1, 0, 0, 0));
            if (jeep.isDriver(p) || gunner) {
                aiming = false;
                aim = Math.max(0, aim - 0.25f);
                return;
            }
        } else if (turretWas) {
            PacketDistributor.sendToServer(new Net.Turret(false));
            turretWas = false;
        }
        // tank / car commander, helicopter pilot and gunner: attack = main weapon, use = machine gun or cannon,
        // flares key = countermeasures, reload key = next weapon
        if (p.getVehicle() instanceof VehicleEntity v && v.firesFor(p)) {
            while (FLARES.consumeClick()) PacketDistributor.sendToServer(new Net.VehicleFire(2, true));
            while (RELOAD.consumeClick()) PacketDistributor.sendToServer(new Net.VehicleFire(3, true));
            boolean a = !inScreen && mc.options.keyAttack.isDown(), u = !inScreen && mc.options.keyUse.isDown();
            if (a != vehAttackWas) {
                PacketDistributor.sendToServer(new Net.VehicleFire(0, a));
                vehAttackWas = a;
            }
            if (u != vehUseWas) {
                PacketDistributor.sendToServer(new Net.VehicleFire(1, u));
                vehUseWas = u;
            }
            aiming = false;
            aim = Math.max(0, aim - 0.25f);
            return;
        } else if (vehAttackWas || vehUseWas) {
            if (p.getVehicle() instanceof VehicleEntity) {
                PacketDistributor.sendToServer(new Net.VehicleFire(0, false));
                PacketDistributor.sendToServer(new Net.VehicleFire(1, false));
            }
            vehAttackWas = vehUseWas = false;
        }

        ItemStack st = p.getMainHandItem();
        if (!(st.getItem() instanceof GunItem gun)) {
            aiming = false;
            aimToggle = false;
            aim = Math.max(0, aim - 0.25f);
            spinSpeed = Math.max(0, spinSpeed - 0.02f);
            spin += spinSpeed;
            lastStack = st;
            return;
        }
        GunType t = gun.type;
        GunAttachments att = GunItem.attachments(st);
        if (st != lastStack && !ItemStack.isSameItem(st, lastStack)) {
            aimToggle = false;
            pendingShots = 0;
        }
        lastStack = st;
        int ammo = GunItem.ammo(st);
        if (ammo != lastAmmoSeen) {
            pendingShots = 0;
            lastAmmoSeen = ammo;
        }

        // ---- aim: tap = toggle, hold = aim while held
        boolean useDown = !inScreen && mc.options.keyUse.isDown() && !wantsVanillaUse(mc);
        if (useDown) useDownTicks++;
        if (!useDown && useWasDown && useDownTicks < 6) aimToggle = !aimToggle;
        if (!useDown) useDownTicks = 0;
        useWasDown = useDown;
        aiming = !inScreen && (aimToggle || useDownTicks >= 6) && !GunItem.reloading(st) && !p.isSprinting();
        if (p.isSprinting() && (aimToggle || useDownTicks >= 6)) {
            p.setSprinting(false);
            aiming = true;
        }
        float speed = att.has(Attachment.ANGLED_GRIP) ? 0.3f : 0.2f;
        if (att.get(Attachment.Slot.SCOPE) == Attachment.SNIPER_SCOPE) speed *= 0.8f;
        aim = Mth.clamp(aim + (aiming ? speed : -speed), 0f, 1f);

        // ---- reload
        while (RELOAD.consumeClick()) {
            if (!GunItem.reloading(st) && ammo < att.magSize(t)) PacketDistributor.sendToServer(new Net.Reload());
        }

        // ---- trigger
        boolean trig = !inScreen && mc.options.keyAttack.isDown();
        if (t == GunType.MINIGUN) {
            spinSpeed = Mth.clamp(spinSpeed + (trig ? 0.035f : -0.02f), 0f, 0.9f);
            spin += spinSpeed;
        }
        if (GunItem.reloading(st)) {
            trigWas = trig;
            burstLeft = 0;
            return;
        }
        if (cooldown <= 0) {
            switch (t.mode) {
                case SEMI -> {
                    if (trig && !trigWas) shoot(p, st, t, att);
                }
                case BURST -> {
                    if (trig && !trigWas && burstLeft == 0) burstLeft = 3;
                    if (burstLeft > 0) {
                        burstLeft--;
                        shoot(p, st, t, att);
                        if (burstLeft == 0) cooldown += 6;
                    }
                }
                case AUTO -> {
                    if (trig && (t != GunType.MINIGUN || spinSpeed > 0.6f)) shoot(p, st, t, att);
                }
            }
        }
        trigWas = trig;
    }

    /** let the vanilla "use" through when looking at doors, containers, soldiers, vehicles... */
    public static boolean wantsVanillaUse(Minecraft mc) {
        HitResult hr = mc.hitResult;
        if (hr instanceof EntityHitResult eh) {
            Entity e = eh.getEntity();
            return e instanceof BotEntity || e instanceof JeepEntity || e instanceof VehicleEntity || !(e instanceof LivingEntity);
        }
        if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK && mc.level != null) {
            BlockState s = mc.level.getBlockState(bh.getBlockPos());
            return s.getBlock() instanceof EntityBlock || s.is(BlockTags.DOORS) || s.is(BlockTags.TRAPDOORS) || s.is(BlockTags.FENCE_GATES)
                    || s.is(BlockTags.BUTTONS) || s.getBlock() instanceof LeverBlock
                    || net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals("gunsmod")
                    || s.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE);
        }
        return false;
    }

    private static void shoot(LocalPlayer p, ItemStack st, GunType t, GunAttachments att) {
        int ammo = GunItem.ammo(st) - pendingShots;
        if (ammo <= 0) {
            p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), Reg.sound("dry_fire"), SoundSource.PLAYERS, 0.8f, 1f, false);
            cooldown = 10;
            if (!GunItem.reloading(st)) PacketDistributor.sendToServer(new Net.Reload());
            return;
        }
        long seed = RNG.nextLong();
        float yaw = p.getYRot(), pitch = p.getXRot();
        boolean ads = aim > 0.6f;
        PacketDistributor.sendToServer(new Net.Shoot(yaw, pitch, seed, ads));
        pendingShots++;
        cooldown = t.fireDelay;
        sinceShot = 0;
        localEffects(p, t, att, yaw, pitch, seed, ads);
        // recoil: kick the model and the camera
        float rec = t.recoil * att.recoilMul() * (ads ? 0.65f : 1f) * (p.isCrouching() ? 0.8f : 1f);
        kick = Math.min(1.6f, kick + 0.35f + rec * 0.06f);
        p.setXRot(p.getXRot() - rec * 0.45f);
        p.setYRot(p.getYRot() + (RNG.nextFloat() - 0.5f) * rec * 0.25f);
        if (!att.has(Attachment.SUPPRESSOR)) flashTicks = 2;
    }

    /** sound, tracers and impacts for our own shot (the server does the real hit detection) */
    private static void localEffects(LocalPlayer p, GunType t, GunAttachments att, float yaw, float pitch, long seed, boolean ads) {
        ClientLevel level = (ClientLevel) p.level();
        Vec3 eye = p.getEyePosition();
        Vec3 look = GunLogic.dir(pitch, yaw);
        Vec3 right = GunLogic.dir(0, yaw + 90);
        Vec3 muzzle = eye.add(look.scale(ads ? 1.1 : 1.3)).add(right.scale(ads ? 0.0 : 0.22)).add(0, ads ? -0.08 : -0.2, 0);
        boolean suppressed = att.has(Attachment.SUPPRESSOR);
        playShot(level, t, muzzle, suppressed, true);
        // the muzzle flash lights up the surroundings (a burst keeps one light alive)
        ClientLights.light(p, muzzle, suppressed ? 6 : t.kind == GunType.Kind.FLAME ? 14 : 13, t.kind == GunType.Kind.FLAME ? 4 : 3);
        if (t.kind == GunType.Kind.FLAME) {
            ClientNet.flames(level, muzzle, look);
            return;
        }
        if (t.kind != GunType.Kind.BULLET) {
            for (int i = 0; i < 6; i++) level.addParticle(Reg.SMOKE.get(), muzzle.x, muzzle.y, muzzle.z, look.x * 0.05 + RNG.nextGaussian() * 0.02, 0.02, look.z * 0.05);
            return;
        }
        float spread = GunLogic.spread(t, att, ads, p);
        List<Vec3> dirs = GunLogic.directions(t, yaw, pitch, seed, spread);
        for (int i = 0; i < dirs.size(); i++) {
            GunLogic.Trace tr = GunLogic.trace(level, p, eye, dirs.get(i), t.range);
            if (i % 2 == 0 || t.pellets < 4) ClientNet.tracer(level, muzzle, tr.end());
            ClientNet.impact(level, tr.end(), tr.kind(), tr.block() != null ? level.getBlockState(tr.block()) : null, dirs.get(i));
        }
        if (t.category != GunType.Category.EXPLOSIVE) {
            Vec3 port = eye.add(look.scale(0.5)).add(right.scale(0.25)).add(0, -0.25, 0);
            level.addParticle(Reg.SHELL.get(), port.x, port.y, port.z, right.x * 0.12 + RNG.nextGaussian() * 0.02, 0.1, right.z * 0.12);
        }
    }

    public static void playShot(ClientLevel level, GunType t, Vec3 at, boolean suppressed, boolean local) {
        SoundEvent s = Reg.sound(suppressed ? "shot_suppressed" : t.sound);
        float vol = suppressed ? 1.2f : switch (t.category) {
            case PISTOL, SMG -> 3.5f;
            case SNIPER, EXPLOSIVE -> 9f;
            case SPECIAL -> 1.2f;
            default -> 5.5f;
        };
        if (t.kind == GunType.Kind.FLAME) return; // looping sound handled separately
        level.playLocalSound(at.x, at.y, at.z, s, local ? SoundSource.PLAYERS : SoundSource.HOSTILE, local ? Math.min(vol, 2.5f) : vol,
                0.95f + RNG.nextFloat() * 0.1f, !local);
    }

    public static void onHitMarker(boolean kill) {
        hitMarker = 8;
        hitKill = kill;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(Reg.sound("hitmarker"), 0.6f, kill ? 0.7f : 1.2f);
    }

    public static boolean flameActive() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.screen == null && GunItem.typeOf(mc.player.getMainHandItem()) == GunType.FLAMETHROWER
                && mc.options.keyAttack.isDown() && GunItem.ammo(mc.player.getMainHandItem()) > 0 && !GunItem.reloading(mc.player.getMainHandItem());
    }
}
