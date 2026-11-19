package fr.nathvavan.nathbot.task;

import fr.nathvavan.nathbot.bot.Actions;
import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.path.Goal;
import fr.nathvavan.nathbot.util.BotLog;
import fr.nathvavan.nathbot.util.Inv;
import fr.nathvavan.nathbot.util.Scanner;
import fr.nathvavan.nathbot.util.WV;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Taches liees aux interfaces : ouvrir, fabriquer, cuire, poser un bloc utilitaire. */
public final class ContainerTasks {
    private ContainerTasks() {}

    static void closeIfOpen() {
        var p = net.minecraft.client.Minecraft.getInstance().player;
        if (p != null && p.containerMenu != p.inventoryMenu) p.closeContainer();
    }

    // ===================================================================== ouvrir

    public static class Open extends Task {
        private final BlockPos pos;
        private final Class<? extends AbstractContainerMenu> type;
        private int wait = 0;
        private int tries = 0;

        public Open(BlockPos pos, Class<? extends AbstractContainerMenu> type) {
            this.pos = pos;
            this.type = type;
        }

        @Override
        public String name() { return "Ouvrir " + WV.st(pos).getBlock().getName().getString(); }

        @Override
        protected Status update() {
            if (type.isInstance(mc.player.containerMenu)) return Status.SUCCESS;
            if (tries > 6) return Status.FAILED;
            if (!Actions.inReach(pos)) {
                run(new GotoTask(new Goal.Reach(pos, 3.5), 3));
                tries++;
                return Status.RUNNING;
            }
            if (wait-- > 0) return Status.RUNNING;
            Direction face = Actions.faceToward(pos);
            Actions.useOnBlock(pos, face);
            tries++;
            wait = 12;
            return Status.RUNNING;
        }
    }

    // ===================================================================== poser

    public static class PlaceUtility extends Task {
        private final Item item;
        private final Block block;
        public BlockPos placed;
        private BlockPos candidate;
        private int attempts = 0;

        public PlaceUtility(Item item, Block block) {
            this.item = item;
            this.block = block;
        }

        @Override
        public String name() { return "Poser " + item.getDescription().getString(); }

        private BlockPos choose() {
            BlockPos f = Bot.feet();
            List<BlockPos> c = new ArrayList<>();
            for (int r = 1; r <= 2; r++)
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++)
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos p = f.offset(dx, dy, dz);
                            if (p.equals(f) || p.equals(f.above())) continue;
                            if (Math.abs(dx) < r && Math.abs(dz) < r) continue;
                            if (WV.replaceable(p) && !WV.isWater(p) && WV.solidSupport(p.below()) && Actions.inReach(p)) c.add(p);
                        }
            c.sort((a, b) -> Double.compare(a.distSqr(f), b.distSqr(f)));
            return c.isEmpty() ? null : c.get(0);
        }

        @Override
        protected Status update() {
            if (candidate != null && WV.st(candidate).is(block)) {
                placed = candidate;
                return Status.SUCCESS;
            }
            if (!Inv.has(item)) return Status.FAILED;
            if (++attempts > 60) return Status.FAILED;
            if (candidate == null || !WV.replaceable(candidate)) candidate = choose();
            if (candidate == null) {
                // on se deplace un peu pour trouver de la place
                run(new GotoTask(new Goal.Away(Bot.feet().getX(), Bot.feet().getZ(), 3), 2));
                return Status.RUNNING;
            }
            Actions.placeAgainst(candidate.below(), Direction.UP, s -> s.is(item));
            return Status.RUNNING;
        }
    }

    // ===================================================================== fabriquer

    public static class Craft extends Task {
        private final Predicate<ItemStack> result;
        private final int target;
        private final String label;
        private int phase = 0; // 0 preparer, 1 attendre, 2 nettoyer
        private int wait = 0;
        private int retries = 0;
        private CraftingRecipe recipe;
        private Item item;
        private CraftingRecipe preferred;

        public Craft(Item item, int targetTotal) {
            this(s -> s.is(item), targetTotal, item.getDescription().getString());
            this.item = item;
        }

        /** Fabriquer avec une recette precise (choisie par le planificateur). */
        public Craft(Item item, int targetTotal, CraftingRecipe preferred) {
            this(item, targetTotal);
            this.preferred = preferred;
        }

        public Craft(Predicate<ItemStack> result, int targetTotal, String label) {
            this.result = result;
            this.target = targetTotal;
            this.label = label;
        }

        @Override
        public String name() { return "Fabriquer " + label + " (" + Inv.count(result) + "/" + target + ")"; }

        private RegistryAccess ra() { return mc.level.registryAccess(); }

        /** Liste "case -> ingredient" pour une grille de largeur gw. */
        private List<int[]> cells(CraftingRecipe r, int gw) {
            List<int[]> out = new ArrayList<>();
            List<Ingredient> ings = r.getIngredients();
            if (r instanceof ShapedRecipe sr) {
                int w = sr.getWidth(), h = sr.getHeight();
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        Ingredient ing = ings.get(y * w + x);
                        if (!ing.isEmpty()) out.add(new int[]{1 + y * gw + x, y * w + x});
                    }
            } else {
                int k = 0;
                for (int i = 0; i < ings.size(); i++) {
                    if (ings.get(i).isEmpty()) continue;
                    out.add(new int[]{1 + k, i});
                    k++;
                }
            }
            return out;
        }

        private static boolean fits2x2(CraftingRecipe r) {
            return r.canCraftInDimensions(2, 2);
        }

        /** Combien de fois peut-on faire cette recette avec l'inventaire actuel ? */
        private int craftable(CraftingRecipe r) {
            List<ItemStack> inv = new ArrayList<>();
            for (ItemStack s : mc.player.getInventory().items) inv.add(s.copy());
            List<Ingredient> needs = new ArrayList<>();
            for (Ingredient i : r.getIngredients()) if (!i.isEmpty()) needs.add(i);
            if (needs.isEmpty()) return 0;
            int n = 0;
            outer:
            while (n < 64) {
                for (Ingredient ing : needs) {
                    boolean ok = false;
                    for (ItemStack s : inv) {
                        if (!s.isEmpty() && ing.test(s)) {
                            s.shrink(1);
                            ok = true;
                            break;
                        }
                    }
                    if (!ok) break outer;
                }
                n++;
            }
            return n;
        }

        private CraftingRecipe choose() {
            if (preferred != null && craftable(preferred) > 0) return preferred;
            CraftingRecipe best = null;
            Iterable<CraftingRecipe> all = item != null ? fr.nathvavan.nathbot.util.RecipeBook.craftingFor(item)
                    : mc.level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
            for (CraftingRecipe r : all) {
                if (r.isSpecial() || !r.canCraftInDimensions(3, 3)) continue;
                ItemStack out = r.getResultItem(ra());
                if (out.isEmpty() || !result.test(out)) continue;
                if (craftable(r) > 0) {
                    if (best == null || fits2x2(r) && !fits2x2(best)) best = r;
                }
            }
            return best;
        }

        private int sourceSlot(AbstractContainerMenu menu, Ingredient ing) {
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot s = menu.slots.get(i);
                if (s.container != mc.player.getInventory()) continue;
                ItemStack st = s.getItem();
                if (!st.isEmpty() && ing.test(st)) return i;
            }
            return -1;
        }

        private boolean gridEmpty(AbstractContainerMenu menu, int size) {
            for (int i = 1; i <= size; i++) if (!menu.getSlot(i).getItem().isEmpty()) return false;
            return true;
        }

        @Override
        protected Status update() {
            int have = Inv.count(result);
            if (have >= target) {
                closeIfOpen();
                return Status.SUCCESS;
            }
            if (phase == 0 && wait == 0 || recipe == null) recipe = choose();
            if (recipe == null) {
                BotLog.warn("Impossible de fabriquer " + label + " : ingredients manquants.");
                closeIfOpen();
                return Status.FAILED;
            }
            AbstractContainerMenu menu = mc.player.containerMenu;
            boolean table = !fits2x2(recipe);
            if (table && !(menu instanceof CraftingMenu)) {
                closeIfOpen();
                BlockPos t = Scanner.nearest(s -> s.is(Blocks.CRAFTING_TABLE), Bot.feet(), 2);
                if (t == null || t.distSqr(Bot.feet()) > 24 * 24) {
                    if (Inv.has(Items.CRAFTING_TABLE)) {
                        run(new PlaceUtility(Items.CRAFTING_TABLE, Blocks.CRAFTING_TABLE));
                        return Status.RUNNING;
                    }
                    if (++retries > 3) return Status.FAILED;
                    run(new GatherTask(Items.CRAFTING_TABLE, 1));
                    return Status.RUNNING;
                }
                run(new Open(t, CraftingMenu.class));
                return Status.RUNNING;
            }
            if (!table && menu != mc.player.inventoryMenu && !(menu instanceof CraftingMenu)) {
                closeIfOpen();
                return Status.RUNNING;
            }
            int gw = menu instanceof CraftingMenu ? 3 : 2;
            int gridSize = gw * gw;
            if (wait > 0) {
                wait--;
                return Status.RUNNING;
            }
            switch (phase) {
                case 0 -> {
                    if (!gridEmpty(menu, gridSize)) {
                        for (int i = 1; i <= gridSize; i++)
                            if (!menu.getSlot(i).getItem().isEmpty()) Inv.click(i, 0, ClickType.QUICK_MOVE);
                        wait = 2;
                        return Status.RUNNING;
                    }
                    int perCraft = Math.max(1, recipe.getResultItem(ra()).getCount());
                    int crafts = Math.min(craftable(recipe), (int) Math.ceil((target - have) / (double) perCraft));
                    crafts = Math.max(1, Math.min(crafts, 64));
                    List<Ingredient> ings = recipe.getIngredients();
                    for (int[] cell : cells(recipe, gw)) {
                        Ingredient ing = ings.get(cell[1]);
                        int remaining = crafts;
                        int guard = 0;
                        while (remaining > 0 && guard++ < 10) {
                            int src = sourceSlot(menu, ing);
                            if (src < 0) break;
                            Inv.click(src, 0, ClickType.PICKUP);
                            int k = Math.min(menu.getCarried().getCount(), remaining);
                            for (int j = 0; j < k; j++) Inv.click(cell[0], 1, ClickType.PICKUP);
                            remaining -= k;
                            if (!menu.getCarried().isEmpty()) Inv.click(src, 0, ClickType.PICKUP);
                        }
                    }
                    phase = 1;
                    wait = 2;
                }
                case 1 -> {
                    ItemStack out = menu.getSlot(0).getItem();
                    if (!out.isEmpty() && result.test(out)) {
                        Inv.click(0, 0, ClickType.QUICK_MOVE);
                        phase = 2;
                        wait = 3;
                    } else if (++retries > 40) {
                        phase = 2;
                    }
                }
                default -> {
                    phase = 0;
                }
            }
            return Status.RUNNING;
        }

        @Override
        protected void onStop() {
            closeIfOpen();
        }
    }

    // ===================================================================== cuire

    public static class Smelt extends Task {
        private final Predicate<ItemStack> input;
        private final Item output;
        private final int target;
        private int wait = 0;
        private int idle = 0;
        private int furnaceTries = 0;

        public Smelt(Predicate<ItemStack> input, Item output, int target) {
            this.input = input;
            this.output = output;
            this.target = target;
        }

        @Override
        public String name() { return "Cuire " + output.getDescription().getString() + " (" + Inv.count(output) + "/" + target + ")"; }

        static boolean isFuel(ItemStack s) {
            if (s.is(Items.CRAFTING_TABLE) || s.is(Items.CHEST)) return false;
            return ForgeHooks.getBurnTime(s, RecipeType.SMELTING) > 0 && !s.is(Items.LAVA_BUCKET);
        }

        @Override
        protected Status update() {
            if (Inv.count(output) >= target) {
                closeIfOpen();
                return Status.SUCCESS;
            }
            AbstractContainerMenu menu = mc.player.containerMenu;
            if (!(menu instanceof AbstractFurnaceMenu fm)) {
                BlockPos f = Scanner.nearest(s -> s.getBlock() instanceof AbstractFurnaceBlock && s.is(Blocks.FURNACE), Bot.feet(), 2);
                if (f == null || f.distSqr(Bot.feet()) > 24 * 24) {
                    if (Inv.has(Items.FURNACE)) {
                        run(new PlaceUtility(Items.FURNACE, Blocks.FURNACE));
                        return Status.RUNNING;
                    }
                    if (++furnaceTries > 3) {
                        BotLog.warn("Pas de four et impossible d'en fabriquer un.");
                        return Status.FAILED;
                    }
                    run(new GatherTask(Items.FURNACE, 1));
                    return Status.RUNNING;
                }
                run(new Open(f, AbstractFurnaceMenu.class));
                return Status.RUNNING;
            }
            if (wait > 0) {
                wait--;
                return Status.RUNNING;
            }
            // recuperer le resultat
            if (!fm.getSlot(2).getItem().isEmpty()) {
                Inv.click(2, 0, ClickType.QUICK_MOVE);
                wait = 2;
                return Status.RUNNING;
            }
            int needed = target - Inv.count(output);
            ItemStack in0 = fm.getSlot(0).getItem();
            if (in0.isEmpty() || in0.getCount() < needed && input.test(in0)) {
                int src = -1;
                for (int i = 3; i < fm.slots.size(); i++) {
                    ItemStack s = fm.getSlot(i).getItem();
                    if (!s.isEmpty() && input.test(s)) { src = i; break; }
                }
                if (src >= 0) {
                    Inv.click(src, 0, ClickType.PICKUP);
                    int k = Math.min(fm.getCarried().getCount(), needed - in0.getCount());
                    for (int j = 0; j < k; j++) Inv.click(0, 1, ClickType.PICKUP);
                    if (!fm.getCarried().isEmpty()) Inv.click(src, 0, ClickType.PICKUP);
                    wait = 2;
                    return Status.RUNNING;
                } else if (in0.isEmpty() && !fm.isLit()) {
                    BotLog.warn("Plus rien a cuire.");
                    closeIfOpen();
                    return Status.FAILED;
                }
            }
            // combustible
            if (fm.getSlot(1).getItem().isEmpty() && !fm.getSlot(0).getItem().isEmpty() && (!fm.isLit() || fm.getLitProgress() < 3)) {
                int src = -1;
                int bestPrio = 99;
                for (int i = 3; i < fm.slots.size(); i++) {
                    ItemStack s = fm.getSlot(i).getItem();
                    if (s.isEmpty() || !isFuel(s) || input.test(s)) continue;
                    int prio = s.is(Items.COAL) || s.is(Items.CHARCOAL) ? 0 : s.is(Items.COAL_BLOCK) ? 1 : s.is(net.minecraft.tags.ItemTags.PLANKS) ? 2 : s.is(net.minecraft.tags.ItemTags.LOGS) ? 3 : 5;
                    if (prio < bestPrio) { bestPrio = prio; src = i; }
                }
                if (src < 0) {
                    if (++idle > 100) {
                        BotLog.warn("Pas de combustible pour le four (charbon, bois...).");
                        closeIfOpen();
                        return Status.FAILED;
                    }
                    return Status.RUNNING;
                }
                ItemStack fuel = fm.getSlot(src).getItem();
                int per = Math.max(1, ForgeHooks.getBurnTime(fuel, RecipeType.SMELTING) / 200);
                int count = (int) Math.ceil(fm.getSlot(0).getItem().getCount() / (double) per);
                Inv.click(src, 0, ClickType.PICKUP);
                int k = Math.min(fm.getCarried().getCount(), Math.max(1, count));
                for (int j = 0; j < k; j++) Inv.click(1, 1, ClickType.PICKUP);
                if (!fm.getCarried().isEmpty()) Inv.click(src, 0, ClickType.PICKUP);
                wait = 2;
                return Status.RUNNING;
            }
            status = "cuisson...";
            if (ticks > 20 * 12 * Math.max(needed, 1) + 1200) return Status.FAILED;
            return Status.RUNNING;
        }

        @Override
        protected void onStop() {
            closeIfOpen();
        }
    }

    // ===================================================================== tailleur de pierre

    public static class Stonecut extends Task {
        private final Predicate<ItemStack> input;
        private final Item output;
        private final int target;
        private int wait = 0;
        private int retries = 0;
        private int stationTries = 0;

        public Stonecut(Predicate<ItemStack> input, Item output, int target) {
            this.input = input;
            this.output = output;
            this.target = target;
        }

        @Override
        public String name() { return "Tailler " + output.getDescription().getString() + " (" + Inv.count(output) + "/" + target + ")"; }

        @Override
        protected Status update() {
            if (Inv.count(output) >= target) {
                closeIfOpen();
                return Status.SUCCESS;
            }
            AbstractContainerMenu menu = mc.player.containerMenu;
            if (!(menu instanceof StonecutterMenu sm)) {
                closeIfOpen();
                BlockPos t = Scanner.nearest(s -> s.is(Blocks.STONECUTTER), Bot.feet(), 2);
                if (t == null || t.distSqr(Bot.feet()) > 24 * 24) {
                    if (Inv.has(Items.STONECUTTER)) {
                        run(new PlaceUtility(Items.STONECUTTER, Blocks.STONECUTTER));
                        return Status.RUNNING;
                    }
                    if (++stationTries > 3) return Status.FAILED;
                    run(new GatherTask(Items.STONECUTTER, 1));
                    return Status.RUNNING;
                }
                run(new Open(t, StonecutterMenu.class));
                return Status.RUNNING;
            }
            if (wait > 0) {
                wait--;
                return Status.RUNNING;
            }
            if (++retries > 200) return Status.FAILED;
            ItemStack res = sm.getSlot(1).getItem();
            if (!res.isEmpty() && res.is(output)) {
                // on prend juste ce qu'il faut (pas tout le stack)
                int need = target - Inv.count(output);
                int picks = Math.max(1, (int) Math.ceil(need / (double) res.getCount()));
                for (int i = 0; i < picks && !sm.getSlot(1).getItem().isEmpty(); i++) {
                    ItemStack carried = sm.getCarried();
                    if (!carried.isEmpty() && carried.getCount() + res.getCount() > carried.getMaxStackSize()) break;
                    Inv.click(1, 0, ClickType.PICKUP);
                }
                ItemStack carried = sm.getCarried();
                int dest = -1;
                for (int i = 2; i < sm.slots.size() && !carried.isEmpty(); i++) {
                    ItemStack st = sm.getSlot(i).getItem();
                    if (st.isEmpty() || ItemStack.isSameItemSameTags(st, carried) && st.getCount() + carried.getCount() <= st.getMaxStackSize()) {
                        dest = i;
                        break;
                    }
                }
                if (dest >= 0) Inv.click(dest, 0, ClickType.PICKUP);
                wait = 2;
                return Status.RUNNING;
            }
            ItemStack in = sm.getSlot(0).getItem();
            if (in.isEmpty() || !input.test(in)) {
                if (!in.isEmpty()) {
                    Inv.click(0, 0, ClickType.QUICK_MOVE);
                    wait = 2;
                    return Status.RUNNING;
                }
                int src = -1;
                for (int i = 2; i < sm.slots.size(); i++) {
                    ItemStack st = sm.getSlot(i).getItem();
                    if (!st.isEmpty() && input.test(st)) { src = i; break; }
                }
                if (src < 0) {
                    BotLog.warn("Plus rien a tailler pour " + output.getDescription().getString());
                    closeIfOpen();
                    return Status.FAILED;
                }
                Inv.click(src, 0, ClickType.PICKUP);
                Inv.click(0, 0, ClickType.PICKUP);
                if (!sm.getCarried().isEmpty()) Inv.click(src, 0, ClickType.PICKUP);
                wait = 3;
                return Status.RUNNING;
            }
            List<StonecutterRecipe> recipes = sm.getRecipes();
            for (int i = 0; i < recipes.size(); i++) {
                if (recipes.get(i).getResultItem(mc.level.registryAccess()).is(output)) {
                    if (sm.getSelectedRecipeIndex() != i) {
                        sm.clickMenuButton(mc.player, i);
                        mc.gameMode.handleInventoryButtonClick(sm.containerId, i);
                        wait = 3;
                    }
                    return Status.RUNNING;
                }
            }
            return Status.RUNNING;
        }

        @Override
        protected void onStop() {
            closeIfOpen();
        }
    }
}
