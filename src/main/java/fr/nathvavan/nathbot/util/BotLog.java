package fr.nathvavan.nathbot.util;

import fr.nathvavan.nathbot.NathBot;
import fr.nathvavan.nathbot.feature.Features;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Journal du bot : chat + historique consultable depuis le site web. */
public final class BotLog {
    private static final Deque<String> LINES = new ArrayDeque<>();
    private static final int MAX = 300;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static long counter = 0;

    private BotLog() {}

    public static void info(String msg) { log(msg, ChatFormatting.WHITE, true); }

    public static void ok(String msg) { log(msg, ChatFormatting.GREEN, true); }

    public static void warn(String msg) { log(msg, ChatFormatting.GOLD, true); }

    public static void error(String msg) { log(msg, ChatFormatting.RED, true); }

    /** Ecrit seulement dans l'historique (pas de chat). */
    public static void quiet(String msg) { log(msg, ChatFormatting.GRAY, false); }

    private static void log(String msg, ChatFormatting color, boolean chat) {
        String line = LocalTime.now().format(FMT) + " " + msg;
        synchronized (LINES) {
            LINES.addLast(line);
            counter++;
            while (LINES.size() > MAX) LINES.removeFirst();
        }
        NathBot.LOG.info("[NathBot] {}", msg);
        if (!chat || !Features.isOn("chat_feedback")) return;
        Minecraft mc = Minecraft.getInstance();
        Runnable r = () -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("[NathBot] ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(msg).withStyle(color)), false);
            }
        };
        if (mc.isSameThread()) r.run(); else mc.execute(r);
    }

    public static List<String> lines() {
        synchronized (LINES) {
            return new ArrayList<>(LINES);
        }
    }

    public static long counter() {
        synchronized (LINES) {
            return counter;
        }
    }
}
