package fr.nathvavan.nathbot.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Journal des raisonnements du bot ("inventaire plein, je jette 64 diorite", "beaucoup d'eau
 * devant, je prends un bateau"...). Affiche dans l'onglet Cerveau du site web.
 */
public final class Brain {
    private Brain() {}

    public record Thought(String time, String kind, String text) {}

    private static final Deque<Thought> THOUGHTS = new ArrayDeque<>();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static long counter = 0;

    /** @param kind inventaire, eau, recette, securite, deplacement... */
    public static void think(String kind, String text) {
        synchronized (THOUGHTS) {
            Thought last = THOUGHTS.peekLast();
            if (last != null && last.text.equals(text)) return; // pas de doublons a la suite
            THOUGHTS.addLast(new Thought(LocalTime.now().format(FMT), kind, text));
            counter++;
            while (THOUGHTS.size() > 120) THOUGHTS.removeFirst();
        }
        BotLog.quiet("[" + kind + "] " + text);
    }

    public static List<Thought> all() {
        synchronized (THOUGHTS) {
            return new ArrayList<>(THOUGHTS);
        }
    }

    public static long counter() {
        synchronized (THOUGHTS) {
            return counter;
        }
    }
}
