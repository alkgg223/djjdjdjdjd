package com.vehicleworks.physics.fluid;

import com.vehicleworks.physics.VoxelSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Coarse buoyancy / drag elements built from a {@link StructureAnalysis}: solid voxels and
 * hull pocket cells are clustered into 2×2×2 groups so a ship of thousands of blocks needs
 * only hundreds of fluid samples per step.
 */
public final class FluidElements {
    public int count;
    public double[] x, y, z, volume, halfHeight;
    /** exposed area per direction: index e*6 + face (−x,+x,−y,+y,−z,+z) */
    public float[] faceArea;
    /** pocket index or −1 for solid material */
    public int[] pocket;
    public double totalSolidVolume, totalPocketVolume;
    public final List<StructureAnalysis.Pocket> pockets;
    public final List<StructureAnalysis.Pocket> gasPockets;

    public FluidElements(StructureAnalysis a, VoxelSet voxelFlags) {
        this.pockets = a.pockets;
        this.gasPockets = a.gasPockets;
        List<double[]> els = new ArrayList<>();
        List<float[]> faces = new ArrayList<>();
        List<Integer> pk = new ArrayList<>();
        // solid clusters
        VoxelSet clusterIdx = new VoxelSet(256);
        voxelFlags.forEach((vx, vy, vz, flags) -> {
            if ((flags & StructureAnalysis.FLAG_SOLID) == 0) return;
            int cx = Math.floorDiv(vx, 2), cy = Math.floorDiv(vy, 2), cz = Math.floorDiv(vz, 2);
            int id = clusterIdx.get(cx, cy, cz, -1);
            if (id < 0) {
                id = els.size();
                clusterIdx.put(cx, cy, cz, id);
                els.add(new double[]{0, 0, 0, 0, Double.MAX_VALUE, -Double.MAX_VALUE});
                faces.add(new float[6]);
                pk.add(-1);
            }
            double[] e = els.get(id);
            e[0] += vx + 0.5; e[1] += vy + 0.5; e[2] += vz + 0.5; e[3] += 1;
            e[4] = Math.min(e[4], vy); e[5] = Math.max(e[5], vy + 1);
            if ((flags & StructureAnalysis.FLAG_NO_BODY_DRAG) == 0) {
                int mask = a.exposedFaces.get(vx, vy, vz, 0);
                float[] f = faces.get(id);
                for (int k = 0; k < 6; k++) if ((mask & (1 << k)) != 0) f[k] += 1f;
            }
        });
        // pocket clusters
        for (int p = 0; p < a.pockets.size(); p++) {
            VoxelSet pIdx = new VoxelSet(64);
            for (long c : a.pockets.get(p).cells) {
                int vx = VoxelSet.unpackX(c), vy = VoxelSet.unpackY(c), vz = VoxelSet.unpackZ(c);
                int cx = Math.floorDiv(vx, 2), cy = Math.floorDiv(vy, 2), cz = Math.floorDiv(vz, 2);
                int id = pIdx.get(cx, cy, cz, -1);
                if (id < 0) {
                    id = els.size();
                    pIdx.put(cx, cy, cz, id);
                    els.add(new double[]{0, 0, 0, 0, Double.MAX_VALUE, -Double.MAX_VALUE});
                    faces.add(new float[6]);
                    pk.add(p);
                }
                double[] e = els.get(id);
                e[0] += vx + 0.5; e[1] += vy + 0.5; e[2] += vz + 0.5; e[3] += 1;
                e[4] = Math.min(e[4], vy); e[5] = Math.max(e[5], vy + 1);
            }
        }
        count = els.size();
        x = new double[count]; y = new double[count]; z = new double[count];
        volume = new double[count]; halfHeight = new double[count];
        faceArea = new float[count * 6];
        pocket = new int[count];
        for (int i = 0; i < count; i++) {
            double[] e = els.get(i);
            x[i] = e[0] / e[3]; y[i] = e[1] / e[3]; z[i] = e[2] / e[3];
            volume[i] = e[3];
            halfHeight[i] = Math.max(0.5, (e[5] - e[4]) * 0.5);
            System.arraycopy(faces.get(i), 0, faceArea, i * 6, 6);
            pocket[i] = pk.get(i);
            if (pocket[i] < 0) totalSolidVolume += e[3];
            else totalPocketVolume += e[3];
        }
    }
}
