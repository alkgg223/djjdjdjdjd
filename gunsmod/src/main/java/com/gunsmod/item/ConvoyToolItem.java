package com.gunsmod.item;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.HelicopterEntity;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.VehicleEntity;
import com.gunsmod.entity.VehicleKind;
import com.gunsmod.entity.ai.Waypoints;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Convoy planner. Use it: a menu to pick the side and the vehicles of the column, the head first, then the one behind
 * it and so on; helicopters fly cover over the column. Attack (left click): the convoy rolls out where you look, crews
 * aboard, the column facing your way, every vehicle keeping its place behind the one ahead. A route planner in the
 * other hand gives the column its route.
 */
public class ConvoyToolItem extends Item {
    public static final String KEY = "gunsmod_convoy";
    public static final int MAX = 10, JEEP = -1;
    /** client: opens the planning screen */
    public static Consumer<ItemStack> openScreen = s -> { };

    public ConvoyToolItem(Properties props) {
        super(props);
    }

    // ------------------------------------------------------------------------------------ the plan on the item
    public record Plan(BotTeam team, int[] kinds) {}

    @Nullable
    public static Plan read(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        CompoundTag t = d.copyTag().getCompound(KEY);
        if (!t.contains("kinds")) return null;
        int[] k = t.getIntArray("kinds");
        List<Integer> ok = new ArrayList<>();
        for (int v : k) if (valid(v) && ok.size() < MAX) ok.add(v);
        return new Plan(BotTeam.byId(t.getInt("team")), ok.stream().mapToInt(Integer::intValue).toArray());
    }

    public static void write(ItemStack s, int team, int[] kinds) {
        CompoundTag t = new CompoundTag();
        t.putInt("team", team);
        t.putIntArray("kinds", kinds);
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = d == null ? new CompoundTag() : d.copyTag();
        root.put(KEY, t);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    /** what can go in a column: every ground vehicle and helicopter (jets and the C-RAM trailer cannot) */
    public static boolean valid(int k) {
        if (k == JEEP) return true;
        if (k < 0 || k >= VehicleKind.values().length) return false;
        VehicleKind v = VehicleKind.values()[k];
        return v.group != VehicleKind.Group.JET && v != VehicleKind.CRAM;
    }

    public static ItemStack stackOf(int k, BotTeam team) {
        if (k == JEEP) return new ItemStack(team == BotTeam.RED ? Reg.RED_JEEP.get() : Reg.GREEN_JEEP.get());
        return new ItemStack(Reg.vehicleItem(VehicleKind.values()[k], team));
    }

    public static boolean heli(int k) {
        return k >= 0 && VehicleKind.values()[k].group == VehicleKind.Group.HELI;
    }

    /** length of the vehicle along the column, in blocks */
    private static double length(int k) {
        if (k == JEEP) return 5;
        VehicleKind v = VehicleKind.values()[k];
        if (v.group == VehicleKind.Group.TANK) return 8 * v.tank().scale;
        if (v == VehicleKind.TOW_HUMVEE) return 5;
        return v == VehicleKind.BTR ? 7.5 : 11;
    }

    // ------------------------------------------------------------------------------------ use
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (level.isClientSide) openScreen.accept(s);
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        return true;
    }

    /** the convoy rolls out where the player looks (server, from the attack key) */
    public static void spawn(ServerPlayer p, ItemStack s) {
        Plan plan = read(s);
        if (plan == null || plan.kinds().length == 0) {
            p.displayClientMessage(Component.translatable("gunsmod.msg.convoy_empty").withStyle(ChatFormatting.RED), true);
            return;
        }
        ServerLevel sl = p.serverLevel();
        HitResult hr = p.pick(140, 1f, false);
        float yaw = p.getYRot();
        Vec3 fwd = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 at = hr.getType() == HitResult.Type.BLOCK ? hr.getLocation() : p.position().add(fwd.scale(14));
        Waypoints route = p.getOffhandItem().getItem() instanceof RouteToolItem ? RouteToolItem.read(p.getOffhandItem()) : null;
        BotTeam team = plan.team();
        double back = 0, lastLen = 0;
        Entity ahead = null, head = null;
        int made = 0, helis = 0;
        for (int k : plan.kinds()) {
            if (heli(k)) continue;
            double len = length(k);
            double gap = ahead == null ? 0 : lastLen / 2 + len / 2 + 4;
            back += gap;
            Mob m = place(sl, k, team, at.subtract(fwd.scale(back)), yaw);
            if (m == null) continue;
            if (route != null) {
                if (m instanceof VehicleEntity v) v.waypoints = route.fresh();
                else if (m instanceof JeepEntity j) j.waypoints = route.fresh();
            }
            if (ahead != null) {
                if (m instanceof VehicleEntity v) {
                    v.convoyAhead = ahead.getUUID();
                    v.convoyGap = (float) gap;
                } else if (m instanceof JeepEntity j) {
                    j.convoyAhead = ahead.getUUID();
                    j.convoyGap = (float) gap;
                }
            }
            crew(sl, m, team);
            ahead = m;
            if (head == null) head = m;
            lastLen = len;
            made++;
        }
        // helicopters set down beside the column and take off to fly cover over its head
        for (int k : plan.kinds()) {
            if (!heli(k)) continue;
            Vec3 spot = at.add(right.scale(16 + helis * 14)).subtract(fwd.scale(6 + helis * 10));
            Mob m = place(sl, k, team, spot, yaw);
            if (m == null) continue;
            if (m instanceof HelicopterEntity h && head != null) h.escort = head.getUUID();
            crew(sl, m, team);
            helis++;
            made++;
        }
        p.displayClientMessage(Component.translatable(route != null ? "gunsmod.msg.convoy_route" : "gunsmod.msg.convoy_out", made), true);
        sl.playSound(null, BlockPos.containing(at), Reg.sound("ui_confirm"), SoundSource.PLAYERS, 1.5f, 0.7f);
    }

    @Nullable
    private static Mob place(ServerLevel sl, int k, BotTeam team, Vec3 spot, float yaw) {
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(spot.x), Mth.floor(spot.z));
        Mob m = VehicleItem.create(sl, stackOf(k, team), spot.x, y + 0.1, spot.z, yaw);
        if (m == null) return null;
        VehicleItem.link(m, BlockPos.containing(spot.x, y, spot.z), null);
        sl.addFreshEntity(m);
        sl.sendParticles(Reg.HOLO.get(), m.getX(), m.getY() + 1, m.getZ(), 30, 1.2, 0.8, 1.2, 0.02);
        return m;
    }

    /** a driver (and a gunner on a jeep) of the convoy's side */
    private static void crew(ServerLevel sl, Mob m, BotTeam team) {
        int n = m instanceof JeepEntity ? 2 : 1;
        for (int i = 0; i < n; i++) {
            BotEntity b = new BotEntity(Reg.BOT.get(), sl);
            b.moveTo(m.getX(), m.getY(), m.getZ(), m.getYRot(), 0);
            b.setup(team, sl.random, null);
            b.home = m.blockPosition();
            sl.addFreshEntity(b);
            if (m instanceof JeepEntity j) j.board(b, i == 0 ? JeepEntity.SEAT_DRIVER : JeepEntity.SEAT_GUNNER);
            else if (m instanceof VehicleEntity v) v.board(b, 0);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return read(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        Plan plan = read(stack);
        if (plan != null && plan.kinds().length > 0) {
            tip.add(Component.translatable("gunsmod.team." + plan.team().name().toLowerCase()).withStyle(plan.team().chat));
            for (int i = 0; i < plan.kinds().length; i++) {
                tip.add(Component.literal((i + 1) + ". ").append(stackOf(plan.kinds()[i], plan.team()).getHoverName()).withStyle(ChatFormatting.GRAY));
            }
        }
        tip.add(Component.translatable("gunsmod.tip.convoy1").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable("gunsmod.tip.convoy2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
