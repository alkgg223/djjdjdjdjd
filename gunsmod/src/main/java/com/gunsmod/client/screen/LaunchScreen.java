package com.gunsmod.client.screen;

import com.gunsmod.Reg;
import com.gunsmod.block.LauncherBlockEntity;
import com.gunsmod.item.MissileItem;
import com.gunsmod.item.RemoteLinks;
import com.gunsmod.net.Net;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Missile launch control. Left: the remote's stations (up to 100, scrollable), middle: the selected station and
 * target entry, right: the launch panel (lift the safety cover to arm, press the red button). With SALVO on the panel
 * arms and fires every linked station at once; LAUNCH ALL (tap twice) fires every loaded station that has a target.
 */
public class LaunchScreen extends Screen {
    private static final ResourceLocation COVER = Reg.id("textures/gui/launch_cover.png");
    private static final ResourceLocation BUTTON = Reg.id("textures/gui/launch_button.png");
    private static final ResourceLocation BUTTON_PRESSED = Reg.id("textures/gui/launch_button_pressed.png");
    private static final String[] TAGS = {"HE", "CL", "IN", "TB", "BB", "TT"};
    private static final int[] TAG_COLORS = {0xFFEDEDE0, 0xFF8FC46A, 0xFFFF9A3A, 0xFFFF6A2A, 0xFFB8BCC2, 0xFFFF4040};
    private static final int ROW = 12, VISIBLE = 15;

    private Net.LaunchStatus status;
    private int selected;
    private BlockPos filledFor;
    private EditBox bx, by, bz;
    private int left, top;
    private final int w = 380, h = 236;
    private boolean coverOpen, salvo;
    private float cover, prevCover;
    private int pressTicks;
    private int ticks;
    private int armTick = -100;
    /** first station row shown in the list, and the countdown of the launch-all confirmation */
    private int scroll;
    private int confirmTicks;

    public LaunchScreen(Net.LaunchStatus status) {
        super(Component.translatable("gunsmod.launch.title"));
        this.status = status;
        this.selected = status.selected();
    }

    public boolean remote() {
        return status.remote();
    }

    private Net.StationInfo current() {
        List<Net.StationInfo> l = status.stations();
        return l.isEmpty() ? Net.StationInfo.missing(BlockPos.ZERO) : l.get(Math.floorMod(selected, l.size()));
    }

    private boolean anyArmed() {
        for (Net.StationInfo s : status.stations()) if (s.state() == LauncherBlockEntity.ARMED) return true;
        return false;
    }

    private boolean armed() {
        return salvo ? anyArmed() : current().state() == LauncherBlockEntity.ARMED;
    }

    public void update(Net.LaunchStatus s) {
        this.status = s;
        this.selected = s.selected();
        if (coverOpen && !armed() && ticks - armTick > 12) coverOpen = false;    // arming was refused
        if (armed()) coverOpen = true;
        fillTarget(false);
    }

    @Override
    protected void init() {
        left = (width - w) / 2;
        top = (height - h) / 2;
        bx = box(left + 142, 0);
        by = box(left + 179, 1);
        bz = box(left + 216, 2);
        fillTarget(true);
        coverOpen = armed();
        cover = prevCover = coverOpen ? 1f : 0f;
        scrollTo(selected);
    }

    private int maxScroll() {
        return Math.max(0, status.stations().size() - VISIBLE);
    }

    private void scrollTo(int row) {
        if (row < scroll) scroll = row;
        else if (row >= scroll + VISIBLE) scroll = row - VISIBLE + 1;
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    /** stations that would fire with LAUNCH ALL: loaded, not busy, with a missile and a target */
    private int readyCount() {
        int n = 0;
        for (Net.StationInfo s : status.stations()) {
            if (s.found() && s.missile() >= 0 && s.hasTarget() && s.state() < LauncherBlockEntity.ERECTING) n++;
        }
        return n;
    }

    /** put the selected station's target into the boxes when the selection changes */
    private void fillTarget(boolean force) {
        Net.StationInfo s = current();
        if (bx == null || !force && s.pos().equals(filledFor)) return;
        filledFor = s.pos();
        if (s.hasTarget()) {
            bx.setValue(String.valueOf(s.tx()));
            by.setValue(String.valueOf(s.ty()));
            bz.setValue(String.valueOf(s.tz()));
        }
    }

    private EditBox box(int x, int i) {
        EditBox e = new EditBox(font, x, top + 150, 34, 14, Component.literal("xyz".substring(i, i + 1)));
        e.setMaxLength(8);
        e.setFilter(s -> s.matches("-?\\d*"));
        addRenderableWidget(e);
        return e;
    }

    private void send(int action, int x, int y, int z) {
        PacketDistributor.sendToServer(new Net.LaunchAction(current().pos(), action, x, y, z, status.remote()));
    }

    @Override
    public void tick() {
        ticks++;
        prevCover = cover;
        cover = Mth.clamp(cover + (coverOpen ? 0.18f : -0.25f), 0f, 1f);
        if (pressTicks > 0) pressTicks--;
        if (confirmTicks > 0) confirmTicks--;
        if (ticks % 10 == 0) send(Net.LaunchAction.QUERY, 0, 0, 0);
    }

    // ------------------------------------------------------------------------------------ layout
    private int px() {
        return left + 258;
    }

    private int py() {
        return top + 60;
    }

    private UiKit.Btn setButton() {
        return new UiKit.Btn(1, left + 142, top + 168, salvoAllowed() ? 52 : 108, 16, Component.translatable("gunsmod.launch.set"), true, false);
    }

    private UiKit.Btn setAllButton() {
        return new UiKit.Btn(6, left + 198, top + 168, 52, 16, Component.translatable("gunsmod.launch.set_all"), salvoAllowed(), false);
    }

    private UiKit.Btn unlinkButton() {
        return new UiKit.Btn(10, left + 142, top + 210, 108, 14, Component.translatable("gunsmod.launch.unlink"), true, true);
    }

    private UiKit.Btn disarmButton() {
        return new UiKit.Btn(4, px(), py() + 122, salvoAllowed() ? 52 : 108, 16, Component.translatable("gunsmod.launch.disarm"), armed(), false);
    }

    private UiKit.Btn salvoButton() {
        return new UiKit.Btn(11, px() + 54, py() + 122, 54, 16,
                Component.translatable(salvo ? "gunsmod.launch.salvo_on" : "gunsmod.launch.salvo_off"), true, salvo);
    }

    private UiKit.Btn fireAllButton() {
        int ready = readyCount();
        Component label = confirmTicks > 0 ? Component.translatable("gunsmod.launch.fire_all_confirm", ready)
                : Component.translatable("gunsmod.launch.fire_all_btn", ready);
        return new UiKit.Btn(12, px(), py() + 142, 108, 20, label, ready > 0, true);
    }

    private UiKit.Btn scrollUpButton() {
        return new UiKit.Btn(13, left + 104, top + 24, 12, 11, Component.literal("\u25B2"), scroll > 0, false);
    }

    private UiKit.Btn scrollDownButton() {
        return new UiKit.Btn(14, left + 117, top + 24, 12, 11, Component.literal("\u25BC"), scroll < maxScroll(), false);
    }

    private boolean salvoAllowed() {
        return status.remote() && status.stations().size() > 1;
    }

    // ------------------------------------------------------------------------------------ render
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x = left, y = top;
        boolean blink = (ticks / 6) % 2 == 0;
        UiKit.title(g, font, this.title, x + 10, y + 8);
        if (status.remote()) {
            String n = status.stations().size() + "/" + RemoteLinks.MAX;
            g.drawString(font, n, x + w - 10 - font.width(n), y + 8, UiKit.MUTED, false);
        }
        renderList(g, mx, my, blink);
        renderDetails(g, mx, my);
        renderPanel(g, mx, my, pt, blink);
        boolean launching = false;
        for (Net.StationInfo s : status.stations()) launching |= s.state() >= LauncherBlockEntity.ERECTING && s.state() <= LauncherBlockEntity.IGNITION;
        if (launching && blink) {
            Component c = Component.translatable("gunsmod.launch.launching");
            g.pose().pushPose();
            g.pose().translate(x + w / 2f - font.width(c) * 0.75f, y + 5, 0);
            g.pose().scale(1.5f, 1.5f, 1f);
            g.drawString(font, c, 0, 0, 0xFFFF4030, true);
            g.pose().popPose();
        }
    }

    private void renderList(GuiGraphics g, int mx, int my, boolean blink) {
        int x = left + 8, y = top + 22;
        UiKit.dark(g, x, y, 124, h - 30);
        g.drawString(font, Component.translatable("gunsmod.launch.stations"), x + 4, y + 4, UiKit.ACCENT, false);
        List<Net.StationInfo> l = status.stations();
        scroll = Mth.clamp(scroll, 0, maxScroll());
        for (int v = 0; v < VISIBLE && scroll + v < l.size(); v++) {
            int i = scroll + v;
            Net.StationInfo s = l.get(i);
            int ry = y + 15 + v * ROW;
            boolean sel = i == Math.floorMod(selected, l.size());
            boolean hover = mx >= x + 2 && mx < x + 117 && my >= ry && my < ry + ROW;
            if (sel) g.fill(x + 2, ry, x + 117, ry + ROW - 1, 0x60E0B040);
            else if (hover) g.fill(x + 2, ry, x + 117, ry + ROW - 1, 0x30FFFFFF);
            g.fill(x + 18, ry + 3, x + 24, ry + 9, lamp(s, blink));
            small(g, String.valueOf(i + 1), x + 3, ry + 3, sel ? 0xFFFFFFFF : UiKit.MUTED);
            String tag = s.missile() >= 0 && s.missile() < TAGS.length ? TAGS[s.missile()] : "--";
            small(g, tag + (s.heavy() ? "*" : ""), x + 27, ry + 3, s.missile() >= 0 ? TAG_COLORS[s.missile()] : 0xFF707070);
            String pos = s.found() ? s.pos().getX() + " " + s.pos().getY() + " " + s.pos().getZ()
                    : Component.translatable("gunsmod.launch.missing").getString();
            small(g, pos, x + 45, ry + 3, s.found() ? UiKit.TEXT : UiKit.BAD);
        }
        if (l.isEmpty()) small(g, Component.translatable("gunsmod.launch.no_stations").getString(), x + 4, y + 18, UiKit.BAD);
        if (l.size() > VISIBLE) {
            // scrollbar + page buttons (touch friendly)
            int top0 = y + 15, len = VISIBLE * ROW;
            int bar = Math.max(10, len * VISIBLE / l.size());
            int by0 = top0 + (len - bar) * scroll / Math.max(1, maxScroll());
            g.fill(x + 118, top0, x + 121, top0 + len, 0x40FFFFFF);
            g.fill(x + 118, by0, x + 121, by0 + bar, 0xFFE0B040);
            UiKit.button(g, font, scrollUpButton(), mx, my);
            UiKit.button(g, font, scrollDownButton(), mx, my);
        }
    }

    private static int lamp(Net.StationInfo s, boolean blink) {
        if (!s.found()) return 0xFF404040;
        if (s.state() >= LauncherBlockEntity.ERECTING) return blink ? 0xFFFF3020 : 0xFF601010;
        if (s.state() == LauncherBlockEntity.ARMED) return 0xFFFFA020;
        return s.missile() >= 0 ? 0xFF40E040 : 0xFF6A6A6A;
    }

    private void small(GuiGraphics g, String s, int x, int y, int col) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, s, 0, 0, col, false);
        g.pose().popPose();
    }

    private void renderDetails(GuiGraphics g, int mx, int my) {
        int x = left + 136, y = top + 22;
        UiKit.dark(g, x, y, 114, 114);
        Net.StationInfo s = current();
        int sy = y + 5;
        g.drawString(font, Component.translatable("gunsmod.launch.station_n", Math.floorMod(selected, Math.max(1, status.stations().size())) + 1,
                Component.translatable(s.heavy() ? "gunsmod.launch.heavy" : "gunsmod.launch.light")), x + 5, sy, UiKit.ACCENT, false);
        g.drawString(font, Component.translatable("gunsmod.launch.missile"), x + 5, sy + 13, UiKit.MUTED, false);
        if (s.missile() >= 0) {
            ItemStack ms = new ItemStack(MissileItem.byKind(s.missile()));
            g.renderItem(ms, x + 5, sy + 22);
            String name = font.plainSubstrByWidth(ms.getHoverName().getString(), 86);
            g.drawString(font, name, x + 24, sy + 26, 0xFFFFFFFF, true);
        } else {
            g.drawString(font, Component.translatable("gunsmod.launch.no_missile"), x + 5, sy + 26, UiKit.BAD, true);
        }
        g.drawString(font, Component.translatable("gunsmod.launch.target"), x + 5, sy + 44, UiKit.MUTED, false);
        if (s.hasTarget()) {
            g.drawString(font, s.tx() + ", " + s.ty() + ", " + s.tz(), x + 5, sy + 54, 0xFFFFFFFF, true);
            double d = Math.sqrt(s.pos().distSqr(new BlockPos(s.tx(), s.ty(), s.tz())));
            g.drawString(font, Component.translatable("gunsmod.launch.distance", (int) d), x + 5, sy + 65, UiKit.ACCENT, false);
        } else {
            g.drawString(font, Component.translatable("gunsmod.launch.no_target"), x + 5, sy + 54, UiKit.BAD, true);
        }
        Component state = Component.translatable(s.found() ? "gunsmod.launch.state." + s.state() : "gunsmod.launch.missing");
        g.drawString(font, state, x + 5, sy + 82, !s.found() || s.state() >= LauncherBlockEntity.ERECTING ? UiKit.BAD : UiKit.GOOD, true);
        g.drawString(font, Component.translatable("gunsmod.launch.coords"), x + 6, top + 140, UiKit.MUTED, false);
        UiKit.button(g, font, setButton(), mx, my);
        if (salvoAllowed()) UiKit.button(g, font, setAllButton(), mx, my);
        small(g, Component.translatable("gunsmod.launch.hint").getString(), x + 6, top + 188, 0xFF8A9080);
        small(g, Component.translatable("gunsmod.launch.hint2").getString(), x + 6, top + 197, 0xFF8A9080);
        if (status.remote() && !status.stations().isEmpty()) UiKit.button(g, font, unlinkButton(), mx, my);
    }

    private void renderPanel(GuiGraphics g, int mx, int my, float pt, boolean blink) {
        int lx = px(), ly = py(), y = top;
        UiKit.dark(g, lx - 6, y + 22, 120, h - 30);
        UiKit.hazard(g, lx - 2, y + 26, 112);
        g.drawString(font, Component.translatable(salvo ? "gunsmod.launch.panel_salvo" : "gunsmod.launch.panel"), lx + 2, y + 38,
                salvo ? 0xFFFF6A40 : UiKit.ACCENT, true);
        boolean armed = armed();
        RenderSystem.enableBlend();
        g.fill(lx + 6, ly + 6, lx + 102, ly + 102, 0xFF1A1A1A);
        float glow = armed ? 0.6f + 0.4f * Mth.sin((ticks + pt) * 0.4f) : 0.3f;
        g.fill(lx + 10, ly + 10, lx + 98, ly + 98, ((int) (glow * 90) << 24) | 0xFF2010);
        g.blit(pressTicks > 0 ? BUTTON_PRESSED : BUTTON, lx + 18, ly + 18, 0, 0, 72, 72, 72, 72);
        String fire = Component.translatable(salvo ? "gunsmod.launch.fire_all" : "gunsmod.launch.fire").getString();
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
        g.drawString(font, hint, lx + 54 - font.width(hint) / 2, ly + 108, armed ? UiKit.BAD : 0xFFDDDDDD, true);
        UiKit.button(g, font, disarmButton(), mx, my);
        if (salvoAllowed()) UiKit.button(g, font, salvoButton(), mx, my);
        if (status.remote()) {
            UiKit.Btn b = fireAllButton();
            if (confirmTicks > 0 && blink) g.fill(b.x() - 1, b.y() - 1, b.x() + b.w() + 1, b.y() + b.h() + 1, 0xFFFF3020);
            UiKit.button(g, font, b, mx, my);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        UiKit.panel(g, left, top, w, h);
    }

    // ------------------------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        // station list
        int lx0 = left + 8, ly0 = top + 22 + 15;
        List<Net.StationInfo> l = status.stations();
        if (l.size() > VISIBLE && (scrollUpButton().hit(mx, my) || scrollDownButton().hit(mx, my))) {
            scroll = Mth.clamp(scroll + (scrollUpButton().hit(mx, my) ? -VISIBLE + 1 : VISIBLE - 1), 0, maxScroll());
            sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f);
            return true;
        }
        int rows = Math.min(VISIBLE, l.size() - scroll);
        if (mx >= lx0 + 2 && mx < lx0 + 117 && my >= ly0 && my < ly0 + rows * ROW) {
            int i = scroll + (int) ((my - ly0) / ROW);
            if (i != Math.floorMod(selected, Math.max(1, l.size()))) {
                selected = i;
                fillTarget(true);
                if (status.remote()) send(Net.LaunchAction.SELECT, i, 0, 0);
                sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f);
            }
            return true;
        }
        if (setButton().hit(mx, my) || salvoAllowed() && setAllButton().hit(mx, my)) {
            boolean all = !setButton().hit(mx, my);
            try {
                int x = Integer.parseInt(bx.getValue()), y = Integer.parseInt(by.getValue()), z = Integer.parseInt(bz.getValue());
                send(all ? Net.LaunchAction.TARGET_ALL : Net.LaunchAction.TARGET, x, y, z);
                sound(SoundEvents.UI_BUTTON_CLICK.value(), 1f);
            } catch (NumberFormatException e) {
                sound(Reg.sound("dry_fire"), 1f);
            }
            return true;
        }
        if (status.remote() && !l.isEmpty() && unlinkButton().hit(mx, my)) {
            send(Net.LaunchAction.UNLINK, 0, 0, 0);
            sound(Reg.sound("cover_open"), 0.6f);
            return true;
        }
        if (status.remote() && fireAllButton().hit(mx, my)) {
            if (readyCount() == 0) {
                sound(Reg.sound("dry_fire"), 1f);
            } else if (confirmTicks > 0) {
                confirmTicks = 0;
                pressTicks = 8;
                send(Net.LaunchAction.FIRE_ALL, 0, 0, 0);
                sound(Reg.sound("arm_switch"), 0.7f);
            } else {
                confirmTicks = 60;
                sound(Reg.sound("cover_open"), 1.3f);
            }
            return true;
        }
        if (salvoAllowed() && salvoButton().hit(mx, my)) {
            salvo = !salvo;
            coverOpen = armed();
            sound(Reg.sound("arm_switch"), salvo ? 1.2f : 0.9f);
            return true;
        }
        int lx = px(), ly = py();
        boolean inPanel = mx >= lx + 6 && mx < lx + 102 && my >= ly + 6 && my < ly + 102;
        if (inPanel) {
            if (armed() && cover > 0.9f) {
                pressTicks = 8;
                send(salvo ? Net.LaunchAction.LAUNCH_ALL : Net.LaunchAction.LAUNCH, 0, 0, 0);
                sound(Reg.sound("arm_switch"), 0.8f);
            } else if (!coverOpen) {
                coverOpen = true;
                armTick = ticks;
                send(salvo ? Net.LaunchAction.ARM_ALL : Net.LaunchAction.ARM, 0, 0, 0);
                sound(Reg.sound("cover_open"), 1f);
            }
            return true;
        }
        if (disarmButton().hit(mx, my) && armed()) {
            coverOpen = false;
            send(salvo ? Net.LaunchAction.DISARM_ALL : Net.LaunchAction.DISARM, 0, 0, 0);
            sound(Reg.sound("cover_open"), 0.7f);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= left + 8 && mx < left + 132 && my >= top + 22 && my < top + h - 8) {
            scroll = Mth.clamp(scroll - (int) Math.signum(sy) * 3, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    /** dragging over the list scrolls it (touch screens have no wheel) */
    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (mx >= left + 8 && mx < left + 132 && my >= top + 37 && my < top + 37 + VISIBLE * ROW) {
            dragAcc += dy;
            int rows = (int) (dragAcc / ROW);
            if (rows != 0) {
                scroll = Mth.clamp(scroll - rows, 0, maxScroll());
                dragAcc -= rows * ROW;
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    private double dragAcc;

    private void sound(SoundEvent s, float pitch) {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(s, pitch));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
