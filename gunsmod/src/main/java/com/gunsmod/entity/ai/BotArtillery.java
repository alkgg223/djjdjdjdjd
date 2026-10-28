package com.gunsmod.entity.ai;

import com.gunsmod.Reg;
import com.gunsmod.block.LauncherBlockEntity;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.item.MissileItem;
import com.gunsmod.item.RemoteItem;
import com.gunsmod.item.RemoteLinks;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A soldier carrying a launch remote acts as a forward observer: every second it looks for enemy groups within
 * {@link #RANGE} blocks, picks a missile that will not hit friendlies, fires from one of its linked stations and keeps
 * the empty stations near it loaded from its missile rack ({@link BotReloadGoal} walks it there).
 */
public class BotArtillery {
    public static final double RANGE = 160;
    /** stations further than this from the soldier are not reloaded by it */
    public static final double RELOAD_RANGE = 48;

    private final BotEntity bot;
    private int think;
    private int launchCooldown = 60;
    @Nullable public BlockPos reloadTarget;
    private final List<Vec3> recent = new ArrayList<>();
    private final List<Long> recentTime = new ArrayList<>();

    public BotArtillery(BotEntity bot) {
        this.bot = bot;
    }

    public boolean active() {
        return bot.getOffhandItem().getItem() instanceof RemoteItem;
    }

    public void tick() {
        if (launchCooldown > 0) launchCooldown--;
        if (--think > 0 || !active() || !(bot.level() instanceof ServerLevel level)) return;
        think = 20;
        RemoteLinks links = RemoteItem.links(bot.getOffhandItem());
        if (links.isEmpty()) return;
        List<LauncherBlockEntity> stations = new ArrayList<>();
        for (BlockPos p : links.stations()) {
            if (!level.isLoaded(p)) continue;
            if (level.getBlockEntity(p) instanceof LauncherBlockEntity be) stations.add(be);
        }
        if (stations.isEmpty()) return;
        planReload(stations);
        if (launchCooldown <= 0) tryLaunch(level, stations);
    }

    // ------------------------------------------------------------------------------------ reloading
    private void planReload(List<LauncherBlockEntity> stations) {
        if (reloadTarget != null) {
            if (!(bot.level().getBlockEntity(reloadTarget) instanceof LauncherBlockEntity be) || be.hasMissile()) reloadTarget = null;
            return;
        }
        for (LauncherBlockEntity be : stations) {
            if (be.hasMissile() || be.isBusy()) continue;
            if (be.getBlockPos().distToCenterSqr(bot.getX(), bot.getY(), bot.getZ()) > RELOAD_RANGE * RELOAD_RANGE) continue;
            if (rackSlotFor(be) >= 0) {
                reloadTarget = be.getBlockPos();
                return;
            }
        }
    }

    /** rack slot holding a missile this station takes, or -1 */
    public int rackSlotFor(LauncherBlockEntity be) {
        for (int i = 0; i < bot.missiles.getContainerSize(); i++) {
            ItemStack s = bot.missiles.getItem(i);
            if (s.getItem() instanceof MissileItem m && be.accepts(m.kind)) return i;
        }
        return -1;
    }

    /** called by the reload goal next to the station */
    public void loadStation(LauncherBlockEntity be) {
        int slot = rackSlotFor(be);
        if (slot >= 0) {
            ItemStack s = bot.missiles.getItem(slot);
            if (be.loadMissile(((MissileItem) s.getItem()).kind)) {
                s.shrink(1);
                bot.missiles.setChanged();
                bot.swing(InteractionHand.MAIN_HAND);
                bot.level().playSound(null, be.getBlockPos(), Reg.sound("mag_in"), SoundSource.BLOCKS, 1.5f, be.isHeavy() ? 0.35f : 0.5f);
            }
        }
        reloadTarget = null;
    }

    // ------------------------------------------------------------------------------------ fire missions
    private void tryLaunch(ServerLevel level, List<LauncherBlockEntity> stations) {
        List<LauncherBlockEntity> ready = new ArrayList<>();
        for (LauncherBlockEntity be : stations) if (be.hasMissile() && be.getState() == LauncherBlockEntity.IDLE) ready.add(be);
        if (ready.isEmpty()) return;
        AABB area = bot.getBoundingBox().inflate(RANGE, 64, RANGE);
        List<LivingEntity> enemies = new ArrayList<>();
        List<LivingEntity> friends = new ArrayList<>();
        BotTeam team = bot.getBotTeam();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && e != bot)) {
            if (e instanceof JeepEntity j) {
                if (!j.getPassengers().isEmpty()) (j.getBotTeam() == team ? friends : enemies).add(j);
            } else if (bot.isEnemy(e)) {
                enemies.add(e);
            } else if (e instanceof BotEntity b && b.getBotTeam() == team || e instanceof Player p && team == BotTeam.GREEN && !p.isSpectator()) {
                friends.add(e);
            }
        }
        friends.add(bot);
        long now = level.getGameTime();
        for (int i = recent.size() - 1; i >= 0; i--) {
            if (now - recentTime.get(i) > 500) {
                recent.remove(i);
                recentTime.remove(i);
            }
        }
        // score every enemy by the size of the group around it
        LivingEntity best = null;
        int bestScore = 0;
        Vec3 bestCentre = null;
        for (LivingEntity e : enemies) {
            int score = 0;
            double sx = 0, sz = 0, minY = e.getY();
            for (LivingEntity o : enemies) {
                if (o.distanceToSqr(e) < 10 * 10) {
                    score += o instanceof JeepEntity ? 4 : o instanceof Player ? 2 : 1;
                    sx += o.getX();
                    sz += o.getZ();
                    minY = Math.min(minY, o.getY());
                }
            }
            int n = 0;
            for (LivingEntity o : enemies) if (o.distanceToSqr(e) < 10 * 10) n++;
            Vec3 centre = new Vec3(sx / n, minY, sz / n);
            if (recentlyHit(centre) && score < 4) continue;
            if (score > bestScore) {
                bestScore = score;
                best = e;
                bestCentre = centre;
            }
        }
        if (best == null) return;
        // the biggest warhead that keeps every friendly (and the soldier itself) out of its blast
        LauncherBlockEntity pick = null;
        double pickRadius = -1;
        for (LauncherBlockEntity be : ready) {
            double danger = MissileItem.dangerRadius(be.missileKind());
            if (!safe(bestCentre, danger, friends)) continue;
            boolean heavy = MissileItem.isHeavy(be.missileKind());
            if (heavy && bestScore < 4) continue;           // keep the big ones for groups and vehicles
            if (danger > pickRadius) {
                pick = be;
                pickRadius = danger;
            }
        }
        if (pick == null) return;
        BlockPos target = BlockPos.containing(bestCentre.x, bestCentre.y, bestCentre.z);
        if (pick.fireAt(bot, target)) {
            recent.add(bestCentre);
            recentTime.add(now);
            launchCooldown = MissileItem.isHeavy(pick.missileKind()) ? 400 : 120;
            bot.swing(InteractionHand.OFF_HAND);
            bot.playBotSound(Reg.sound("ui_confirm"), 1f, 0.8f);
            bot.getLookControl().setLookAt(bestCentre.x, bestCentre.y + 1, bestCentre.z);
        }
    }

    private static boolean safe(Vec3 at, double radius, List<LivingEntity> friends) {
        for (Entity f : friends) {
            if (f.distanceToSqr(at) < (radius + 4) * (radius + 4)) return false;
        }
        return true;
    }

    private boolean recentlyHit(Vec3 c) {
        for (Vec3 r : recent) if (r.distanceToSqr(c) < 12 * 12) return true;
        return false;
    }
}
