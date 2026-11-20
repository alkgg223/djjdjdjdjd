package com.trafficcraft.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.trafficcraft.TrafficCraft;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** A generated vehicle mesh (.tcm), split by part and material, uploaded once into GPU vertex buffers. */
public final class VehicleMesh {
    // material ids (must match tools/vehgen/geom.py)
    public static final int PAINT = 0, SOLID = 1, GLASS = 2, HEAD = 3, HIGH = 4, DRL = 5, TAIL = 6, BRAKE = 7, IND_L = 8, IND_R = 9, REVERSE = 10,
            INTERIOR_LIGHT = 11, PLATE_LIGHT = 12, BEACON_A = 13, BEACON_B = 14, TAXI_SIGN = 15, MARKER = 16, PAINT2 = 17, SCREEN = 18,
            DECO_A = 19, DECO_B = 20, DECO_Y = 21;   // Christmas bulbs (red / green, blinking in turn), pumpkin glow

    /** ARGB of window glass: neutral (slightly warm to cancel the cool reflection texture), ~15 % opaque. */
    public static final int CLEAR_GLASS = (38 << 24) | (255 << 16) | (250 << 8) | 240;

    public static final class Group {
        public final String part;
        public final int mat;
        public final int count;
        public final float[] data; // per vertex: x y z nx ny nz u v (8 floats)
        public final int[] colors;
        public VertexBuffer vbo;

        Group(String part, int mat, int count, float[] data, int[] colors) {
            this.part = part;
            this.mat = mat;
            this.count = count;
            this.data = data;
            this.colors = colors;
        }
    }

    public final Map<String, List<Group>> parts = new LinkedHashMap<>();
    private boolean uploaded;
    public boolean failed;

    private static final Map<String, VehicleMesh> CACHE = new HashMap<>();

    public static VehicleMesh get(String id, int lod) {
        String key = id + (lod > 0 ? "_lod1" : "");
        return CACHE.computeIfAbsent(key, k -> load(TrafficCraft.id("vehicles/" + k + ".tcm")));
    }

    public static void clearCache() {
        for (VehicleMesh m : CACHE.values()) m.close();
        CACHE.clear();
    }

    private static VehicleMesh load(ResourceLocation rl) {
        VehicleMesh m = new VehicleMesh();
        try (InputStream raw = Minecraft.getInstance().getResourceManager().open(rl);
             DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(raw), 1 << 16))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            int groups = in.readInt();
            for (int g = 0; g < groups; g++) {
                String part = in.readUTF();
                int mat = in.readInt();
                int n = in.readInt();
                float[] d = new float[n * 8];
                int[] c = new int[n];
                for (int i = 0; i < n; i++) {
                    int o = i * 8;
                    d[o] = in.readFloat();
                    d[o + 1] = in.readFloat();
                    d[o + 2] = in.readFloat();
                    d[o + 3] = in.readByte() / 127f;
                    d[o + 4] = in.readByte() / 127f;
                    d[o + 5] = in.readByte() / 127f;
                    d[o + 6] = in.readUnsignedShort() / 65535f;
                    d[o + 7] = in.readUnsignedShort() / 65535f;
                    c[i] = in.readInt();
                    // clear, colourless glazing (the generator bakes a tinted glass colour)
                    if (mat == GLASS) c[i] = CLEAR_GLASS;
                }
                m.parts.computeIfAbsent(part, k -> new ArrayList<>()).add(new Group(part, mat, n, d, c));
            }
        } catch (Exception e) {
            TrafficCraft.LOG.error("Could not load vehicle mesh {}", rl, e);
            m.failed = true;
        }
        return m;
    }

    /** Uploads all groups as static VBOs (render thread only). */
    public void ensureUploaded() {
        if (uploaded || failed) return;
        uploaded = true;
        for (List<Group> list : parts.values()) {
            for (Group g : list) {
                if (g.count == 0) continue;
                ByteBufferBuilder bb = new ByteBufferBuilder(g.count * DefaultVertexFormat.NEW_ENTITY.getVertexSize());
                BufferBuilder buf = new BufferBuilder(bb, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);
                float[] d = g.data;
                for (int i = 0; i < g.count; i++) {
                    int o = i * 8;
                    buf.addVertex(d[o], d[o + 1], d[o + 2], g.colors[i], d[o + 6], d[o + 7], OverlayTexture.NO_OVERLAY, 0xF000F0, d[o + 3], d[o + 4], d[o + 5]);
                }
                MeshData md = buf.build();
                if (md != null) {
                    VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    vb.bind();
                    vb.upload(md);
                    VertexBuffer.unbind();
                    g.vbo = vb;
                }
                bb.close();
            }
        }
    }

    public void close() {
        for (List<Group> list : parts.values()) for (Group g : list) if (g.vbo != null) {
            g.vbo.close();
            g.vbo = null;
        }
        uploaded = false;
    }
}
