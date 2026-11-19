package fr.nathvavan.nathbot.util;

import fr.nathvavan.nathbot.BotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Gestion de la camera du joueur.
 *
 * Le bot ne "teleporte" plus sa camera : il donne une cible, et la camera s'en approche
 * en douceur a chaque image (pas a chaque tick), avec une acceleration naturelle.
 * Resultat : plus de rotation d'un seul coup, meme a 144 FPS.
 */
public final class Rot {
    private Rot() {}

    private static float targetYaw, targetPitch;
    private static int targetTick = -100;
    private static long lastFrame = 0;

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

    private static boolean smooth() { return BotConfig.get(BotConfig.SMOOTH_ROTATION, true); }

    /** Fixe la direction voulue. Renvoie true si la camera y est deja (a 3 degres pres). */
    public static boolean setRotation(float yaw, float pitch) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return false;
        targetYaw = yaw;
        targetPitch = Mth.clamp(pitch, -90f, 90f);
        targetTick = p.tickCount;
        if (!smooth()) {
            apply(p, 1f, 100000f);
            return true;
        }
        // si aucune image n'a ete dessinee depuis un moment (jeu reduit), on avance quand meme
        if (System.nanoTime() - lastFrame > 120_000_000L) step(p, 0.05f);
        float dy = Math.abs(Mth.wrapDegrees(targetYaw - p.getYRot()));
        float dp = Math.abs(targetPitch - p.getXRot());
        return dy < 3f && dp < 3f;
    }

    /** Appele a chaque image : rapproche la camera de la cible. */
    public static void frame() {
        LocalPlayer p = Minecraft.getInstance().player;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (p == null || !smooth()) return;
        step(p, dt);
    }

    private static void step(LocalPlayer p, float dt) {
        if (p.tickCount - targetTick > 2) return; // plus de cible : la souris est au joueur
        // vitesse proportionnelle a l'ecart (acceleration / freinage), bornee
        float alpha = 1f - (float) Math.exp(-dt * 13f);
        apply(p, alpha, 720f * dt + 0.0001f);
    }

    private static void apply(LocalPlayer p, float alpha, float maxStep) {
        float dy = Mth.wrapDegrees(targetYaw - p.getYRot());
        float dp = targetPitch - p.getXRot();
        float minStep = Math.min(maxStep, 0.9f); // evite de "ramper" sur les derniers degres
        float sy = dy * alpha, sp = dp * alpha;
        if (Math.abs(sy) < minStep) sy = Math.copySign(Math.min(Math.abs(dy), minStep), dy);
        if (Math.abs(sp) < minStep) sp = Math.copySign(Math.min(Math.abs(dp), minStep), dp);
        sy = Mth.clamp(sy, -maxStep, maxStep);
        sp = Mth.clamp(sp, -maxStep, maxStep);
        p.setYRot(p.getYRot() + sy);
        p.setXRot(Mth.clamp(p.getXRot() + sp, -90f, 90f));
        p.setYHeadRot(p.getYRot());
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
