package fr.nathvavan.nathbot.task;

import fr.nathvavan.nathbot.util.BotLog;
import fr.nathvavan.nathbot.util.DropIndex;
import fr.nathvavan.nathbot.util.Inv;
import fr.nathvavan.nathbot.util.RecipeBook;
import fr.nathvavan.nathbot.util.Targets;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Obtenir un objet en survie, tout seul : le miner, le fabriquer, le cuire ou le tailler,
 * en remontant toute la chaine de recettes (vanilla + tous les mods). Voir {@link Planner}.
 */
public class GatherTask extends Task {
    private final Item item;              // mode "objet precis"
    private final Ingredient ingredient;  // mode "n'importe lequel de ces objets" (ex : des planches)
    private final String label;
    private final int target;
    private int attempts = 0;
    private int lastHave = -1;
    private int noProgress = 0;
    private final Set<Object> failed = new HashSet<>();
    private Planner.Plan current;
    private MineTask mining;
    private int haveBeforeMine;

    public GatherTask(Item item, int targetTotal) {
        this.item = item;
        this.ingredient = null;
        this.target = targetTotal;
        this.label = item.getDescription().getString();
    }

    public GatherTask(Ingredient ingredient, int targetTotal) {
        this.item = null;
        this.ingredient = ingredient;
        this.target = targetTotal;
        this.label = RecipeBook.label(ingredient);
    }

    @Override
    public String name() { return "Recolter " + label + " (" + have() + "/" + target + ")"; }

    public static boolean isLog(ItemStack s) { return s.is(ItemTags.LOGS); }

    /** Le bot sait-il obtenir cet objet tout seul ? (recettes de tous les mods comprises) */
    public static boolean canGather(Item item) {
        return directMine(item) != null || item == Items.GLASS || item == Items.STONE || item == Items.CHARCOAL || new Planner().plan(item).ok();
    }

    /** Bloc a miner directement pour obtenir l'objet. */
    static Targets.Spec directMine(Item item) {
        ItemStack s = new ItemStack(item);
        if (s.is(ItemTags.LOGS) && Block.byItem(item) != Blocks.AIR) return new Targets.Spec("bois", Set.of(Block.byItem(item)), null);
        if (item == Items.COBBLESTONE) return new Targets.Spec("pierre", Set.of(Blocks.STONE, Blocks.COBBLESTONE), null);
        if (item == Items.COBBLED_DEEPSLATE) return new Targets.Spec("deepslate", Set.of(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE), null);
        if (item == Items.DIRT) return Targets.parse("dirt");
        if (item == Items.SAND) return Targets.parse("sand");
        if (item == Items.GRAVEL || item == Items.FLINT) return Targets.parse("gravel");
        if (item == Items.CLAY_BALL) return Targets.parse("clay");
        if (item == Items.SNOWBALL || item == Items.SNOW_BLOCK) return Targets.parse("snow_block");
        if (item == Items.COAL) return Targets.parse("coal");
        if (item == Items.RAW_IRON) return Targets.parse("iron");
        if (item == Items.RAW_GOLD) return Targets.parse("gold");
        if (item == Items.RAW_COPPER) return Targets.parse("copper");
        if (item == Items.DIAMOND) return Targets.parse("diamond");
        if (item == Items.REDSTONE) return Targets.parse("redstone");
        if (item == Items.LAPIS_LAZULI) return Targets.parse("lapis");
        if (item == Items.EMERALD) return Targets.parse("emerald");
        if (item == Items.OBSIDIAN) return Targets.parse("obsidian");
        if (item == Items.NETHERRACK) return Targets.parse("netherrack");
        if (item == Items.ANDESITE || item == Items.DIORITE || item == Items.GRANITE || item == Items.TUFF || item == Items.CALCITE)
            return new Targets.Spec(Inv.id(item), Set.of(Block.byItem(item)), null);
        return null;
    }

    private int have() {
        return item != null ? Inv.count(item) : Inv.count(ingredient);
    }

    /** Niveau de la meilleure pioche : 0 aucune, 1 bois/or, 2 pierre, 3 fer, 4 diamant+. */
    public static int pickTier() {
        int t = 0;
        if (Inv.has(Items.WOODEN_PICKAXE) || Inv.has(Items.GOLDEN_PICKAXE)) t = 1;
        if (Inv.has(Items.STONE_PICKAXE)) t = 2;
        if (Inv.has(Items.IRON_PICKAXE)) t = 3;
        if (Inv.has(Items.DIAMOND_PICKAXE) || Inv.has(Items.NETHERITE_PICKAXE)) t = 4;
        return t;
    }

    static int tierNeeded(Item item) {
        if (item == Items.COBBLESTONE || item == Items.COBBLED_DEEPSLATE || item == Items.COAL || item == Items.NETHERRACK
                || item == Items.ANDESITE || item == Items.DIORITE || item == Items.GRANITE || item == Items.TUFF || item == Items.CALCITE) return 1;
        if (item == Items.RAW_IRON || item == Items.LAPIS_LAZULI || item == Items.RAW_COPPER) return 2;
        if (item == Items.RAW_GOLD || item == Items.DIAMOND || item == Items.REDSTONE || item == Items.EMERALD) return 3;
        if (item == Items.OBSIDIAN || item == Items.ANCIENT_DEBRIS) return 4;
        return 0;
    }

    static Item pickFor(int tier) {
        return switch (tier) {
            case 1 -> Items.WOODEN_PICKAXE;
            case 2 -> Items.STONE_PICKAXE;
            case 3 -> Items.IRON_PICKAXE;
            default -> Items.DIAMOND_PICKAXE;
        };
    }

    @Override
    protected Status update() {
        int h = have();
        if (h >= target) return Status.SUCCESS;
        if (h > lastHave) { lastHave = h; noProgress = 0; } else noProgress++;
        if (++attempts > 80 || noProgress > 16) {
            BotLog.warn("Je n'arrive pas a obtenir " + label);
            return Status.FAILED;
        }
        return item != null ? updateItem(h) : updateIngredient(h);
    }

    /** Mode ingredient : on choisit la variante la plus facile a obtenir. */
    private Status updateIngredient(int h) {
        int n = target - h;
        List<Item> opts = RecipeBook.options(ingredient, 64);
        if (opts.isEmpty()) {
            BotLog.warn("Ingredient vide : " + label);
            return Status.FAILED;
        }
        boolean allPlanks = opts.stream().allMatch(o -> new ItemStack(o).is(ItemTags.PLANKS));
        if (allPlanks && opts.contains(Items.OAK_PLANKS) && opts.contains(Items.SPRUCE_PLANKS)) {
            // n'importe quelles planches : l'arbre le plus proche fait l'affaire
            int logs = Inv.count(GatherTask::isLog);
            if (logs * 4 < n) {
                run(new MineTask(Targets.parse("wood"), 0).until(() -> Inv.count(GatherTask::isLog) * 4 >= target - have()));
                return Status.RUNNING;
            }
            run(new ContainerTasks.Craft(ingredient, target, "planches"));
            return Status.RUNNING;
        }
        boolean allLogs = opts.stream().allMatch(o -> new ItemStack(o).is(ItemTags.LOGS));
        if (allLogs && opts.contains(Items.OAK_LOG) && opts.contains(Items.SPRUCE_LOG)) {
            run(new MineTask(Targets.parse("wood"), 0).until(() -> have() >= target));
            return Status.RUNNING;
        }
        Item best = new Planner(failed).bestOption(ingredient);
        if (best == null) {
            BotLog.warn("Je ne sais obtenir aucune variante de " + label);
            return Status.FAILED;
        }
        run(new GatherTask(best, Inv.count(best) + n));
        return Status.RUNNING;
    }

    private Status updateItem(int h) {
        int n = target - h;
        Targets.Spec spec = directMine(item);
        if (spec != null && !spec.blocks().isEmpty() && !failed.contains("mine:" + Inv.id(item))) {
            int tier = tierNeeded(item);
            if (tier > pickTier()) {
                run(new GatherTask(pickFor(tier), 1));
                return Status.RUNNING;
            }
            final int goal = target;
            run(new MineTask(spec, 0).until(() -> have() >= goal));
            return Status.RUNNING;
        }
        if (item == Items.STONE) {
            if (Inv.count(Items.COBBLESTONE) < n) { run(new GatherTask(Items.COBBLESTONE, n + Inv.count(Items.COBBLESTONE))); return Status.RUNNING; }
            return smelt(Ingredient.of(Items.COBBLESTONE));
        }
        if (item == Items.GLASS) {
            if (Inv.count(Items.SAND) < n) { run(new GatherTask(Items.SAND, n)); return Status.RUNNING; }
            return smelt(Ingredient.of(Items.SAND));
        }
        if (item == Items.IRON_INGOT || item == Items.GOLD_INGOT || item == Items.COPPER_INGOT) {
            Item raw = item == Items.IRON_INGOT ? Items.RAW_IRON : item == Items.GOLD_INGOT ? Items.RAW_GOLD : Items.RAW_COPPER;
            if (Inv.count(raw) < n) { run(new GatherTask(raw, n)); return Status.RUNNING; }
            return smelt(Ingredient.of(raw));
        }
        if (item == Items.CHARCOAL) {
            if (Inv.count(GatherTask::isLog) < n + 1) { run(new GatherTask(Items.OAK_LOG, n + 1)); return Status.RUNNING; }
            run(new ContainerTasks.Smelt(GatherTask::isLog, Items.CHARCOAL, target));
            return Status.RUNNING;
        }

        // Cas general : le planificateur remonte toutes les recettes connues (vanilla + mods)
        Planner.Plan p = new Planner(failed).plan(item);
        current = p;
        switch (p.way()) {
            case MINE, MINE_SPECIAL -> {
                if (p.tool() != null && Planner.toolNeeded(p.blocks().iterator().next().defaultBlockState()) != null) {
                    status = "outil : " + p.tool().getDescription().getString();
                    run(new GatherTask(p.tool(), 1));
                    return Status.RUNNING;
                }
                if (!p.nearby()) BotLog.info("Je pars chercher " + label + " (pas vu dans les environs).");
                haveBeforeMine = h;
                final MineTask m = new MineTask(p.blocks(), 0, label, null).exploreLimit(20 * 120);
                m.until(() -> have() >= target || m.mined() >= 4 && have() <= haveBeforeMine);
                mining = m;
                run(m);
                return Status.RUNNING;
            }
            case RECIPE -> {
                RecipeBook.Source s = p.source();
                int crafts = (int) Math.ceil(n / (double) s.outCount());
                for (Map.Entry<Ingredient, Integer> e : RecipeBook.grouped(s.ingredients())) {
                    int needed = crafts * e.getValue();
                    if (Inv.count(e.getKey()) < needed) {
                        status = "ingredient : " + RecipeBook.label(e.getKey());
                        run(new GatherTask(e.getKey(), needed));
                        return Status.RUNNING;
                    }
                }
                status = s.describe();
                switch (s.kind()) {
                    case CRAFT -> run(new ContainerTasks.Craft(item, target, (CraftingRecipe) s.recipe()));
                    case SMELT -> { return smelt(s.ingredients().get(0)); }
                    case STONECUT -> run(new ContainerTasks.Stonecut(s.ingredients().get(0), item, target));
                }
                return Status.RUNNING;
            }
            default -> {
                BotLog.warn("Je ne sais pas comment obtenir " + label + " : aucune recette ni aucun bloc connu ("
                        + RecipeBook.recipeCount() + " recettes connues, index des blocs " + DropIndex.progressText() + ").");
                return Status.FAILED;
            }
        }
    }

    /** Cuire au four, en allant chercher du combustible si besoin. */
    private Status smelt(Ingredient input) {
        if (Inv.count(st -> ContainerTasks.Smelt.isFuel(st) && !input.test(st)) == 0) {
            status = "combustible";
            run(new MineTask(Targets.parse("wood"), 0).until(() -> Inv.count(GatherTask::isLog) >= 3));
            return Status.RUNNING;
        }
        run(new ContainerTasks.Smelt(input, item, target));
        return Status.RUNNING;
    }

    @Override
    protected void onChildDone(Task child, Status result) {
        if (child == mining) {
            mining = null;
            if (item != null && have() <= haveBeforeMine && current != null) {
                // on a casse des blocs sans rien recevoir (ou rien trouve) : on n'insiste pas
                if (((MineTask) child).mined() >= 4) for (Block b : current.blocks()) DropIndex.markFailed(b, item);
                failed.add("mine:" + Inv.id(item));
            }
            return;
        }
        if (result != Status.FAILED || current == null || current.source() == null) return;
        if (child instanceof ContainerTasks.Craft || child instanceof ContainerTasks.Smelt || child instanceof ContainerTasks.Stonecut) {
            failed.add(current.source().recipe()); // on essaiera une autre recette
        }
    }
}
