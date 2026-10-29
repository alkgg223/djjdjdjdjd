package com.eggsteal.client;

import com.eggsteal.EggSteal;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * OBJ reader for the creature models (written by tools/gen_models.py).
 * Supports {@code v x y z [shade]}, {@code vn}, {@code f} (triangles and quads) and
 * {@code g <colour> [<animation> <pivot x y z> <phase>]}, plus a {@code # float} flag for creatures that hover.
 */
public class ObjModel {
    public static final int NONE = 0, BLINK = 1, WING_L = 2, WING_R = 3, TAIL = 4, EAR_L = 5, EAR_R = 6, SWAY = 7, SPIN = 8, FIN_L = 9, FIN_R = 10,
            LEG_L = 11, LEG_R = 12, ARM_L = 13, ARM_R = 14, HEAD = 15;
    static final String[] ANIMS = {"none", "blink", "wingL", "wingR", "tail", "earL", "earR", "sway", "spin", "finL", "finR", "legL", "legR", "armL", "armR", "head"};
    /** Floats per vertex in {@link Mesh#data}: x, y, z, nx, ny, nz, shade, u, v. */
    public static final int STRIDE = 9;

    /** One coloured, separately animated part. Every polygon is stored as a quad (triangles repeat their last vertex). */
    public static class Mesh {
        public String color;
        public int anim;
        public float px, py, pz, phase;
        /** x, y, z, nx, ny, nz, shade, u, v for 4 vertices per polygon. */
        public float[] data;
        public int quads;
    }

    public final List<Mesh> meshes = new ArrayList<>();
    public boolean floats;
    /** Named colours of voxel models ("# pal <name> <rrggbb>"). */
    public final Map<String, Integer> palette = new HashMap<>();
    public float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
    private static final Map<String, ObjModel> CACHE = new HashMap<>();

    public static ObjModel get(String id) {
        return CACHE.computeIfAbsent(id.indexOf('/') >= 0 ? id : "creature/" + id, ObjModel::load);
    }

    /** id is a path below assets/eggsteal/models/, without ".obj" (e.g. "brainrot/pizzolo"). */
    private static ObjModel load(String id) {
        ObjModel m = new ObjModel();
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(EggSteal.MODID, "models/" + id + ".obj");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(Minecraft.getInstance().getResourceManager().open(rl), StandardCharsets.UTF_8))) {
            List<float[]> v = new ArrayList<>(), n = new ArrayList<>(), tx = new ArrayList<>();
            Mesh cur = null;
            List<Float> buf = new ArrayList<>();
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) {
                    if (line.equals("# float")) m.floats = true;
                    else if (line.startsWith("# pal ")) {
                        String[] q = line.split("\\s+");
                        if (q.length >= 4) m.palette.put(q[2], Integer.parseInt(q[3], 16));
                    }
                    continue;
                }
                String[] p = line.split("\\s+");
                switch (p[0]) {
                    case "v" -> v.add(new float[]{Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]), p.length > 4 ? Float.parseFloat(p[4]) : 1f});
                    case "vn" -> n.add(new float[]{Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])});
                    case "vt" -> tx.add(new float[]{Float.parseFloat(p[1]), Float.parseFloat(p[2])});
                    case "g" -> {
                        finish(m, cur, buf);
                        cur = new Mesh();
                        cur.color = p.length > 1 ? p[1] : "main";
                        if (p.length > 2) {
                            for (int i = 0; i < ANIMS.length; i++) if (ANIMS[i].equals(p[2])) cur.anim = i;
                            if (p.length > 5) { cur.px = Float.parseFloat(p[3]); cur.py = Float.parseFloat(p[4]); cur.pz = Float.parseFloat(p[5]); }
                            if (p.length > 6) cur.phase = Float.parseFloat(p[6]);
                        }
                    }
                    case "f" -> {
                        if (cur == null) { cur = new Mesh(); cur.color = "main"; }
                        int k = p.length - 1;
                        int[] vi = new int[k], ni = new int[k], ti = new int[k];
                        for (int i = 0; i < k; i++) {
                            String[] t = p[i + 1].split("/");
                            vi[i] = Integer.parseInt(t[0]) - 1;
                            ti[i] = t.length >= 2 && !t[1].isEmpty() ? Integer.parseInt(t[1]) - 1 : -1;
                            ni[i] = t.length >= 3 && !t[2].isEmpty() ? Integer.parseInt(t[2]) - 1 : -1;
                        }
                        // quads stay quads; bigger polygons are fanned into triangles
                        int[][] polys = k == 4 ? new int[][]{{0, 1, 2, 3}} : fan(k);
                        for (int[] poly : polys) {
                            for (int c = 0; c < 4; c++) {
                                int idx = poly[Math.min(c, poly.length - 1)];
                                float[] pos = v.get(vi[idx]);
                                float[] nor = ni[idx] >= 0 ? n.get(ni[idx]) : new float[]{0, 1, 0};
                                float[] uv = ti[idx] >= 0 ? tx.get(ti[idx]) : new float[]{0.5f, 0.5f};
                                buf.add(pos[0]); buf.add(pos[1]); buf.add(pos[2]);
                                buf.add(nor[0]); buf.add(nor[1]); buf.add(nor[2]);
                                buf.add(pos[3]);
                                buf.add(uv[0]); buf.add(uv[1]);
                                m.minX = Math.min(m.minX, pos[0]); m.maxX = Math.max(m.maxX, pos[0]);
                                m.minY = Math.min(m.minY, pos[1]); m.maxY = Math.max(m.maxY, pos[1]);
                                m.minZ = Math.min(m.minZ, pos[2]); m.maxZ = Math.max(m.maxZ, pos[2]);
                            }
                        }
                    }
                    default -> { }
                }
            }
            finish(m, cur, buf);
        } catch (Exception ex) {
            System.err.println("[eggsteal] could not load model " + id + ": " + ex);
        }
        return m;
    }

    private static int[][] fan(int k) {
        int[][] out = new int[Math.max(0, k - 2)][];
        for (int i = 1; i < k - 1; i++) out[i - 1] = new int[]{0, i, i + 1};
        return out;
    }

    private static void finish(ObjModel m, Mesh cur, List<Float> buf) {
        if (cur == null || buf.isEmpty()) { buf.clear(); return; }
        cur.data = new float[buf.size()];
        for (int i = 0; i < buf.size(); i++) cur.data[i] = buf.get(i);
        cur.quads = buf.size() / (STRIDE * 4);
        m.meshes.add(cur);
        buf.clear();
    }
}
