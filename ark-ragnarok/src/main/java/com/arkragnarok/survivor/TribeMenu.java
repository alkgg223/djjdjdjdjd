package com.arkragnarok.survivor;

import com.arkragnarok.registry.ModMenus;
import com.arkragnarok.survivor.world.SurvivorWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The survivor management screen's data: every tribe (who, temperament, civilisation stage, base design and how far
 * it is built, current task, stores, dinosaurs, raids and defences, what it thinks of you, skills).  Buttons send
 * tribe * 256 + design (255 = let it choose by itself) to assign a blueprint.
 */
public class TribeMenu extends AbstractContainerMenu {
    public record Entry(UUID id, String name, String temperament, int stage, int design, boolean manual, int built, int total, String task,
                        @Nullable BlockPos base, int tames, int raids, int defended, int piecesBuilt, int piecesLost, int relation, String stock,
                        String skills, String relations) {}

    public final List<Entry> tribes;

    public TribeMenu(int id, Inventory inv, List<Entry> tribes) {
        super(ModMenus.TRIBES.get(), id);
        this.tribes = tribes;
    }

    public static void open(ServerPlayer sp) {
        List<Entry> l = snapshot(sp.serverLevel(), sp);
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new TribeMenu(id, inv, l), Component.translatable("screen.arkragnarok.tribes")),
                buf -> write(buf, l));
    }

    static List<Entry> snapshot(ServerLevel level, Player viewer) {
        SurvivorWorld w = SurvivorWorld.get(level);
        List<Entry> out = new ArrayList<>();
        for (SurvivorWorld.Tribe t : w.all()) {
            Entity e = level.getEntity(t.id);
            String task = e instanceof Survivor s ? s.task() : "away";
            StringBuilder stock = new StringBuilder();
            t.stock.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(8)
                    .forEach(en -> stock.append(en.getKey().replace("arkragnarok:", "").replace('_', ' ')).append(' ').append(en.getValue()).append(", "));
            StringBuilder rel = new StringBuilder();
            for (Map.Entry<UUID, Integer> r : t.relations.entrySet()) {
                SurvivorWorld.Tribe o = w.find(r.getKey());
                if (o == null) continue;
                rel.append(o.name.split(" ")[0]).append(' ').append(r.getValue()).append(", ");
            }
            int total = t.base == null ? 0 : Builder.plan(t).size();
            out.add(new Entry(t.id, t.name, t.personality().temperament(), t.stage, Math.max(0, t.design), t.manualDesign,
                    t.base == null ? 0 : Builder.builtCount(t), total, task, t.base, t.tames.size(), t.raids, t.defended, t.piecesBuilt, t.piecesLost,
                    t.relation(viewer.getUUID()), trim(stock), t.skills.summary(), trim(rel)));
        }
        return out;
    }

    private static String trim(StringBuilder b) {
        String s = b.toString();
        return s.endsWith(", ") ? s.substring(0, s.length() - 2) : s;
    }

    static void write(RegistryFriendlyByteBuf buf, List<Entry> l) {
        buf.writeVarInt(l.size());
        for (Entry e : l) {
            buf.writeUUID(e.id());
            buf.writeUtf(e.name());
            buf.writeUtf(e.temperament());
            buf.writeVarInt(e.stage());
            buf.writeVarInt(e.design());
            buf.writeBoolean(e.manual());
            buf.writeVarInt(e.built());
            buf.writeVarInt(e.total());
            buf.writeUtf(e.task());
            buf.writeBoolean(e.base() != null);
            if (e.base() != null) buf.writeBlockPos(e.base());
            buf.writeVarInt(e.tames());
            buf.writeVarInt(e.raids());
            buf.writeVarInt(e.defended());
            buf.writeVarInt(e.piecesBuilt());
            buf.writeVarInt(e.piecesLost());
            buf.writeInt(e.relation());
            buf.writeUtf(e.stock());
            buf.writeUtf(e.skills());
            buf.writeUtf(e.relations());
        }
    }

    public static TribeMenu client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            UUID uid = buf.readUUID();
            String name = buf.readUtf(), temp = buf.readUtf();
            int stage = buf.readVarInt(), design = buf.readVarInt();
            boolean manual = buf.readBoolean();
            int built = buf.readVarInt(), total = buf.readVarInt();
            String task = buf.readUtf();
            BlockPos base = buf.readBoolean() ? buf.readBlockPos() : null;
            int tames = buf.readVarInt(), raids = buf.readVarInt(), def = buf.readVarInt(), pb = buf.readVarInt(), pl = buf.readVarInt();
            int rel = buf.readInt();
            String stock = buf.readUtf(), skills = buf.readUtf(), rels = buf.readUtf();
            l.add(new Entry(uid, name, temp, stage, design, manual, built, total, task, base, tames, raids, def, pb, pl, rel, stock, skills, rels));
        }
        return new TribeMenu(id, inv, l);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        int ti = id / 256, design = id % 256;
        if (ti < 0 || ti >= tribes.size() || !(player.level() instanceof ServerLevel level)) return false;
        SurvivorWorld w = SurvivorWorld.get(level);
        SurvivorWorld.Tribe t = w.find(tribes.get(ti).id());
        if (t == null) return false;
        if (design == 255) {
            t.manualDesign = false;
        } else if (design < BaseDesign.COUNT) {
            t.manualDesign = true;
            t.design = design;
            if (t.base != null) Builder.settle(t, t.base, design, t.rotation);
        }
        w.setDirty();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
