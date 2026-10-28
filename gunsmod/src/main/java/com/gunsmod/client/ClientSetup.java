package com.gunsmod.client;

import com.gunsmod.GunsMod;
import com.gunsmod.Reg;
import com.gunsmod.client.particle.GunParticles;
import com.gunsmod.client.render.BakedRender;
import com.gunsmod.client.render.BotRenderer;
import com.gunsmod.client.render.GunRenderer;
import com.gunsmod.client.render.JeepItemRenderer;
import com.gunsmod.client.render.JeepRenderer;
import com.gunsmod.client.render.LauncherRenderer;
import com.gunsmod.client.render.MissileRenderer;
import com.gunsmod.client.render.ProjectileRenderer;
import com.gunsmod.client.render.SpawnerRenderer;
import com.gunsmod.client.screen.ArenaScreen;
import com.gunsmod.client.screen.BotScreen;
import com.gunsmod.client.screen.ModTableScreen;
import com.gunsmod.client.screen.SpawnerScreen;
import com.gunsmod.client.screen.WorkbenchScreen;
import com.gunsmod.client.sound.JeepSound;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.gun.GunType;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
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
    }

    @SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent e) {
        Item[] guns = new Item[GunType.values().length];
        for (GunType g : GunType.values()) guns[g.ordinal()] = Reg.gun(g);
        e.registerItem(GunRenderer.EXTENSIONS, guns);
        e.registerItem(JeepItemRenderer.EXTENSIONS, Reg.GREEN_JEEP.get(), Reg.RED_JEEP.get());
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional e) {
        for (GunType g : GunType.values()) {
            GunGeometry.Geo geo = GunGeometry.get(g.id);
            if (geo == null) continue;
            for (String part : geo.parts()) e.register(BakedRender.id("gun/" + g.id + "/" + part));
        }
        for (String p : new String[]{"entity/jeep/body", "entity/jeep/mark_green", "entity/jeep/mark_red", "entity/jeep/turret", "entity/jeep/gun",
                "entity/jeep/wheel", "entity/gear/helmet", "entity/gear/vest", "block/launcher_erector", "fx/muzzle_flash"}) {
            e.register(BakedRender.id(p));
        }
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(Reg.BOT.get(), BotRenderer::new);
        e.registerEntityRenderer(Reg.JEEP.get(), JeepRenderer::new);
        e.registerEntityRenderer(Reg.ROCKET.get(), ProjectileRenderer::new);
        e.registerEntityRenderer(Reg.GRENADE.get(), ProjectileRenderer::new);
        e.registerEntityRenderer(Reg.MISSILE.get(), MissileRenderer::new);
        e.registerBlockEntityRenderer(Reg.LAUNCHER_BE.get(), LauncherRenderer::new);
        e.registerBlockEntityRenderer(Reg.SPAWNER_BE.get(), SpawnerRenderer::new);
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
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(GunClient.RELOAD);
        e.register(GunClient.SEAT);
        e.register(GunClient.HORN);
        e.register(GunClient.ORDER);
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
        e.registerBelow(VanillaGuiLayers.HOTBAR, Reg.id("scope"), Hud::scope);
        e.registerAbove(VanillaGuiLayers.CROSSHAIR, Reg.id("crosshair"), Hud::crosshair);
        e.registerAbove(VanillaGuiLayers.HOTBAR, Reg.id("ammo"), Hud::ammo);
        e.registerAbove(VanillaGuiLayers.HOTBAR, Reg.id("vehicle"), Hud::vehicle);
        e.registerAbove(VanillaGuiLayers.CROSSHAIR, Reg.id("bot_info"), Hud::botInfo);
    }
}
