#!/bin/bash
# Temporary: print vanilla shader sources and API signatures used by the mod.
set +e
echo "=== JARS"
find build "$HOME/.gradle" -name '*.jar' 2>/dev/null | grep -iE 'neoforge|minecraft|client-extra' | grep -v sources | xargs ls -la 2>/dev/null | awk '{print $5, $9}' | sort -n | tail -15
CLS=$(find "$PWD/build" "$HOME/.gradle" -name 'neoforge-21.1.*.jar' 2>/dev/null | grep -v -e sources -e universal -e userdev -e installer | xargs ls -S 2>/dev/null | head -1)
RES=$(find "$HOME/.gradle" -name 'minecraft_1.21.1_client.jar' 2>/dev/null | head -1)
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
for c in net.minecraft.server.packs.repository.PackSource 'net.minecraft.world.level.biome.Biome$Precipitation' net.minecraft.client.resources.sounds.SimpleSoundInstance; do echo "=== CLASS $c"; $J "$c" 2>&1 | grep -v 'lambda\$'; done
$J net.minecraft.client.renderer.ShaderInstance | grep -iE 'uniform|GAME_TIME'
$J com.mojang.blaze3d.shaders.Uniform | grep -E 'public void set\('
$J net.minecraft.client.sounds.SoundManager | grep -iE 'play|isActive|stop'
$J net.minecraft.client.Minecraft | grep -iE 'useShaderTransparency|useFancyGraphics|getSoundManager|particleEngine|isPaused|levelRenderer|public .*options;|getCameraEntity|gameRenderer;'
$J net.minecraft.client.particle.ParticleEngine | grep -iE ' add|createParticle'
$J net.minecraft.client.multiplayer.ClientLevel | grep -iE 'addParticle|playLocalSound|getBiome|getSkyColor|getCloudColor'
$J 'net.neoforged.neoforge.common.ModConfigSpec$Builder' | grep -vE 'lambda|private'
$J net.minecraft.world.entity.ai.village.poi.PoiTypes | grep -i HOME
$J net.minecraft.world.level.material.Fluid | grep -iE 'randomTick|isRandomlyTicking|animateTick'
$J 'net.minecraft.world.level.block.state.BlockBehaviour$Properties' | grep -iE 'ofFullCopy|noLootTable|replaceable|liquid|noCollission|strength|pushReaction|mapColor\('
$J net.minecraft.world.level.block.state.BlockBehaviour | grep -iE 'canBeReplaced'
$J 'net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase' | grep -iE 'canBeReplaced|isFaceSturdy|isSolidRender|blocksMotion|is\(|getFluidState|isAir'
$J net.minecraft.world.entity.Entity | grep -iE 'moveTo\(|isInWater\(|getBbHeight|blockPosition|level\(\)'
$J net.minecraft.client.renderer.texture.TextureAtlasSprite | grep -iE 'getU\(|getV\(|getU0|getV0'
echo "=== END DUMP"
