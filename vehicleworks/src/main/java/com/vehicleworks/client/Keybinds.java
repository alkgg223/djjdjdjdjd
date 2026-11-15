package com.vehicleworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** All VehicleWorks key bindings (rebindable under Options → Controls → VehicleWorks). */
public final class Keybinds {
    public static final String CATEGORY = "key.categories.vehicleworks";
    public static final KeyMapping YAW_LEFT = key("yaw_left", GLFW.GLFW_KEY_Z);
    public static final KeyMapping YAW_RIGHT = key("yaw_right", GLFW.GLFW_KEY_C);
    public static final KeyMapping THROTTLE_UP = key("throttle_up", GLFW.GLFW_KEY_R);
    public static final KeyMapping THROTTLE_DOWN = key("throttle_down", GLFW.GLFW_KEY_F);
    public static final KeyMapping PITCH_UP = key("pitch_up", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping PITCH_DOWN = key("pitch_down", GLFW.GLFW_KEY_UP);
    public static final KeyMapping ENGINES = key("engines", GLFW.GLFW_KEY_I);
    public static final KeyMapping GEAR = key("gear", GLFW.GLFW_KEY_G);
    public static final KeyMapping FLAPS = key("flaps", GLFW.GLFW_KEY_V);
    public static final KeyMapping ARM = key("master_arm", GLFW.GLFW_KEY_J);
    public static final KeyMapping ASSIST = key("assist", GLFW.GLFW_KEY_H);
    public static final KeyMapping MOUSE_AIM = key("mouse_aim", GLFW.GLFW_KEY_M);
    public static final KeyMapping MENU = key("vehicle_menu", GLFW.GLFW_KEY_N);
    public static final KeyMapping PARKING = key("parking_brake", GLFW.GLFW_KEY_P);
    public static final KeyMapping BRAKE = key("brake", GLFW.GLFW_KEY_B);
    public static final KeyMapping AUTOPILOT = key("autopilot", GLFW.GLFW_KEY_O);
    public static final KeyMapping PANEL = key("panel", GLFW.GLFW_KEY_X);
    public static final KeyMapping FIRE_3 = key("fire_3", GLFW.GLFW_KEY_LEFT_ALT);
    public static final KeyMapping FIRE_4 = key("fire_4", GLFW.GLFW_KEY_K);
    public static final KeyMapping FIRE_5 = key("fire_5", GLFW.GLFW_KEY_U);
    public static final KeyMapping FIRE_6 = key("fire_6", GLFW.GLFW_KEY_Y);
    public static final KeyMapping[] ALL = {YAW_LEFT, YAW_RIGHT, THROTTLE_UP, THROTTLE_DOWN, PITCH_UP, PITCH_DOWN, ENGINES, GEAR, FLAPS,
            ARM, ASSIST, MOUSE_AIM, MENU, PARKING, BRAKE, AUTOPILOT, PANEL, FIRE_3, FIRE_4, FIRE_5, FIRE_6};

    private Keybinds() {}

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.vehicleworks." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    public static void register(RegisterKeyMappingsEvent e) {
        for (KeyMapping k : ALL) e.register(k);
    }
}
