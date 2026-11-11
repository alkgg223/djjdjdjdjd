package com.rpbusiness.client.screen;

import com.rpbusiness.food.Dish;
import com.rpbusiness.food.Foods;
import com.rpbusiness.core.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Every recipe with its ingredients, preparation steps, equipment and cooking time. */
public class RecipeBookScreen extends BaseScreen {
    private final Screen parent;
    private int page;

    public RecipeBookScreen(Screen parent) {
        super(Component.translatable("item.rpbusiness.recipe_book"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout(380, 260);
        int y = top + 26;
        for (int i = 0; i < Dish.values().length; i++) {
            int idx = i;
            Button b = button(left + 6, y, 96, 19, Component.translatable(Dish.values()[i].key()), () -> { page = idx; scroll = 0; rebuildWidgets(); });
            b.active = i != page;
            y += 21;
        }
        button(left + w - 58, top + h - 24, 52, 18, Component.translatable("gui.rpbusiness.close"), this::onClose);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    private static String name(Item i) { return new ItemStack(i).getHoverName().getString(); }

    /** Expands how to obtain an item into human readable steps (deepest first). */
    private static void steps(Item it, List<String> out, List<String> raw, int depth) {
        if (depth > 6) return;
        Foods.CookLine l = Foods.cookLineProducing(it);
        if (l != null) {
            steps(l.input().get(), out, raw, depth + 1);
            int st = Foods.stageIndex(it);
            String eq = Component.translatable("block.rpbusiness." + Steps.equipKey(l.cooker())).getString();
            out.add(Component.translatable("recipe.rpbusiness.cook", name(l.input().get()), eq, l.times()[st] / 20, l.times()[Math.min(3, st + 1)] / 20).getString()
                    + (l.needsFlip() ? " " + Component.translatable("recipe.rpbusiness.flip").getString() : ""));
            return;
        }
        Foods.Chop c = Foods.chopProducing(it);
        if (c != null) {
            if (!raw.contains(name(c.in().get()))) raw.add(name(c.in().get()));
            out.add(Component.translatable("recipe.rpbusiness.chop", name(c.in().get()), c.chops(), name(it)).getString());
            return;
        }
        Foods.Assembly a = Foods.assemblyProducing(it);
        if (a != null) {
            StringBuilder parts = new StringBuilder();
            for (Foods.Group g : a.parts()) {
                steps(g.primary(), out, raw, depth + 1);
                if (parts.length() > 0) parts.append(" + ");
                parts.append(name(g.primary()));
            }
            out.add(Component.translatable("recipe.rpbusiness.assemble", parts.toString(), name(it)).getString());
            return;
        }
        if (it == ModItems.COLA.get() || it == ModItems.COFFEE.get()) {
            raw.add(name(ModItems.PAPER_CUP.get()));
            raw.add(name(it == ModItems.COLA.get() ? ModItems.COLA_SYRUP.get() : ModItems.COFFEE_BEANS.get()));
            out.add(Component.translatable("recipe.rpbusiness.drink", name(it == ModItems.COLA.get() ? ModItems.COLA_SYRUP.get() : ModItems.COFFEE_BEANS.get()), name(it)).getString());
            return;
        }
        if (!raw.contains(name(it))) raw.add(name(it));
    }

    static final class Steps {
        static String equipKey(Foods.Cooker c) { return switch (c) { case GRILL -> "grill"; case FRYER -> "fryer"; case OVEN -> "oven"; case POT -> "cooking_pot"; }; }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        frame(g, title);
        Dish d = Dish.values()[page];
        int x = left + 110, y = top + 28, cw = w - 118;
        panel(g, x - 4, y - 4, cw + 2, h - 56, PANEL);
        g.renderItem(new ItemStack(d.target()), x, y);
        text(g, Component.translatable(d.key()), x + 20, y + 4, ACCENT);
        y += 22;
        List<String> out = new ArrayList<>(), raw = new ArrayList<>();
        steps(d.target(), out, raw, 0);
        text(g, Component.translatable("recipe.rpbusiness.ingredients"), x, y, WARN);
        y += 11;
        for (String line : font.getSplitter().splitLines(String.join(", ", raw), cw, net.minecraft.network.chat.Style.EMPTY).stream().map(f -> f.getString()).toList()) {
            text(g, line, x, y, TEXT);
            y += 10;
        }
        y += 4;
        text(g, Component.translatable("recipe.rpbusiness.steps"), x, y, WARN);
        y += 11;
        int n = 1;
        for (String s : out) {
            for (String line : font.getSplitter().splitLines(n + ". " + s, cw, net.minecraft.network.chat.Style.EMPTY).stream().map(f -> f.getString()).toList()) {
                if (y < top + h - 30) text(g, line, x, y, TEXT);
                y += 10;
            }
            n++;
        }
        text(g, Component.translatable("recipe.rpbusiness.base_price", com.rpbusiness.economy.Money.fmt(d.basePrice)), left + 110, top + h - 20, DIM);
        super.render(g, mx, my, pt);
    }
}
