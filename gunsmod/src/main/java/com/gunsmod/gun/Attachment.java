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
    FLASHLIGHT("flashlight", Slot.LASER, 1.0f, 1.0f, 0.92f, 1.0f, 0f),
    /** folding bipod: rested (lying down, or crouched with a block in front) the gun barely moves */
    BIPOD("bipod", Slot.GRIP, 1.0f, 0.92f, 1.0f, 0.9f, 0f),
    /** night vision scope (3.5x): the dark turns into a bright green and white picture */
    NV_SCOPE("nv_scope", Slot.SCOPE, 3.5f, 0.55f, 1.0f, 1.0f, 2.6f),
    /** thermal scope (4x): white-hot picture, living things outlined */
    THERMAL_SCOPE("thermal_scope", Slot.SCOPE, 4.0f, 0.5f, 1.0f, 1.0f, 2.6f),
    /** variable power scope: 2.5x to 10x, the mouse wheel or the zoom key changes it */
    VARIABLE_SCOPE("variable_scope", Slot.SCOPE, 4.0f, 0.3f, 1.0f, 1.0f, 2.6f),
    /** muzzle brake: throws the gas sideways, much less kick, a bit more spread from the blast */
    MUZZLE_BRAKE("muzzle_brake", Slot.MUZZLE, 1.0f, 1.05f, 1.0f, 0.58f, 0f);

    /** the variable scope's steps and the one chosen (client side: it only changes the view) */
    public static final float[] ZOOM_STEPS = {2.5f, 4f, 6f, 8f, 10f};
    public static int zoomStep = 1;

    public static float variableZoom() {
        return ZOOM_STEPS[Math.floorMod(zoomStep, ZOOM_STEPS.length)];
    }

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

    /** aiming through it shows the sight picture (the gun model would block the view through the optic) */
    public boolean overlayScope() {
        return slot == Slot.SCOPE;
    }

    public static Attachment fromIndex(int index) {
        Attachment[] v = values();
        return index >= 1 && index <= v.length ? v[index - 1] : null;
    }
}
