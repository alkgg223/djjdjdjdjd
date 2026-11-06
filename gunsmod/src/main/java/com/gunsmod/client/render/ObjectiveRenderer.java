package com.gunsmod.client.render;

import com.gunsmod.entity.BotTeam;
import com.gunsmod.game.ObjectiveBlock;
import com.gunsmod.game.ObjectiveBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * The objectives: the capture point's flag climbs its mast as a side takes it and flies that side's colour; the
 * capture-the-flag stand shows its flag while it is home; a target darkens as it is shot up and burns when it is gone.
 * Close up, a label says what it is and how it stands.
 */
public class ObjectiveRenderer implements BlockEntityRenderer<ObjectiveBlockEntity> {
    public static final String[] PARTS = {"pole", "flag", "stand", "target", "destination", "control"};
    private static final float MAST_TOP = 64f;

    public ObjectiveRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    private static float[] rgb(BotTeam t) {
        if (t == null) return new float[]{0.92f, 0.92f, 0.9f};
        return new float[]{((t.color >> 16) & 0xFF) / 255f, ((t.color >> 8) & 0xFF) / 255f, (t.color & 0xFF) / 255f};
    }

    private static void part(String n, PoseStack pose, MultiBufferSource buf, int light, float r, float g, float b) {
        BakedRender.entity("entity/objective/" + n, pose, buf, light, OverlayTexture.NO_OVERLAY, r, g, b);
    }

    /** the flag at a height (model units), waving */
    private static void flag(PoseStack pose, MultiBufferSource buf, int light, float y, float t, float[] c) {
        pose.pushPose();
        pose.translate(0.5f, y / 16f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(t * 0.07f) * 14f + Mth.sin(t * 0.19f) * 4f));
        pose.translate(-0.5f, 0, -0.5f);
        part("flag", pose, buf, light, c[0], c[1], c[2]);
        pose.popPose();
    }

    public static void renderModel(ObjectiveBlock.Kind kind, PoseStack pose, MultiBufferSource buf, int light, float t, BotTeam owner, float balance,
                                   boolean flagHome, float hp) {
        switch (kind) {
            case POINT -> {
                part("pole", pose, buf, light, 1f, 1f, 1f);
                BotTeam side = balance > 0.001f ? BotTeam.GREEN : balance < -0.001f ? BotTeam.RED : null;
                float[] c = rgb(side);
                float y = 12f + Math.abs(balance) * (MAST_TOP - 13f);
                flag(pose, buf, light, y, t, c);
            }
            case FLAG -> {
                float[] c = rgb(owner);
                part("stand", pose, buf, light, c[0] * 0.5f + 0.5f, c[1] * 0.5f + 0.5f, c[2] * 0.5f + 0.5f);
                if (flagHome) flag(pose, buf, light, 35f, t, c);
            }
            case TARGET -> {
                float k = hp <= 0 ? 0.22f : 0.55f + 0.45f * hp / ObjectiveBlockEntity.TARGET_HP;
                part("target", pose, buf, light, k, k, k);
            }
            case DESTINATION -> part("destination", pose, buf, light, 1f, 1f, 1f);
            case CONTROL -> part("control", pose, buf, light, 1f, 1f, 1f);
        }
    }

    @Override
    public void render(ObjectiveBlockEntity be, float pt, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        ObjectiveBlock.Kind kind = be.kind();
        float base = 180f - be.getBlockState().getValue(HorizontalDirectionalBlock.FACING).toYRot();
        float t = (be.getLevel() == null ? 0 : be.getLevel().getGameTime()) + pt;
        pose.pushPose();
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(base));
        pose.translate(-0.5f, 0f, -0.5f);
        renderModel(kind, pose, buf, light, t, be.owner(), be.balance(), be.flagHome(), be.hp());
        pose.popPose();
        if (kind == ObjectiveBlock.Kind.TARGET && be.destroyed() && be.getLevel() != null && be.getLevel().random.nextInt(4) == 0) {
            var p = be.getBlockPos();
            be.getLevel().addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 0, 0.06, 0);
        }
        // the label
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (cam.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > 32 * 32) return;
        Component l1 = switch (kind) {
            case POINT -> Component.translatable("gunsmod.obj.label.point", String.valueOf(be.label()));
            default -> Component.translatable(be.getBlockState().getBlock().getDescriptionId());
        };
        Component l2 = be.status();
        if (kind == ObjectiveBlock.Kind.TARGET && be.charge() >= 0) l2 = Component.translatable("gunsmod.hud.charge", String.valueOf(be.label()), be.charge() / 20);
        float y = switch (kind) {
            case POINT -> 4.6f;
            case FLAG -> 2.8f;
            case TARGET -> 3.6f;
            case DESTINATION -> 1.8f;
            case CONTROL -> 1.6f;
        };
        Font font = mc.font;
        pose.pushPose();
        pose.translate(0.5f, y, 0.5f);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.028f, -0.028f, 0.028f);
        BotTeam o = be.owner();
        int col = o == null ? 0xFFE8E8E8 : o == BotTeam.RED ? 0xFFFF6050 : 0xFF60FF70;
        font.drawInBatch(l1, -font.width(l1) / 2f, -11, col, false, pose.last().pose(), buf, Font.DisplayMode.NORMAL, 0x70000000, LightTexture.FULL_BRIGHT);
        font.drawInBatch(l2, -font.width(l2) / 2f, 0, 0xFFFFFFFF, false, pose.last().pose(), buf, Font.DisplayMode.NORMAL, 0x70000000, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ObjectiveBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(ObjectiveBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(2, 5, 2);
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    /** the objective items show their model */
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
            if (!(stack.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof ObjectiveBlock b)) return;
            boolean tall = b.kind == ObjectiveBlock.Kind.POINT || b.kind == ObjectiveBlock.Kind.FLAG || b.kind == ObjectiveBlock.Kind.TARGET;
            pose.pushPose();
            pose.translate(0.5f, 0.05f, 0.5f);
            float s = ctx == ItemDisplayContext.GUI ? (tall ? 0.3f : 0.7f) : tall ? 0.3f : 0.6f;
            pose.scale(s, s, s);
            pose.translate(-0.5f, 0f, -0.5f);
            renderModel(b.kind, pose, buf, light, 0f, b.kind == ObjectiveBlock.Kind.FLAG ? BotTeam.GREEN : null, b.kind == ObjectiveBlock.Kind.POINT ? 1f : 0f, true,
                    ObjectiveBlockEntity.TARGET_HP);
            pose.popPose();
        }
    }
}
