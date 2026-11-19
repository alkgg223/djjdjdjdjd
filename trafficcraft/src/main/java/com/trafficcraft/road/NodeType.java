package com.trafficcraft.road;

/** Behaviour of a network node. Road kinds are used by vehicles, PED kinds by pedestrians. */
public enum NodeType {
    ROAD(false, 0xFFE0E0E0), SPAWN(false, 0xFF40E060), DESPAWN(false, 0xFFE04040), PARKING(false, 0xFF4080FF), BORDER(false, 0xFFFFC020),
    GAS_STATION(false, 0xFFFF7020), BUS_STOP(false, 0xFF40C0C0), PED(true, 0xFFA0FFA0), PED_SPAWN(true, 0xFF20C040), PED_DEST(true, 0xFFC0FF40);

    public final boolean ped;
    public final int color;

    NodeType(boolean ped, int color) {
        this.ped = ped;
        this.color = color;
    }

    public static NodeType byName(String n) {
        try {
            return valueOf(n);
        } catch (Exception e) {
            return ROAD;
        }
    }
}
