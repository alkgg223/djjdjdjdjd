package com.gunsmod.client.sound;

import com.gunsmod.Reg;
import com.gunsmod.client.GunClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Roar of the local player's flamethrower while the trigger is held. */
public class FlameSound extends AbstractTickableSoundInstance {
    private static FlameSound current;
    private final LocalPlayer player;

    private FlameSound(LocalPlayer p) {
        super(Reg.sound("flamethrower_loop"), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = p;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.1f;
        this.x = p.getX();
        this.y = p.getY();
        this.z = p.getZ();
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (GunClient.flameActive() && (current == null || current.isStopped())) {
            current = new FlameSound(mc.player);
            mc.getSoundManager().play(current);
        }
    }

    @Override
    public void tick() {
        this.x = player.getX();
        this.y = player.getEyeY();
        this.z = player.getZ();
        if (!GunClient.flameActive() || player.isRemoved()) {
            this.volume -= 0.25f;
            if (this.volume <= 0.02f) this.stop();
        } else {
            this.volume = Math.min(1f, this.volume + 0.3f);
        }
    }
}
