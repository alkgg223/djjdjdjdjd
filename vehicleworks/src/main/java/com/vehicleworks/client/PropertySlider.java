package com.vehicleworks.client;

import com.vehicleworks.component.PropertyDef;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Slider bound to a numeric component property. */
public final class PropertySlider extends AbstractSliderButton {
    private final PropertyDef def;
    private final CompoundTag tag;
    private final Runnable onChange;

    public PropertySlider(int x, int y, int w, int h, PropertyDef def, CompoundTag tag, Runnable onChange) {
        super(x, y, w, h, Component.empty(), (def.get(tag) - def.min) / Math.max(1e-9, def.max - def.min));
        this.def = def;
        this.tag = tag;
        this.onChange = onChange;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        double v = def.get(tag);
        String num = def.kind == PropertyDef.Kind.INT ? String.valueOf((int) v) : (Math.abs(v) >= 100 ? String.format("%.0f", v) : String.format("%.2f", v));
        String unit = def.unit == null || def.unit.isEmpty() ? "" : " " + def.unit;
        if (def.key.equals("stall_angle") && v <= 0.01) num = Component.translatable("vehicleworks.ui.auto").getString();
        setMessage(Component.translatable(def.translationKey()).append(": " + num + unit));
    }

    @Override
    protected void applyValue() {
        double v = def.min + value * (def.max - def.min);
        def.set(tag, v);
        if (onChange != null) onChange.run();
    }
}
