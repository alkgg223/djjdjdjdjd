package com.gunsmod.client;

import com.gunsmod.GunsMod;
import com.gunsmod.Reg;
import com.gunsmod.client.particle.BlastParticles;
import com.gunsmod.client.particle.GunParticles;
import com.gunsmod.client.render.BakedRender;
import com.gunsmod.client.render.ArmoredCarRenderer;
import com.gunsmod.client.render.BotRenderer;
import com.gunsmod.client.render.GunRenderer;
import com.gunsmod.client.render.HelicopterRenderer;
import com.gunsmod.client.render.MineRenderer;
import com.gunsmod.client.render.HeavyLauncherRenderer;
import com.gunsmod.client.render.JeepItemRenderer;
import com.gunsmod.client.render.JeepRenderer;
import com.gunsmod.client.render.JetRenderer;
import com.gunsmod.client.render.LauncherRenderer;
import com.gunsmod.client.render.MissileRenderer;
import com.gunsmod.client.render.ProjectileRenderer;
import com.gunsmod.client.render.SpawnerRenderer;
import com.gunsmod.client.render.TankRenderer;
import com.gunsmod.client.render.VehicleSpawnerRenderer;
import com.gunsmod.client.screen.ArenaScreen;
import com.gunsmod.client.screen.BotScreen;
import com.gunsmod.client.screen.ModTableScreen;
import com.gunsmod.client.screen.SpawnerScreen;
import com.gunsmod.client.screen.WorkbenchScreen;
import com.gunsmod.client.sound.JeepSound;
import com.gunsmod.client.sound.VehicleSound;
import com.gunsmod.entity.CarType;
import com.gunsmod.entity.FxHooks;
import com.gunsmod.entity.HeliType;
import com.gunsmod.entity.HelicopterEntity;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.TankType;
import com.gunsmod.entity.VehicleEntity;
import com.gunsmod.entity.VehicleKind;
import net.minecraft.client.Minecraft;
import com.gunsmod.gun.GunType;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = GunsMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        JeepEntity.clientTick = JeepSound::tickJeep;
        VehicleEntity.clientTick = VehicleSound::tick;
        VehicleCamera.init();
        JetControl.init();
        com.gunsmod.item.CommandRadioItem.openScreen = () -> net.minecraft.client.Minecraft.getInstance().setScreen(new com.gunsmod.client.screen.SquadScreen());
        com.gunsmod.item.ConvoyToolItem.openScreen = s -> net.minecraft.client.Minecraft.getInstance().setScreen(new com.gunsmod.client.screen.ConvoyScreen(s));
        FxHooks.light = (en, lvl) -> ClientLights.light(en, en.position(), lvl, 2);
        // helicopter collective: jump climbs, sneak descends
        HelicopterEntity.liftInput = () -> {
            var o = Minecraft.getInstance().options;
            return o.keyJump.isDown() ? 1 : o.keyShift.isDown() ? -1 : 0;
        };
    }

    @SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent e) {
        Item[] guns = new Item[GunType.values().length];
        for (GunType g : GunType.values()) guns[g.ordinal()] = Reg.gun(g);
        e.registerItem(GunRenderer.EXTENSIONS, guns);
        java.util.List<Item> vehicles = new java.util.ArrayList<>(java.util.List.of(Reg.GREEN_JEEP.get(), Reg.RED_JEEP.get()));
        for (VehicleKind k : VehicleKind.values()) {
            vehicles.add(Reg.GREEN_VEHICLES.get(k).get());
            vehicles.add(Reg.RED_VEHICLES.get(k).get());
        }
        e.registerItem(JeepItemRenderer.EXTENSIONS, vehicles.toArray(new Item[0]));
        e.registerItem(com.gunsmod.client.render.AirDefenseItemRenderer.EXTENSIONS, Reg.AD_ITEMS.stream().map(d -> (Item) d.get()).toArray(Item[]::new));
        e.registerItem(com.gunsmod.client.render.DroneRenderer.Item.EXTENSIONS, Reg.FPV_DRONE.get(), Reg.SHAHED.get(),
                Reg.RECON_DRONE.get(), Reg.BOMBER_DRONE.get(), Reg.SURVEIL_DRONE.get(), Reg.LANCET.get());
        e.registerItem(com.gunsmod.client.render.ObjectiveRenderer.Item.EXTENSIONS, Reg.CAPTURE_POINT.asItem(), Reg.CTF_FLAG.asItem(), Reg.OBJECTIVE_TARGET.asItem(),
                Reg.CONVOY_DESTINATION.asItem(), Reg.MISSION_CONTROL.asItem());
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional e) {
        for (GunType g : GunType.values()) {
            GunGeometry.Geo geo = GunGeometry.get(g.id);
            if (geo == null) continue;
            for (String part : geo.parts()) e.register(BakedRender.id("gun/" + g.id + "/" + part));
        }
        for (String p : new String[]{"entity/jeep/body", "entity/jeep/mark_green", "entity/jeep/mark_red", "entity/jeep/turret", "entity/jeep/gun",
                "entity/jeep/steering", "entity/jeep/gear",
                "entity/jeep/wheel", "entity/gear/helmet", "entity/gear/vest", "block/launcher_erector", "block/heavy_launcher_base",
                "block/heavy_launcher_erector", "fx/muzzle_flash", "entity/tank/body", "entity/tank/mark_green", "entity/tank/mark_red",
                "entity/tank/turret", "entity/tank/gun", "entity/tank/roadwheel", "entity/tank/idler", "entity/tank/sprocket",
                "entity/tank/track_bottom", "entity/tank/track_top", "entity/heli/body", "entity/heli/mark_green", "entity/heli/mark_red",
                "entity/heli/chin", "entity/heli/rotor", "entity/heli/tail_rotor", "entity/vehicle_ammo/shell", "entity/vehicle_ammo/rocket",
                "entity/vehicle_ammo/atgm", "entity/vehicle_ammo/sam", "entity/vehicle_ammo/bomb", "entity/jeep_tow/launcher", "entity/car/wheel", "entity/mine/body"}) {
            e.register(BakedRender.id(p));
        }
        for (TankType t : TankType.values()) {
            e.register(BakedRender.id("entity/" + t.geo.model() + "/tmark_green"));
            e.register(BakedRender.id("entity/" + t.geo.model() + "/tmark_red"));
            if (t == TankType.ABRAMS) continue;
            for (String p : new String[]{"body", "mark_green", "mark_red", "turret", "gun"}) e.register(BakedRender.id("entity/" + t.geo.model() + "/" + p));
        }
        for (HeliType h : HeliType.values()) {
            if (h == HeliType.COBRA) continue;
            for (String p : new String[]{"body", "mark_green", "mark_red", "hub", "blade"}) e.register(BakedRender.id("entity/" + h.geo.model() + "/" + p));
            if (h.geo.tail() != null) {
                e.register(BakedRender.id("entity/" + h.geo.model() + "/tail_hub"));
                e.register(BakedRender.id("entity/" + h.geo.model() + "/tail_blade"));
            }
            if (h.geo.chin() != null) e.register(BakedRender.id("entity/" + h.geo.model() + "/chin"));
        }
        for (String p : new String[]{"body", "mark_green", "mark_red", "gear_nose", "gear_main", "aim9_l", "aim9_r", "mav_0", "mav_1", "mav_2",
                "mav_3", "burner", "cockpit", "stick", "throttle", "body_fp"}) {
            e.register(BakedRender.id("entity/f16/" + p));
        }
        e.register(BakedRender.id("entity/car/civ_wheel"));
        for (String p : com.gunsmod.entity.WreckPieceEntity.KINDS) e.register(BakedRender.id("entity/debris/" + p));
        for (String p : com.gunsmod.client.render.DroneRenderer.PARTS) e.register(BakedRender.id("entity/drone/" + p));
        for (String p : com.gunsmod.client.render.ObjectiveRenderer.PARTS) e.register(BakedRender.id("entity/objective/" + p));
        for (var g : com.gunsmod.block.airdef.AirDefenseGeo.GEO.entrySet()) {
            e.register(BakedRender.id("entity/airdef/" + g.getKey() + "/base"));
            e.register(BakedRender.id("entity/airdef/" + g.getKey() + "/head"));
            if (g.getValue().muzzles().length > 0) e.register(BakedRender.id("entity/airdef/" + g.getKey() + "/arm"));
        }
        for (com.gunsmod.entity.JetType jt : com.gunsmod.entity.JetType.values()) {
            if (jt == com.gunsmod.entity.JetType.F16) continue;
            for (String p : JetRenderer.parts(jt)) e.register(BakedRender.id("entity/" + jt.model + "/" + p));
        }
        for (CarType c : CarType.values()) {
            String[] parts = c.civilian() ? new String[]{"body", "mark_green", "mark_red"} : new String[]{"body", "mark_green", "mark_red", "turret", "gun"};
            for (String p : parts) e.register(BakedRender.id("entity/" + c.geo.model() + "/" + p));
            if (c.geo.radar() != null) e.register(BakedRender.id("entity/" + c.geo.model() + "/radar"));
        }
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(Reg.BOT.get(), BotRenderer::new);
        e.registerEntityRenderer(Reg.JEEP.get(), JeepRenderer::new);
        e.registerEntityRenderer(Reg.JEEP_TOW.get(), JeepRenderer::new);
        e.registerEntityRenderer(Reg.TANK.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.TANK_LEOPARD.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.TANK_T90.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.TANK_M1A2SEP.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.TANK_LEOPARD2A7.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.TANK_T14.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.TANK_T80BVM.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.TANK_MERKAVA4.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.TANK_K2.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.TANK_TYPE99A.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.TANK_BRADLEY.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.TANK_BMP3.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.TANK_CV90.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.TANK_BMD4.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.TANK_CHALLENGER.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.TANK_MAUS.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.TANK_OBJ279.get(), TankRenderer::new);
        e.registerEntityRenderer(Reg.HELICOPTER.get(), HelicopterRenderer::new);
        e.registerEntityRenderer(Reg.HELI_APACHE.get(), HelicopterRenderer::new);
        e.registerEntityRenderer(Reg.HELI_HIND.get(), HelicopterRenderer::new);
        e.registerEntityRenderer(Reg.HELI_KA52.get(), HelicopterRenderer::new);
        e.registerEntityRenderer(Reg.BTR.get(), ArmoredCarRenderer::new);
        e.registerEntityRenderer(Reg.PANTSIR.get(), ArmoredCarRenderer::new);
        e.registerEntityRenderer(Reg.HIMARS.get(), ArmoredCarRenderer::new);
        e.registerEntityRenderer(Reg.GRAD.get(), ArmoredCarRenderer::new);
        e.registerEntityRenderer(Reg.CRAM.get(), ArmoredCarRenderer::new);
        e.registerEntityRenderer(Reg.PICKUP.get(), ArmoredCarRenderer::new);
        e.registerEntityRenderer(Reg.CAR_STRYKER.get(), ArmoredCarRenderer::new);
        e.registerEntityRenderer(Reg.CAR_BOXER.get(), ArmoredCarRenderer::new);
        e.registerEntityRenderer(Reg.CAR_MRAP.get(), ArmoredCarRenderer::new);
        e.registerEntityRenderer(Reg.CAR_JLTV.get(), ArmoredCarRenderer::new);
        e.registerEntityRenderer(Reg.CAR_TRUCK.get(), ArmoredCarRenderer::new);
        e.registerEntityRenderer(Reg.CAR_FUEL_TRUCK.get(), ArmoredCarRenderer::new);
        e.registerEntityRenderer(Reg.CAR_AMMO_TRUCK.get(), ArmoredCarRenderer::new);
        e.registerEntityRenderer(Reg.CAR_COMMAND_TRUCK.get(), ArmoredCarRenderer::new);
        e.registerEntityRenderer(Reg.CAR_RECOVERY_TRUCK.get(), ArmoredCarRenderer::new);
        e.registerEntityRenderer(Reg.SUV.get(), ArmoredCarRenderer::new);
        e.registerEntityRenderer(Reg.F16.get(), JetRenderer::new);
        e.registerEntityRenderer(Reg.B2.get(), JetRenderer::new);
        e.registerEntityRenderer(Reg.F22.get(), JetRenderer::new);
        e.registerEntityRenderer(Reg.F35.get(), JetRenderer::new);
        e.registerEntityRenderer(Reg.SU35.get(), JetRenderer::new);
        e.registerEntityRenderer(Reg.MIG29.get(), JetRenderer::new);
        e.registerEntityRenderer(Reg.A10.get(), JetRenderer::new);
        e.registerEntityRenderer(Reg.GUIDED_MISSILE.get(), ProjectileRenderer::new);
        e.registerEntityRenderer(Reg.FLARE_ENTITY.get(), NoopRenderer::new);
        e.registerEntityRenderer(Reg.WRECK_PIECE.get(), com.gunsmod.client.render.WreckPieceRenderer::new);
        e.registerEntityRenderer(Reg.DRONE.get(), com.gunsmod.client.render.DroneRenderer::new);
        e.registerEntityRenderer(Reg.MINE.get(), MineRenderer::new);
        e.registerEntityRenderer(Reg.ROCKET.get(), ProjectileRenderer::new);
        e.registerEntityRenderer(Reg.GRENADE.get(), ProjectileRenderer::new);
        e.registerEntityRenderer(Reg.MISSILE.get(), MissileRenderer::new);
        e.registerBlockEntityRenderer(Reg.LAUNCHER_BE.get(), LauncherRenderer::new);
        e.registerBlockEntityRenderer(Reg.HEAVY_LAUNCHER_BE.get(), HeavyLauncherRenderer::new);
        e.registerBlockEntityRenderer(Reg.SPAWNER_BE.get(), SpawnerRenderer::new);
        e.registerBlockEntityRenderer(Reg.VEHICLE_SPAWNER_BE.get(), VehicleSpawnerRenderer::new);
        e.registerBlockEntityRenderer(Reg.RADAR_BE.get(), com.gunsmod.client.render.AirDefenseRenderer::new);
        e.registerBlockEntityRenderer(Reg.OBJECTIVE_BE.get(), com.gunsmod.client.render.ObjectiveRenderer::new);
        e.registerBlockEntityRenderer(Reg.DEFENSE_BE.get(), com.gunsmod.client.render.AirDefenseRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(Reg.MUZZLE_FLASH.get(), GunParticles.FlashProvider::new);
        e.registerSpriteSet(Reg.TRACER.get(), GunParticles.TracerProvider::new);
        e.registerSpriteSet(Reg.SMOKE.get(), s -> new GunParticles.SmokeProvider(s, false));
        e.registerSpriteSet(Reg.MISSILE_SMOKE.get(), s -> new GunParticles.SmokeProvider(s, true));
        e.registerSpriteSet(Reg.FLAME.get(), GunParticles.FlameProvider::new);
        e.registerSpriteSet(Reg.SPARK.get(), GunParticles.SparkProvider::new);
        e.registerSpriteSet(Reg.SHELL.get(), GunParticles.ShellProvider::new);
        e.registerSpriteSet(Reg.HOLO.get(), GunParticles.HoloProvider::new);
        e.registerSpriteSet(Reg.FIREBALL.get(), BlastParticles.FireballProvider::new);
        e.registerSpriteSet(Reg.DUST_CLOUD.get(), BlastParticles.DustProvider::new);
        e.registerSpriteSet(Reg.SHOCKWAVE.get(), BlastParticles.ShockwaveProvider::new);
        e.registerSpriteSet(Reg.EMBER.get(), BlastParticles.EmberProvider::new);
        e.registerSpriteSet(Reg.BLAST_FLASH.get(), BlastParticles.FlashProvider::new);
        e.registerSpriteSet(Reg.FLARE.get(), GunParticles.FlareProvider::new);
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(GunClient.RELOAD);
        e.register(GunClient.SEAT);
        e.register(GunClient.HORN);
        e.register(GunClient.FLARES);
        e.register(GunClient.TEAM);
        e.register(GunClient.PRONE);
        e.register(GunClient.SCOPE_ZOOM);
        e.register(GunClient.SQUAD);
        e.register(GunClient.MAP);
        e.register(JetControl.VIEW);
    }

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent e) {
        e.register(Reg.WORKBENCH_MENU.get(), WorkbenchScreen::new);
        e.register(Reg.MOD_TABLE_MENU.get(), ModTableScreen::new);
        e.register(Reg.SPAWNER_MENU.get(), SpawnerScreen::new);
        e.register(Reg.ARENA_MENU.get(), ArenaScreen::new);
        e.register(Reg.BOT_MENU.get(), BotScreen::new);
    }

    @SubscribeEvent
    public static void gui(RegisterGuiLayersEvent e) {
        e.registerBelow(VanillaGuiLayers.HOTBAR, Reg.id("scope"), (g, dt) -> g.drawManaged(() -> Hud.scope(g, dt)));
        e.registerAbove(VanillaGuiLayers.CROSSHAIR, Reg.id("crosshair"), (g, dt) -> g.drawManaged(() -> Hud.crosshair(g, dt)));
        e.registerAbove(VanillaGuiLayers.HOTBAR, Reg.id("ammo"), (g, dt) -> g.drawManaged(() -> Hud.ammo(g, dt)));
        e.registerAbove(VanillaGuiLayers.HOTBAR, Reg.id("vehicle"), (g, dt) -> g.drawManaged(() -> Hud.vehicle(g, dt)));
        e.registerAbove(VanillaGuiLayers.CROSSHAIR, Reg.id("bot_info"), (g, dt) -> g.drawManaged(() -> Hud.botInfo(g, dt)));
        e.registerAboveAll(Reg.id("blast_flash"), Hud::flash);
        // night vision: the post effect over the world, before any other layer
        e.registerBelowAll(Reg.id("night_vision"), NightVision::layer);
        e.registerBelowAll(Reg.id("thermal"), Thermal::layer);
        e.registerAbove(VanillaGuiLayers.HOTBAR, Reg.id("match"), MatchHud::layer);
        e.registerAboveAll(Reg.id("drone"), DroneClient::layer);
        e.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, Reg.id("battle_score"), (g, dt) -> g.drawManaged(() -> BattleHud.render(g, dt)));
        e.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, Reg.id("squad"), (g, dt) -> g.drawManaged(() -> SquadHud.render(g, dt)));
    }
}
