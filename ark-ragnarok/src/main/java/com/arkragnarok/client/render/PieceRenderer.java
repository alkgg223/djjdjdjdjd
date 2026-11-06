package com.arkragnarok.client.render;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.building.DoorState;
import com.arkragnarok.building.PieceBlock;
import com.arkragnarok.building.PieceBlockEntity;
import com.arkragnarok.building.Tier;
import com.arkragnarok.client.mesh.ArkMesh;
import com.arkragnarok.client.mesh.StaticMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/** the door hanging in a doorframe: swings 95 degrees inwards on its hinges with an eased motion */
public class PieceRenderer implements BlockEntityRenderer<PieceBlockEntity> {
    public PieceRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(PieceBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Tier t = be.doorTier();
        BlockState s = be.getBlockState();
        if (t == null || !(s.getBlock() instanceof PieceBlock) || s.getValue(PieceBlock.DOOR) == DoorState.NONE) return;
        Direction f = s.getValue(PieceBlock.FACING);
        float k = Mth.lerp(partialTick, be.swingO, be.swing);
        float eased = k * k * (3 - 2 * k);
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180f - f.toYRot()));
        // hinge axis (model space, px): x = -7.8, z = +5.8 - swing towards the inside (+z)
        pose.translate(-7.8 / 16.0, 0, 5.8 / 16.0);
        pose.mulPose(Axis.YP.rotationDegrees(-95f * eased));
        pose.translate(7.8 / 16.0, 0, -5.8 / 16.0);
        ArkMesh mesh = ArkMesh.get(ArkRagnarok.id("arkmesh/door_" + t.id + ".arkm"));
        StaticMeshRenderer.draw(pose.last(), mesh, ArkRagnarok.id("textures/entity/door/" + t.id + ".png"), buffers, light, overlay, 0xFFFFFFFF);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(PieceBlockEntity be) {
        return true;
    }
}
