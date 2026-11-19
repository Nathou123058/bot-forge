package fr.nathvavan.nathbot.util;

import fr.nathvavan.nathbot.bot.Bot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Le "cerveau inventaire" : ouvre l'inventaire comme un joueur, regroupe les piles,
 * jette ce qui a le moins de valeur quand c'est plein, et range la barre rapide :
 *   1 epee | 2 pioche | 3 hache | 4 pelle | ... | 8 blocs a poser | 9 nourriture
 * Il ne touche jamais a ton inventaire quand le bot ne fait rien (sauf commande "ranger").
 */
public final class InvManager {
    private InvManager() {}

    private static final int HB_WEAPON = 0, HB_PICK = 1, HB_AXE = 2, HB_SHOVEL = 3, HB_BLOCKS = 7, HB_FOOD = 8;

    private static boolean session = false;
    private static boolean forced = false;
    private static boolean openedScreen = false;
    private static int wait = 0;
    private static int sessionTicks = 0;
    private static int lastOrganize = 0;
    private static boolean lookedBack = false;
    private static boolean aiming = false;
    private static float backYaw;

    private static Minecraft mc() { return Minecraft.getInstance(); }

    /** Commande "ranger" : session forcee, meme si le bot ne fait rien. */
    public static void requestOrganize() {
        forced = true;
        lastOrganize = -100000;
    }

    public static boolean busy() { return session; }

    // ------------------------------------------------------------------ boucle

    /** Appele chaque tick (via la fonction "Inventaire intelligent"). */
    public static void tick() {
        Minecraft mc = mc();
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (session) {
            runSession(p);
            return;
        }
        if (p.tickCount % 10 != 0) return;
        boolean botActive = Bot.I.task() != null && !Bot.I.paused;
        if (!botActive && !forced) return;
        if (!safeToStop(p)) return;
        boolean full = Inv.freeSlots() <= 1;
        boolean organizeTime = forced || p.tickCount - lastOrganize > 20 * 60;
        if (full || organizeTime && nextAction(p, false) != null) {
            if (full && nextAction(p, true) == null && !organizeTime) return; // rien a jeter de raisonnable
            startSession(p, full);
        } else if (organizeTime) {
            lastOrganize = p.tickCount;
            forced = false;
        }
    }

    /** Pas de rangement en plein combat, dans l'eau ou en pleine chute. */
    private static boolean safeToStop(LocalPlayer p) {
        Minecraft mc = mc();
        if (mc.screen != null || p.containerMenu != p.inventoryMenu) return false;
        if (!p.onGround() || p.isInWater() || p.isInLava() || p.isPassenger()) return false;
        if (mc.options.keyAttack.isDown()) return false;
        return mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, p.getBoundingBox().inflate(8),
                e -> e instanceof Enemy && e.isAlive()).isEmpty();
    }

    private static void startSession(LocalPlayer p, boolean full) {
        session = true;
        sessionTicks = 0;
        wait = 4;
        lookedBack = false;
        aiming = false;
        backYaw = p.getYRot() + 180f;
        if (full) Brain.think("inventaire", "Inventaire plein : j'ouvre l'inventaire pour faire de la place.");
        else Brain.think("inventaire", "Je range mon inventaire (barre rapide, piles).");
        if (fr.nathvavan.nathbot.feature.Features.isOn("show_inventory") && mc().screen == null) {
            mc().setScreen(new InventoryScreen(p));
            openedScreen = true;
        }
    }

    private static void endSession(LocalPlayer p) {
        session = false;
        forced = false;
        lastOrganize = p.tickCount;
        if (!p.inventoryMenu.getCarried().isEmpty()) {
            int free = Inv.find(ItemStack::isEmpty);
            if (free >= 0) Inv.click(Inv.menuSlotFor(free), 0, ClickType.PICKUP);
        }
        if (openedScreen && mc().screen instanceof InventoryScreen) p.closeContainer();
        openedScreen = false;
    }

    private static void runSession(LocalPlayer p) {
        Bot.I.featureBusy = true; // la tache du bot attend pendant qu'on range
        if (++sessionTicks > 20 * 15 || p.isInWater() || p.hurtTime > 0 && sessionTicks > 10) {
            endSession(p);
            return;
        }
        if (mc().screen != null && !(mc().screen instanceof InventoryScreen)) {
            endSession(p);
            return;
        }
        if (aiming) {
            // on se tourne pour jeter derriere soi (sinon on ramasse aussitot ce qu'on jette)
            if (!Rot.setRotation(backYaw, 25f)) return;
            aiming = false;
            lookedBack = true;
            wait = 0;
        }
        if (wait-- > 0) return;
        Runnable a = nextAction(p, Inv.freeSlots() <= 2);
        if (a == null) {
            endSession(p);
            return;
        }
        a.run();
        wait = 3; // un clic toutes les 3 ticks : on voit ce qu'il fait
    }

    // ------------------------------------------------------------------ decisions

    /**
     * Prochaine action utile, ou null si tout est en ordre.
     * @param makeRoom true si on doit liberer de la place
     */
    private static Runnable nextAction(LocalPlayer p, boolean makeRoom) {
        List<ItemStack> items = p.getInventory().items;
        // 1) regrouper deux piles incompletes du meme objet
        for (int i = 0; i < 36; i++) {
            ItemStack a = items.get(i);
            if (a.isEmpty() || !a.isStackable() || a.getCount() >= a.getMaxStackSize()) continue;
            for (int k = i + 1; k < 36; k++) {
                ItemStack b = items.get(k);
                if (b.isEmpty() || b.getCount() >= b.getMaxStackSize() || !ItemStack.isSameItemSameTags(a, b)) continue;
                final int from = k, to = i;
                final String name = a.getHoverName().getString();
                return () -> {
                    int ms = Inv.menuSlotFor(from), mt = Inv.menuSlotFor(to);
                    Inv.click(ms, 0, ClickType.PICKUP);
                    Inv.click(mt, 0, ClickType.PICKUP);
                    if (!p.inventoryMenu.getCarried().isEmpty()) Inv.click(ms, 0, ClickType.PICKUP);
                    Brain.think("inventaire", "Je regroupe mes piles de " + name + ".");
                };
            }
        }
        // 2) faire de la place
        if (makeRoom) {
            int worst = -1;
            int wv = 30;
            for (int i = 0; i < 36; i++) {
                ItemStack s = items.get(i);
                if (s.isEmpty()) continue;
                int v = value(p, s, i);
                if (v < wv) { wv = v; worst = i; }
            }
            if (worst >= 0) {
                final int slot = worst;
                final ItemStack st = items.get(slot).copy();
                return () -> {
                    if (!lookedBack) {
                        aiming = true;
                        return;
                    }
                    Rot.syncNow();
                    Inv.click(Inv.menuSlotFor(slot), 1, ClickType.THROW);
                    Brain.think("inventaire", "Inventaire plein : je jette " + st.getCount() + "x " + st.getHoverName().getString() + " (" + reason(p, st, slot) + ").");
                };
            }
        }
        // 3) barre rapide
        int[] wanted = {HB_WEAPON, HB_PICK, HB_AXE, HB_SHOVEL, HB_BLOCKS, HB_FOOD};
        for (int hb : wanted) {
            int best = bestFor(p, hb);
            if (best < 0 || best == hb) continue;
            ItemStack cur = items.get(hb);
            if (!cur.isEmpty() && score(p, cur, hb) >= score(p, items.get(best), hb)) continue;
            final int from = best, to = hb;
            final String name = items.get(best).getHoverName().getString();
            return () -> {
                Inv.click(Inv.menuSlotFor(from), to, ClickType.SWAP);
                Brain.think("inventaire", "Je mets " + name + " dans la case " + (to + 1) + " de la barre rapide.");
            };
        }
        return null;
    }

    // ------------------------------------------------------------------ valeur des objets

    private static final Set<Item> JUNK = Set.of(Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.DEAD_BUSH, Items.SPIDER_EYE,
            Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.PUMPKIN_SEEDS, Items.MELON_SEEDS, Items.PUFFERFISH, Items.TROPICAL_FISH,
            Items.LILY_PAD, Items.KELP, Items.SEAGRASS, Items.GRASS, Items.FERN, Items.TALL_GRASS, Items.VINE, Items.GLOW_LICHEN);
    private static final Set<Item> STONY = Set.of(Items.DIORITE, Items.GRANITE, Items.ANDESITE, Items.TUFF, Items.CALCITE, Items.GRAVEL,
            Items.NETHERRACK, Items.BASALT, Items.BLACKSTONE, Items.SANDSTONE, Items.RED_SAND, Items.SAND, Items.DIRT,
            Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.SMOOTH_BASALT, Items.DRIPSTONE_BLOCK, Items.POINTED_DRIPSTONE, Items.FLINT);
    private static final Set<Item> PRECIOUS = Set.of(Items.DIAMOND, Items.EMERALD, Items.IRON_INGOT, Items.GOLD_INGOT, Items.RAW_IRON,
            Items.RAW_GOLD, Items.ENDER_PEARL, Items.ENDER_EYE, Items.BLAZE_ROD, Items.BLAZE_POWDER, Items.OBSIDIAN, Items.TOTEM_OF_UNDYING,
            Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.ANCIENT_DEBRIS, Items.WATER_BUCKET, Items.BUCKET, Items.LAVA_BUCKET,
            Items.FLINT_AND_STEEL, Items.BOW, Items.ARROW, Items.CRAFTING_TABLE, Items.FURNACE, Items.TORCH, Items.COAL, Items.CHARCOAL,
            Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.SHIELD, Items.LAPIS_LAZULI, Items.REDSTONE);

    /** Valeur 0..100 : sous 30, l'objet peut etre jete si l'inventaire est plein. */
    private static int value(LocalPlayer p, ItemStack s, int slot) {
        Item it = s.getItem();
        if (s.isEnchanted() || s.hasCustomHoverName()) return 100;
        if (PRECIOUS.contains(it) || it instanceof BoatItem) return 100;
        String kind = toolKind(s);
        if (kind != null) return isBestOfKind(p, s, kind) ? 100 : 8;
        if (JUNK.contains(it)) return it == Items.ROTTEN_FLESH && foodCount(p) == 0 ? 40 : 3;
        if (Inv.isGoodFood(s)) return 85;
        if (STONY.contains(it)) {
            // on garde une reserve de blocs a poser (ponts, piliers)
            boolean throwaway = Inv.isThrowaway(s);
            if (throwaway && firstSlotOf(p, it) == slot && Inv.throwawayCount() <= 192) return 45;
            return (it == Items.DIORITE || it == Items.GRANITE || it == Items.ANDESITE || it == Items.TUFF || it == Items.CALCITE
                    || it == Items.GRAVEL || it == Items.DRIPSTONE_BLOCK || it == Items.POINTED_DRIPSTONE) ? 10 : 20;
        }
        if (s.is(ItemTags.SAPLINGS) || s.is(ItemTags.SMALL_FLOWERS)) return 15;
        return 60;
    }

    private static String reason(LocalPlayer p, ItemStack s, int slot) {
        if (toolKind(s) != null) return "j'ai mieux";
        if (JUNK.contains(s.getItem())) return "inutile";
        if (STONY.contains(s.getItem())) return "bloc commun, j'en ai assez";
        return "peu utile";
    }

    private static int firstSlotOf(LocalPlayer p, Item it) {
        List<ItemStack> items = p.getInventory().items;
        for (int i = 0; i < 36; i++) if (items.get(i).is(it)) return i;
        return -1;
    }

    private static int foodCount(LocalPlayer p) {
        return Inv.count(Inv::isGoodFood);
    }

    // ------------------------------------------------------------------ outils

    /** Type d'outil / arme / armure, ou null. */
    static String toolKind(ItemStack s) {
        Item it = s.getItem();
        if (it instanceof SwordItem) return "sword";
        if (it instanceof PickaxeItem) return "pickaxe";
        if (it instanceof AxeItem) return "axe";
        if (it instanceof ShovelItem) return "shovel";
        if (it instanceof HoeItem) return "hoe";
        if (it instanceof ArmorItem a) return "armor_" + a.getEquipmentSlot().getName();
        return null;
    }

    private static boolean isBestOfKind(LocalPlayer p, ItemStack s, String kind) {
        double mine = toolScore(s, kind);
        // compare aussi a l'armure portee
        if (kind.startsWith("armor_")) {
            for (ItemStack w : p.getInventory().armor) if (!w.isEmpty() && kind.equals(toolKind(w)) && toolScore(w, kind) > mine) return false;
        }
        for (ItemStack o : p.getInventory().items) {
            if (o == s || o.isEmpty() || !kind.equals(toolKind(o))) continue;
            double v = toolScore(o, kind);
            if (v > mine + 0.001) return false;
            if (Math.abs(v - mine) < 0.001 && o.getDamageValue() < s.getDamageValue()) return false;
        }
        return true;
    }

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState();
    private static final BlockState DIAMOND_ORE = Blocks.DIAMOND_ORE.defaultBlockState();
    private static final BlockState IRON_ORE = Blocks.IRON_ORE.defaultBlockState();

    static double toolScore(ItemStack s, String kind) {
        double durab = s.isDamageableItem() ? 1.0 - s.getDamageValue() / (double) Math.max(1, s.getMaxDamage()) : 1;
        double ench = EnchantmentHelper.getEnchantments(s).size() * 0.5;
        switch (kind) {
            case "sword" -> { return Inv.weaponScore(s) + ench + durab * 0.1; }
            case "pickaxe" -> {
                int tier = s.isCorrectToolForDrops(OBSIDIAN) ? 4 : s.isCorrectToolForDrops(DIAMOND_ORE) ? 3 : s.isCorrectToolForDrops(IRON_ORE) ? 2 : s.isCorrectToolForDrops(STONE) ? 1 : 0;
                return tier * 20 + s.getDestroySpeed(STONE) + ench + durab * 0.1;
            }
            case "axe" -> { return s.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()) + ench + durab * 0.1; }
            case "shovel" -> { return s.getDestroySpeed(Blocks.DIRT.defaultBlockState()) + ench + durab * 0.1; }
            case "hoe" -> { return s.getDestroySpeed(Blocks.HAY_BLOCK.defaultBlockState()) + ench + durab * 0.1; }
            default -> {
                if (s.getItem() instanceof ArmorItem a) return a.getDefense() * 2 + a.getToughness() + ench + durab * 0.1;
                return 0;
            }
        }
    }

    /** Score d'un objet pour une case de la barre rapide (plus haut = mieux). */
    private static double score(LocalPlayer p, ItemStack s, int hb) {
        if (s.isEmpty()) return -1;
        return switch (hb) {
            case HB_WEAPON -> s.getItem() instanceof SwordItem ? toolScore(s, "sword") : -1;
            case HB_PICK -> s.getItem() instanceof PickaxeItem ? toolScore(s, "pickaxe") : -1;
            case HB_AXE -> s.getItem() instanceof AxeItem ? toolScore(s, "axe") : -1;
            case HB_SHOVEL -> s.getItem() instanceof ShovelItem ? toolScore(s, "shovel") : -1;
            case HB_BLOCKS -> Inv.isThrowaway(s) ? s.getCount() : -1;
            case HB_FOOD -> Inv.isGoodFood(s) ? Inv.foodValue(s) * 10 + s.getCount() / 8.0 : -1;
            default -> -1;
        };
    }

    private static int bestFor(LocalPlayer p, int hb) {
        List<ItemStack> items = p.getInventory().items;
        int best = -1;
        double bs = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = items.get(i);
            double sc = score(p, s, hb);
            if (sc < 0) continue;
            // ne pas voler un objet deja bien range dans une autre case reservee
            if (i != hb && i < 9 && isReserved(i) && score(p, s, i) >= 0) continue;
            if (sc > bs + 0.001) { bs = sc; best = i; }
        }
        return best;
    }

    private static boolean isReserved(int i) {
        return i == HB_WEAPON || i == HB_PICK || i == HB_AXE || i == HB_SHOVEL || i == HB_BLOCKS || i == HB_FOOD;
    }

    /** Resume pour le site web. */
    public static Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        LocalPlayer p = mc().player;
        if (p == null) return m;
        m.put("free", Inv.freeSlots());
        m.put("session", session);
        m.put("show", fr.nathvavan.nathbot.feature.Features.isOn("show_inventory"));
        return m;
    }
}
