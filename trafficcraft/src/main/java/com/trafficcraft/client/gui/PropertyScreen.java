package com.trafficcraft.client.gui;

import com.trafficcraft.road.Props;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Generic schema-driven settings editor. Values live in {@code root} (group "root"), {@code root.cfg} (group "cfg")
 * or as bits of {@code root.flags} (group "flags"). Paged two-column layout that also works on phones.
 */
public class PropertyScreen extends Screen {
    protected final CompoundTag root;
    protected List<Props.Prop> props;
    private final Consumer<CompoundTag> onSave;
    private int page;
    private final List<Runnable> refresh = new ArrayList<>();
    protected final List<Button> extra = new ArrayList<>();

    public PropertyScreen(Component title, CompoundTag root, List<Props.Prop> props, Consumer<CompoundTag> onSave) {
        super(title);
        this.root = root;
        this.props = props;
        this.onSave = onSave;
    }

    protected CompoundTag tag(Props.Prop p) {
        if (p.group().equals("cfg")) {
            if (!root.contains("cfg")) root.put("cfg", new CompoundTag());
            return root.getCompound("cfg");
        }
        return root;
    }

    protected int rowsPerPage() {
        return Math.max(3, (height - 110) / 24);
    }

    protected void addExtraButtons(int y) {
    }

    @Override
    protected void init() {
        refresh.clear();
        labels.clear();
        int cols = width > 420 ? 2 : 1;
        int colW = cols == 2 ? 190 : Math.min(300, width - 20);
        int rows = rowsPerPage();
        int per = rows * cols;
        int pages = Math.max(1, (props.size() + per - 1) / per);
        page = Math.min(page, pages - 1);
        int x0 = width / 2 - (cols * colW + (cols - 1) * 10) / 2;
        int y0 = 40;
        for (int i = page * per; i < Math.min(props.size(), (page + 1) * per); i++) {
            Props.Prop p = props.get(i);
            int k = i - page * per;
            int x = x0 + (k / rows) * (colW + 10), y = y0 + (k % rows) * 24;
            addProp(p, x, y, colW);
        }
        int by = height - 52;
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page = (page + pages - 1) % pages;
                rebuildWidgets();
            }).bounds(width / 2 - 150, by, 30, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                page = (page + 1) % pages;
                rebuildWidgets();
            }).bounds(width / 2 + 120, by, 30, 20).build());
        }
        addExtraButtons(by);
        addRenderableWidget(Button.builder(Component.translatable("gui.trafficcraft.save"), b -> {
            onSave.accept(root);
            onClose();
        }).bounds(width / 2 - 104, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.trafficcraft.cancel"), b -> onClose()).bounds(width / 2 + 4, height - 28, 100, 20).build());
    }

    private void addProp(Props.Prop p, int x, int y, int w) {
        Component label = Component.translatable("prop.trafficcraft." + p.key().replaceFirst("^m_.*", "model"), p.key().startsWith("m_") ?
                com.trafficcraft.vehicle.VehicleSpecs.get(p.key().substring(2)).name : "");
        int lw = w / 2;
        switch (p.kind()) {
            case BOOL, FLAG -> {
                Button b = Button.builder(Component.empty(), btn -> {
                    if (p.kind() == Props.Kind.FLAG) root.putInt("flags", root.getInt("flags") ^ p.bit());
                    else tag(p).putBoolean(p.key(), !tag(p).getBoolean(p.key()));
                }).bounds(x + lw, y, w - lw, 20).build();
                Runnable r = () -> {
                    boolean on = p.kind() == Props.Kind.FLAG ? (root.getInt("flags") & p.bit()) != 0 : tag(p).getBoolean(p.key());
                    b.setMessage(Component.translatable(on ? "gui.trafficcraft.on" : "gui.trafficcraft.off").withStyle(s -> s.withColor(on ? 0x60E060 : 0xE06060)));
                };
                r.run();
                refresh.add(r);
                addRenderableWidget(b);
            }
            case ENUM -> {
                Button b = Button.builder(Component.empty(), btn -> {
                    String cur = tag(p).getString(p.key());
                    int idx = 0;
                    for (int k = 0; k < p.options().length; k++) if (p.options()[k].equalsIgnoreCase(cur)) idx = k;
                    if (p.key().equals("mode") && p.group().equals("root")) tag(p).putInt(p.key(), (tag(p).getInt(p.key()) + 1) % p.options().length);
                    else tag(p).putString(p.key(), p.options()[(idx + 1) % p.options().length]);
                }).bounds(x + lw, y, w - lw, 20).build();
                Runnable r = () -> {
                    String v = p.key().equals("mode") && p.group().equals("root") ? p.options()[Math.floorMod(tag(p).getInt(p.key()), p.options().length)]
                            : tag(p).contains(p.key()) ? tag(p).getString(p.key()) : p.options()[0];
                    b.setMessage(Component.translatable("opt.trafficcraft." + v.toLowerCase()));
                };
                r.run();
                refresh.add(r);
                addRenderableWidget(b);
            }
            case INT, FLOAT -> {
                int bw = 20;
                Button minus = Button.builder(Component.literal("-"), btn -> step(p, -1)).bounds(x + lw, y, bw, 20).build();
                Button plus = Button.builder(Component.literal("+"), btn -> step(p, 1)).bounds(x + w - bw, y, bw, 20).build();
                addRenderableWidget(minus);
                addRenderableWidget(plus);
                Button val = Button.builder(Component.empty(), btn -> step(p, hasShiftDown() ? -10 : 10)).bounds(x + lw + bw + 2, y, w - lw - 2 * bw - 4, 20).build();
                Runnable r = () -> val.setMessage(Component.literal(p.kind() == Props.Kind.INT ? String.valueOf(tag(p).getInt(p.key()))
                        : String.format("%.2f", tag(p).getFloat(p.key()))));
                r.run();
                refresh.add(r);
                addRenderableWidget(val);
            }
            case TEXT -> {
                EditBox box = new EditBox(font, x + lw, y, w - lw, 20, Component.empty());
                box.setValue(tag(p).getString(p.key()));
                box.setResponder(v -> tag(p).putString(p.key(), v));
                addRenderableWidget(box);
            }
        }
        labels.add(new Label(label, x, y + 6, lw - 4));
    }

    private void step(Props.Prop p, int dir) {
        CompoundTag t = tag(p);
        if (p.kind() == Props.Kind.INT) {
            int v = (int) Math.max(p.min(), Math.min(p.max(), t.getInt(p.key()) + dir * p.step()));
            t.putInt(p.key(), v);
        } else {
            float v = (float) Math.max(p.min(), Math.min(p.max(), t.getFloat(p.key()) + dir * p.step()));
            t.putFloat(p.key(), Math.round(v * 100) / 100f);
        }
    }

    record Label(Component text, int x, int y, int w) {}

    private final List<Label> labels = new ArrayList<>();

    @Override
    protected void rebuildWidgets() {
        labels.clear();
        super.rebuildWidgets();
    }

    @Override
    public void tick() {
        refresh.forEach(Runnable::run);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        for (Label l : labels) g.drawString(font, font.substrByWidth(l.text(), l.w()).getString(), l.x(), l.y(), 0xD0D8E0, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
