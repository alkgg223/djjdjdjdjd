package com.realweather.client.radar;

import com.realweather.client.ClientStorms;
import com.realweather.client.ClientWeather;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

/** Air temperature, dew point and humidity at a place, from the biome, the time of day, altitude and the weather. */
public final class Thermo {
    private Thermo() {}

    public static float celsius(ClientLevel level, BlockPos pos) {
        Biome biome = level.getBiome(pos).value();
        float t = biome.getBaseTemperature();
        float c = t <= 1F ? -14F + t * 44F : 30F + (t - 1F) * 12F;
        // Warmest mid-afternoon, coldest before dawn.
        float day = (level.getDayTime() % 24000L) / 24000F;
        c += 5.5F * Mth.cos((day - 0.33F) * Mth.TWO_PI);
        // Higher up is colder (6.5 C per km).
        c -= Math.max(0, pos.getY() - level.getSeaLevel()) * 0.0065F * 12F;
        // Rain-cooled air under storms; snow and blizzards bite harder.
        float rain = ClientStorms.synced ? ClientStorms.rainAt(pos.getX(), pos.getZ()) * level.getRainLevel(1F) : ClientWeather.rain * level.getRainLevel(1F);
        c -= rain * 6F + ClientWeather.blizzard * 6F;
        return c;
    }

    /** Relative humidity, 0..100. */
    public static float humidity(ClientLevel level, BlockPos pos) {
        Biome biome = level.getBiome(pos).value();
        float base = Mth.clamp(biome.getModifiedClimateSettings().downfall(), 0F, 1F);
        float rain = ClientStorms.synced ? ClientStorms.rainAt(pos.getX(), pos.getZ()) * level.getRainLevel(1F) : ClientWeather.rain * level.getRainLevel(1F);
        return Mth.clamp(25F + base * 45F + rain * 45F + level.getRainLevel(1F) * 10F, 5F, 100F);
    }

    public static float dewPoint(float celsius, float humidity) {
        double a = 17.62;
        double b = 243.12;
        double g = Math.log(Math.max(1, humidity) / 100.0) + a * celsius / (b + celsius);
        return (float) (b * g / (a - g));
    }
}
