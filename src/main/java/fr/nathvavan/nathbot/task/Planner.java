package fr.nathvavan.nathbot.task;

import fr.nathvavan.nathbot.BotConfig;
import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.util.DropIndex;
import fr.nathvavan.nathbot.util.Inv;
import fr.nathvavan.nathbot.util.RecipeBook;
import fr.nathvavan.nathbot.util.Scanner;
import fr.nathvavan.nathbot.util.Targets;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Le "cerveau fabrication" : pour un objet, cherche la facon la moins chere de l'obtenir
 * en remontant toutes les recettes (fabrication, four, tailleur de pierre) jusqu'aux blocs
 * a casser. Ex : planches de bambou <- bloc de bambou <- 9 bambous <- casser du bambou.
 *
 * Le calcul est borne (profondeur + nombre de noeuds) pour ne jamais faire ramer le jeu,
 * et la presence des blocs autour est mise en cache.
 */
public final class Planner {
    public static final double INF = 1e9;

    public enum Way { HAVE, MINE_SPECIAL, MINE, RECIPE, NONE }

    public record Plan(Way way, double cost, Set<Block> blocks, boolean nearby, RecipeBook.Source source, Item tool) {
        public boolean ok() { return way != Way.NONE && cost < INF; }
    }

    private static final Plan NONE = new Plan(Way.NONE, INF, Set.of(), false, null, null);
    private static final Item[] TOOLS = {Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE,
            Items.NETHERITE_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SHOVEL, Items.WOODEN_HOE, Items.SHEARS, Items.STONE_AXE,
            Items.IRON_AXE, Items.IRON_SHOVEL, Items.DIAMOND_AXE};

    // presence des blocs dans les chunks charges : cache ~15 s
    private static final Map<Item, long[]> PRESENCE = new HashMap<>();

    private final Map<Item, Plan> memo = new HashMap<>();
    private final Set<Item> visiting = new HashSet<>();
    private final Set<Object> excluded;
    private int budget = 3000;

    public Planner() { this(Set.of()); }

    /** @param excluded recettes (Recipe) ou "mine:<objet>" qui ont deja echoue. */
    public Planner(Set<Object> excluded) { this.excluded = excluded; }

    public Plan plan(Item item) { return plan(item, 0); }

    private static Plan better(Plan a, Plan b) { return b.cost < a.cost ? b : a; }

    private Plan plan(Item item, int depth) {
        if (item == null || item == Items.AIR) return NONE;
        Plan m = memo.get(item);
        if (m != null) return m;
        if (depth > 0 && Inv.has(item)) return new Plan(Way.HAVE, 0.1, Set.of(), true, null, null);
        if (visiting.contains(item) || depth > 9 || --budget < 0) return NONE;
        visiting.add(item);
        try {
            Plan best = NONE;
            // 1) blocs "classiques" (minerais avec la bonne couche, bois...)
            Targets.Spec spec = GatherTask.directMine(item);
            if (spec != null && !spec.blocks().isEmpty() && !excluded.contains("mine:" + key(item))) {
                int tier = GatherTask.tierNeeded(item);
                double tc = tier > GatherTask.pickTier() ? plan(GatherTask.pickFor(tier), depth + 1).cost : 0;
                if (tc < INF) best = new Plan(Way.MINE_SPECIAL, 3 + tc / 8, spec.blocks(), true, null, null);
            }
            // 2) n'importe quel bloc (vanilla ou mod) qui donne cet objet quand on le casse
            if (spec == null && !excluded.contains("mine:" + key(item))) {
                Set<Block> blocks = DropIndex.blocksFor(item);
                if (!blocks.isEmpty()) {
                    Set<Block> usable = new HashSet<>();
                    Item tool = null;
                    double toolCost = INF;
                    for (Block b : blocks) {
                        Item t = toolNeeded(b.defaultBlockState());
                        if (t == Items.AIR) continue; // aucun outil connu ne marche
                        double c = t == null ? 0 : plan(t, depth + 1).cost;
                        if (c >= INF) continue;
                        if (c < toolCost - 0.01) { usable.clear(); toolCost = c; tool = t; }
                        if (Math.abs(c - toolCost) < 0.01) usable.add(b);
                    }
                    if (!usable.isEmpty()) {
                        boolean near = present(item, usable);
                        best = better(best, new Plan(Way.MINE, (near ? 4 : 40) + toolCost / 8, usable, near, null, tool));
                    }
                }
            }
            // 3) recettes
            for (RecipeBook.Source s : RecipeBook.sourcesFor(item)) {
                if (excluded.contains(s.recipe())) continue;
                double c = switch (s.kind()) {
                    case CRAFT -> 1;
                    case SMELT -> 3;
                    case STONECUT -> 2.5;
                };
                for (Ingredient ing : s.ingredients()) {
                    c += ingredientCost(ing, depth + 1);
                    if (c / s.outCount() >= best.cost) break;
                }
                c /= s.outCount();
                if (c < best.cost) best = new Plan(Way.RECIPE, c, Set.of(), false, s, null);
            }
            if (best.ok()) memo.put(item, best);
            return best;
        } finally {
            visiting.remove(item);
        }
    }

    private double ingredientCost(Ingredient ing, int depth) {
        if (Inv.count(ing) > 0) return 0.1;
        double best = INF;
        for (Item o : RecipeBook.options(ing, 12)) {
            best = Math.min(best, plan(o, depth).cost);
            if (best <= 3) break;
        }
        return best;
    }

    /** Meilleure option pour un ingredient (ex : quel type de planche aller chercher). */
    public Item bestOption(Ingredient ing) {
        Item best = null;
        double bc = INF;
        for (Item o : RecipeBook.options(ing, 16)) {
            if (Inv.has(o)) return o;
            double c = plan(o, 1).cost;
            if (c < bc) { bc = c; best = o; }
        }
        return best;
    }

    /** null = rien a faire, AIR = aucun outil connu, sinon l'outil a fabriquer. */
    public static Item toolNeeded(BlockState st) {
        if (!st.requiresCorrectToolForDrops()) return null;
        if (Inv.count(s -> s.isCorrectToolForDrops(st)) > 0) return null;
        for (Item t : TOOLS) if (new ItemStack(t).isCorrectToolForDrops(st)) return t;
        return Items.AIR;
    }

    private static String key(Item i) { return Inv.id(i); }

    private static boolean present(Item item, Set<Block> blocks) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level.getGameTime();
        long[] c = PRESENCE.get(item);
        if (c != null && now - c[0] < 300 && now >= c[0]) return c[1] == 1;
        int r = BotConfig.get(BotConfig.SCAN_RADIUS_CHUNKS, 6);
        boolean found = Scanner.any(s -> blocks.contains(s.getBlock()), Bot.feet(), r);
        if (PRESENCE.size() > 2000) PRESENCE.clear();
        PRESENCE.put(item, new long[]{now, found ? 1 : 0});
        return found;
    }

    // ------------------------------------------------------------------ explication

    /** Explique comment le bot obtiendrait cet objet (commande "recette"). */
    public List<String> explain(Item item, int count) {
        List<String> out = new ArrayList<>();
        explain(item, count, 0, out, new HashSet<>());
        return out;
    }

    private void explain(Item item, int count, int depth, List<String> out, Set<Item> seen) {
        String pad = "  ".repeat(depth);
        String name = count + "x " + item.getDescription().getString();
        if (out.size() > 40) return;
        if (Inv.count(item) >= count && depth > 0) { out.add(pad + "- " + name + " : deja dans l'inventaire"); return; }
        if (!seen.add(item) || depth > 6) { out.add(pad + "- " + name); return; }
        Plan p = plan(item, 0);
        switch (p.way) {
            case NONE, HAVE -> out.add(pad + "- " + name + " : je ne sais pas l'obtenir");
            case MINE_SPECIAL -> out.add(pad + "- " + name + " : a miner");
            case MINE -> {
                List<String> names = new ArrayList<>();
                for (Block b : p.blocks) { if (names.size() >= 3) { names.add("..."); break; } names.add(b.getName().getString()); }
                out.add(pad + "- " + name + " : casser " + String.join(", ", names) + (p.nearby ? " (il y en a autour)" : " (pas vu autour, il faudra explorer)")
                        + (p.tool != null ? " avec " + p.tool.getDescription().getString() : ""));
            }
            case RECIPE -> {
                RecipeBook.Source s = p.source;
                int crafts = (int) Math.ceil(count / (double) s.outCount());
                out.add(pad + "- " + name + " : " + s.describe() + " (" + crafts + " fois)");
                for (Map.Entry<Ingredient, Integer> e : RecipeBook.grouped(s.ingredients())) {
                    Item o = bestOption(e.getKey());
                    if (o == null) { out.add(pad + "  - " + RecipeBook.label(e.getKey()) + " : introuvable"); continue; }
                    explain(o, crafts * e.getValue(), depth + 1, out, seen);
                }
            }
        }
        seen.remove(item);
    }
}
