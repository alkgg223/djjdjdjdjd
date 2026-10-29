#!/bin/bash
# Temporary: print vanilla shader sources and API signatures used by the mod.
set +e
echo "=== JARS"
find build "$HOME/.gradle" -name '*.jar' 2>/dev/null | grep -iE 'neoforge|minecraft|client-extra' | grep -v sources | xargs ls -la 2>/dev/null | awk '{print $5, $9}' | sort -n | tail -15
CLS=$(find build "$HOME/.gradle" -name 'neoforge-21.1.*.jar' 2>/dev/null | grep -v -e sources -e universal -e userdev -e installer | xargs ls -S 2>/dev/null | head -1)
RES=$(find build "$HOME/.gradle" -name '*client-extra*.jar' 2>/dev/null | head -1)
echo "CLS=$CLS RES=$RES"
mkdir -p /tmp/res && cd /tmp/res && unzip -q -o "$RES" 'assets/minecraft/shaders/*' 'assets/minecraft/sounds.json' 'assets/minecraft/textures/block/water*' 'assets/minecraft/textures/environment/*' 2>/dev/null; cd - >/dev/null
for f in core/rendertype_translucent.json core/rendertype_translucent.vsh core/rendertype_translucent.fsh core/particle.json core/particle.vsh include/fog.glsl include/light.glsl; do echo "=== SHADER $f"; cat /tmp/res/assets/minecraft/shaders/$f; echo; done
echo "=== WATER TEX"; ls -la /tmp/res/assets/minecraft/textures/block/ | grep water; cat /tmp/res/assets/minecraft/textures/block/water_still.png.mcmeta /tmp/res/assets/minecraft/textures/block/water_flow.png.mcmeta
pip install -q pillow >/dev/null 2>&1; python3 - <<'PY'
from PIL import Image
for n in ['water_still','water_flow','water_overlay']:
    im=Image.open('/tmp/res/assets/minecraft/textures/block/%s.png'%n).convert('RGBA'); a=[p[3] for p in im.getdata()]; c=[p[0] for p in im.getdata()]
    print(n, im.size, 'alpha min/max/mean', min(a), max(a), sum(a)/len(a), 'r min/max', min(c), max(c))
PY
echo "=== SOUNDS"; python3 -c "
import json;d=json.load(open('/tmp/res/assets/minecraft/sounds.json'))
for k in ['weather.rain','weather.rain.above','entity.lightning_bolt.thunder','entity.lightning_bolt.impact','ambient.underwater.loop']: print(k, json.dumps(d.get(k)))"
J="javap -p -cp $CLS"
for c in \
 net.minecraft.client.renderer.DimensionSpecialEffects \
 'net.minecraft.client.renderer.DimensionSpecialEffects$OverworldEffects' \
 net.neoforged.neoforge.client.extensions.IDimensionSpecialEffectsExtension \
 net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent \
 'net.neoforged.neoforge.client.event.ViewportEvent$RenderFog' \
 'net.neoforged.neoforge.client.event.ViewportEvent$ComputeFogColor' \
 net.neoforged.neoforge.client.event.ViewportEvent \
 'net.minecraft.client.renderer.FogRenderer$FogMode' \
 net.minecraft.client.renderer.ItemBlockRenderTypes \
 net.minecraft.client.particle.SingleQuadParticle \
 net.minecraft.client.particle.TextureSheetParticle \
 net.minecraft.client.particle.ParticleRenderType \
 net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent \
 net.minecraft.core.particles.SimpleParticleType \
 net.minecraft.world.level.block.LiquidBlock \
 net.neoforged.neoforge.fluids.BaseFlowingFluid \
 'net.neoforged.neoforge.fluids.BaseFlowingFluid$Properties' \
 'net.neoforged.neoforge.fluids.BaseFlowingFluid$Source' \
 'net.neoforged.neoforge.fluids.FluidType$Properties' \
 net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions \
 net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent \
 net.minecraft.world.entity.ai.behavior.LocateHidingPlace \
 net.minecraft.world.entity.ai.behavior.SetHiddenState \
 net.minecraft.world.entity.ai.memory.WalkTarget \
 net.minecraft.client.resources.sounds.AbstractTickableSoundInstance \
 net.minecraft.client.resources.sounds.AbstractSoundInstance \
 net.neoforged.neoforge.client.event.sound.PlaySoundEvent \
 net.neoforged.neoforge.client.event.sound.SoundEvent \
 com.mojang.blaze3d.vertex.Tesselator \
 com.mojang.blaze3d.vertex.BufferUploader \
 net.minecraft.client.renderer.LightTexture \
 net.neoforged.neoforge.client.event.RegisterGuiLayersEvent \
 net.neoforged.neoforge.client.gui.VanillaGuiLayers \
 net.neoforged.neoforge.event.AddPackFindersEvent \
 net.minecraft.server.packs.repository.Pack \
 net.neoforged.neoforge.client.gui.ConfigurationScreen \
 net.neoforged.neoforge.client.gui.IConfigScreenFactory \
 'net.minecraft.world.level.saveddata.SavedData$Factory' \
 net.neoforged.neoforge.network.registration.PayloadRegistrar \
 net.neoforged.neoforge.network.PacketDistributor \
 net.minecraft.world.entity.LightningBolt \
 net.neoforged.neoforge.client.event.ClientTickEvent \
 net.neoforged.neoforge.event.tick.LevelTickEvent \
 net.neoforged.neoforge.client.event.RegisterClientCommandsEvent \
 ; do echo "=== CLASS $c"; $J "$c" 2>&1 | grep -v 'private static final' | grep -v '^Compiled'; done
echo "=== GREP big classes"
$J net.minecraft.client.renderer.LevelRenderer | grep -iE 'rain|snow|getLightColor|ticks|weather'
$J net.minecraft.client.renderer.GameRenderer | grep -iE 'getParticleShader|getPositionTexColorShader|getPositionColorShader|getRendertypeTranslucent'
$J net.minecraft.client.multiplayer.ClientLevel | grep -iE 'flash|rain|thunder|effects'
$J net.minecraft.world.level.Level | grep -iE 'rain|thunder|flash|canSeeSky|getHeight\(|getHeightmapPos|isLoaded|getSeaLevel'
$J net.minecraft.server.level.ServerLevel | grep -iE 'weather|getEntities|isRaining|findLightning|getPoiManager|getDayTime|tickPrecipitation'
$J net.minecraft.world.level.biome.Biome | grep -iE 'precip|snow|rain|cold|warm|temperature|getWaterColor'
$J net.minecraft.world.entity.ai.Brain | grep -iE 'activity|memory|schedule'
$J net.minecraft.world.entity.Entity | grep -iE 'ticksFrozen|canFreeze|isInWaterOrRain|isInRain|getEyePosition\(\)'
$J com.mojang.blaze3d.systems.RenderSystem | grep -iE 'fog|depthMask|setShaderTexture|setShader\(|enableBlend|disableCull|setShaderColor|blendFunc|getShaderGameTime|ColorModulator'
$J com.mojang.blaze3d.vertex.VertexConsumer | grep -iE 'addVertex|setUv|setColor|setLight|setNormal|setOverlay'
$J com.mojang.blaze3d.vertex.BufferBuilder | grep -iE 'build|addVertex'
$J net.minecraft.client.renderer.FogRenderer | grep -iE 'static|setupFog|levelFogColor'
$J net.minecraft.world.level.block.SnowLayerBlock | grep -iE 'LAYERS|canSurvive'
$J net.minecraft.client.gui.GuiGraphics | grep -iE ' fill\(|blit\(|guiWidth|guiHeight|setColor'
$J net.minecraft.client.particle.Particle | grep -iE 'protected|public' | head -60
$J net.minecraft.client.Camera | grep -iE 'getPosition|getFluidInCamera|getBlockPosition|getLookVector|getYRot'
$J net.minecraft.world.entity.npc.Villager | grep -iE 'getBrain|refreshBrain|isSleeping'
$J net.minecraft.server.level.ServerPlayer | grep -iE 'serverLevel|getCommandSenderWorld'
$J net.minecraft.client.renderer.RenderStateShard | grep -iE 'TRANSLUCENT'
$J 'net.neoforged.neoforge.client.event.RenderLevelStageEvent$Stage' | grep -iE 'AFTER'
$J net.neoforged.neoforge.client.event.RenderLevelStageEvent | grep -iE 'public'
$J net.minecraft.world.level.levelgen.Heightmap | grep -i getFirstAvailable
$J net.minecraft.world.entity.ai.village.poi.PoiManager | grep -iE 'find|getRandom|findClosest'
echo "=== END DUMP"
