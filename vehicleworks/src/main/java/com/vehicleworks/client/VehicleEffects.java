package com.vehicleworks.client;

import com.vehicleworks.physics.propulsion.EngineType;
import com.vehicleworks.vehicle.VehicleBuilder;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import com.vehicleworks.weapon.WeaponSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import org.joml.Vector3d;

/** Particles: exhaust, afterburner flame, damage smoke and fire, tyre smoke, water spray, muzzle flashes. */
public final class VehicleEffects {
    private VehicleEffects() {}

    public static void tick(VehicleEntity v, VehicleClientState s) {
        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || mc.player == null || v.distanceToSqr(mc.player) > 160 * 160) return;
        VehicleTelemetry t = v.telemetry;
        RandomSource r = level.random;
        Vector3d w = new Vector3d();
        for (int i = 0; i < t.engineKeys.length && i < t.engineState.length; i++) {
            long k = t.engineKeys[i];
            var st = v.structure.get(k);
            Direction f = VehicleBuilder.facing(st);
            EngineType type = EngineType.byIndex(t.engineType[i]);
            boolean running = t.engineState[i] == 1 || t.engineState[i] == 2;
            // exhaust at the back of the engine
            v.localToWorld(BlockPos.getX(k) + 0.5 - f.getStepX() * 0.6, BlockPos.getY(k) + 0.5 - f.getStepY() * 0.6, BlockPos.getZ(k) + 0.5 - f.getStepZ() * 0.6, w);
            Vector3d back = v.localDirToWorld(-f.getStepX(), -f.getStepY(), -f.getStepZ(), new Vector3d());
            if (running && r.nextFloat() < 0.25f + t.engineSpool[i] * 0.5f) {
                if (type == EngineType.PISTON && r.nextFloat() < 0.6f) level.addParticle(ParticleTypes.SMOKE, w.x, w.y, w.z, back.x * 0.05, 0.02, back.z * 0.05);
                if (type.isTurbine() && t.engineSpool[i] > 0.6f) level.addParticle(ParticleTypes.CLOUD, w.x, w.y, w.z, back.x * 0.2, back.y * 0.2, back.z * 0.2);
            }
            if (i < t.engineAb.length && t.engineAb[i]) {
                for (int n = 0; n < 3; n++) level.addParticle(ParticleTypes.FLAME, w.x + r.nextGaussian() * 0.1, w.y + r.nextGaussian() * 0.1, w.z + r.nextGaussian() * 0.1, back.x * 0.6, back.y * 0.6, back.z * 0.6);
            }
            if (t.engineDamage[i] > 0.5f && r.nextFloat() < t.engineDamage[i] * 0.6f) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, w.x, w.y + 0.3, w.z, 0, 0.05, 0);
            }
            if (t.engineState[i] == 4 && t.engineDamage[i] > 0.8f) level.addParticle(ParticleTypes.FLAME, w.x, w.y + 0.2, w.z, 0, 0.04, 0);
        }
        for (int i = 0; i < t.wheelKeys.length && i < t.wheelSkid.length; i++) {
            if (!t.wheelSkid[i] || r.nextFloat() > 0.5f) continue;
            long k = t.wheelKeys[i];
            v.localToWorld(BlockPos.getX(k) + 0.5, BlockPos.getY(k) - 0.2, BlockPos.getZ(k) + 0.5, w);
            level.addParticle(ParticleTypes.CLOUD, w.x, w.y, w.z, 0, 0.02, 0);
        }
        if (t.inWater && t.groundSpeed > 2.5f) {
            var bb = v.getBoundingBox();
            Vector3d fw = v.localDirToWorld(0, 0, -1, new Vector3d());
            int n = (int) Math.min(6, t.groundSpeed / 2);
            for (int i = 0; i < n; i++) {
                double x = v.getX() + fw.x * bb.getXsize() * 0.45 + r.nextGaussian() * 0.8;
                double z = v.getZ() + fw.z * bb.getZsize() * 0.45 + r.nextGaussian() * 0.8;
                double y = bb.minY + 0.6;
                level.addParticle(ParticleTypes.SPLASH, x, y, z, r.nextGaussian() * 0.1, 0.15, r.nextGaussian() * 0.1);
            }
            if (r.nextFloat() < 0.5f) level.addParticle(ParticleTypes.BUBBLE, v.getX(), bb.minY + 0.3, v.getZ(), 0, 0.05, 0);
        }
        if (t.fireFlash > 0) {
            for (int i = 0; i < t.weaponKeys.length && i < t.weaponFlags.length; i++) {
                if ((t.weaponFlags[i] & WeaponSystem.FLAG_FIRING) == 0 && t.fireFlash < 3) continue;
                long k = t.weaponKeys[i];
                var st = v.structure.get(k);
                Direction f = VehicleBuilder.facing(st);
                v.localToWorld(BlockPos.getX(k) + 0.5 + f.getStepX() * 0.8, BlockPos.getY(k) + 0.5 + f.getStepY() * 0.8, BlockPos.getZ(k) + 0.5 + f.getStepZ() * 0.8, w);
                level.addParticle(ParticleTypes.SMALL_FLAME, w.x, w.y, w.z, 0, 0, 0);
                if (r.nextFloat() < 0.4f) level.addParticle(ParticleTypes.SMOKE, w.x, w.y, w.z, 0, 0.02, 0);
            }
        }
        // heavily damaged blocks smoke
        if (v.tickCount % 4 == 0) {
            int shown = 0;
            for (var e : v.structure.damageMap().long2FloatEntrySet()) {
                if (shown > 4) break;
                if (e.getFloatValue() < 30 || r.nextFloat() > 0.3f) continue;
                long k = e.getLongKey();
                v.localToWorld(BlockPos.getX(k) + 0.5, BlockPos.getY(k) + 1.0, BlockPos.getZ(k) + 0.5, w);
                level.addParticle(ParticleTypes.SMOKE, w.x, w.y, w.z, 0, 0.03, 0);
                shown++;
            }
        }
        VehicleSounds.tick(v, s);
    }
}
