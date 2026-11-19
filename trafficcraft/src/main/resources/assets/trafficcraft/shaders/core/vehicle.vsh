#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 NormalMat;
uniform int FogShape;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
uniform vec2 LightUV;
uniform float Emissive;
uniform vec4 MatcapRect;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec2 matcapCoord;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;
    vertexDistance = fog_distance(viewPos.xyz, FogShape);
    vec3 n = normalize(NormalMat * Normal);
    vertexColor = Emissive > 0.5 ? Color : minecraft_mix_light(Light0_Direction, Light1_Direction, n, Color);
    lightMapColor = Emissive > 0.5 ? vec4(1.0) : texelFetch(Sampler2, ivec2(LightUV) / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
    vec3 vn = normalize(mat3(ModelViewMat) * Normal);
    vec2 m = vn.xy * 0.48 + 0.5;
    matcapCoord = vec2(mix(MatcapRect.x, MatcapRect.z, m.x), mix(MatcapRect.w, MatcapRect.y, m.y));
}
