#version 150

// The telescope's observing modes. Infrared: the warm glow of dust and cool stars in false colour (black, red,
// orange, yellow, white). Ultraviolet: only hot blue light, in violet and blue. X-ray: only the hottest and most
// violent places stand out (discs round black holes, neutron stars, flares, shock fronts). Out of focus: blurred.
uniform sampler2D Sampler0;
uniform float Mode;
uniform vec2 Blur;

in vec2 ab;

out vec4 fragColor;

vec3 sampleBlur(vec2 uv) {
    if (Blur.x <= 0.0) {
        return texture(Sampler0, uv).rgb;
    }
    vec3 s = vec3(0.0);
    float w = 0.0;
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 0.5236;
        vec2 o = vec2(cos(a), sin(a)) * Blur;
        s += texture(Sampler0, uv + o).rgb + texture(Sampler0, uv + o * 0.5).rgb;
        w += 2.0;
    }
    return (s + texture(Sampler0, uv).rgb) / (w + 1.0);
}

vec3 heat(float x) {
    x = clamp(x, 0.0, 1.0);
    return clamp(vec3(x * 3.0, x * 3.0 - 1.0, x * 3.0 - 2.0), 0.0, 1.0);
}

void main() {
    vec3 c = sampleBlur(ab);
    if (Mode > 2.5) {
        float l = max(c.r, max(c.g, c.b));
        float x = smoothstep(0.55, 1.0, l);
        fragColor = vec4(mix(vec3(0.0), mix(vec3(0.45, 0.1, 0.8), vec3(0.75, 0.95, 1.0), x), sqrt(x)), 1.0);
    } else if (Mode > 1.5) {
        float u = clamp(c.b * 1.2 - c.r * 0.5 + c.g * 0.15, 0.0, 1.0);
        fragColor = vec4(mix(vec3(0.0), mix(vec3(0.3, 0.1, 0.7), vec3(0.85, 0.9, 1.0), u), pow(u, 0.8)), 1.0);
    } else if (Mode > 0.5) {
        float i = clamp(c.r * 0.65 + c.g * 0.3 + c.b * 0.05, 0.0, 1.0);
        fragColor = vec4(heat(pow(i, 0.7)), 1.0);
    } else {
        fragColor = vec4(c, 1.0);
    }
}
