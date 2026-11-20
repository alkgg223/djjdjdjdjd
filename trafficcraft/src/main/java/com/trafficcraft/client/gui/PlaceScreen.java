package com.trafficcraft.client.gui;

import com.trafficcraft.net.PlacePayload;
import com.trafficcraft.road.ClientRoads;
import com.trafficcraft.road.Place;
import com.trafficcraft.road.PlaceTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Location tool screen: name the place and pick one of the 25 location types; edit or delete a location standing here. */
public class PlaceScreen extends Screen {
    final Vec3 at;
    final Place existing;
    EditBox name;
    String type;

    public PlaceScreen(Vec3 at) {
        super(Component.translatable("screen.trafficcraft.place"));
        this.at = at;
        this.existing = ClientRoads.net.placeNear(at, 4);
        this.type = existing != null ? existing.type : "supermarket";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int x0 = width / 2 - 160, y = 30;
        name = new EditBox(font, x0, y, 320, 18, Component.translatable("screen.trafficcraft.place.name"));
        name.setHint(Component.translatable("screen.trafficcraft.place.name"));
        name.setMaxLength(40);
        if (existing != null) name.setValue(existing.name);
        addRenderableWidget(name);
        y += 28;
        List<String> keys = new ArrayList<>(PlaceTypes.ALL.keySet());
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            int bx = x0 + (i % 5) * 65, by = y + (i / 5) * 24;
            addRenderableWidget(new Button(bx, by, 62, 20, Component.translatable("place.trafficcraft." + k), b -> type = k, s -> s.get()) {
                @Override
                protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    boolean sel = type.equals(k);
                    g.fill(getX(), getY(), getX() + width, getY() + height, sel ? 0xFFFFFFFF : isHovered() ? 0xFF5A6270 : 0xFF2A2F37);
                    g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, sel ? 0xFF1C2430 : 0xFF1A1D22);
                    g.fill(getX() + 3, getY() + 4, getX() + 15, getY() + 16, 0xFF000000 | PlaceTypes.color(k));
                    g.drawCenteredString(font, PlaceTypes.glyph(k), getX() + 9, getY() + 6, 0xFFFFFFFF);
                    String t = getMessage().getString();
                    float sc = Math.min(1f, 44f / Math.max(1, font.width(t)));
                    g.pose().pushPose();
                    g.pose().translate(getX() + 18, getY() + 10 - 4 * sc, 0);
                    g.pose().scale(sc, sc, 1);
                    g.drawString(font, t, 0, 0, sel ? 0xFFFFE080 : 0xFFE0E0E0, false);
                    g.pose().popPose();
                }
            });
        }
        y += 5 * 24 + 10;
        addRenderableWidget(Button.builder(Component.translatable(existing != null ? "screen.trafficcraft.place.save" : "screen.trafficcraft.place.create"), b -> save())
                .bounds(x0, y, 156, 20).build());
        if (existing != null) addRenderableWidget(Button.builder(Component.translatable("screen.trafficcraft.place.delete"), b -> {
            CompoundTag t = new CompoundTag();
            t.putInt("id", existing.id);
            PacketDistributor.sendToServer(new PlacePayload("del", t));
            onClose();
        }).bounds(x0 + 164, y, 156, 20).build());
        else addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x0 + 164, y, 156, 20).build());
        setInitialFocus(name);
    }

    void save() {
        CompoundTag t = new CompoundTag();
        t.putString("type", type);
        t.putString("name", name.getValue());
        if (existing != null) {
            t.putInt("id", existing.id);
            PacketDistributor.sendToServer(new PlacePayload("edit", t));
        } else {
            t.putDouble("x", at.x);
            t.putDouble("y", at.y);
            t.putDouble("z", at.z);
            PacketDistributor.sendToServer(new PlacePayload("add", t));
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, Component.translatable(existing != null ? "screen.trafficcraft.place.edit" : "screen.trafficcraft.place"), width / 2, 12, 0xFFFFD060);
    }
}
