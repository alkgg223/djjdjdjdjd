package com.vehicleworks.vehicle;

/**
 * Click areas of the centre pedestal and the FCU, shared by the renderer (client) and the
 * server (a player standing next to the pedestal right-clicks it: the hit point picks the control).
 * Coordinates are block-local (block centre = 0, floor = 0), for a block facing NORTH (front = -z).
 */
public final class CockpitLayout {
    private CockpitLayout() {}

    // ---------------------------------------------------------------- centre pedestal
    public static final int P_THRUST_UP = 0, P_THRUST_DOWN = 1, P_SPEEDBRAKE = 2, P_FLAPS = 3, P_ENG_MASTER = 4, P_ENG_START = 5,
            P_GEAR = 6, P_PARKING = 7, P_MCDU1 = 8, P_MCDU2 = 9, P_COUNT = 10;
    /** top surface height of the pedestal (the lever bases) */
    public static final float PED_TOP = 0.62f;
    /** {control, x0, z0, x1, z1} on the top surface */
    public static final float[][] PEDESTAL = {
            {P_MCDU1, -0.33f, -0.5f, -0.01f, -0.22f},
            {P_MCDU2, 0.01f, -0.5f, 0.33f, -0.22f},
            {P_THRUST_UP, -0.14f, -0.22f, 0.14f, -0.06f},
            {P_THRUST_DOWN, -0.14f, -0.06f, 0.14f, 0.1f},
            {P_SPEEDBRAKE, -0.33f, -0.22f, -0.14f, 0.1f},
            {P_FLAPS, 0.14f, -0.22f, 0.33f, 0.1f},
            {P_ENG_MASTER, -0.33f, 0.1f, 0f, 0.3f},
            {P_ENG_START, 0f, 0.1f, 0.33f, 0.3f},
            {P_PARKING, -0.33f, 0.3f, 0f, 0.5f},
            {P_GEAR, 0f, 0.3f, 0.33f, 0.5f},
    };

    /** control at a top-surface point (block-local, already rotated to the NORTH frame), or -1 */
    public static int pedestalAt(double x, double z) {
        for (float[] r : PEDESTAL) if (x >= r[1] && x <= r[3] && z >= r[2] && z <= r[4]) return (int) r[0];
        return -1;
    }

    public static final double[] THRUST_DETENTS = {0, 0.55, 0.85, 1.0};   // IDLE, CL, FLX / MCT, TOGA
    public static final double[] FLAP_DETENTS = {0, 0.25, 0.5, 0.75, 1.0}; // 0, 1, 2, 3, FULL

    // ---------------------------------------------------------------- FCU (glareshield)
    public static final int F_SPD_DN = 0, F_SPD_UP = 1, F_HDG_DN = 2, F_HDG_UP = 3, F_ALT_DN = 4, F_ALT_UP = 5, F_AP = 6, F_ATHR = 7, F_COUNT = 8;
    /** front face plane of the FCU (block-local z) and its height range */
    public static final float FCU_FACE_Z = -0.28f, FCU_Y0 = 0.0f, FCU_Y1 = 0.15f; // a low glareshield strip: the pilots look over it through the windshield
    /** {control, x0, x1} along the front face */
    public static final float[][] FCU = {
            {F_SPD_DN, -1.4f, -1.1f}, {F_SPD_UP, -1.1f, -0.8f},
            {F_HDG_DN, -0.75f, -0.45f}, {F_HDG_UP, -0.45f, -0.15f},
            {F_AP, -0.1f, 0.1f}, {F_ATHR, 0.1f, 0.3f},
            {F_ALT_DN, 0.35f, 0.7f}, {F_ALT_UP, 0.7f, 1.05f},
    };

    public static int fcuAt(double x, double y) {
        if (y < FCU_Y0 || y > FCU_Y1) return -1;
        for (float[] r : FCU) if (x >= r[1] && x <= r[2]) return (int) r[0];
        return -1;
    }

    /** rotate a block-local offset (relative to the block centre) into the NORTH frame of a block facing f */
    public static double[] toNorth(net.minecraft.core.Direction f, double rx, double rz) {
        return switch (f) {
            case SOUTH -> new double[]{-rx, -rz};
            case EAST -> new double[]{rz, -rx};
            case WEST -> new double[]{-rz, rx};
            default -> new double[]{rx, rz};
        };
    }
}
