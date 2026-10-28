package com.gunsmod.gun;

/** Weapon attachments. Index 0 in {@link GunAttachments} means "none", so stored values are ordinal + 1. */
public enum Attachment {
    RED_DOT("red_dot", Slot.SCOPE, 1.25f, 0.9f, 1.0f, 1.0f, 2.2f),
    HOLO_SIGHT("holo_sight", Slot.SCOPE, 1.3f, 0.85f, 1.0f, 1.0f, 2.35f),
    ACOG("acog", Slot.SCOPE, 2.5f, 0.7f, 1.0f, 1.0f, 2.6f),
    SNIPER_SCOPE("sniper_scope", Slot.SCOPE, 6.0f, 0.35f, 1.0f, 1.0f, 2.6f),
    SUPPRESSOR("suppressor", Slot.MUZZLE, 1.0f, 1.0f, 1.0f, 0.95f, 0f),
    COMPENSATOR("compensator", Slot.MUZZLE, 1.0f, 1.0f, 1.0f, 0.7f, 0f),
    VERTICAL_GRIP("vertical_grip", Slot.GRIP, 1.0f, 0.95f, 0.85f, 0.8f, 0f),
    ANGLED_GRIP("angled_grip", Slot.GRIP, 1.0f, 0.9f, 0.9f, 0.88f, 0f),
    LASER_SIGHT("laser_sight", Slot.LASER, 1.0f, 1.0f, 0.6f, 1.0f, 0f),
    EXTENDED_MAG("extended_mag", Slot.MAG, 1.0f, 1.0f, 1.0f, 1.0f, 0f),
    /** weapon light on the side rail (shares the slot with the laser); lights up the dark */
    FLASHLIGHT("flashlight", Slot.LASER, 1.0f, 1.0f, 0.92f, 1.0f, 0f);

    public enum Slot { SCOPE, MUZZLE, GRIP, LASER, MAG }

    public final String id;
    public final Slot slot;
    public final float zoom;
    public final float adsSpreadMul;
    public final float hipSpreadMul;
    public final float recoilMul;
    /** height of the optical axis above the mount, in model units */
    public final float eyeHeight;

    Attachment(String id, Slot slot, float zoom, float adsSpreadMul, float hipSpreadMul, float recoilMul, float eyeHeight) {
        this.id = id;
        this.slot = slot;
        this.zoom = zoom;
        this.adsSpreadMul = adsSpreadMul;
        this.hipSpreadMul = hipSpreadMul;
        this.recoilMul = recoilMul;
        this.eyeHeight = eyeHeight;
    }

    public boolean overlayScope() {
        return this == SNIPER_SCOPE;
    }

    public static Attachment fromIndex(int index) {
        Attachment[] v = values();
        return index >= 1 && index <= v.length ? v[index - 1] : null;
    }
}
