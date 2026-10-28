package com.gunsmod.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Attachments installed on a gun, one per slot (0 = empty, otherwise {@link Attachment} ordinal + 1). */
public record GunAttachments(int scope, int muzzle, int grip, int laser, int mag) {
    public static final GunAttachments NONE = new GunAttachments(0, 0, 0, 0, 0);

    public static final Codec<GunAttachments> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("scope", 0).forGetter(GunAttachments::scope),
            Codec.INT.optionalFieldOf("muzzle", 0).forGetter(GunAttachments::muzzle),
            Codec.INT.optionalFieldOf("grip", 0).forGetter(GunAttachments::grip),
            Codec.INT.optionalFieldOf("laser", 0).forGetter(GunAttachments::laser),
            Codec.INT.optionalFieldOf("mag", 0).forGetter(GunAttachments::mag)
    ).apply(i, GunAttachments::new));

    public static final StreamCodec<ByteBuf, GunAttachments> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GunAttachments::scope,
            ByteBufCodecs.VAR_INT, GunAttachments::muzzle,
            ByteBufCodecs.VAR_INT, GunAttachments::grip,
            ByteBufCodecs.VAR_INT, GunAttachments::laser,
            ByteBufCodecs.VAR_INT, GunAttachments::mag,
            GunAttachments::new);

    public Attachment get(Attachment.Slot slot) {
        return Attachment.fromIndex(raw(slot));
    }

    public int raw(Attachment.Slot slot) {
        return switch (slot) {
            case SCOPE -> scope;
            case MUZZLE -> muzzle;
            case GRIP -> grip;
            case LASER -> laser;
            case MAG -> mag;
        };
    }

    public GunAttachments with(Attachment.Slot slot, Attachment a) {
        int v = a == null ? 0 : a.ordinal() + 1;
        return switch (slot) {
            case SCOPE -> new GunAttachments(v, muzzle, grip, laser, mag);
            case MUZZLE -> new GunAttachments(scope, v, grip, laser, mag);
            case GRIP -> new GunAttachments(scope, muzzle, v, laser, mag);
            case LASER -> new GunAttachments(scope, muzzle, grip, v, mag);
            case MAG -> new GunAttachments(scope, muzzle, grip, laser, v);
        };
    }

    public boolean has(Attachment a) {
        return get(a.slot) == a;
    }

    public float zoom(GunType type) {
        Attachment s = get(Attachment.Slot.SCOPE);
        return s != null ? s.zoom : type.builtInZoom();
    }

    public float recoilMul() {
        float m = 1f;
        for (Attachment.Slot s : Attachment.Slot.values()) {
            Attachment a = get(s);
            if (a != null) m *= a.recoilMul;
        }
        return m;
    }

    public float hipSpreadMul() {
        float m = 1f;
        for (Attachment.Slot s : Attachment.Slot.values()) {
            Attachment a = get(s);
            if (a != null) m *= a.hipSpreadMul;
        }
        return m;
    }

    public float adsSpreadMul() {
        float m = 1f;
        for (Attachment.Slot s : Attachment.Slot.values()) {
            Attachment a = get(s);
            if (a != null) m *= a.adsSpreadMul;
        }
        return m;
    }

    public int magSize(GunType type) {
        return has(Attachment.EXTENDED_MAG) && type.supports(Attachment.Slot.MAG) ? Math.round(type.magSize * 1.5f) : type.magSize;
    }

    public int reloadTicks(GunType type) {
        return has(Attachment.EXTENDED_MAG) ? Math.round(type.reloadTicks * 1.12f) : type.reloadTicks;
    }
}
