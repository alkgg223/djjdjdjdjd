package com.arkragnarok.client.mesh;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * A skinned triangle mesh (.arkm, little endian): bone names, positions (blocks, GeckoLib model space; v2 stores them as
 * uint16 inside the bounding box), normals (int8), uvs (v2: uint16), 4 bone indices and 4 weights (uint8, sum 255) per
 * vertex, then triangles (v2: uint16 when the mesh has fewer than 65536 vertices).
 */
public final class ArkMesh {
    public final String[] bones;
    public final int vertexCount;
    public final float[] pos;      // 3 per vertex
    public final float[] nrm;      // 3 per vertex
    public final float[] uv;       // 2 per vertex
    public final byte[] boneIdx;   // 4 per vertex
    public final float[] weight;   // 4 per vertex
    public final int[] tris;       // 3 per triangle

    private static final Map<ResourceLocation, ArkMesh> CACHE = new HashMap<>();

    private ArkMesh(ByteBuffer b) {
        byte[] magic = new byte[4];
        b.get(magic);
        if (magic[0] != 'A' || magic[1] != 'R' || magic[2] != 'K' || magic[3] != 'M') throw new IllegalStateException("not an ARKM mesh");
        int version = b.getInt();
        if (version != 1 && version != 2) throw new IllegalStateException("unsupported ARKM version " + version);
        int nb = b.getInt();
        bones = new String[nb];
        for (int i = 0; i < nb; i++) {
            int len = b.getShort() & 0xFFFF;
            byte[] s = new byte[len];
            b.get(s);
            bones[i] = new String(s, StandardCharsets.UTF_8);
        }
        int n = b.getInt();
        vertexCount = n;
        pos = new float[n * 3];
        if (version == 1) {
            for (int i = 0; i < n * 3; i++) pos[i] = b.getFloat();
        } else {
            float[] lo = {b.getFloat(), b.getFloat(), b.getFloat()};
            float[] hi = {b.getFloat(), b.getFloat(), b.getFloat()};
            for (int i = 0; i < n * 3; i++) {
                int c = i % 3;
                pos[i] = lo[c] + (b.getShort() & 0xFFFF) / 65535f * (hi[c] - lo[c]);
            }
        }
        nrm = new float[n * 3];
        for (int i = 0; i < n * 3; i++) nrm[i] = b.get() / 127f;
        uv = new float[n * 2];
        if (version == 1) {
            for (int i = 0; i < n * 2; i++) uv[i] = b.getFloat();
        } else {
            for (int i = 0; i < n * 2; i++) uv[i] = (b.getShort() & 0xFFFF) / 65535f;
        }
        boneIdx = new byte[n * 4];
        b.get(boneIdx);
        weight = new float[n * 4];
        for (int i = 0; i < n * 4; i++) weight[i] = (b.get() & 0xFF) / 255f;
        int nt = b.getInt();
        tris = new int[nt * 3];
        boolean wide = version == 1 || b.get() != 0;
        for (int i = 0; i < nt * 3; i++) tris[i] = wide ? b.getInt() : (b.getShort() & 0xFFFF);
    }

    /** loads (once) from the client resource manager; returns null if the file is missing or broken */
    public static synchronized ArkMesh get(ResourceLocation id) {
        if (CACHE.containsKey(id)) return CACHE.get(id);
        ArkMesh mesh = null;
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    mesh = new ArkMesh(ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN));
                }
            } else {
                org.slf4j.LoggerFactory.getLogger("arkragnarok").error("Missing mesh {}", id);
            }
        } catch (IOException | RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger("arkragnarok").error("Could not load mesh {}", id, ex);
        }
        CACHE.put(id, mesh);
        return mesh;
    }

    public int triangleCount() {
        return tris.length / 3;
    }
}
