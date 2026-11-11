package com.rpbusiness.client;

import com.rpbusiness.client.screen.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/** Client-only entry points (only ever called on the client thread). */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openScreen(String screen, CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        switch (screen) {
            case "business" -> {
                if (mc.screen instanceof BusinessScreen bs && bs.sameBusiness(data)) bs.update(data);
                else mc.setScreen(new BusinessScreen(data));
            }
            case "supplier" -> {
                if (mc.screen instanceof SupplierScreen ss && ss.sameBusiness(data)) ss.update(data);
                else mc.setScreen(new SupplierScreen(data));
            }
            case "register" -> mc.setScreen(new RegisterScreen(NbtUtils.readBlockPos(data, "a").orElse(null), NbtUtils.readBlockPos(data, "b").orElse(null)));
            default -> {}
        }
    }

    public static void openRegistration(BlockPos a, BlockPos b) { Minecraft.getInstance().setScreen(new RegisterScreen(a, b)); }

    public static void openRecipeBook() { Minecraft.getInstance().setScreen(new RecipeBookScreen(null)); }
}
