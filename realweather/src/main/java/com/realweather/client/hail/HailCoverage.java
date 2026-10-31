package com.realweather.client.hail;

import com.realweather.client.ClientStorms;
import com.realweather.client.ClientWeather;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** How much hail has piled up on each creature standing out in a hailstorm; it melts off once they are under cover. */
public final class HailCoverage {
    private static final Int2FloatOpenHashMap COVER = new Int2FloatOpenHashMap();

    private HailCoverage() {}

    public static float get(int entityId) {
        return COVER.get(entityId);
    }

    public static void tick(Minecraft mc, ClientLevel level) {
        if (!ClientWeather.active(level)) {
            COVER.clear();
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float rainLevel = level.getRainLevel(1F);
        boolean any = ClientStorms.synced && rainLevel > 0.05F && !ClientStorms.cells().isEmpty();
        if (!any && COVER.isEmpty()) {
            return;
        }
        Int2FloatOpenHashMap next = new Int2FloatOpenHashMap();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity) || e.distanceToSqr(cam) > 48 * 48) {
                continue;
            }
            float c = COVER.get(e.getId());
            float hail = any ? ClientStorms.hailAt(e.getX(), e.getZ()) * rainLevel : 0F;
            boolean exposed = level.canSeeSky(BlockPos.containing(e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ()));
            if (exposed && hail > 0.1F) {
                c = Math.min(1F, c + 0.0028F * hail);
            } else {
                c = Math.max(0F, c - (e.isInWaterOrRain() && !exposed ? 0.002F : 0.0009F));
            }
            if (c > 0F) {
                next.put(e.getId(), c);
            }
        }
        COVER.clear();
        COVER.putAll(next);
    }
}
