package com.vehicleworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vehicleworks.Config;
import com.vehicleworks.VehicleWorks;
import com.vehicleworks.block.ComponentBlock;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.vehicle.VehicleBuilder;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/**
 * Renders the animated parts of a vehicle (propellers, rotors, wheels and suspension,
 * turrets), block entities such as chests, and the centre-of-mass marker. The static
 * structure is drawn from the cached mesh in {@link RenderEvents}.
 */
public class VehicleRenderer extends EntityRenderer<VehicleEntity> {
    private static final ResourceLocation BLADE = ResourceLocation.fromNamespaceAndPath(VehicleWorks.MODID, "block/rotor_blade");
    private final Long2ObjectOpenHashMap<BlockEntity> beCache = new Long2ObjectOpenHashMap<>();

    public VehicleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(VehicleEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public boolean shouldRender(VehicleEntity v, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(v.getBoundingBoxForCulling());
    }

    @Override
    public void render(VehicleEntity v, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        VehicleClientState s = ClientProxy.state(v);
        var cam = entityRenderDispatcher.camera;
        double dist = cam.getPosition().distanceTo(v.position());
        if (dist > Config.CLIENT.lodDistance.get()) return;
        Quaternionf q = new Quaternionf(v.renderOrientation(partial, new org.joml.Quaterniond()));
        ps.pushPose();
        ps.mulPose(q);
        ps.translate(-v.comLocal.x, -v.comLocal.y, -v.comLocal.z);
        VehicleTelemetry t = v.telemetry;
        BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();
        float time = (v.tickCount + partial) * 0.05f;
        // engines' propellers
        for (int i = 0; i < t.deviceKeys.length; i++) {
            long k = t.deviceKeys[i];
            BlockState st = v.structure.get(k);
            ComponentType ct = VehicleBuilder.typeOf(st);
            if (ct != ComponentType.PROPELLER && ct != ComponentType.MARINE_PROPELLER) continue;
            float omega = i < t.propOmega.length ? t.propOmega[i] : 0;
            float angle = phase(s, "p" + i, omega, partial);
            Direction f = VehicleBuilder.facing(st);
            spinBlock(ps, buffers, brd, st, k, f, angle, light);
        }
        // rotors
        for (int i = 0; i < t.rotorKeys.length; i++) {
            long k = t.rotorKeys[i];
            BlockState st = v.structure.get(k);
            ComponentType ct = VehicleBuilder.typeOf(st);
            if (ct != ComponentType.MAIN_ROTOR && ct != ComponentType.TAIL_ROTOR) continue;
            float omega = i < t.rotorOmega.length ? t.rotorOmega[i] : 0;
            float angle = phase(s, "r" + i, omega, partial);
            Direction f = VehicleBuilder.facing(st);
            CompoundTag cfg = VehicleBuilder.config(v.structure, k);
            double radius = ct.property("radius").get(cfg);
            int blades = ct.property("blades").getInt(cfg);
            spinBlock(ps, buffers, brd, st, k, f, angle, light);
            renderBlades(ps, buffers, k, f, angle, radius, blades, ct == ComponentType.MAIN_ROTOR ? 0.45f : 0.22f,
                    ct == ComponentType.MAIN_ROTOR ? (float) Math.toRadians(4 + 8 * t.collective) : 0.15f, light, Math.abs(omega) > 20);
        }
        // wheels with suspension, steering and retraction
        for (int i = 0; i < t.wheelKeys.length; i++) {
            long k = t.wheelKeys[i];
            BlockState st = v.structure.get(k);
            ComponentType ct = VehicleBuilder.typeOf(st);
            if (ct == null) continue;
            CompoundTag cfg = VehicleBuilder.config(v.structure, k);
            double travel = ct.property("travel").get(cfg);
            float comp = i < t.wheelComp.length ? t.wheelComp[i] : 0;
            float ext = i < t.wheelExt.length ? t.wheelExt[i] : 1;
            float steer = i < t.wheelSteer.length ? t.wheelSteer[i] : 0;
            float omega = i < t.wheelOmega.length ? t.wheelOmega[i] : 0;
            float angle = phase(s, "w" + i, omega, partial);
            double drop = comp > 0 ? Math.max(0, travel - comp) : travel * 0.6;
            drop *= ext;
            Direction f = VehicleBuilder.facing(st);
            ps.pushPose();
            ps.translate(BlockPos.getX(k) + 0.5, BlockPos.getY(k) + 0.5 - drop, BlockPos.getZ(k) + 0.5);
            ps.mulPose(Axis.YP.rotation(-steer));
            Direction axle = f.getClockWise();
            ps.mulPose(axisRot(axle, -angle));
            float scale = (float) (ct.property("radius").get(cfg) / 0.5);
            ps.scale(scale, scale, scale);
            ps.translate(-0.5, -0.5, -0.5);
            brd.renderSingleBlock(st, ps, buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
            if (ext > 0.1 && drop > 0.2) {
                // suspension strut
                renderStrut(ps, buffers, k, drop, light);
            }
        }
        // turret-mounted weapons
        for (int i = 0; i < t.weaponKeys.length; i++) {
            if (i >= t.weaponFlags.length || (t.weaponFlags[i] & com.vehicleworks.weapon.WeaponSystem.FLAG_TURRET) == 0) continue;
            long k = t.weaponKeys[i];
            BlockState st = v.structure.get(k);
            Direction f = VehicleBuilder.facing(st);
            ps.pushPose();
            ps.translate(BlockPos.getX(k) + 0.5, BlockPos.getY(k) + 0.5, BlockPos.getZ(k) + 0.5);
            ps.mulPose(Axis.YP.rotation(-t.turretYaw[i]));
            Direction side = f.getAxis() == Direction.Axis.Y ? Direction.EAST : f.getCounterClockWise();
            ps.mulPose(axisRot(side, t.turretPitch[i]));
            ps.translate(-0.5, -0.5, -0.5);
            brd.renderSingleBlock(st, ps, buffers, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        // block entities (chests, signs, banners…)
        renderWarningLamps(v, ps, buffers);
        if (Config.CLIENT.renderBlockEntities.get()) renderBlockEntities(v, ps, buffers, partial, light);
        // centre-of-mass marker
        long core = BlockPos.asLong(0, 0, 0);
        CompoundTag coreCfg = VehicleBuilder.config(v.structure, core);
        if (VehicleBuilder.typeOf(v.structure.get(core)) == ComponentType.VEHICLE_CORE && coreCfg.getBoolean("show_com")) {
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            double cx = v.comLocal.x, cy = v.comLocal.y, cz = v.comLocal.z;
            LevelRenderer.renderLineBox(ps, lines, new AABB(cx - 0.15, cy - 0.15, cz - 0.15, cx + 0.15, cy + 0.15, cz + 0.15), 1f, 0.9f, 0.1f, 1f);
            LevelRenderer.renderLineBox(ps, lines, new AABB(cx - 0.02, cy - 0.02, cz - 2.5, cx + 0.02, cy + 0.02, cz + 2.5), 0.2f, 0.4f, 1f, 1f);
            LevelRenderer.renderLineBox(ps, lines, new AABB(cx - 2.5, cy - 0.02, cz - 0.02, cx + 2.5, cy + 0.02, cz + 0.02), 1f, 0.2f, 0.2f, 1f);
            LevelRenderer.renderLineBox(ps, lines, new AABB(cx - 0.02, cy - 2.5, cz - 0.02, cx + 0.02, cy + 2.5, cz + 0.02), 0.2f, 1f, 0.2f, 1f);
        }
        ps.popPose();
        super.render(v, yaw, partial, ps, buffers, light);
    }

    /** Current animation phase (advanced once per frame by {@link #advancePhases}). */
    private static float phase(VehicleClientState s, String key, float omega, float partial) {
        var map = PHASES.computeIfAbsent(s, x -> new java.util.HashMap<>());
        return map.computeIfAbsent(key, x -> new float[2])[0];
    }

    private static final java.util.Map<VehicleClientState, java.util.Map<String, float[]>> PHASES = new java.util.WeakHashMap<>();

    /** Advance all animation phases once per frame (called from the render event). */
    public static void advancePhases(VehicleEntity v, float frameSeconds) {
        VehicleClientState s = ClientProxy.state(v);
        var map = PHASES.computeIfAbsent(s, x -> new java.util.HashMap<>());
        VehicleTelemetry t = v.telemetry;
        for (int i = 0; i < t.propOmega.length; i++) map.computeIfAbsent("p" + i, x -> new float[2])[0] += t.propOmega[i] * frameSeconds;
        for (int i = 0; i < t.rotorOmega.length; i++) map.computeIfAbsent("r" + i, x -> new float[2])[0] += clampVisual(t.rotorOmega[i]) * frameSeconds;
        for (int i = 0; i < t.wheelOmega.length; i++) map.computeIfAbsent("w" + i, x -> new float[2])[0] += t.wheelOmega[i] * frameSeconds;
        for (float[] p : map.values()) p[0] %= (float) (Math.PI * 2);
    }

    /** Rotor blades look better slightly below the true rate (avoids wagon-wheel aliasing). */
    private static float clampVisual(float omega) {
        return omega;
    }

    private static Quaternionf axisRot(Direction d, float angle) {
        return switch (d.getAxis()) {
            case X -> Axis.XP.rotation(angle * d.getAxisDirection().getStep());
            case Y -> Axis.YP.rotation(angle * d.getAxisDirection().getStep());
            case Z -> Axis.ZP.rotation(angle * d.getAxisDirection().getStep());
        };
    }

    private static void spinBlock(PoseStack ps, MultiBufferSource buffers, BlockRenderDispatcher brd, BlockState st, long k, Direction f, float angle, int light) {
        ps.pushPose();
        ps.translate(BlockPos.getX(k) + 0.5, BlockPos.getY(k) + 0.5, BlockPos.getZ(k) + 0.5);
        ps.mulPose(axisRot(f, angle));
        ps.translate(-0.5, -0.5, -0.5);
        brd.renderSingleBlock(st, ps, buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    private void renderBlades(PoseStack ps, MultiBufferSource buffers, long k, Direction axis, float angle, double radius, int blades,
                              float chord, float pitch, int light, boolean blur) {
        TextureAtlasSprite sp = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(BLADE);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        ps.pushPose();
        ps.translate(BlockPos.getX(k) + 0.5, BlockPos.getY(k) + 0.5, BlockPos.getZ(k) + 0.5);
        // align local +Y with the rotor axis
        switch (axis) {
            case DOWN -> ps.mulPose(Axis.XP.rotationDegrees(180));
            case NORTH -> ps.mulPose(Axis.XP.rotationDegrees(-90));
            case SOUTH -> ps.mulPose(Axis.XP.rotationDegrees(90));
            case EAST -> ps.mulPose(Axis.ZP.rotationDegrees(-90));
            case WEST -> ps.mulPose(Axis.ZP.rotationDegrees(90));
            default -> {}
        }
        ps.translate(0, 0.35, 0);
        for (int b = 0; b < blades; b++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotation(angle + (float) (b * Math.PI * 2 / blades)));
            ps.mulPose(Axis.XP.rotation(pitch));
            Box.cuboid(ps, vc, sp, 0.15f, -0.03f, -chord / 2, (float) radius, 0.03f, chord / 2, light, blur ? 0xC0FFFFFF : 0xFFFFFFFF);
            ps.popPose();
        }
        ps.popPose();
    }

    /** Red master-warning lamp on top of every cockpit panel / instrument block; flashes while a warning is active. */
    private void renderWarningLamps(VehicleEntity v, PoseStack ps, MultiBufferSource buffers) {
        boolean mine = ClientEvents.ridden() == v;
        boolean on = mine && VoiceCallouts.blink();
        TextureAtlasSprite sp = null;
        VertexConsumer vc = null;
        for (long k : v.structure.blockEntities().keySet()) {
            ComponentType ct = VehicleBuilder.typeOf(v.structure.get(k));
            if (ct != ComponentType.COCKPIT_PANEL && ct != ComponentType.FLIGHT_INSTRUMENTS) continue;
            if (sp == null) {
                sp = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.withDefaultNamespace("block/white_concrete"));
                vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
            }
            ps.pushPose();
            ps.translate(BlockPos.getX(k) + 0.5, BlockPos.getY(k) + (ct == ComponentType.COCKPIT_PANEL ? 0.76 : 1.0), BlockPos.getZ(k) + 0.5);
            Box.cuboid(ps, vc, sp, -0.12f, 0f, -0.12f, 0.12f, 0.14f, 0.12f, on ? 0xF000F0 : 0x400040, on ? 0xFFFF2020 : 0xFF501010);
            ps.popPose();
        }
    }

    private void renderStrut(PoseStack ps, MultiBufferSource buffers, long k, double drop, int light) {
        TextureAtlasSprite sp = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(
                ResourceLocation.fromNamespaceAndPath(VehicleWorks.MODID, "block/strut"));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        ps.pushPose();
        ps.translate(BlockPos.getX(k) + 0.5, BlockPos.getY(k) + 0.5, BlockPos.getZ(k) + 0.5);
        Box.cuboid(ps, vc, sp, -0.06f, (float) -drop, -0.06f, 0.06f, 0.3f, 0.06f, light, 0xFFFFFFFF);
        ps.popPose();
    }

    private void renderBlockEntities(VehicleEntity v, PoseStack ps, MultiBufferSource buffers, float partial, int light) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        for (var e : v.structure.blockEntities().long2ObjectEntrySet()) {
            long k = e.getLongKey();
            BlockState st = v.structure.get(k);
            if (st.getRenderShape() != RenderShape.ENTITYBLOCK_ANIMATED && !(st.getBlock() instanceof net.minecraft.world.level.block.SignBlock)
                    && !(st.getBlock() instanceof net.minecraft.world.level.block.BannerBlock)) continue;
            BlockEntity be = beCache.get(k);
            if (be == null || be.getBlockState() != st) {
                try {
                    be = BlockEntity.loadStatic(BlockPos.of(k), st, e.getValue(), level.registryAccess());
                } catch (Exception ex) {
                    be = null;
                }
                if (be == null) continue;
                beCache.put(k, be);
            }
            BlockEntityRenderer<BlockEntity> r = dispatcher.getRenderer(be);
            if (r == null) continue;
            ps.pushPose();
            ps.translate(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k));
            try {
                r.render(be, partial, ps, buffers, light, OverlayTexture.NO_OVERLAY);
            } catch (Throwable ex) {
                beCache.remove(k);
            }
            ps.popPose();
        }
    }
}
