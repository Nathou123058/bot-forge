package fr.nathvavan.nathbot.task;

import fr.nathvavan.nathbot.util.BotLog;
import fr.nathvavan.nathbot.util.Inv;
import fr.nathvavan.nathbot.util.Targets;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/**
 * Obtenir un objet en survie : le miner, le fabriquer ou le cuire.
 * Gere les materiaux de base (bois, planches, pierre, terre, sable, verre...) et les recettes simples.
 */
public class GatherTask extends Task {
    private final Item item;
    private final int target;
    private int attempts = 0;

    public GatherTask(Item item, int targetTotal) {
        this.item = item;
        this.target = targetTotal;
    }

    @Override
    public String name() { return "Recolter " + item.getDescription().getString() + " (" + have() + "/" + target + ")"; }

    public static boolean isLog(ItemStack s) { return s.is(ItemTags.LOGS); }

    public static boolean canGather(Item item) {
        return directMine(item) != null || item == Items.GLASS || item == Items.STONE || item == Items.CHARCOAL || craftableFromBasics(item);
    }

    /** Bloc a miner directement pour obtenir l'objet. */
    static Targets.Spec directMine(Item item) {
        ItemStack s = new ItemStack(item);
        if (s.is(ItemTags.LOGS)) return new Targets.Spec("bois", Set.of(net.minecraft.world.level.block.Block.byItem(item)), null);
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
        if (item == Items.DIAMOND) return Targets.parse("diamond");
        if (item == Items.REDSTONE) return Targets.parse("redstone");
        if (item == Items.LAPIS_LAZULI) return Targets.parse("lapis");
        if (item == Items.EMERALD) return Targets.parse("emerald");
        if (item == Items.OBSIDIAN) return Targets.parse("obsidian");
        if (item == Items.NETHERRACK) return Targets.parse("netherrack");
        if (item == Items.ANDESITE || item == Items.DIORITE || item == Items.GRANITE || item == Items.TUFF || item == Items.CALCITE)
            return new Targets.Spec(Inv.id(item), Set.of(net.minecraft.world.level.block.Block.byItem(item)), null);
        return null;
    }

    static boolean craftableFromBasics(Item item) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return false;
        for (CraftingRecipe r : mc.level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (r.isSpecial() || !r.getResultItem(mc.level.registryAccess()).is(item)) continue;
            boolean ok = true;
            for (Ingredient ing : r.getIngredients()) {
                if (ing.isEmpty()) continue;
                boolean basic = false;
                for (ItemStack c : ing.getItems()) {
                    if (c.is(ItemTags.PLANKS) || c.is(Items.STICK) || c.is(Items.COBBLESTONE) || c.is(ItemTags.LOGS) || c.is(Items.COAL)
                            || c.is(Items.SAND) || c.is(Items.DIRT) || c.is(Items.GLASS) || c.is(Items.STONE) || c.is(Items.IRON_INGOT)) {
                        basic = true;
                        break;
                    }
                }
                if (!basic) { ok = false; break; }
            }
            if (ok) return true;
        }
        return false;
    }

    private int need() { return target - have(); }

    private int have() {
        ItemStack st = new ItemStack(item);
        if (st.is(ItemTags.PLANKS)) return Inv.count(s -> s.is(ItemTags.PLANKS));
        if (st.is(ItemTags.LOGS)) return Inv.count(GatherTask::isLog);
        return Inv.count(item);
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
        if (have() >= target) return Status.SUCCESS;
        if (++attempts > 12) {
            BotLog.warn("Je n'arrive pas a obtenir " + item.getDescription().getString());
            return Status.FAILED;
        }
        int n = need();
        Targets.Spec spec = directMine(item);
        if (spec != null && !spec.blocks().isEmpty()) {
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
            run(new ContainerTasks.Smelt(s -> s.is(Items.COBBLESTONE), Items.STONE, target));
            return Status.RUNNING;
        }
        if (item == Items.GLASS) {
            if (Inv.count(Items.SAND) < n) { run(new GatherTask(Items.SAND, n)); return Status.RUNNING; }
            run(new ContainerTasks.Smelt(s -> s.is(Items.SAND), Items.GLASS, target));
            return Status.RUNNING;
        }
        if (item == Items.IRON_INGOT || item == Items.GOLD_INGOT || item == Items.COPPER_INGOT) {
            Item raw = item == Items.IRON_INGOT ? Items.RAW_IRON : item == Items.GOLD_INGOT ? Items.RAW_GOLD : Items.RAW_COPPER;
            if (Inv.count(raw) < n) { run(new GatherTask(raw, n)); return Status.RUNNING; }
            run(new ContainerTasks.Smelt(s -> s.is(raw), item, target));
            return Status.RUNNING;
        }
        if (item == Items.CHARCOAL) {
            if (Inv.count(GatherTask::isLog) < n + 1) { run(new GatherTask(Items.OAK_LOG, n + 1)); return Status.RUNNING; }
            run(new ContainerTasks.Smelt(GatherTask::isLog, Items.CHARCOAL, target));
            return Status.RUNNING;
        }
        if (new ItemStack(item).is(ItemTags.PLANKS)) {
            int logs = Inv.count(GatherTask::isLog);
            if (logs * 4 < n) {
                run(new MineTask(Targets.parse("wood"), 0).until(() -> Inv.count(GatherTask::isLog) * 4 >= need()));
                return Status.RUNNING;
            }
            run(new ContainerTasks.Craft(s -> s.is(ItemTags.PLANKS), target, "planches"));
            return Status.RUNNING;
        }
        // Recette : on prepare les ingredients de base puis on fabrique
        var mc = net.minecraft.client.Minecraft.getInstance();
        for (CraftingRecipe r : mc.level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (r.isSpecial() || !r.getResultItem(mc.level.registryAccess()).is(item)) continue;
            int per = Math.max(1, r.getResultItem(mc.level.registryAccess()).getCount());
            int crafts = (int) Math.ceil(n / (double) per);
            // verifier chaque ingredient
            for (Ingredient ing : r.getIngredients()) {
                if (ing.isEmpty()) continue;
                int needed = 0;
                for (Ingredient o : r.getIngredients()) if (o == ing || o.equals(ing)) needed += crafts;
                int have = Inv.count(ing);
                if (have >= needed) continue;
                ItemStack[] options = ing.getItems();
                if (options.length == 0) continue;
                Item base = options[0].getItem();
                for (ItemStack o : options) if (o.is(ItemTags.PLANKS) || o.is(Items.COBBLESTONE) || o.is(ItemTags.LOGS)) { base = o.getItem(); break; }
                if (new ItemStack(base).is(ItemTags.PLANKS)) {
                    run(new GatherTask(Items.OAK_PLANKS, needed - have + Inv.count(Items.OAK_PLANKS)));
                } else {
                    run(new GatherTask(base, needed - have + Inv.count(base)));
                }
                return Status.RUNNING;
            }
            run(new ContainerTasks.Craft(item, target));
            return Status.RUNNING;
        }
        BotLog.warn("Je ne sais pas comment obtenir " + item.getDescription().getString());
        return Status.FAILED;
    }
}
