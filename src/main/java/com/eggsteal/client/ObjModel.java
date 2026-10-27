package com.eggsteal.client;

import com.eggsteal.EggSteal;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Minimal OBJ reader: v, vn, g, f. Keeps per-vertex normals so the models render perfectly smooth. */
public class ObjModel {
    /** group name -> triangles; each triangle is 9 position floats then 9 normal floats. */
    public final Map<String, List<float[]>> groups = new LinkedHashMap<>();
    private static final Map<String, ObjModel> CACHE = new HashMap<>();

    public static ObjModel get(String id) {
        return CACHE.computeIfAbsent(id, ObjModel::load);
    }

    private static ObjModel load(String id) {
        ObjModel m = new ObjModel();
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(EggSteal.MODID, "models/creature/" + id + ".obj");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(Minecraft.getInstance().getResourceManager().open(rl), StandardCharsets.UTF_8))) {
            List<float[]> v = new ArrayList<>(), n = new ArrayList<>();
            String g = "main", line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\s+");
                switch (p[0]) {
                    case "v" -> v.add(new float[]{Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])});
                    case "vn" -> n.add(new float[]{Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])});
                    case "g" -> g = p.length > 1 ? p[1] : "main";
                    case "f" -> {
                        int k = p.length - 1;
                        int[] vi = new int[k], ni = new int[k];
                        for (int i = 0; i < k; i++) {
                            String[] t = p[i + 1].split("/");
                            vi[i] = Integer.parseInt(t[0]) - 1;
                            ni[i] = t.length >= 3 && !t[2].isEmpty() ? Integer.parseInt(t[2]) - 1 : -1;
                        }
                        for (int i = 1; i < k - 1; i++) {
                            int[] ids = {0, i, i + 1};
                            float[] tri = new float[18];
                            for (int c = 0; c < 3; c++) {
                                float[] pos = v.get(vi[ids[c]]);
                                float[] nor = ni[ids[c]] >= 0 ? n.get(ni[ids[c]]) : new float[]{0, 1, 0};
                                System.arraycopy(pos, 0, tri, c * 3, 3);
                                System.arraycopy(nor, 0, tri, 9 + c * 3, 3);
                            }
                            m.groups.computeIfAbsent(g, x -> new ArrayList<>()).add(tri);
                        }
                    }
                    default -> { }
                }
            }
        } catch (Exception ex) {
            System.err.println("[eggsteal] could not load model " + id + ": " + ex);
        }
        return m;
    }
}
