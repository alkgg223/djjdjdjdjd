package com.arkragnarok.economy;

import com.arkragnarok.registry.ModMenus;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** recipe screen of a station: the player's inventory below, the station's recipes above (click = craft) */
public class StationMenu extends AbstractContainerMenu {
    public final Station station;
    public final BlockPos pos;
    private final Inventory inv;

    public StationMenu(int id, Inventory inv, Station station, BlockPos pos) {
        super(ModMenus.STATION.get(), id);
        this.station = station;
        this.pos = pos;
        this.inv = inv;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 140 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 198));
    }

    public static StationMenu client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new StationMenu(id, inv, Station.values()[buf.readVarInt()], buf.readBlockPos());
    }

    public List<Recipes.Recipe> recipes() {
        return Recipes.of(station);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        List<Recipes.Recipe> rs = recipes();
        if (id < 0 || id >= rs.size()) return false;
        boolean ok = Recipes.craft(player.getInventory(), rs.get(id));
        if (ok) player.level().playSound(null, pos, station == Station.SMITHY ? SoundEvents.ANVIL_USE : station == Station.FORGE
                ? SoundEvents.FIRECHARGE_USE : station == Station.CAMPFIRE ? SoundEvents.CAMPFIRE_CRACKLE : SoundEvents.UI_STONECUTTER_TAKE_RESULT,
                SoundSource.BLOCKS, 0.7f, 1.0f);
        return ok;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64.0;
    }
}
