package com.realweather.client.vehicle;

import com.mojang.blaze3d.platform.InputConstants;
import com.realweather.ModContent;
import com.realweather.client.ClientWeather;
import com.realweather.client.radar.RadarCanvas;
import com.realweather.client.radar.RadarProducts;
import com.realweather.client.tornado.ClientTornadoes;
import com.realweather.net.VehicleInputPayload;
import com.realweather.server.Tornado;
import com.realweather.vehicle.StormVehicle;
import com.realweather.vehicle.VehicleEnv;
import com.realweather.vehicle.VehicleType;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of the vehicles: the driver's controls and buttons, the weather the physics feels here, the snow and
 * sand building up on the windshield (and wiped off where the blades pass), engine and road sounds, the radar on
 * the dash, and the instruments that measure a tornado's wind.
 */
public final class VehicleClient {
    public static final KeyMapping WIPERS = new KeyMapping("key.realweather.wipers", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.realweather");
    public static final KeyMapping HORN = new KeyMapping("key.realweather.horn", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.realweather");
    public static final KeyMapping SPIKES = new KeyMapping("key.realweather.spikes", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.realweather");
    public static final KeyMapping LIGHTS = new KeyMapping("key.realweather.lights", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, "key.categories.realweather");

    private static final Int2ObjectOpenHashMap<RadarCanvas> SCREENS = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<EngineSound> ENGINES = new Int2ObjectOpenHashMap<>();
    private static final float[] CELL_NOISE = new float[VehicleRenderer.GRID_X * VehicleRenderer.GRID_Y];
    private static float lastThrottle, lastSteer;
    private static boolean lastHandbrake;
    private static int sendTimer;

    static {
        RandomSource r = RandomSource.create(77);
        for (int i = 0; i < CELL_NOISE.length; i++) {
            CELL_NOISE[i] = 0.55F + r.nextFloat() * 0.9F;
        }
    }

    private VehicleClient() {}

    public static void init() {
        StormVehicle.clientTick = VehicleClient::tick;
        VehicleEnv.client = VehicleClient::weather;
    }

    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(WIPERS);
        event.register(HORN);
        event.register(SPIKES);
        event.register(LIGHTS);
    }

    // ------------------------------------------------------------------------------------ driver input
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !(p.getVehicle() instanceof StormVehicle v) || v.getControllingPassenger() != p) {
            WIPERS.consumeClick();
            HORN.consumeClick();
            SPIKES.consumeClick();
            LIGHTS.consumeClick();
            return;
        }
        boolean handbrake = p.input.jumping;
        v.handbrakeIn = handbrake;
        int actions = 0;
        while (WIPERS.consumeClick()) {
            actions |= 1;
        }
        while (HORN.consumeClick()) {
            actions |= 2;
        }
        while (SPIKES.consumeClick()) {
            actions |= 4;
        }
        while (LIGHTS.consumeClick()) {
            actions |= 8;
        }
        if ((actions & 2) != 0) {
            mc.getSoundManager().play(new SimpleSoundInstance(v.kind == VehicleType.SEDAN ? ModContent.VEHICLE_HORN.get() : ModContent.VEHICLE_HORN_TRUCK.get(),
                    SoundSource.NEUTRAL, 1F, 1F, RandomSource.create(), v.getX(), v.getY(), v.getZ()));
        }
        sendTimer++;
        if (actions != 0 || handbrake != lastHandbrake || Math.abs(v.throttleIn - lastThrottle) > 0.05F || Math.abs(v.steerIn - lastSteer) > 0.05F
                || sendTimer >= 10) {
            PacketDistributor.sendToServer(new VehicleInputPayload(v.getId(), v.throttleIn, v.steerIn, handbrake, actions));
            lastThrottle = v.throttleIn;
            lastSteer = v.steerIn;
            lastHandbrake = handbrake;
            sendTimer = 0;
        }
    }

    // ------------------------------------------------------------------------------------ weather felt by the physics
    private static void weather(StormVehicle v, VehicleEnv e) {
        e.sandstorm = ClientWeather.camSand;
        e.blizzard = ClientWeather.camBlizzard;
        e.snowfall = ClientWeather.camSnow;
        e.wet = Math.max(ClientWeather.wetness, ClientWeather.camRain);
        for (ClientTornadoes.T t : ClientTornadoes.all()) {
            VehicleEnv.wind(e, v, t.x, t.z, t.groundY, t.coreRadius(), Tornado.WIND[t.rating] * t.contact * (0.4F + 0.6F * t.strength),
                    Tornado.LIFT[t.rating]);
        }
    }

    // ------------------------------------------------------------------------------------ per-vehicle client tick
    private static void tick(StormVehicle v) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = (ClientLevel) v.level();
        double speed = v.displaySpeed();
        // Engine revs through five gears.
        double frac = Math.abs(speed) / v.kind.maxSpeed();
        double gears = frac * 5.0;
        int gear = (int) Math.min(4, Math.floor(gears));
        float target = v.getControllingPassenger() == null ? 0F : (float) (850 + (gears - gear) * 4200 + gear * 300);
        if (v.getControllingPassenger() != null && Math.abs(speed) < 0.02) {
            target = 850F + Math.max(0F, v.displayThrottle()) * 2500F;
        }
        v.rpm += (target - v.rpm) * 0.2F;
        // Instruments: anemometer cups follow the wind, the dish and turret turn.
        float wind = (float) (ClientWeather.wind * 10F + Math.abs(speed) * 20F) + localWind(v) * 20F;
        v.anemometer += Math.min(60F, wind * 3F);
        if (v.kind == VehicleType.PICKUP) {
            v.dishSpin += 9F;
        }
        if (v.kind == VehicleType.INTERCEPT) {
            ClientTornadoes.T t = nearest(v, 400);
            float want = v.turretYaw;
            if (t != null) {
                float bearing = (float) Math.toDegrees(Math.atan2(-(t.x - v.getX()), t.z - v.getZ()));
                want = Mth.wrapDegrees(bearing - v.getYRot()) * -1F + 180F;
            } else {
                want = v.turretYaw + 0.6F;
            }
            v.turretYaw += Mth.clamp(Mth.wrapDegrees(want - v.turretYaw), -3F, 3F);
        }
        measure(v);
        windshield(v, level);
        sounds(v, mc);
        RadarCanvas c = SCREENS.get(v.getId());
        if (c != null) {
            c.ox = v.getX();
            c.oz = v.getZ();
            c.sweep(level, 1);
            c.upload();
            if (level.getGameTime() - c.lastUsed > 200) {
                c.close();
                SCREENS.remove(v.getId());
            }
        }
    }

    public static RadarCanvas screen(StormVehicle v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || v.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 24 * 24) {
            return null;
        }
        RadarCanvas c = SCREENS.get(v.getId());
        if (c == null) {
            c = new RadarCanvas(128);
            c.range = 512F;
            c.ox = v.getX();
            c.oz = v.getZ();
            c.product = RadarProducts.REF;
            c.redrawAll(mc.level);
            SCREENS.put(v.getId(), c);
        }
        c.lastUsed = mc.level.getGameTime();
        return c;
    }

    // ------------------------------------------------------------------------------------ measuring a tornado
    /** Range of the chase instruments, blocks. */
    public static final double PROBE_RANGE = 150.0;

    private static void measure(StormVehicle v) {
        if (!v.kind.instruments) {
            return;
        }
        ClientTornadoes.T t = nearest(v, PROBE_RANGE);
        if (t == null) {
            v.measuredTornado = -1;
            v.measuredWind *= 0.9F;
            v.measuredPeak = 0F;
            return;
        }
        float peak = RadarProducts.peakWind(t);
        if (v.kind == VehicleType.PICKUP) {
            // Mobile Doppler: reads the rotation of the whole vortex from up to 150 blocks away.
            v.measuredWind += (peak - v.measuredWind) * 0.1F;
        } else {
            // In-situ probe: what the wind actually does at the vehicle (only with the spikes down and the mast up).
            v.measuredWind += (localWindMps(v, t) - v.measuredWind) * 0.25F;
        }
        boolean reading = v.kind == VehicleType.PICKUP || v.spikeDeploy > 0.9F;
        if (reading) {
            v.measuredPeak = Math.max(v.measuredPeak, v.measuredWind);
        }
        if (v.measuredTornado != t.id) {
            v.measuredTornado = t.id;
            v.measuredPeak = 0F;
            if (v.getControllingPassenger() == Minecraft.getInstance().player) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModContent.VEHICLE_BEEP.get(), 1F, 0.8F));
            }
        }
    }

    public static ClientTornadoes.T nearest(StormVehicle v, double range) {
        ClientTornadoes.T best = null;
        double bd = range * range;
        for (ClientTornadoes.T t : ClientTornadoes.all()) {
            double d = (t.x - v.getX()) * (t.x - v.getX()) + (t.z - v.getZ()) * (t.z - v.getZ());
            if (d < bd && t.strength > 0.05F) {
                bd = d;
                best = t;
            }
        }
        return best;
    }

    /** Wind of the nearest tornado at the vehicle, m/s (EF-scale speeds). */
    public static float localWindMps(StormVehicle v, ClientTornadoes.T t) {
        float rc = Math.max(2F, t.coreRadius());
        double d = Math.sqrt((t.x - v.getX()) * (t.x - v.getX()) + (t.z - v.getZ()) * (t.z - v.getZ()));
        float peak = RadarProducts.peakWind(t);
        return (float) (d < rc ? peak * d / rc : peak * Math.pow(rc / d, 0.7));
    }

    private static float localWind(StormVehicle v) {
        ClientTornadoes.T t = nearest(v, 200);
        return t == null ? 0F : localWindMps(v, t) / 20F;
    }

    // ------------------------------------------------------------------------------------ windshield
    public static float wiperAngle(StormVehicle v, float pt) {
        float phase = Mth.lerp(pt, v.prevWiperPhase, v.wiperPhase);
        return 92F * (1F - Mth.cos(phase)) / 2F;
    }

    private static void windshield(StormVehicle v, ClientLevel level) {
        int n = VehicleRenderer.GRID_X * VehicleRenderer.GRID_Y;
        if (v.glassSnow == null) {
            v.glassSnow = new float[n];
            v.glassSand = new float[n];
        }
        Minecraft mc = Minecraft.getInstance();
        if (v.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 64 * 64) {
            return;
        }
        BlockPos top = BlockPos.containing(v.getX(), v.getY() + v.kind.size[1] + 0.5, v.getZ());
        boolean covered = level.getHeight(Heightmap.Types.MOTION_BLOCKING, top.getX(), top.getZ()) > top.getY();
        float headwind = 1F + (float) Math.abs(v.displaySpeed()) * 0.8F;
        float snowRate = covered ? 0F : (ClientWeather.camSnow * 0.0015F + ClientWeather.camBlizzard * 0.0045F + ClientWeather.camHail * 0.002F) * headwind;
        float sandRate = covered ? 0F : ClientWeather.camSand * 0.0045F * headwind;
        boolean washing = !covered && ClientWeather.camRain > 0.2F;
        for (int i = 0; i < n; i++) {
            if (snowRate > 0) {
                v.glassSnow[i] = Math.min(1F, v.glassSnow[i] + snowRate * CELL_NOISE[i]);
            } else {
                v.glassSnow[i] = Math.max(0F, v.glassSnow[i] - (washing ? 0.003F : 0.0004F));
            }
            if (sandRate > 0) {
                v.glassSand[i] = Math.min(1F, v.glassSand[i] + sandRate * CELL_NOISE[n - 1 - i]);
            } else {
                v.glassSand[i] = Math.max(0F, v.glassSand[i] - (washing ? 0.004F : 0.0003F));
            }
        }
        // Body: snow and hail on the roof and hood.
        float bodyRate = covered ? 0F : ClientWeather.camSnow * 0.0007F + ClientWeather.camBlizzard * 0.0018F;
        v.bodySnow = bodyRate > 0 ? Math.min(1F, v.bodySnow + bodyRate) : Math.max(0F, v.bodySnow - (washing ? 0.002F : 0.0003F));
        // The wipers clear what they pass over.
        if (Mth.sin(v.wiperPhase) != 0F || v.wipers() > 0) {
            float[][] ws = VehicleRenderer.windshield(v.kind);
            Vector3f[] f = VehicleRenderer.frame(ws);
            float blade = wiperAngle(v, 1F) * Mth.DEG_TO_RAD;
            float prev = wiperAngle(v, 0F) * Mth.DEG_TO_RAD;
            float lo = Math.min(blade, prev) - 0.12F;
            float hi = Math.max(blade, prev) + 0.12F;
            float len = VehicleRenderer.wiperLength(v.kind);
            boolean moved = false;
            for (float[] pivot : VehicleRenderer.wipers(v.kind)) {
                for (int j = 0; j < VehicleRenderer.GRID_Y; j++) {
                    for (int i = 0; i < VehicleRenderer.GRID_X; i++) {
                        float[] c = VehicleRenderer.point(ws, (i + 0.5F) / VehicleRenderer.GRID_X, (j + 0.5F) / VehicleRenderer.GRID_Y);
                        float dx = c[0] - pivot[0], dy = c[1] - pivot[1], dz = c[2] - pivot[2];
                        float du = dx * f[0].x + dy * f[0].y + dz * f[0].z;
                        float dv = dx * f[1].x + dy * f[1].y + dz * f[1].z;
                        float r = Mth.sqrt(du * du + dv * dv);
                        if (r > len * 1.05F || r < len * 0.15F) {
                            continue;
                        }
                        float a = (float) Math.atan2(dv, du);
                        if (a >= lo && a <= hi) {
                            int k = j * VehicleRenderer.GRID_X + i;
                            if (v.glassSnow[k] > 0.05F || v.glassSand[k] > 0.05F) {
                                moved = true;
                            }
                            v.glassSnow[k] *= 0.15F;
                            v.glassSand[k] *= 0.25F;
                        }
                    }
                }
            }
            // A swish at each end of the stroke.
            if (Math.signum(Mth.sin(v.prevWiperPhase)) != Math.signum(Mth.sin(v.wiperPhase)) && v.distanceToSqr(mc.player) < 30 * 30) {
                level.playLocalSound(v.getX(), v.getY() + 1, v.getZ(), ModContent.VEHICLE_WIPER.get(), SoundSource.NEUTRAL,
                        moved ? 0.6F : 0.35F, moved ? 0.85F : 1F, false);
            }
        }
    }

    /** Average snow and sand left on the windshield, for the dashboard warning. */
    public static float glassCover(StormVehicle v) {
        if (v.glassSnow == null) {
            return 0F;
        }
        float s = 0F;
        for (int i = 0; i < v.glassSnow.length; i++) {
            s += Math.max(v.glassSnow[i], v.glassSand[i]);
        }
        return s / v.glassSnow.length;
    }

    // ------------------------------------------------------------------------------------ sounds
    private static void sounds(StormVehicle v, Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        EngineSound s = ENGINES.get(v.getId());
        boolean running = v.getControllingPassenger() != null && v.isAlive();
        if (running && (s == null || s.isStopped()) && v.distanceToSqr(mc.player) < 48 * 48) {
            if (s == null) {
                mc.getSoundManager().play(new SimpleSoundInstance(ModContent.VEHICLE_START.get(), SoundSource.NEUTRAL, 0.9F, 1F, RandomSource.create(),
                        v.getX(), v.getY(), v.getZ()));
            }
            s = new EngineSound(v);
            ENGINES.put(v.getId(), s);
            mc.getSoundManager().play(s);
        } else if (!running && s != null) {
            s.finish();
            ENGINES.remove(v.getId());
        }
        if (v.hornTicks() == 11 && v.getControllingPassenger() != mc.player) {
            mc.getSoundManager().play(new SimpleSoundInstance(v.kind == VehicleType.SEDAN ? ModContent.VEHICLE_HORN.get() : ModContent.VEHICLE_HORN_TRUCK.get(),
                    SoundSource.NEUTRAL, 1F, 1F, RandomSource.create(), v.getX(), v.getY(), v.getZ()));
        }
        if (v.sliding && v.onGround() && Math.abs(v.displaySpeed()) > 0.25 && v.tickCount % 6 == 0) {
            v.level().playLocalSound(v.getX(), v.getY(), v.getZ(), ModContent.VEHICLE_SKID.get(), SoundSource.NEUTRAL, 0.5F,
                    0.9F + v.getRandom().nextFloat() * 0.2F, false);
        }
    }

    /** Engine note following the revs; heavier diesel for the pickup and the intercept vehicle. */
    static final class EngineSound extends AbstractTickableSoundInstance {
        private final StormVehicle v;

        EngineSound(StormVehicle v) {
            super(v.kind == VehicleType.SEDAN ? ModContent.VEHICLE_ENGINE.get() : ModContent.VEHICLE_DIESEL.get(), SoundSource.NEUTRAL, RandomSource.create());
            this.v = v;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.6F;
            this.x = v.getX();
            this.y = v.getY();
            this.z = v.getZ();
        }

        void finish() {
            stop();
        }

        @Override
        public void tick() {
            if (!v.isAlive() || v.isRemoved()) {
                stop();
                return;
            }
            x = v.getX();
            y = v.getY() + 0.8;
            z = v.getZ();
            pitch = Mth.clamp(0.55F + v.rpm / 5200F, 0.5F, 2F);
            volume = 0.45F + 0.4F * Math.max(0F, v.displayThrottle());
        }
    }
}
