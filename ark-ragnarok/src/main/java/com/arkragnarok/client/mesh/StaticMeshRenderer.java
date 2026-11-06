package com.arkragnarok.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** draws an unanimated {@link ArkMesh} (resource nodes, building pieces) with the current pose */
public final class StaticMeshRenderer {
    private StaticMeshRenderer() {}

    private static final Vector4f P = new Vector4f();
    private static final Vector3f N = new Vector3f();

    public static void draw(PoseStack.Pose pose, ArkMesh mesh, ResourceLocation texture, MultiBufferSource buffers, int light,
                            int overlay, int colour) {
        if (mesh == null) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        int[] T = mesh.tris;
        for (int t = 0; t < T.length; t += 3) {
            emit(vc, m, nm, mesh, T[t], light, overlay, colour);
            emit(vc, m, nm, mesh, T[t + 1], light, overlay, colour);
            emit(vc, m, nm, mesh, T[t + 2], light, overlay, colour);
            emit(vc, m, nm, mesh, T[t + 2], light, overlay, colour);
        }
    }

    private static void emit(VertexConsumer vc, Matrix4f m, Matrix3f nm, ArkMesh mesh, int i, int light, int overlay, int colour) {
        float[] p = mesh.pos, n = mesh.nrm, uv = mesh.uv;
        P.set(p[i * 3], p[i * 3 + 1], p[i * 3 + 2], 1f).mul(m);
        N.set(n[i * 3], n[i * 3 + 1], n[i * 3 + 2]).mul(nm).normalize();
        vc.addVertex(P.x, P.y, P.z, colour, uv[i * 2], uv[i * 2 + 1], overlay, light, N.x, N.y, N.z);
    }
}
