package com.gunsmod.client.screen;

import com.gunsmod.Reg;
import com.gunsmod.block.LauncherBlockEntity;
import com.gunsmod.client.render.MissileRenderer;
import com.gunsmod.net.Net;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Missile launch control: station status, target coordinates, and the launch panel: lift the safety cover to arm,
 * then press the red button.
 */
public class LaunchScreen extends Screen {
    private static final ResourceLocation COVER = Reg.id("textures/gui/launch_cover.png");
    private static final ResourceLocation BUTTON = Reg.id("textures/gui/launch_button.png");
    private static final ResourceLocation BUTTON_PRESSED = Reg.id("textures/gui/launch_button_pressed.png");

    private Net.LaunchStatus status;
    private EditBox bx, by, bz;
    private int left, top;
    private final int w = 300, h = 212;
    private boolean coverOpen;
    private float cover, prevCover;
    private int pressTicks;
    private int ticks;
    private int armTick = -100;

    public LaunchScreen(Net.LaunchStatus status) {
        super(Component.translatable("gunsmod.launch.title"));
        this.status = status;
    }

    public BlockPos station() {
        return status.station();
    }

    public void update(Net.LaunchStatus s) {
        this.status = s;
        if (s.state() == LauncherBlockEntity.IDLE && coverOpen && ticks - armTick > 12) {
            // the server refused to arm (no missile / no target): close the cover again
            coverOpen = false;
        }
        if (s.state() == LauncherBlockEntity.ARMED) coverOpen = true;
    }

    @Override
    protected void init() {
        left = (width - w) / 2;
        top = (height - h) / 2;
        bx = box(left + 14, 0);
        by = box(left + 64, 1);
        bz = box(left + 114, 2);
        if (status.hasTarget()) {
            bx.setValue(String.valueOf(status.tx()));
            by.setValue(String.valueOf(status.ty()));
            bz.setValue(String.valueOf(status.tz()));
        }
        coverOpen = status.state() == LauncherBlockEntity.ARMED;
        cover = prevCover = coverOpen ? 1f : 0f;
    }

    private EditBox box(int x, int i) {
        EditBox e = new EditBox(font, x, top + 128, 46, 16, Component.literal("xyz".substring(i, i + 1)));
        e.setMaxLength(9);
        e.setFilter(s -> s.matches("-?\\d*"));
        addRenderableWidget(e);
        return e;
    }

    private void send(int action, int x, int y, int z) {
        PacketDistributor.sendToServer(new Net.LaunchAction(status.station(), action, x, y, z));
    }

    @Override
    public void tick() {
        ticks++;
        prevCover = cover;
        cover = Mth.clamp(cover + (coverOpen ? 0.18f : -0.25f), 0f, 1f);
        if (pressTicks > 0) pressTicks--;
        if (ticks % 10 == 0) send(0, 0, 0, 0);
    }

    // layout of the launch panel
    private int px() {
        return left + 178;
    }

    private int py() {
        return top + 70;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x = left, y = top;
        // header
        UiKit.title(g, font, this.title, x + 10, y + 8);
        String pos = status.station().getX() + " " + status.station().getY() + " " + status.station().getZ();
        g.drawString(font, pos, x + w - 10 - font.width(pos), y + 8, UiKit.MUTED, false);
        boolean blink = (ticks / 6) % 2 == 0;
        int lamp = !status.found() ? 0xFF505050 : status.state() >= LauncherBlockEntity.ERECTING ? (blink ? 0xFFFF3020 : 0xFF601010)
                : status.state() == LauncherBlockEntity.ARMED ? 0xFFFFA020 : 0xFF40E040;
        g.fill(x + w - 22 - font.width(pos), y + 8, x + w - 14 - font.width(pos), y + 16, lamp);
        // status panel
        UiKit.dark(g, x + 8, y + 26, 160, 92);
        int sy = y + 32;
        g.drawString(font, Component.translatable("gunsmod.launch.missile"), x + 14, sy, UiKit.MUTED, false);
        if (status.missile() >= 0) {
            ItemStack ms = new ItemStack(MissileRenderer.item(status.missile()));
            g.renderItem(ms, x + 14, sy + 10);
            g.drawString(font, ms.getHoverName(), x + 34, sy + 14, 0xFFFFFFFF, true);
        } else {
            g.drawString(font, Component.translatable("gunsmod.launch.no_missile"), x + 14, sy + 14, UiKit.BAD, true);
        }
        g.drawString(font, Component.translatable("gunsmod.launch.target"), x + 14, sy + 32, UiKit.MUTED, false);
        if (status.hasTarget()) {
            g.drawString(font, status.tx() + ", " + status.ty() + ", " + status.tz(), x + 14, sy + 42, 0xFFFFFFFF, true);
            double d = Math.sqrt(status.station().distSqr(new BlockPos(status.tx(), status.ty(), status.tz())));
            g.drawString(font, Component.translatable("gunsmod.launch.distance", (int) d), x + 14, sy + 53, UiKit.ACCENT, false);
        } else {
            g.drawString(font, Component.translatable("gunsmod.launch.no_target"), x + 14, sy + 42, UiKit.BAD, true);
        }
        Component state = Component.translatable("gunsmod.launch.state." + status.state());
        g.drawString(font, state, x + 14, sy + 70, status.state() >= LauncherBlockEntity.ERECTING ? UiKit.BAD : UiKit.GOOD, true);
        // target entry
        g.drawString(font, Component.translatable("gunsmod.launch.coords"), x + 14, y + 118 - 0, UiKit.MUTED, false);
        UiKit.Btn set = setButton();
        UiKit.button(g, font, set, mx, my);
        g.drawString(font, Component.translatable("gunsmod.launch.hint"), x + 14, y + 150, 0xFF8A9080, false);
        g.drawString(font, Component.translatable("gunsmod.launch.hint2"), x + 14, y + 160, 0xFF8A9080, false);
        // launch panel
        int lx = px(), ly = py();
        UiKit.dark(g, lx - 6, y + 26, 120, 176);
        UiKit.hazard(g, lx - 2, y + 30, 112);
        g.drawString(font, Component.translatable("gunsmod.launch.panel"), lx + 2, y + 42, UiKit.ACCENT, true);
        // the big button (under the cover)
        boolean armed = status.state() == LauncherBlockEntity.ARMED;
        RenderSystem.enableBlend();
        g.fill(lx + 6, ly + 6, lx + 102, ly + 102, 0xFF1A1A1A);
        float glow = armed ? 0.6f + 0.4f * Mth.sin((ticks + pt) * 0.4f) : 0.3f;
        g.fill(lx + 10, ly + 10, lx + 98, ly + 98, ((int) (glow * 90) << 24) | 0xFF2010);
        g.blit(pressTicks > 0 ? BUTTON_PRESSED : BUTTON, lx + 18, ly + 18, 0, 0, 72, 72, 72, 72);
        String fire = Component.translatable("gunsmod.launch.fire").getString();
        g.drawString(font, fire, lx + 54 - font.width(fire) / 2, ly + 50, 0xFFFFFFFF, true);
        // safety cover hinged at the top edge
        float c = Mth.lerp(pt, prevCover, cover);
        g.pose().pushPose();
        g.pose().translate(lx + 54, ly + 4, 50);
        g.pose().mulPose(Axis.XP.rotationDegrees(-c * 105f));
        g.blit(COVER, -50, 0, 0, 0, 100, 100, 100, 100);
        g.pose().popPose();
        RenderSystem.disableBlend();
        Component hint = Component.translatable(armed ? "gunsmod.launch.press" : coverOpen ? "gunsmod.launch.arming" : "gunsmod.launch.lift");
        g.drawString(font, hint, lx + 54 - font.width(hint) / 2, ly + 110, armed ? UiKit.BAD : 0xFFDDDDDD, true);
        UiKit.Btn disarm = new UiKit.Btn(4, lx, ly + 124, 108, 16, Component.translatable("gunsmod.launch.disarm"), armed, false);
        UiKit.button(g, font, disarm, mx, my);
        if (status.state() >= LauncherBlockEntity.ERECTING) {
            Component launching = Component.translatable("gunsmod.launch.launching");
            g.pose().pushPose();
            g.pose().translate(x + w / 2f - font.width(launching), y + h - 22, 0);
            g.pose().scale(2f, 2f, 1f);
            if (blink) g.drawString(font, launching, 0, 0, 0xFFFF4030, true);
            g.pose().popPose();
        }
    }

    private UiKit.Btn setButton() {
        return new UiKit.Btn(1, left + 14, top + 172, 146, 18, Component.translatable("gunsmod.launch.set"), true, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        UiKit.panel(g, left, top, w, h);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (setButton().hit(mx, my)) {
            try {
                int x = Integer.parseInt(bx.getValue()), y = Integer.parseInt(by.getValue()), z = Integer.parseInt(bz.getValue());
                send(1, x, y, z);
                sound(SoundEvents.UI_BUTTON_CLICK.value(), 1f);
            } catch (NumberFormatException e) {
                sound(Reg.sound("dry_fire"), 1f);
            }
            return true;
        }
        int lx = px(), ly = py();
        boolean inPanel = mx >= lx + 6 && mx < lx + 102 && my >= ly + 6 && my < ly + 102;
        if (inPanel) {
            if (status.state() == LauncherBlockEntity.ARMED && cover > 0.9f) {
                pressTicks = 8;
                send(3, 0, 0, 0);
                sound(Reg.sound("arm_switch"), 0.8f);
            } else if (status.state() == LauncherBlockEntity.IDLE && !coverOpen) {
                coverOpen = true;
                armTick = ticks;
                send(2, 0, 0, 0);
                sound(Reg.sound("cover_open"), 1f);
            }
            return true;
        }
        UiKit.Btn disarm = new UiKit.Btn(4, lx, ly + 124, 108, 16, Component.empty(), status.state() == LauncherBlockEntity.ARMED, false);
        if (disarm.hit(mx, my)) {
            coverOpen = false;
            send(4, 0, 0, 0);
            sound(Reg.sound("cover_open"), 0.7f);
            return true;
        }
        return false;
    }

    private void sound(net.minecraft.sounds.SoundEvent s, float pitch) {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(s, pitch));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
