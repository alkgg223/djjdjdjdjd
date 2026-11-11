package com.rpbusiness.client.screen;

import com.rpbusiness.business.BusinessType;
import com.rpbusiness.network.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Register a building the player built (selected with the Business Planner) as a business. */
public class RegisterScreen extends BaseScreen {
    private final BlockPos a, b;
    private EditBox name;
    private int type;

    public RegisterScreen(BlockPos a, BlockPos b) {
        super(Component.translatable("gui.rpbusiness.register_title"));
        this.a = a;
        this.b = b;
    }

    @Override
    protected void init() {
        layout(300, 190);
        String keep = name == null ? "" : name.getValue();
        name = new EditBox(font, left + 10, top + 42, w - 20, 18, Component.literal("name"));
        name.setMaxLength(32);
        name.setValue(keep);
        name.setHint(Component.translatable("gui.rpbusiness.name_hint"));
        addRenderableWidget(name);
        BusinessType[] types = BusinessType.values();
        button(left + 10, top + 78, w - 20, 20, Component.translatable(types[type].key()), () -> { type = (type + 1) % types.length; rebuildWidgets(); });
        Button reg = button(left + 10, top + h - 30, (w - 24) / 2, 20, Component.translatable("gui.rpbusiness.register"), () -> {
            CompoundTag t = new CompoundTag();
            t.putString("name", name.getValue());
            t.putString("type", types[type].name());
            Net.toServer("register", t);
            onClose();
        });
        reg.active = a != null && b != null;
        button(left + w / 2 + 2, top + h - 30, (w - 24) / 2, 20, Component.translatable("gui.cancel"), this::onClose);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        frame(g, title);
        text(g, Component.translatable("gui.rpbusiness.business_name"), left + 10, top + 30, DIM);
        text(g, Component.translatable("gui.rpbusiness.business_type"), left + 10, top + 66, DIM);
        BusinessType t = BusinessType.values()[type];
        if (!t.foodService) text(g, Component.translatable("gui.rpbusiness.type_coming"), left + 10, top + 102, WARN);
        if (a == null || b == null) text(g, Component.translatable("gui.rpbusiness.need_corners"), left + 10, top + 116, BAD);
        else {
            int dx = Math.abs(a.getX() - b.getX()) + 1, dy = Math.abs(a.getY() - b.getY()) + 1, dz = Math.abs(a.getZ() - b.getZ()) + 1;
            text(g, Component.translatable("gui.rpbusiness.area_size", dx, dy, dz), left + 10, top + 116, TEXT);
            text(g, clip(a.toShortString() + "  →  " + b.toShortString(), w - 20), left + 10, top + 128, DIM);
        }
        text(g, Component.translatable("gui.rpbusiness.register_hint"), left + 10, top + 142, DIM);
        super.render(g, mx, my, pt);
    }
}
