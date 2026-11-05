package com.gunsmod.client.render;

import com.gunsmod.entity.DroneEntity;
import com.gunsmod.item.DroneItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** FPV quad (four spinning props) and the Shahed-136 (pusher prop), banking and pitching with their flight. */
public class DroneRenderer extends EntityRenderer<DroneEntity> {
    /** model units: the FPV motor centres (props drawn on each), the Shahed prop shaft, the attitude pivots */
    private static final float[][] FPV_ROTORS = {{2.5f, 7f, 2.5f}, {13.5f, 7f, 2.5f}, {2.5f, 7f, 13.5f}, {13.5f, 7f, 13.5f}};
    private static final float[] SHAHED_PROP = {8f, 6.4f, 37.6f};
    // 3.0 (tools/drones_mesh.py)
    private static final float[][] RECON_ROTORS = {{2.8f, 5.6f, 3.6f}, {13.2f, 5.6f, 3.6f}, {2.8f, 5.6f, 12.6f}, {13.2f, 5.6f, 12.6f}};
    private static final float[][] BOMBER_ROTORS = new float[6][];
    private static final float[][] BOMB_SLOTS = {{5.4f, 8f}, {7.1f, 8f}, {8.9f, 8f}, {10.6f, 8f}};
    private static final float[] SURVEIL_PROP = {8f, 6f, -11.6f}, LANCET_PROP = {8f, 4f, 12.9f};

    static {
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(30 + 60 * k);
            BOMBER_ROTORS[k] = new float[]{(float) (8 + 10.5 * Math.sin(a)), 8.3f, (float) (8 - 10.5 * Math.cos(a))};
        }
    }

    public static final String[] PARTS = {"fpv/body", "fpv/prop", "shahed/body", "shahed/prop", "recon/body", "recon/prop", "bomber/body", "bomber/prop",
            "bomber/bomb", "surveil/body", "surveil/prop", "lancet/body", "lancet/prop"};
    /** the Shahed's paint (light grey) */
    private static final float SR = 0.66f, SG = 0.67f, SB = 0.65f;

    public DroneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.4f;
    }

    @Override
    public void render(DroneEntity d, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        DroneEntity.Kind k = d.kind();
        float s = scale(k);
        float py = k.quad() ? 0.35f : 0.4f;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, d.yRotO, d.getYRot())));
        pose.translate(0, py * s, 0);
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, d.prevVisPitch, d.visPitch)));
        pose.mulPose(Axis.ZP.rotationDegrees(-Mth.lerp(pt, d.prevVisRoll, d.visRoll)));
        pose.translate(0, -py * s, 0);
        pose.scale(s, s, s);
        pose.translate(-0.5f, 0f, -0.5f);
        renderModel(k, pose, buf, light, Mth.lerp(pt, d.rotorO, d.rotor), d.bombs());
        pose.popPose();
        super.render(d, yaw, pt, pose, buf, light);
    }

    private static float scale(DroneEntity.Kind k) {
        return switch (k) {
            case FPV -> 0.8f;
            case RECON -> 0.9f;
            default -> 1.0f;
        };
    }

    /** the old entry point (FPV or Shahed) */
    public static void renderModel(boolean fpv, PoseStack pose, MultiBufferSource buf, int light, float rotor) {
        renderModel(fpv ? DroneEntity.Kind.FPV : DroneEntity.Kind.SHAHED, pose, buf, light, rotor, 0);
    }

    private static void rotors(String part, float[][] at, PoseStack pose, MultiBufferSource buf, int light, float rotor) {
        for (int i = 0; i < at.length; i++) {
            float[] r = at[i];
            pose.pushPose();
            pose.translate(r[0] / 16f, 0, r[2] / 16f);
            // neighbouring props turn opposite ways, as on a real multirotor
            pose.mulPose(Axis.YP.rotationDegrees(i % 2 == 0 ^ (at.length == 4 && i >= 2) ? rotor : -rotor));
            pose.translate(-0.5f, 0, -0.5f);
            BakedRender.entity(part, pose, buf, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f);
            pose.popPose();
        }
    }

    private static void pusher(String part, float[] c, PoseStack pose, MultiBufferSource buf, int light, float rotor, float r, float g, float b) {
        pose.pushPose();
        pose.translate(c[0] / 16f, c[1] / 16f, c[2] / 16f);
        pose.mulPose(Axis.ZP.rotationDegrees(rotor));
        pose.translate(-c[0] / 16f, -c[1] / 16f, -c[2] / 16f);
        BakedRender.entity(part, pose, buf, light, OverlayTexture.NO_OVERLAY, r, g, b);
        pose.popPose();
    }

    /** the model in model space (1/16 block units, centred on 8, 0, 8), props turned by {@code rotor} degrees */
    public static void renderModel(DroneEntity.Kind k, PoseStack pose, MultiBufferSource buf, int light, float rotor, int bombs) {
        int ov = OverlayTexture.NO_OVERLAY;
        switch (k) {
            case FPV -> {
                BakedRender.entity("entity/drone/fpv/body", pose, buf, light, ov, 1f, 1f, 1f);
                for (int i = 0; i < FPV_ROTORS.length; i++) {
                    float[] r = FPV_ROTORS[i];
                    pose.pushPose();
                    pose.translate(r[0] / 16f, 0, r[2] / 16f);
                    // diagonal pairs turn opposite ways, as on a real quad
                    pose.mulPose(Axis.YP.rotationDegrees((i == 0 || i == 3) ? rotor : -rotor));
                    pose.translate(-0.5f, 0, -0.5f);
                    BakedRender.entity("entity/drone/fpv/prop", pose, buf, light, ov, 1f, 1f, 1f);
                    pose.popPose();
                }
            }
            case SHAHED -> {
                BakedRender.entity("entity/drone/shahed/body", pose, buf, light, ov, SR, SG, SB);
                pusher("entity/drone/shahed/prop", SHAHED_PROP, pose, buf, light, rotor, 1f, 1f, 1f);
            }
            case RECON -> {
                BakedRender.entity("entity/drone/recon/body", pose, buf, light, ov, 1f, 1f, 1f);
                rotors("entity/drone/recon/prop", RECON_ROTORS, pose, buf, light, rotor);
            }
            case BOMBER -> {
                BakedRender.entity("entity/drone/bomber/body", pose, buf, light, ov, 1f, 1f, 1f);
                rotors("entity/drone/bomber/prop", BOMBER_ROTORS, pose, buf, light, rotor);
                for (int i = 0; i < Math.min(bombs, BOMB_SLOTS.length); i++) {
                    pose.pushPose();
                    pose.translate((BOMB_SLOTS[i][0] - 8f) / 16f, 0, (BOMB_SLOTS[i][1] - 8f) / 16f);
                    BakedRender.entity("entity/drone/bomber/bomb", pose, buf, light, ov, 1f, 1f, 1f);
                    pose.popPose();
                }
            }
            case SURVEIL -> {
                BakedRender.entity("entity/drone/surveil/body", pose, buf, light, ov, 1f, 1f, 1f);
                pusher("entity/drone/surveil/prop", SURVEIL_PROP, pose, buf, light, rotor, 1f, 1f, 1f);
            }
            case LANCET -> {
                BakedRender.entity("entity/drone/lancet/body", pose, buf, light, ov, 1f, 1f, 1f);
                pusher("entity/drone/lancet/prop", LANCET_PROP, pose, buf, light, rotor, 1f, 1f, 1f);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(DroneEntity d) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    /** the drone items show the model */
    public static class Item extends BlockEntityWithoutLevelRenderer {
        private static Item instance;

        public Item() {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        }

        public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (instance == null) instance = new Item();
                return instance;
            }
        };

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
            if (!(stack.getItem() instanceof DroneItem di)) return;
            DroneEntity.Kind k = di.kind;
            boolean gui = ctx == ItemDisplayContext.GUI;
            pose.pushPose();
            pose.translate(0.5f, k.quad() ? 0.1f : 0.3f, 0.5f);
            float s = switch (k) {
                case FPV -> gui ? 0.85f : 0.6f;
                case RECON -> gui ? 0.95f : 0.65f;
                case BOMBER -> gui ? 0.55f : 0.42f;
                case SURVEIL -> gui ? 0.3f : 0.3f;
                case LANCET -> gui ? 0.5f : 0.45f;
                case SHAHED -> gui ? 0.3f : 0.32f;
            };
            pose.scale(s, s, s);
            pose.translate(-0.5f, 0f, -0.5f);
            renderModel(k, pose, buf, light, 15f, DroneEntity.BOMBS);
            pose.popPose();
        }
    }
}
