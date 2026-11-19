package com.trafficcraft.client.gui;

import com.trafficcraft.net.RoadEditPayload;
import com.trafficcraft.road.Lane;
import com.trafficcraft.road.NodeType;
import com.trafficcraft.road.Props;
import com.trafficcraft.road.RoadEdge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Opens the editor screens requested by the server. */
public final class Editors {
    public static void open(String kind, CompoundTag d) {
        Minecraft mc = Minecraft.getInstance();
        switch (kind) {
            case "tool" -> mc.setScreen(new PropertyScreen(Component.translatable("gui.trafficcraft.tool_title"), d, Props.tool(),
                    t -> PacketDistributor.sendToServer(new RoadEditPayload("tool", t))));
            case "node" -> mc.setScreen(new NodeScreen(d));
            case "edge" -> mc.setScreen(new EdgeScreen(d));
            case "sign" -> mc.setScreen(new SignScreen(d));
            case "lights" -> mc.setScreen(new LightsScreen(d));
            default -> { }
        }
    }

    /** Node editor: type selector + type specific properties. */
    static class NodeScreen extends PropertyScreen {
        NodeScreen(CompoundTag d) {
            super(Component.translatable("gui.trafficcraft.node_title", d.getInt("id")), d, Props.node(NodeType.byName(d.getString("type"))),
                    t -> PacketDistributor.sendToServer(new RoadEditPayload("node", t)));
            Props.defaults(NodeType.byName(d.getString("type")), d.getCompound("cfg"));
            if (!d.contains("cfg")) d.put("cfg", new CompoundTag());
        }

        @Override
        protected void addExtraButtons(int y) {
            NodeType t = NodeType.byName(root.getString("type"));
            addRenderableWidget(Button.builder(Component.translatable("gui.trafficcraft.type").append(": ").append(Component.translatable("node.trafficcraft." + t.name().toLowerCase())),
                    b -> {
                        NodeType[] all = NodeType.values();
                        NodeType nt = all[(t.ordinal() + 1) % all.length];
                        root.putString("type", nt.name());
                        CompoundTag cfg = root.getCompound("cfg");
                        Props.defaults(nt, cfg);
                        root.put("cfg", cfg);
                        props = Props.node(nt);
                        rebuildWidgets();
                    }).bounds(width / 2 - 114, y, 150, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.trafficcraft.delete"), b -> {
                CompoundTag x = new CompoundTag();
                x.putInt("id", root.getInt("id"));
                PacketDistributor.sendToServer(new RoadEditPayload("delete_node", x));
                onClose();
            }).bounds(width / 2 + 40, y, 74, 20).build());
        }
    }

    /** Edge editor with a per-lane direction/type list. */
    static class EdgeScreen extends Screen {
        final CompoundTag d;
        final RoadEdge e;

        EdgeScreen(CompoundTag d) {
            super(Component.translatable("gui.trafficcraft.edge_title", d.getInt("id")));
            this.d = d;
            this.e = RoadEdge.load(d);
        }

        @Override
        protected void init() {
            int x = width / 2 - 155, y = 34;
            addRenderableWidget(Button.builder(Component.translatable("prop.trafficcraft.width").append(": " + e.width), b -> {
                e.width = e.width >= 20 ? 2 : e.width + (hasShiftDown() ? -0.5f : 0.5f);
                rebuildWidgets();
            }).bounds(x, y, 150, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("prop.trafficcraft.speed").append(": " + Math.round(e.speed) + " km/h"), b -> {
                e.speed = e.speed >= 200 ? 10 : e.speed + (hasShiftDown() ? -10 : 10);
                rebuildWidgets();
            }).bounds(x + 160, y, 150, 20).build());
            y += 24;
            addRenderableWidget(Button.builder(Component.translatable("prop.trafficcraft.overtake").append(": ").append(onOff(e.overtake)), b -> {
                e.overtake = !e.overtake;
                rebuildWidgets();
            }).bounds(x, y, 150, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("prop.trafficcraft.no_trucks").append(": ").append(onOff((e.restrict & RoadEdge.R_NO_TRUCKS) != 0)), b -> {
                e.restrict ^= RoadEdge.R_NO_TRUCKS;
                rebuildWidgets();
            }).bounds(x + 160, y, 150, 20).build());
            y += 24;
            addRenderableWidget(Button.builder(Component.translatable("prop.trafficcraft.lanes").append(": " + e.lanes.size() + "  +"), b -> {
                if (e.lanes.size() < 6) e.lanes.add(new Lane(1, Lane.NORMAL));
                rebuildWidgets();
            }).bounds(x, y, 150, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.trafficcraft.remove_lane"), b -> {
                if (e.lanes.size() > 1) e.lanes.remove(e.lanes.size() - 1);
                rebuildWidgets();
            }).bounds(x + 160, y, 150, 20).build());
            y += 28;
            for (int i = 0; i < e.lanes.size(); i++) {
                final int k = i;
                Lane l = e.lanes.get(i);
                addRenderableWidget(Button.builder(Component.literal((i + 1) + ": ").append(Component.translatable(l.dir() > 0 ? "gui.trafficcraft.dir_ab" : "gui.trafficcraft.dir_ba")),
                        b -> {
                            e.lanes.set(k, new Lane(-e.lanes.get(k).dir(), e.lanes.get(k).type()));
                            rebuildWidgets();
                        }).bounds(x, y, 150, 18).build());
                addRenderableWidget(Button.builder(Component.translatable("lane.trafficcraft." + Lane.TYPE_NAMES[l.type()]), b -> {
                    e.lanes.set(k, new Lane(e.lanes.get(k).dir(), (e.lanes.get(k).type() + 1) % Lane.TYPE_NAMES.length));
                    rebuildWidgets();
                }).bounds(x + 160, y, 150, 18).build());
                y += 20;
            }
            y += 6;
            EditBox name = new EditBox(font, x, y, 310, 18, Component.translatable("prop.trafficcraft.name"));
            name.setValue(e.name);
            name.setHint(Component.translatable("prop.trafficcraft.name"));
            name.setResponder(v -> e.name = v);
            addRenderableWidget(name);
            int by = height - 28;
            addRenderableWidget(Button.builder(Component.translatable("gui.trafficcraft.save"), b -> {
                CompoundTag t = e.save();
                PacketDistributor.sendToServer(new RoadEditPayload("edge", t));
                onClose();
            }).bounds(width / 2 - 155, by, 74, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.trafficcraft.save_repave"), b -> {
                CompoundTag t = e.save();
                t.putBoolean("repave", true);
                PacketDistributor.sendToServer(new RoadEditPayload("edge", t));
                onClose();
            }).bounds(width / 2 - 77, by, 74, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.trafficcraft.delete"), b -> {
                CompoundTag t = new CompoundTag();
                t.putInt("id", e.id);
                PacketDistributor.sendToServer(new RoadEditPayload("delete_edge", t));
                onClose();
            }).bounds(width / 2 + 3, by, 74, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.trafficcraft.cancel"), b -> onClose()).bounds(width / 2 + 81, by, 74, 20).build());
        }

        static Component onOff(boolean b) {
            return Component.translatable(b ? "gui.trafficcraft.on" : "gui.trafficcraft.off");
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            super.render(g, mx, my, pt);
            g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    /** Text of direction / road-name signs. */
    static class SignScreen extends Screen {
        final CompoundTag d;

        SignScreen(CompoundTag d) {
            super(Component.translatable("gui.trafficcraft.sign_title"));
            this.d = d;
        }

        @Override
        protected void init() {
            for (int i = 0; i < 3; i++) {
                final String key = "line" + i;
                EditBox b = new EditBox(font, width / 2 - 100, height / 2 - 40 + i * 24, 200, 20, Component.empty());
                b.setMaxLength(24);
                b.setValue(d.getString(key));
                b.setResponder(v -> d.putString(key, v));
                addRenderableWidget(b);
            }
            addRenderableWidget(Button.builder(Component.translatable("gui.trafficcraft.save"), b -> {
                PacketDistributor.sendToServer(new RoadEditPayload("sign_text", d));
                onClose();
            }).bounds(width / 2 - 50, height / 2 + 40, 100, 20).build());
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            super.render(g, mx, my, pt);
            g.drawCenteredString(font, title, width / 2, height / 2 - 60, 0xFFFFFF);
        }
    }

    /** Traffic control centre: manual / automatic control of every signalised junction. */
    static class LightsScreen extends Screen {
        final List<CompoundTag> nodes = new ArrayList<>();

        LightsScreen(CompoundTag d) {
            super(Component.translatable("gui.trafficcraft.lights_title"));
            for (Tag t : d.getList("nodes", Tag.TAG_COMPOUND)) nodes.add((CompoundTag) t);
        }

        @Override
        protected void init() {
            int y = 36;
            for (CompoundTag n : nodes) {
                if (y > height - 40) break;
                int id = n.getInt("id");
                int x = width / 2 - 170;
                String[] cmds = {"auto", "manual", "next", "flash", "off"};
                for (int i = 0; i < cmds.length; i++) {
                    String c = cmds[i];
                    addRenderableWidget(Button.builder(Component.translatable("opt.trafficcraft." + c), b -> {
                        CompoundTag t = new CompoundTag();
                        t.putInt("id", id);
                        t.putString("cmd", c);
                        PacketDistributor.sendToServer(new RoadEditPayload("light", t));
                    }).bounds(x + 110 + i * 46, y, 44, 18).build());
                }
                y += 22;
            }
            addRenderableWidget(Button.builder(Component.translatable("gui.trafficcraft.close"), b -> onClose()).bounds(width / 2 - 50, height - 28, 100, 20).build());
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            super.render(g, mx, my, pt);
            g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
            int y = 41;
            for (CompoundTag n : nodes) {
                if (y > height - 40) break;
                String mode = n.getCompound("cfg").getString("mode");
                g.drawString(font, "#" + n.getInt("id") + "  (" + (int) n.getDouble("x") + ", " + (int) n.getDouble("z") + ") " + (mode.isEmpty() ? "auto" : mode),
                        width / 2 - 170, y, 0xD0D8E0, false);
                y += 22;
            }
            if (nodes.isEmpty()) g.drawCenteredString(font, Component.translatable("gui.trafficcraft.no_lights"), width / 2, 60, 0xA0A0A0);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
