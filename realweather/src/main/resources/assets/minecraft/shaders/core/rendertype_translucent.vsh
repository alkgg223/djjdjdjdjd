#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

// Real Weather: vanilla translucent terrain shader plus gentle waves on water.
// Water is recognised by the alpha of its texture (the mod's water textures use exactly WATER_ALPHA).

#define WATER_ALPHA (179.0 / 255.0)
#define TAU 6.2831853

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;
uniform float GameTime;
uniform vec4 RealWater;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec4 lightColor;
out vec3 viewPos;
out vec3 localPos;
out vec3 faceNormal;
out float waterFlag;

// The water textures are grey (tinted by the biome) with alpha exactly WATER_ALPHA.
float isWater(vec2 uv) {
    vec4 c = texture(Sampler0, uv);
    return (abs(c.a - WATER_ALPHA) < 0.003 && abs(c.r - c.g) < 0.004 && abs(c.g - c.b) < 0.004) ? 1.0 : 0.0;
}

// Sum of waves whose wavelengths divide 16 blocks, so they line up across chunk sections.
float waveHeight(vec2 p, float g) {
    float h = 0.0;
    h += sin(TAU * (p.x * 0.125 + p.y * 0.0625 + g * 250.0)) * 0.45;
    h += sin(TAU * (p.x * -0.0625 + p.y * 0.1875 + g * 325.0)) * 0.30;
    h += sin(TAU * (p.x * 0.25 + p.y * 0.25 + g * 500.0)) * 0.15;
    h += sin(TAU * (p.x * 0.3125 - p.y * 0.1875 + g * 590.0)) * 0.10;
    return h;
}

void main() {
    vec3 local = Position;
    float flag = 0.0;
    if (RealWater.x > 0.5) {
        // UV0 sits on the corner of the water sprite: probe just inside it in every direction.
        float o = 0.0002;
        flag = max(max(isWater(UV0 + vec2(o, o)), isWater(UV0 + vec2(-o, -o))),
                   max(isWater(UV0 + vec2(o, -o)), isWater(UV0 + vec2(-o, o))));
        if (flag > 0.5) {
            float amp = 0.03 + 0.05 * RealWater.y;
            local.y += waveHeight(local.xz, GameTime) * amp;
        }
    }
    vec3 pos = local + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    lightColor = minecraft_sample_lightmap(Sampler2, UV2);
    vertexColor = Color * lightColor;
    texCoord0 = UV0;
    viewPos = pos;
    localPos = Position;
    faceNormal = Normal;
    waterFlag = flag;
}
