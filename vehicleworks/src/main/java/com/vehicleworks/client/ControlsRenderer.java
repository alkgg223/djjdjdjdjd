package com.vehicleworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.vehicle.VehicleBuilder;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Flight controls drawn live from the control state: the airplane yoke (column moves in and out
 * with pitch, the wheel turns with roll) and the Airbus-style side stick (base, rubber boot,
 * ergonomic handle with trigger and take-over / priority button, tilting on both axes).
 */
public final class ControlsRenderer {
    private ControlsRenderer() {}

    public static void render(VehicleEntity v, PoseStack ps, MultiBufferSource buffers, int light, VehicleClientState s) {
        VehicleTelemetry t = v.telemetry;
        s.yokePitch += (t.stickPitch - s.yokePitch) * 0.25f;
        s.yokeRoll += (t.stickRoll - s.yokeRoll) * 0.25f;
        float sp = s.yokePitch, sr = s.yokeRoll;
        VertexConsumer vc = null;
        TextureAtlasSprite metal = null, black = null, knob = null, white = null, guard = null;
        for (long k : v.structure.blockEntities().keySet()) {
            BlockState st = v.structure.get(k);
            ComponentType ct = VehicleBuilder.typeOf(st);
            if (ct != ComponentType.YOKE && ct != ComponentType.SIDESTICK) continue;
            if (vc == null) {
                vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
                metal = CockpitParts.sprite(CockpitParts.METAL); black = CockpitParts.sprite(CockpitParts.BLACK); knob = CockpitParts.sprite(CockpitParts.KNOB);
                white = CockpitParts.sprite(CockpitParts.WHITE); guard = CockpitParts.sprite(CockpitParts.GUARD);
            }
            ps.pushPose();
            ps.translate(BlockPos.getX(k) + 0.5, BlockPos.getY(k), BlockPos.getZ(k) + 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(-CockpitRenderer.facingRot(VehicleBuilder.facing(st))));
            if (ct == ComponentType.YOKE) yoke(ps, vc, metal, black, knob, white, guard, sp, sr, light);
            else {
                boolean right = ComponentType.SIDESTICK.property("side").getInt(VehicleBuilder.config(v.structure, k)) == 1;
                sidestick(ps, vc, metal, black, knob, white, guard, sp, sr, right, light);
            }
            ps.popPose();
        }
    }

    /** horizontal cylinder along z (from z0 to z1) */
    private static void zCyl(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, float x, float y, float z0, float z1, float r, int n, int light, int c1, int c2) {
        ps.pushPose();
        ps.translate(x, y, z0);
        ps.mulPose(Axis.XP.rotationDegrees(-90)); // local -y (prism direction) -> -z
        CockpitParts.prism(ps, vc, sp, 0, 0, r, r, 0, -(z0 - z1), n, light, c1, c2, true);
        ps.popPose();
    }

    /**
     * Yoke. Local frame: origin at the block's floor centre, -z toward the pilot. The column comes
     * out of the instrument panel (+z) at hand height; pull = column toward the pilot.
     */
    static void yoke(PoseStack ps, VertexConsumer vc, TextureAtlasSprite metal, TextureAtlasSprite black, TextureAtlasSprite knob, TextureAtlasSprite white,
                     TextureAtlasSprite red, float sp, float sr, int light) {
        float h = 0.62f;
        float travel = 0.11f * sp; // pull (positive pitch) brings the wheel toward the pilot
        // panel collar and rubber boot
        zCyl(ps, vc, black, 0, h, 0.5f, 0.42f, 0.065f, 14, light, 0xFF1C1E21, 0xFF24272B);
        zCyl(ps, vc, knob, 0, h, 0.42f, 0.34f, 0.045f, 14, light, 0xFF141516, 0xFF1E2023);
        // chrome column
        zCyl(ps, vc, metal, 0, h, 0.42f, -0.12f - travel, 0.026f, 12, light, 0xFFC8CED4, 0xFFB4BAC0);
        ps.pushPose();
        ps.translate(0, h, -0.14f - travel);
        ps.mulPose(Axis.ZP.rotationDegrees(-55 * sr)); // wheel turns with roll
        // hub with a centre plate
        zCyl(ps, vc, black, 0, 0, 0.03f, -0.035f, 0.055f, 16, light, 0xFF2A2D31, 0xFF33373C);
        Box.cuboid(ps, vc, black, -0.04f, -0.03f, -0.038f, 0.04f, 0.03f, -0.034f, light, 0xFF3D4248);
        Box.cuboid(ps, vc, white, -0.025f, -0.004f, -0.0385f, 0.025f, 0.004f, -0.0375f, light, 0xFFB8C0C8);
        // cross bar and the two horns (ram's horn grips), slightly swept up
        Box.cuboid(ps, vc, black, -0.2f, -0.022f, -0.022f, 0.2f, 0.018f, 0.018f, light, 0xFF26292D);
        for (int side = -1; side <= 1; side += 2) {
            ps.pushPose();
            ps.translate(side * 0.21f, 0, 0);
            ps.mulPose(Axis.ZP.rotationDegrees(side * -12));
            Box.cuboid(ps, vc, knob, -0.026f, -0.05f, -0.026f, 0.026f, 0.13f, 0.026f, light, 0xFF1A1B1D);     // grip
            Box.cuboid(ps, vc, black, -0.03f, 0.13f, -0.03f, 0.03f, 0.15f, 0.03f, light, 0xFF2E3136);          // cap
            Box.cuboid(ps, vc, black, -0.03f, -0.07f, -0.03f, 0.03f, -0.05f, 0.03f, light, 0xFF2E3136);        // lower cap
            if (side < 0) {
                Box.cuboid(ps, vc, red, -0.012f, 0.15f, -0.012f, 0.012f, 0.162f, 0.012f, light, 0xFFD02A1E);    // autopilot disconnect
                Box.cuboid(ps, vc, black, 0.026f, 0.08f, -0.012f, 0.034f, 0.11f, 0.012f, light, 0xFF8A9096);   // pitch trim rocker
            } else {
                Box.cuboid(ps, vc, black, -0.034f, 0.07f, -0.014f, -0.026f, 0.1f, 0.014f, light, 0xFF5A6068);  // push-to-talk
            }
            ps.popPose();
        }
        ps.popPose();
    }

    /**
     * Side stick on its side console. Local frame: -z = the pilot's forward, +x = right. The
     * console sits outboard (left stick for the captain, right for the first officer).
     */
    static void sidestick(PoseStack ps, VertexConsumer vc, TextureAtlasSprite metal, TextureAtlasSprite black, TextureAtlasSprite knob, TextureAtlasSprite white,
                          TextureAtlasSprite red, float sp, float sr, boolean right, int light) {
        float ox = right ? 0.3f : -0.3f, oz = 0.32f;
        ps.pushPose();
        ps.translate(ox, 0, oz);
        // side console with an arm rest pad behind the stick
        Box.cuboid(ps, vc, black, -0.13f, 0, -0.22f, 0.13f, 0.5f, 0.2f, light, 0xFF34383D);
        Box.cuboid(ps, vc, black, -0.12f, 0.5f, 0.02f, 0.12f, 0.54f, 0.2f, light, 0xFF26292D);   // arm rest
        Box.cuboid(ps, vc, white, -0.125f, 0.5f, -0.21f, 0.125f, 0.502f, 0.02f, light, 0xFF3E434A); // console face
        // base plate, bezel and rubber boot
        CockpitParts.prism(ps, vc, metal, 0, -0.1f, 0.07f, 0.07f, 0.525f, 0.5f, 16, light, 0xFF4A4F55, 0xFF44494F, false);
        ps.pushPose();
        ps.translate(0, 0.52f, -0.1f);
        // pivot: pitch tilts about x (pull = top toward the pilot, +z), roll about z (right = +x)
        ps.mulPose(Axis.XP.rotationDegrees(25 * sp));
        ps.mulPose(Axis.ZP.rotationDegrees(-25 * sr));
        ps.mulPose(Axis.XP.rotationDegrees(180)); // prisms grow toward -y: flip so the stick points up
        CockpitParts.prism(ps, vc, knob, 0, 0, 0.05f, 0.028f, 0, -0.05f, 12, light, 0xFF141516, 0xFF1C1D1F, false);      // boot
        CockpitParts.prism(ps, vc, metal, 0, 0, 0.018f, 0.018f, -0.05f, -0.09f, 10, light, 0xFF9AA0A6, 0xFF8C9298, false); // shaft
        ps.popPose();
        // handle (follows the same tilt)
        ps.pushPose();
        ps.translate(0, 0.52f, -0.1f);
        ps.mulPose(Axis.XP.rotationDegrees(25 * sp));
        ps.mulPose(Axis.ZP.rotationDegrees(-25 * sr));
        ps.translate(0, 0.09f, 0);
        ps.mulPose(Axis.XP.rotationDegrees(-8)); // ergonomic forward rake
        Box.cuboid(ps, vc, knob, -0.032f, 0, -0.03f, 0.032f, 0.13f, 0.035f, light, 0xFF1B1C1E);          // grip
        Box.cuboid(ps, vc, black, -0.036f, 0.13f, -0.036f, 0.036f, 0.155f, 0.04f, light, 0xFF26282B);    // head
        Box.cuboid(ps, vc, black, right ? -0.04f : 0.032f, 0.02f, -0.02f, right ? -0.032f : 0.04f, 0.1f, 0.03f, light, 0xFF26282B); // thumb rest
        Box.cuboid(ps, vc, red, right ? 0.004f : -0.024f, 0.155f, -0.01f, right ? 0.024f : -0.004f, 0.166f, 0.014f, light, 0xFFD02A1E); // take-over / priority button
        Box.cuboid(ps, vc, black, -0.014f, 0.085f, -0.046f, 0.014f, 0.115f, -0.03f, light, 0xFF0E0F10); // trigger (radio)
        ps.popPose();
        ps.popPose();
    }
}
