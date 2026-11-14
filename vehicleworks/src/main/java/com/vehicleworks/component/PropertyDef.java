package com.vehicleworks.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * Definition of one configurable component property. Values are stored in a CompoundTag
 * under {@link #key}; the server validates every incoming value against these bounds.
 */
public final class PropertyDef {
    public enum Kind { NUMBER, INT, BOOL, ENUM }

    public final String key;
    public final Kind kind;
    public final double min, max, step, def;
    public final String[] options;
    /** Optional unit shown in the UI. */
    public final String unit;

    private PropertyDef(String key, Kind kind, double min, double max, double step, double def, String[] options, String unit) {
        this.key = key;
        this.kind = kind;
        this.min = min;
        this.max = max;
        this.step = step;
        this.def = def;
        this.options = options;
        this.unit = unit;
    }

    public static PropertyDef number(String key, double min, double max, double step, double def, String unit) {
        return new PropertyDef(key, Kind.NUMBER, min, max, step, def, null, unit);
    }

    public static PropertyDef integer(String key, int min, int max, int def) {
        return new PropertyDef(key, Kind.INT, min, max, 1, def, null, "");
    }

    public static PropertyDef bool(String key, boolean def) {
        return new PropertyDef(key, Kind.BOOL, 0, 1, 1, def ? 1 : 0, null, "");
    }

    public static PropertyDef choice(String key, int def, String... options) {
        return new PropertyDef(key, Kind.ENUM, 0, options.length - 1, 1, def, options, "");
    }

    public double get(CompoundTag tag) {
        if (!tag.contains(key)) return def;
        return switch (kind) {
            case BOOL -> tag.getBoolean(key) ? 1 : 0;
            case INT, ENUM -> tag.getInt(key);
            case NUMBER -> tag.getDouble(key);
        };
    }

    public boolean getBool(CompoundTag tag) {
        return get(tag) > 0.5;
    }

    public int getInt(CompoundTag tag) {
        return (int) Math.round(get(tag));
    }

    public void set(CompoundTag tag, double value) {
        double v = sanitize(value);
        switch (kind) {
            case BOOL -> tag.putBoolean(key, v > 0.5);
            case INT, ENUM -> tag.putInt(key, (int) Math.round(v));
            case NUMBER -> tag.putDouble(key, v);
        }
    }

    public double sanitize(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return def;
        double v = Mth.clamp(value, min, max);
        if (kind == Kind.NUMBER && step > 0) v = Math.round(v / step) * step;
        if (kind != Kind.NUMBER) v = Math.round(v);
        return Mth.clamp(v, min, max);
    }

    public String translationKey() {
        return "vehicleworks.prop." + key;
    }
}
