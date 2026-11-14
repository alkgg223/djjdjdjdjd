package com.vehicleworks.client;

import com.vehicleworks.network.Payloads;
import com.vehicleworks.vehicle.VehicleControls;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * Programmable Remote Control Unit: every linked weapon gets a fire channel (an input key)
 * and a fire mode. Channels 1/2 are the attack/use buttons, 3–6 are rebindable keys.
 */
public class RemoteControlScreen extends Screen {
    private final int vehicleId;
    private final long pos;
    private final ListTag links;

    public RemoteControlScreen(int vehicleId, long pos, CompoundTag data) {
        super(Component.translatable("block.vehicleworks.remote_control_unit"));
        this.vehicleId = vehicleId;
        this.pos = pos;
        this.links = data.getList("Links", Tag.TAG_COMPOUND).copy();
    }

    @Override
    protected void init() {
        clearWidgets();
        int y = 46;
        for (int i = 0; i < Math.min(12, links.size()); i++) {
            CompoundTag l = links.getCompound(i);
            int idx = i;
            addRenderableWidget(Button.builder(channelLabel(l), b -> {
                l.putInt("Ch", (l.getInt("Ch") + 1) % VehicleControls.CHANNELS);
                b.setMessage(channelLabel(l));
            }).bounds(width / 2 + 10, y + i * 22, 110, 20).build());
            addRenderableWidget(Button.builder(modeLabel(l), b -> {
                l.putInt("Mode", (l.getInt("Mode") + 1) % 3);
                b.setMessage(modeLabel(l));
            }).bounds(width / 2 + 124, y + i * 22, 70, 20).build());
            addRenderableWidget(Button.builder(Component.literal("✕"), b -> {
                links.remove(idx);
                init();
            }).bounds(width / 2 + 198, y + i * 22, 20, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("vehicleworks.ui.apply_close"), b -> {
            CompoundTag t = new CompoundTag();
            t.put("Links", links);
            Payloads.toServer(new Payloads.Configure(vehicleId, pos, true, t));
            onClose();
        }).bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    private static Component channelLabel(CompoundTag l) {
        int ch = l.getInt("Ch");
        return Component.translatable("vehicleworks.ui.channel", ch + 1, Component.translatable("vehicleworks.channel_key." + (ch + 1)));
    }

    private static Component modeLabel(CompoundTag l) {
        return Component.translatable("vehicleworks.fire_mode." + l.getInt("Mode"));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("vehicleworks.ui.rcu_hint"), width / 2, 26, 0xAAAAAA);
        if (links.isEmpty()) g.drawCenteredString(font, Component.translatable("vehicleworks.ui.rcu_empty"), width / 2, height / 2, 0xFFC040);
        for (int i = 0; i < Math.min(12, links.size()); i++) {
            CompoundTag l = links.getCompound(i);
            int[] o = l.getIntArray("Off");
            String off = o.length == 3 ? String.format(" (%d, %d, %d)", o[0], o[1], o[2]) : "";
            int c = l.getBoolean("Valid") || !l.contains("Valid") ? 0xE0E0E0 : 0xFF6060;
            g.drawString(font, Component.literal((i + 1) + ". ").append(Component.translatable(l.getString("Name"))).append(off), width / 2 - 210, 52 + i * 22, c);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
