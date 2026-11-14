package com.realweather.client.space;

import com.realweather.device.LightDrive;
import com.realweather.net.DrivePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/** The light drive's controls: its speed (1% to 100% of the speed of light) and switching it on or off. */
public class DriveScreen extends Screen {
    private final boolean offhand;
    private int percent;
    private boolean on;
    private Button toggle;

    public DriveScreen(boolean offhand) {
        super(Component.translatable("item.realweather.light_drive"));
        this.offhand = offhand;
    }

    @Override
    protected void init() {
        ItemStack s = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (percent == 0) {
            percent = s.getItem() instanceof LightDrive ? LightDrive.percent(s) : 10;
            on = s.getItem() instanceof LightDrive && LightDrive.on(s);
        }
        int w = Math.min(260, width - 40), x = width / 2 - w / 2, y = height / 2 - 30;
        addRenderableWidget(new AbstractSliderButton(x, y, w, 20, Component.empty(), (percent - 1) / 99.0) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.translatable("realweather.drive.slider", percent));
            }

            @Override
            protected void applyValue() {
                percent = 1 + (int) Math.round(value * 99);
            }
        });
        toggle = addRenderableWidget(Button.builder(label(), b -> {
            on = !on;
            b.setMessage(label());
            send();
        }).bounds(x, y + 26, w / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + w / 2 + 2, y + 26, w / 2 - 2, 20).build());
    }

    private Component label() {
        return Component.translatable(on ? "realweather.drive.turn_off" : "realweather.drive.turn_on");
    }

    private void send() {
        PacketDistributor.sendToServer(new DrivePayload(percent, on, offhand));
    }

    @Override
    public void onClose() {
        send();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int y = height / 2 - 70;
        g.drawCenteredString(font, title, width / 2, y, 0xFFFFE080);
        double kms = percent / 100.0 * SolarSystem.C;
        g.drawCenteredString(font, Component.translatable("realweather.drive.speed", String.format(Locale.ROOT, "%,.0f", kms),
                String.format(Locale.ROOT, "%.1f", 1.496e8 / kms / 60.0)), width / 2, y + 14, 0xFFC8E0FF);
        g.drawCenteredString(font, Component.translatable(on ? "realweather.drive.state_on" : "realweather.drive.state_off"), width / 2, y + 26,
                on ? 0xFF80FF90 : 0xFFB0B0B0);
        g.drawCenteredString(font, Component.translatable("realweather.drive.help1"), width / 2, height / 2 + 26, 0xFFB0B0B0);
        g.drawCenteredString(font, Component.translatable("realweather.drive.help2"), width / 2, height / 2 + 38, 0xFFB0B0B0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
