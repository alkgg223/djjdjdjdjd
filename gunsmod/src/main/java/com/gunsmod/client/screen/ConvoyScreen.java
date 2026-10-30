package com.gunsmod.client.screen;

import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.VehicleKind;
import com.gunsmod.item.ConvoyToolItem;
import com.gunsmod.net.Net;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Convoy planner: pick the side, then click the vehicles in the order of the column (the head first). Click a vehicle
 * in the column to take it out again. Helicopters fly cover over the column.
 */
public class ConvoyScreen extends Screen {
    private static final int[] OPTIONS = {ConvoyToolItem.JEEP, VehicleKind.TOW_HUMVEE.ordinal(), VehicleKind.BTR.ordinal(), VehicleKind.PANTSIR.ordinal(),
            VehicleKind.HIMARS.ordinal(), VehicleKind.GRAD.ordinal(), VehicleKind.ABRAMS.ordinal(), VehicleKind.LEOPARD.ordinal(),
            VehicleKind.T90.ordinal(), VehicleKind.CHALLENGER.ordinal(), VehicleKind.MAUS.ordinal(), VehicleKind.OBJ279.ordinal(),
            VehicleKind.COBRA.ordinal(), VehicleKind.APACHE.ordinal(), VehicleKind.HIND.ordinal(), VehicleKind.KA52.ordinal()};
    private static final int W = 360, H = 236, CELL = 42, SLOT = 32;
    private int left, top;
    private int team;
    private final List<Integer> column = new ArrayList<>();
    private UiKit.Btn green, red, clear, save, close;

    public ConvoyScreen(ItemStack stack) {
        super(Component.translatable("gunsmod.convoy.title"));
        ConvoyToolItem.Plan plan = ConvoyToolItem.read(stack);
        if (plan != null) {
            team = plan.team().ordinal();
            for (int k : plan.kinds()) column.add(k);
        }
    }

    @Override
    protected void init() {
        left = (this.width - W) / 2;
        top = (this.height - H) / 2;
        buttons();
    }

    private void buttons() {
        green = new UiKit.Btn(0, left + W - 168, top + 8, 76, 16, Component.translatable("gunsmod.team.green"), team != 0, false);
        red = new UiKit.Btn(1, left + W - 88, top + 8, 76, 16, Component.translatable("gunsmod.team.red"), team != 1, true);
        clear = new UiKit.Btn(2, left + 12, top + H - 24, 90, 16, Component.translatable("gunsmod.convoy.clear"), !column.isEmpty(), true);
        save = new UiKit.Btn(3, left + W - 196, top + H - 24, 90, 16, Component.translatable("gunsmod.convoy.save"), !column.isEmpty(), false);
        close = new UiKit.Btn(4, left + W - 100, top + H - 24, 88, 16, Component.translatable("gunsmod.team.close"), true, false);
    }

    private int optX(int i) {
        return left + 12 + (i % 8) * CELL;
    }

    private int optY(int i) {
        return top + 32 + (i / 8) * CELL;
    }

    private int colX(int i) {
        return left + 12 + i * (SLOT + 2);
    }

    private int colY() {
        return top + 142;
    }

    private BotTeam side() {
        return BotTeam.byId(team);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        UiKit.panel(g, left, top, W, H);
        UiKit.title(g, this.font, this.title, left + 12, top + 12);
        UiKit.button(g, this.font, green, mx, my);
        UiKit.button(g, this.font, red, mx, my);
        Component hovered = null;
        int c = side().color;
        for (int i = 0; i < OPTIONS.length; i++) {
            int x = optX(i), y = optY(i);
            boolean over = mx >= x && my >= y && mx < x + CELL - 2 && my < y + CELL - 2;
            g.fill(x, y, x + CELL - 2, y + CELL - 2, over ? 0xC0000000 | c : 0x90101418);
            if (ConvoyToolItem.heli(OPTIONS[i])) g.fill(x, y, x + CELL - 2, y + 2, 0xFF60A0FF);
            ItemStack st = ConvoyToolItem.stackOf(OPTIONS[i], side());
            icon(g, st, x + 4, y + 4, 2f);
            if (over) hovered = st.getHoverName();
        }
        Component head = Component.translatable("gunsmod.convoy.column", column.size(), ConvoyToolItem.MAX);
        g.drawString(this.font, head, left + 12, colY() - 12, UiKit.ACCENT, false);
        for (int i = 0; i < ConvoyToolItem.MAX; i++) {
            int x = colX(i), y = colY();
            boolean over = mx >= x && my >= y && mx < x + SLOT && my < y + SLOT;
            g.fill(x, y, x + SLOT, y + SLOT, i < column.size() && over ? 0xC0802020 : 0x80000000);
            if (i >= column.size()) continue;
            ItemStack st = ConvoyToolItem.stackOf(column.get(i), side());
            icon(g, st, x + 2, y + 2, 1.75f);
            g.drawString(this.font, String.valueOf(i + 1), x + 2, y + 2, 0xFFFFFFFF, true);
            if (ConvoyToolItem.heli(column.get(i))) g.drawString(this.font, "^", x + SLOT - 7, y + 2, 0xFF60A0FF, true);
            if (over) hovered = Component.translatable("gunsmod.convoy.remove", st.getHoverName());
        }
        // the head of the column on the left: an arrow showing the way it drives
        g.drawString(this.font, Component.translatable("gunsmod.convoy.front"), left + 12, colY() + SLOT + 4, UiKit.MUTED, false);
        Component hint = hovered != null ? hovered : Component.translatable("gunsmod.convoy.hint");
        g.drawString(this.font, hint, left + (W - this.font.width(hint)) / 2, top + H - 46, hovered != null ? UiKit.TEXT : UiKit.MUTED, false);
        UiKit.button(g, this.font, clear, mx, my);
        UiKit.button(g, this.font, save, mx, my);
        UiKit.button(g, this.font, close, mx, my);
    }

    private void icon(GuiGraphics g, ItemStack st, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(st, 0, 0);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (green.hit(mx, my) || red.hit(mx, my)) {
            team = green.hit(mx, my) ? 0 : 1;
            buttons();
            return true;
        }
        for (int i = 0; i < OPTIONS.length; i++) {
            int x = optX(i), y = optY(i);
            if (mx >= x && my >= y && mx < x + CELL - 2 && my < y + CELL - 2) {
                if (column.size() < ConvoyToolItem.MAX) column.add(OPTIONS[i]);
                buttons();
                return true;
            }
        }
        for (int i = 0; i < column.size(); i++) {
            int x = colX(i), y = colY();
            if (mx >= x && my >= y && mx < x + SLOT && my < y + SLOT) {
                column.remove(i);
                buttons();
                return true;
            }
        }
        if (clear.hit(mx, my)) {
            column.clear();
            buttons();
            return true;
        }
        if (save.hit(mx, my) && !column.isEmpty()) {
            PacketDistributor.sendToServer(new Net.ConvoySave(team, column.stream().mapToInt(Integer::intValue).toArray()));
            this.onClose();
            return true;
        }
        if (close.hit(mx, my)) {
            this.onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
