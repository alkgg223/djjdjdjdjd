package com.trafficcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class VehicleKeys {
    public static final String CAT = "key.categories.trafficcraft";
    public static final KeyMapping HORN = k("horn", GLFW.GLFW_KEY_H);
    public static final KeyMapping LIGHTS = k("lights", GLFW.GLFW_KEY_L);
    public static final KeyMapping HIGH_BEAM = k("high_beam", GLFW.GLFW_KEY_K);
    public static final KeyMapping IND_LEFT = k("indicator_left", GLFW.GLFW_KEY_Z);
    public static final KeyMapping IND_RIGHT = k("indicator_right", GLFW.GLFW_KEY_C);
    public static final KeyMapping HAZARD = k("hazard", GLFW.GLFW_KEY_X);
    public static final KeyMapping ENGINE = k("engine", GLFW.GLFW_KEY_V);
    public static final KeyMapping INTERIOR = k("interior_light", GLFW.GLFW_KEY_N);
    public static final KeyMapping SIREN = k("siren", GLFW.GLFW_KEY_G);
    public static final KeyMapping GEAR_MODE = k("gear_mode", GLFW.GLFW_KEY_M);
    public static final KeyMapping GEAR_UP = k("gear_up", GLFW.GLFW_KEY_R);
    public static final KeyMapping GEAR_DOWN = k("gear_down", GLFW.GLFW_KEY_Y);
    public static final KeyMapping MENU = k("vehicle_menu", GLFW.GLFW_KEY_U);
    public static final KeyMapping SCREEN_NEXT = k("screen_next", GLFW.GLFW_KEY_B);
    public static final KeyMapping SCREEN_OK = k("screen_ok", GLFW.GLFW_KEY_J);
    public static final KeyMapping[] ALL = {HORN, LIGHTS, HIGH_BEAM, IND_LEFT, IND_RIGHT, HAZARD, ENGINE, INTERIOR, SIREN, GEAR_MODE, GEAR_UP, GEAR_DOWN, MENU, SCREEN_NEXT, SCREEN_OK};

    private static KeyMapping k(String name, int key) {
        return new KeyMapping("key.trafficcraft." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, key, CAT);
    }
}
