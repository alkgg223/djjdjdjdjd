package com.arkragnarok.client.mesh;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Draws an {@link ArkMesh} deformed by the current (animated) GeckoLib bones: linear blend skinning on the CPU.
 * Bone matrices are built exactly like GeckoLib's RenderUtil.prepMatrixForBone, relative to the model root, so a
 * vertex authored in the rest pose follows its bones wherever the animation puts them.
 */
public final class SkinnedMeshRenderer {
    private SkinnedMeshRenderer() {}

    private static final Map<BakedGeoModel, Map<ArkMesh, GeoBone[]>> BONE_LOOKUP = new IdentityHashMap<>();
    private static float[] tPos = new float[0];
    private static float[] tNrm = new float[0];
    private static float[] boneM = new float[0];

    private static GeoBone[] bonesFor(BakedGeoModel model, ArkMesh mesh) {
        return BONE_LOOKUP.computeIfAbsent(model, k -> new IdentityHashMap<>()).computeIfAbsent(mesh, m -> {
            GeoBone[] out = new GeoBone[m.bones.length];
            for (int i = 0; i < out.length; i++) out[i] = model.getBone(m.bones[i]).orElse(null);
            return out;
        });
    }

    /** bone matrix relative to the model root (same order as GeckoLib: position, pivot, rotate ZYX, scale, un-pivot) */
    private static Matrix4f boneMatrix(GeoBone bone, Map<GeoBone, Matrix4f> memo) {
        Matrix4f m = memo.get(bone);
        if (m != null) return m;
        Matrix4f parent = bone.getParent() != null ? boneMatrix(bone.getParent(), memo) : new Matrix4f();
        m = new Matrix4f(parent);
        m.translate(-bone.getPosX() / 16f, bone.getPosY() / 16f, bone.getPosZ() / 16f);
        float px = bone.getPivotX() / 16f, py = bone.getPivotY() / 16f, pz = bone.getPivotZ() / 16f;
        m.translate(px, py, pz);
        if (bone.getRotZ() != 0 || bone.getRotY() != 0 || bone.getRotX() != 0) m.rotateZYX(bone.getRotZ(), bone.getRotY(), bone.getRotX());
        m.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
        m.translate(-px, -py, -pz);
        memo.put(bone, m);
        return m;
    }

    public static void draw(Matrix4f base, BakedGeoModel model, ArkMesh mesh, ResourceLocation texture, MultiBufferSource buffers,
                            int light, int overlay, int colour) {
        if (mesh == null) return;
        GeoBone[] bones = bonesFor(model, mesh);
        int nb = bones.length;
        if (boneM.length < nb * 12) boneM = new float[nb * 12];
        Map<GeoBone, Matrix4f> memo = new IdentityHashMap<>();
        Matrix4f tmp = new Matrix4f();
        for (int i = 0; i < nb; i++) {
            Matrix4f m = bones[i] != null ? boneMatrix(bones[i], memo) : new Matrix4f();
            tmp.set(base).mul(m);   // final = entity/camera transform * bone skinning matrix
            int o = i * 12;
            boneM[o] = tmp.m00(); boneM[o + 1] = tmp.m10(); boneM[o + 2] = tmp.m20(); boneM[o + 3] = tmp.m30();
            boneM[o + 4] = tmp.m01(); boneM[o + 5] = tmp.m11(); boneM[o + 6] = tmp.m21(); boneM[o + 7] = tmp.m31();
            boneM[o + 8] = tmp.m02(); boneM[o + 9] = tmp.m12(); boneM[o + 10] = tmp.m22(); boneM[o + 11] = tmp.m32();
        }
        // normals: the camera rotation part of the base matrix is already inside boneM (rotation*scale); renormalise below
        int n = mesh.vertexCount;
        if (tPos.length < n * 3) {
            tPos = new float[n * 3];
            tNrm = new float[n * 3];
        }
        float[] P = mesh.pos, N = mesh.nrm, W = mesh.weight;
        byte[] B = mesh.boneIdx;
        for (int v = 0; v < n; v++) {
            float x = P[v * 3], y = P[v * 3 + 1], z = P[v * 3 + 2];
            float nx = N[v * 3], ny = N[v * 3 + 1], nz = N[v * 3 + 2];
            float ox = 0, oy = 0, oz = 0, qx = 0, qy = 0, qz = 0;
            for (int k = 0; k < 4; k++) {
                float w = W[v * 4 + k];
                if (w <= 0) continue;
                int o = (B[v * 4 + k] & 0xFF) * 12;
                if (o >= nb * 12) continue;
                ox += w * (boneM[o] * x + boneM[o + 1] * y + boneM[o + 2] * z + boneM[o + 3]);
                oy += w * (boneM[o + 4] * x + boneM[o + 5] * y + boneM[o + 6] * z + boneM[o + 7]);
                oz += w * (boneM[o + 8] * x + boneM[o + 9] * y + boneM[o + 10] * z + boneM[o + 11]);
                qx += w * (boneM[o] * nx + boneM[o + 1] * ny + boneM[o + 2] * nz);
                qy += w * (boneM[o + 4] * nx + boneM[o + 5] * ny + boneM[o + 6] * nz);
                qz += w * (boneM[o + 8] * nx + boneM[o + 9] * ny + boneM[o + 10] * nz);
            }
            float len = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
            if (len > 1e-6f) {
                qx /= len;
                qy /= len;
                qz /= len;
            }
            tPos[v * 3] = ox;
            tPos[v * 3 + 1] = oy;
            tPos[v * 3 + 2] = oz;
            tNrm[v * 3] = qx;
            tNrm[v * 3 + 1] = qy;
            tNrm[v * 3 + 2] = qz;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(JpegTextures.ensure(texture)));
        float[] UV = mesh.uv;
        int[] T = mesh.tris;
        for (int t = 0; t < T.length; t += 3) {
            // the entity render types draw quads: a triangle is a quad whose last corner repeats the third one
            emit(vc, T[t], UV, colour, overlay, light);
            emit(vc, T[t + 1], UV, colour, overlay, light);
            emit(vc, T[t + 2], UV, colour, overlay, light);
            emit(vc, T[t + 2], UV, colour, overlay, light);
        }
    }

    private static void emit(VertexConsumer vc, int i, float[] uv, int colour, int overlay, int light) {
        vc.addVertex(tPos[i * 3], tPos[i * 3 + 1], tPos[i * 3 + 2], colour, uv[i * 2], uv[i * 2 + 1], overlay, light,
                tNrm[i * 3], tNrm[i * 3 + 1], tNrm[i * 3 + 2]);
    }

    /** forget cached bone lookups (models are re-baked on resource reload) */
    public static void clearCache() {
        BONE_LOOKUP.clear();
    }

    @SuppressWarnings("unused")
    private static Matrix3f normalOf(Matrix4f m) {
        return m.normal(new Matrix3f());
    }
}
