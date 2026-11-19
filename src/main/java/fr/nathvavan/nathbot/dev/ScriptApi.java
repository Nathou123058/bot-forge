package fr.nathvavan.nathbot.dev;

import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.command.Commands;
import fr.nathvavan.nathbot.feature.Features;
import fr.nathvavan.nathbot.util.Brain;
import fr.nathvavan.nathbot.util.BotLog;
import fr.nathvavan.nathbot.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import org.mozilla.javascript.Function;

/**
 * L'objet "bot" accessible dans les scripts JavaScript (dossier nathbot/scripts).
 * Toutes les methodes s'executent sur le fil du jeu.
 */
public final class ScriptApi {
    final String file;

    ScriptApi(String file) { this.file = file; }

    private static Minecraft mc() { return Minecraft.getInstance(); }

    // ---------------------------------------------------------------- commandes
    /** Execute une commande NathBot tout de suite (ex : "miner fer 10"). */
    public void cmd(String c) { Commands.execute(c); }

    /** Ajoute une commande a la file : elle sera lancee quand le bot n'aura plus rien a faire. */
    public void queue(String c) { Scripts.QUEUE.add(c); }

    public void clearQueue() { Scripts.QUEUE.clear(); }

    public int queueSize() { return Scripts.QUEUE.size(); }

    public void stop() { Bot.I.stop(); }

    // ---------------------------------------------------------------- evenements
    /** bot.on("tick", fn) chaque tick, bot.on("chat", fn(texte)) a chaque message recu. */
    public void on(String event, Function fn) { Scripts.addHandler(file, event, fn, 1); }

    /** bot.every(20, fn) : appelle fn toutes les N ticks (20 ticks = 1 seconde). */
    public void every(int ticks, Function fn) { Scripts.addHandler(file, "tick", fn, Math.max(1, ticks)); }

    /** bot.command("nom", "description", function(args){...}) : nouvelle commande /nb nom. */
    public void command(String name, String desc, Function fn) { Scripts.addCommand(file, name, desc, fn); }

    // ---------------------------------------------------------------- messages
    public void say(Object msg) { BotLog.info(String.valueOf(msg)); }

    public void log(Object msg) { BotLog.quiet("[" + file + "] " + msg); }

    public void think(String kind, Object msg) { Brain.think(kind, String.valueOf(msg)); }

    // ---------------------------------------------------------------- etat
    public boolean busy() { return Bot.I.task() != null; }

    public boolean paused() { return Bot.I.paused; }

    public String status() { return Bot.I.statusText(); }

    public double x() { return mc().player.getX(); }

    public double y() { return mc().player.getY(); }

    public double z() { return mc().player.getZ(); }

    public double health() { return mc().player.getHealth(); }

    public int food() { return mc().player.getFoodData().getFoodLevel(); }

    public String dim() { return mc().level.dimension().location().toString(); }

    /** Heure du jeu (0 = 6h du matin, 13000 = nuit). */
    public long time() { return mc().level.getDayTime() % 24000; }

    public int free() { return Inv.freeSlots(); }

    public boolean onFeature(String id) { return Features.isOn(id); }

    /** Active / coupe une fonction (ex : bot.feature("auto_eat", true)). */
    public void feature(String id, boolean on) {
        if (Features.isOn(id) != on) Features.toggle(id);
    }

    /** Change un reglage et l'enregistre (ex : bot.set("max_fall", "4")). */
    public String set(String key, String value) { return DevConfig.set(key, value); }

    // ---------------------------------------------------------------- monde / inventaire
    private static Item item(String id) { return Commands.parseItem(id); }

    public int count(String id) {
        Item i = item(id);
        return i == null ? 0 : Inv.count(i);
    }

    public boolean has(String id) { return count(id) > 0; }

    /** Id du bloc a cette position (ex : "minecraft:stone"). */
    public String block(int x, int y, int z) {
        return BuiltInRegistries.BLOCK.getKey(mc().level.getBlockState(new BlockPos(x, y, z)).getBlock()).toString();
    }

    /** Id complet d'un objet a partir d'un nom ("bambou" -> "minecraft:bamboo"), ou null. */
    public String itemId(String name) {
        Item i = item(name);
        return i == null ? null : BuiltInRegistries.ITEM.getKey(i).toString();
    }
}
