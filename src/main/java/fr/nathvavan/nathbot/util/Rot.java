package fr.nathvavan.nathbot.util;

import fr.nathvavan.nathbot.BotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Gestion de la camera du joueur (rotation vers une cible). */
public final class Rot {
    private Rot() {}

    public static float[] calc(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float) -(Mth.atan2(dy, h) * (180.0 / Math.PI));
        return new float[]{Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90f, 90f)};
    }

    /** Tourne la camera vers la cible. Renvoie true si on vise deja (presque) la cible. */
    public static boolean lookAt(Vec3 target) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return false;
        float[] r = calc(p.getEyePosition(), target);
        return setRotation(r[0], r[1]);
    }

    public static boolean setRotation(float yaw, float pitch) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return false;
        float cy = p.getYRot(), cp = p.getXRot();
        float dy = Mth.wrapDegrees(yaw - cy);
        float dp = pitch - cp;
        boolean smooth = BotConfig.get(BotConfig.SMOOTH_ROTATION, true);
        float max = smooth ? 40f : 360f;
        float sy = Mth.clamp(dy, -max, max);
        float sp = Mth.clamp(dp, -max, max);
        p.setYRot(cy + sy);
        p.setXRot(Mth.clamp(cp + sp, -90f, 90f));
        p.setYHeadRot(p.getYRot());
        return Math.abs(dy - sy) < 0.01f && Math.abs(dp - sp) < 0.01f || (Math.abs(dy) < 3f && Math.abs(dp) < 3f);
    }

    /** Oriente uniquement le lacet (pour marcher). */
    public static void yawTowards(Vec3 target) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        float[] r = calc(p.position(), target);
        float pitch = p.getXRot();
        if (Math.abs(pitch) > 45) pitch = pitch * 0.7f;
        setRotation(r[0], pitch);
    }

    /** Envoie immediatement la rotation au serveur (utile avant de poser un bloc oriente). */
    public static void syncNow() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        p.connection.send(new ServerboundMovePlayerPacket.Rot(p.getYRot(), p.getXRot(), p.onGround()));
    }
}
