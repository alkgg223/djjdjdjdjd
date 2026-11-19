package com.trafficcraft.road;

/** dir: +1 = travels from node A to node B, -1 = B to A. type: NORMAL, LEFT_ONLY, RIGHT_ONLY, BUS, PARKING. */
public record Lane(int dir, int type) {
    public static final int NORMAL = 0, LEFT_ONLY = 1, RIGHT_ONLY = 2, BUS = 3, PARKING = 4;
    public static final String[] TYPE_NAMES = {"normal", "left_only", "right_only", "bus", "parking"};
}
