package com.realweather.selftest;

import com.realweather.server.WeatherServer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development-only (compiled with -Pselftest): dedicated server half of the automated run. */
public final class SelfTestServer {
    private static final Logger LOG = LoggerFactory.getLogger("RW-SELFTEST");

    private SelfTestServer() {}

    /** Dedicated server: exercise the commands, then shut down so the world can be reused by the client. */
    public static void init() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            MinecraftServer server = e.getServer();
            for (String c : new String[]{"rw rain 100 5", "rw thunder 100 5", "rw sandstorm 60 3", "rw blizzard 60 3", "rw wind 50 90",
                    "rw flood depth 3", "rw info", "rw auto", "rw clear", "rw info"}) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c);
            }
        });
        int[] ticks = {0};
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (++ticks[0] == 200) {
                LOG.info("server ticks ok, rain={} thunder={} wind={}", WeatherServer.rain, WeatherServer.thunder, WeatherServer.wind);
                e.getServer().halt(false);
            }
        });
    }

}
