package fr.nathvavan.nathbot.task;

import fr.nathvavan.nathbot.util.Brain;
import fr.nathvavan.nathbot.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Gestion "pro" de l'equipement pour Finir le jeu :
 *  - on use un outil jusqu'au bout (jamais de retour a la pioche en bois tant que la pierre tient),
 *  - mais on fabrique le remplacant AVANT qu'il casse, du meilleur niveau possible avec ce qu'on a,
 *  - armure en fer complete avant le Nether,
 *  - viande crue -> cuite (2x plus nourrissante).
 */
final class ProTools {
    private ProTools() {}

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState IRON_ORE = Blocks.IRON_ORE.defaultBlockState();
    private static final BlockState DIAMOND_ORE = Blocks.DIAMOND_ORE.defaultBlockState();
    private static final BlockState OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState();

    private static final Item[] PICKS = {Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE, Items.WOODEN_PICKAXE};
    private static final Item[] SWORDS = {Items.DIAMOND_SWORD, Items.IRON_SWORD, Items.STONE_SWORD, Items.WOODEN_SWORD};

    /** Dernier echec par objet : on ne s'acharne pas (2 minutes de pause). */
    private static final java.util.Map<Item, Long> FAILED_AT = new java.util.HashMap<>();

    static int usesLeft(ItemStack s) { return s.getMaxDamage() - s.getDamageValue(); }

    private static boolean almostBroken(ItemStack s) {
        return s.isDamageableItem() && usesLeft(s) <= Math.max(8, s.getMaxDamage() * 7 / 100);
    }

    static int pickTier(ItemStack s) {
        if (!(s.getItem() instanceof PickaxeItem)) return -1;
        return s.isCorrectToolForDrops(OBSIDIAN) ? 4 : s.isCorrectToolForDrops(DIAMOND_ORE) ? 3 : s.isCorrectToolForDrops(IRON_ORE) ? 2
                : s.isCorrectToolForDrops(STONE) ? 1 : 0;
    }

    static int swordTier(ItemStack s) {
        if (!(s.getItem() instanceof SwordItem sw)) return -1;
        return (int) sw.getDamage();
    }

    private static long now() {
        var mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    static void markFailed(Item i) { FAILED_AT.put(i, now()); }

    private static boolean recentlyFailed(Item i) {
        Long t = FAILED_AT.get(i);
        return t != null && now() - t < 20 * 120;
    }

    /**
     * Un outil va bientot casser et il n'y a pas de rechange : renvoie l'outil a fabriquer, sinon null.
     */
    static Item replacementNeeded() {
        Item p = check(true);
        return p != null ? p : check(false);
    }

    private static Item check(boolean pick) {
        LocalPlayer pl = Minecraft.getInstance().player;
        if (pl == null) return null;
        ItemStack best = ItemStack.EMPTY;
        int bestTier = -1;
        List<ItemStack> items = pl.getInventory().items;
        for (ItemStack s : items) {
            int t = pick ? pickTier(s) : swordTier(s);
            if (t > bestTier || t == bestTier && t >= 0 && usesLeft(s) > usesLeft(best)) { bestTier = t; best = s; }
        }
        if (bestTier < 0 || !almostBroken(best)) return null;
        // une rechange du meme niveau (ou mieux) en bon etat ?
        for (ItemStack s : items) {
            if (s == best) continue;
            int t = pick ? pickTier(s) : swordTier(s);
            if (t >= bestTier && !almostBroken(s)) return null;
        }
        // le meilleur remplacant qu'on peut faire maintenant (sans redescendre de niveau si possible)
        Item[] list = pick ? PICKS : SWORDS;
        Item sameTier = null;
        for (Item cand : list) {
            ItemStack cs = new ItemStack(cand);
            int t = pick ? pickTier(cs) : swordTier(cs);
            if (t < bestTier) break;
            if (recentlyFailed(cand)) continue;
            Planner.Plan plan = new Planner().plan(cand);
            if (!plan.ok()) continue;
            if (plan.cost() < 12) {
                Brain.think("outils", best.getHoverName().getString() + " presque cassee (" + usesLeft(best) + " coups) : je fabrique "
                        + cs.getHoverName().getString() + " pour prendre le relais.");
                return cand;
            }
            if (t == bestTier && plan.cost() < 60) sameTier = cand;
        }
        if (sameTier != null) Brain.think("outils", best.getHoverName().getString() + " presque cassee : j'en refais une du meme niveau.");
        return sameTier;
    }

    // ------------------------------------------------------------------ armure

    private static final Item[] IRON_ARMOR = {Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_HELMET, Items.IRON_BOOTS};

    /** Prochaine piece d'armure en fer a fabriquer (portee ou dans l'inventaire = deja ok), ou null. */
    static Item nextArmorPiece() {
        LocalPlayer pl = Minecraft.getInstance().player;
        if (pl == null) return null;
        for (Item piece : IRON_ARMOR) {
            ArmorItem ai = (ArmorItem) piece;
            EquipmentSlot slot = ai.getEquipmentSlot();
            ItemStack worn = pl.getItemBySlot(slot);
            if (worn.getItem() instanceof ArmorItem w && w.getDefense() >= ai.getDefense()) continue;
            boolean inInv = false;
            for (ItemStack s : pl.getInventory().items)
                if (s.getItem() instanceof ArmorItem a && a.getEquipmentSlot() == slot && a.getDefense() >= ai.getDefense()) { inInv = true; break; }
            if (inInv || recentlyFailed(piece)) continue;
            return piece;
        }
        return null;
    }

    static int ironFor(Item piece) {
        if (piece == Items.IRON_CHESTPLATE) return 8;
        if (piece == Items.IRON_LEGGINGS) return 7;
        if (piece == Items.IRON_HELMET) return 5;
        return 4;
    }

    // ------------------------------------------------------------------ nourriture

    private static final Item[][] COOK = {
            {Items.BEEF, Items.COOKED_BEEF}, {Items.PORKCHOP, Items.COOKED_PORKCHOP}, {Items.MUTTON, Items.COOKED_MUTTON},
            {Items.CHICKEN, Items.COOKED_CHICKEN}, {Items.RABBIT, Items.COOKED_RABBIT}, {Items.COD, Items.COOKED_COD},
            {Items.SALMON, Items.COOKED_SALMON}};

    /** {cru, cuit} a cuire en priorite (3 crus au moins), ou null. */
    static Item[] meatToCook() {
        Item[] best = null;
        int bc = 2;
        for (Item[] c : COOK) {
            int n = Inv.count(c[0]);
            if (n > bc && !recentlyFailed(c[1])) { bc = n; best = c; }
        }
        return best;
    }
}
