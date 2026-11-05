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
    public static final String[] PARTS = {"fpv/body", "fpv/prop", "shahed/body", "shahed/prop"};
    /** the Shahed's paint (light grey) */
    private static final float SR = 0.66f, SG = 0.67f, SB = 0.65f;

    public DroneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.4f;
    }

    @Override
    public void render(DroneEntity d, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        boolean fpv = d.fpv();
        float s = fpv ? 0.8f : 1.0f;
        float py = fpv ? 0.35f : 0.4f;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, d.yRotO, d.getYRot())));
        pose.translate(0, py * s, 0);
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, d.prevVisPitch, d.visPitch)));
        pose.mulPose(Axis.ZP.rotationDegrees(-Mth.lerp(pt, d.prevVisRoll, d.visRoll)));
        pose.translate(0, -py * s, 0);
        pose.scale(s, s, s);
        pose.translate(-0.5f, 0f, -0.5f);
        renderModel(fpv, pose, buf, light, Mth.lerp(pt, d.rotorO, d.rotor));
        pose.popPose();
        super.render(d, yaw, pt, pose, buf, light);
    }

    /** the model in model space (1/16 block units, centred on 8, 0, 8), props turned by {@code rotor} degrees */
    public static void renderModel(boolean fpv, PoseStack pose, MultiBufferSource buf, int light, float rotor) {
        int ov = OverlayTexture.NO_OVERLAY;
        if (fpv) {
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
            return;
        }
        BakedRender.entity("entity/drone/shahed/body", pose, buf, light, ov, SR, SG, SB);
        pose.pushPose();
        pose.translate(SHAHED_PROP[0] / 16f, SHAHED_PROP[1] / 16f, SHAHED_PROP[2] / 16f);
        pose.mulPose(Axis.ZP.rotationDegrees(rotor));
        pose.translate(-SHAHED_PROP[0] / 16f, -SHAHED_PROP[1] / 16f, -SHAHED_PROP[2] / 16f);
        BakedRender.entity("entity/drone/shahed/prop", pose, buf, light, ov, 1f, 1f, 1f);
        pose.popPose();
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
            boolean fpv = di.kind == DroneEntity.Kind.FPV;
            boolean gui = ctx == ItemDisplayContext.GUI;
            pose.pushPose();
            pose.translate(0.5f, fpv ? 0.1f : 0.3f, 0.5f);
            float s = fpv ? (gui ? 0.85f : 0.6f) : (gui ? 0.3f : 0.32f);
            pose.scale(s, s, s);
            pose.translate(-0.5f, 0f, -0.5f);
            renderModel(fpv, pose, buf, light, 15f);
            pose.popPose();
        }
    }
}
