package fr.nathvavan.nathbot.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

import java.util.*;

/**
 * Index de TOUTES les recettes connues par le jeu (vanilla + tous les mods installes).
 * Le serveur envoie toutes ses recettes au client a la connexion : pas besoin d'internet,
 * c'est exactement la meme source que JEI.
 * L'index est construit une seule fois (quelques millisecondes) puis reconstruit seulement
 * si les recettes changent (/reload, autre monde...).
 */
public final class RecipeBook {
    private RecipeBook() {}

    public enum Kind { CRAFT, SMELT, STONECUT }

    /** Une facon de fabriquer un objet. */
    public record Source(Kind kind, Recipe<?> recipe, Item output, int outCount, List<Ingredient> ingredients) {
        public boolean needsTable() {
            return kind == Kind.CRAFT && !((CraftingRecipe) recipe).canCraftInDimensions(2, 2);
        }

        public String describe() {
            String st = switch (kind) {
                case CRAFT -> needsTable() ? "etabli" : "inventaire";
                case SMELT -> "four";
                case STONECUT -> "tailleur de pierre";
            };
            return st + " -> " + outCount + "x";
        }
    }

    private static final Map<Item, List<Source>> BY_OUTPUT = new HashMap<>();
    private static Object builtFor;
    private static volatile boolean dirty = true;
    private static int total = 0;

    /** Appele quand le serveur renvoie ses recettes (connexion, /reload). */
    public static void invalidate() { dirty = true; }

    private static RegistryAccess ra() { return Minecraft.getInstance().level.registryAccess(); }

    private static void ensure() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            BY_OUTPUT.clear();
            builtFor = null;
            return;
        }
        RecipeManager rm = mc.level.getRecipeManager();
        if (builtFor == rm && !dirty) return;
        dirty = false;
        long t0 = System.nanoTime();
        BY_OUTPUT.clear();
        total = 0;
        for (CraftingRecipe r : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
            try {
                if (r.isSpecial() || !r.canCraftInDimensions(3, 3)) continue;
                add(Kind.CRAFT, r);
            } catch (Throwable ignored) {}
        }
        for (SmeltingRecipe r : rm.getAllRecipesFor(RecipeType.SMELTING)) {
            try { add(Kind.SMELT, r); } catch (Throwable ignored) {}
        }
        for (StonecutterRecipe r : rm.getAllRecipesFor(RecipeType.STONECUTTING)) {
            try { add(Kind.STONECUT, r); } catch (Throwable ignored) {}
        }
        builtFor = rm;
        BotLog.quiet("Livre de recettes : " + total + " recettes pour " + BY_OUTPUT.size() + " objets (" + (System.nanoTime() - t0) / 1_000_000 + " ms)");
    }

    private static void add(Kind kind, Recipe<?> r) {
        ItemStack out = r.getResultItem(ra());
        if (out.isEmpty()) return;
        List<Ingredient> ings = new ArrayList<>();
        for (Ingredient i : r.getIngredients()) if (!i.isEmpty()) ings.add(i);
        if (ings.isEmpty()) return;
        BY_OUTPUT.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(new Source(kind, r, out.getItem(), Math.max(1, out.getCount()), ings));
        total++;
    }

    /** Toutes les facons connues de fabriquer cet objet. */
    public static List<Source> sourcesFor(Item item) {
        ensure();
        return BY_OUTPUT.getOrDefault(item, List.of());
    }

    public static List<CraftingRecipe> craftingFor(Item item) {
        List<CraftingRecipe> l = new ArrayList<>();
        for (Source s : sourcesFor(item)) if (s.kind == Kind.CRAFT) l.add((CraftingRecipe) s.recipe);
        return l;
    }

    public static int recipeCount() {
        ensure();
        return total;
    }

    /** Les options d'un ingredient, en mettant d'abord celles qu'on a deja. */
    public static List<Item> options(Ingredient ing, int max) {
        LinkedHashSet<Item> out = new LinkedHashSet<>();
        ItemStack[] all = ing.getItems();
        for (ItemStack s : all) if (Inv.has(s.getItem())) out.add(s.getItem());
        for (ItemStack s : all) {
            if (out.size() >= max) break;
            out.add(s.getItem());
        }
        return new ArrayList<>(out);
    }

    /** Nombre d'exemplaires de chaque ingredient (les ingredients identiques sont regroupes). */
    public static List<Map.Entry<Ingredient, Integer>> grouped(List<Ingredient> ings) {
        List<Map.Entry<Ingredient, Integer>> out = new ArrayList<>();
        outer:
        for (Ingredient i : ings) {
            for (int k = 0; k < out.size(); k++) {
                if (same(out.get(k).getKey(), i)) {
                    out.set(k, Map.entry(out.get(k).getKey(), out.get(k).getValue() + 1));
                    continue outer;
                }
            }
            out.add(Map.entry(i, 1));
        }
        return out;
    }

    public static boolean same(Ingredient a, Ingredient b) {
        if (a == b) return true;
        ItemStack[] x = a.getItems(), y = b.getItems();
        if (x.length != y.length) return false;
        for (int i = 0; i < x.length; i++) if (x[i].getItem() != y[i].getItem()) return false;
        return true;
    }

    public static String label(Ingredient ing) {
        ItemStack[] it = ing.getItems();
        if (it.length == 0) return "?";
        if (it.length == 1) return it[0].getHoverName().getString();
        return it[0].getHoverName().getString() + " (ou " + (it.length - 1) + " autre" + (it.length > 2 ? "s" : "") + ")";
    }
}
