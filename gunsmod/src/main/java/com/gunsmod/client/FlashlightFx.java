package com.gunsmod.client;

import com.gunsmod.gun.Attachment;
import com.gunsmod.item.GunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Weapon lights switch on by themselves in the dark and really light up the spot they point at. */
public final class FlashlightFx {
    private FlashlightFx() {}

    public static boolean hasLight(ItemStack gun) {
        return gun.getItem() instanceof GunItem && GunItem.attachments(gun).has(Attachment.FLASHLIGHT);
    }

    /** night time, or little sky light (caves, buildings, the Nether) */
    public static boolean dark(Level level, BlockPos at) {
        long t = level.getDayTime() % 24000L;
        boolean night = t > 12900L && t < 23100L;
        return night || level.getBrightness(LightLayer.SKY, at) < 7;
    }

    /** for the renderer: packed light of the holder plus the time of day */
    public static boolean darkAt(int packedLight) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        long t = level.getDayTime() % 24000L;
        return t > 12900L && t < 23100L || (packedLight >> 20 & 15) < 7;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        AABB box = mc.player.getBoundingBox().inflate(48);
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && hasLight(e.getMainHandItem()))) {
            if (n++ >= 4) break;
            Vec3 eye = e.getEyePosition();
            if (!dark(level, BlockPos.containing(eye))) continue;
            Vec3 look = e.getViewVector(1f);
            Vec3 end = eye.add(look.scale(28));
            HitResult hr = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
            Vec3 hit = hr.getType() == HitResult.Type.MISS ? end : hr.getLocation();
            double d = hit.distanceTo(eye);
            Vec3 spot = hit.subtract(look.scale(0.6));
            int lvl = d < 10 ? 15 : d < 18 ? 13 : 10;
            if (hr.getType() == HitResult.Type.MISS) lvl = 0;
            if (lvl > 0) ClientLights.light("fl" + e.getId(), spot, lvl, 3);
            // a softer glow around the holder so the beam's source lights the surroundings too
            ClientLights.light("fh" + e.getId(), eye.add(look.scale(2.0)), 8, 3);
        }
    }
}
