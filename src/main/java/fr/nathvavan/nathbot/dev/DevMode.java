package fr.nathvavan.nathbot.dev;

import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.util.BotLog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Mode "Claude code le bot" : pendant qu'une session Claude modifie le bot (scripts,
 * reglages, site web), le bot est mis en pause et PERSONNE ne peut le relancer.
 * Quand c'est fini, le chat affiche "Le bot est repret !" et il reprend ce qu'il faisait.
 *
 * Securite : les commandes /api/dev/* exigent le jeton de nathbot/dev-token.txt
 * (lisible seulement sur ce PC), en plus des protections localhost du serveur web.
 */
public final class DevMode {
    private DevMode() {}

    private static volatile boolean locked = false;
    private static volatile String reason = "";
    private static volatile long until = 0;
    private static boolean pausedBefore = false;
    /** Vrai pendant une commande envoyee par Claude (elle a le droit de lancer une tache). */
    public static volatile boolean bypass = false;
    private static String token;

    public static File dir() {
        File d = new File(Minecraft.getInstance().gameDirectory, "nathbot");
        d.mkdirs();
        return d;
    }

    /** Jeton secret (cree au premier lancement). */
    public static synchronized String token() {
        if (token != null) return token;
        File f = new File(dir(), "dev-token.txt");
        try {
            if (f.exists()) token = Files.readString(f.toPath(), StandardCharsets.UTF_8).trim();
            if (token == null || token.length() < 16) {
                token = UUID.randomUUID().toString().replace("-", "");
                Files.writeString(f.toPath(), token, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            token = UUID.randomUUID().toString().replace("-", "");
        }
        return token;
    }

    public static boolean locked() { return locked; }

    public static String reason() { return reason; }

    public static long secondsLeft() { return locked ? Math.max(0, (until - System.currentTimeMillis()) / 1000) : 0; }

    private static void chat(String msg, ChatFormatting color) {
        Minecraft mc = Minecraft.getInstance();
        Runnable r = () -> {
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("[NathBot] ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(msg).withStyle(color)), false);
        };
        if (mc.isSameThread()) r.run(); else mc.execute(r);
        BotLog.quiet(msg);
    }

    /** A appeler sur le fil du jeu. */
    public static void lock(String why, int minutes) {
        minutes = Math.max(1, Math.min(120, minutes));
        until = System.currentTimeMillis() + minutes * 60_000L;
        String w = why == null || why.isBlank() ? "mise a jour" : why.trim();
        if (w.length() > 120) w = w.substring(0, 120);
        if (locked) {
            reason = w;
            return; // simple prolongation
        }
        pausedBefore = Bot.I.paused;
        Bot.I.paused = true;
        Bot.I.releaseKeys();
        reason = w;
        locked = true;
        chat("Claude modifie le bot (" + w + "). Bot en pause, impossible de le relancer pendant ce temps...", ChatFormatting.GOLD);
    }

    /** A appeler sur le fil du jeu. */
    public static void unlock(String message) {
        if (!locked) return;
        locked = false;
        Bot.I.paused = pausedBefore;
        String m = message == null || message.isBlank() ? "" : " " + message.trim();
        chat("Le bot est repret !" + m, ChatFormatting.GREEN);
    }

    /** Chaque tick : maintient la pause, et libere le bot si Claude a oublie (delai depasse). */
    public static void tick() {
        if (!locked) return;
        Bot.I.paused = true;
        if (System.currentTimeMillis() > until) unlock("(delai depasse)");
    }

    /** Message quand quelqu'un essaie de relancer pendant le verrou. */
    public static void refuse() {
        chat("Impossible de relancer : Claude est en train de coder le bot (" + reason + "). Je te previens des que c'est fini.", ChatFormatting.RED);
    }
}
