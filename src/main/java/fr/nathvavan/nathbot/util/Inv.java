package fr.nathvavan.nathbot.util;

import fr.nathvavan.nathbot.BotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Outils d'inventaire : compter, selectionner, deplacer des objets. */
public final class Inv {
    private Inv() {}

    private static Minecraft mc() { return Minecraft.getInstance(); }

    public static LocalPlayer player() { return mc().player; }

    /** Index 0..35 de l'inventaire principal (0..8 = barre rapide). */
    public static ItemStack get(int i) {
        return player().getInventory().items.get(i);
    }

    public static int count(Predicate<ItemStack> pred) {
        LocalPlayer p = player();
        if (p == null) return 0;
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (!s.isEmpty() && pred.test(s)) n += s.getCount();
        for (ItemStack s : p.getInventory().offhand) if (!s.isEmpty() && pred.test(s)) n += s.getCount();
        return n;
    }

    public static int count(Item item) {
        return count(s -> s.is(item));
    }

    public static boolean has(Item item) { return count(item) > 0; }

    public static int freeSlots() {
        int n = 0;
        for (ItemStack s : player().getInventory().items) if (s.isEmpty()) n++;
        return n;
    }

    public static int find(Predicate<ItemStack> pred) {
        LocalPlayer p = player();
        if (p == null) return -1;
        List<ItemStack> items = p.getInventory().items;
        for (int i = 0; i < 9; i++) if (!items.get(i).isEmpty() && pred.test(items.get(i))) return i;
        for (int i = 9; i < items.size(); i++) if (!items.get(i).isEmpty() && pred.test(items.get(i))) return i;
        return -1;
    }

    /** Slot du menu courant correspondant a l'index d'inventaire du joueur. */
    public static int menuSlotFor(int invIndex) {
        LocalPlayer p = player();
        AbstractContainerMenu menu = p.containerMenu;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.container == p.getInventory() && s.getContainerSlot() == invIndex) return i;
        }
        return -1;
    }

    public static void click(int menuSlot, int button, ClickType type) {
        LocalPlayer p = player();
        mc().gameMode.handleInventoryMouseClick(p.containerMenu.containerId, menuSlot, button, type, p);
    }

    /** Met en main un objet qui correspond. Renvoie false si absent. */
    public static boolean select(Predicate<ItemStack> pred, int preferredHotbar) {
        LocalPlayer p = player();
        if (p == null) return false;
        ItemStack cur = p.getMainHandItem();
        if (!cur.isEmpty() && pred.test(cur)) return true;
        int i = find(pred);
        if (i < 0) return false;
        if (i < 9) {
            p.getInventory().selected = i;
            return true;
        }
        int ms = menuSlotFor(i);
        if (ms < 0) return false;
        int hb = preferredHotbar;
        // si un slot de la barre est vide, on le prend
        for (int h = 0; h < 9; h++) if (p.getInventory().items.get(h).isEmpty()) { hb = h; break; }
        click(ms, hb, ClickType.SWAP);
        p.getInventory().selected = hb;
        return true;
    }

    public static boolean select(Item item) {
        return select(s -> s.is(item), 3);
    }

    public static List<ItemStack> toolSnapshot() {
        List<ItemStack> l = new ArrayList<>();
        LocalPlayer p = player();
        if (p == null) return l;
        for (ItemStack s : p.getInventory().items) if (!s.isEmpty()) l.add(s.copy());
        return l;
    }

    /** Selectionne le meilleur outil pour casser ce bloc. */
    public static void selectBestTool(BlockState state) {
        LocalPlayer p = player();
        if (p == null) return;
        List<ItemStack> items = p.getInventory().items;
        int best = -1;
        float bestSpeed = 1f;
        boolean bestHarvest = !state.requiresCorrectToolForDrops();
        boolean durabilityGuard = fr.nathvavan.nathbot.feature.Features.isOn("durability_guard");
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) continue;
            if (durabilityGuard && s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() <= 5) continue;
            float sp = s.getDestroySpeed(state);
            boolean h = !state.requiresCorrectToolForDrops() || s.isCorrectToolForDrops(state);
            if ((h && !bestHarvest) || (h == bestHarvest && sp > bestSpeed)) {
                best = i;
                bestSpeed = sp;
                bestHarvest = h;
            }
        }
        if (best < 0) {
            // pas d'outil utile : eviter de taper avec un outil precieux
            ItemStack cur = p.getMainHandItem();
            if (cur.isDamageableItem()) {
                int empty = find(ItemStack::isEmpty);
                for (int h = 0; h < 9; h++) if (items.get(h).isEmpty() || !items.get(h).isDamageableItem()) { p.getInventory().selected = h; break; }
            }
            return;
        }
        final int target = best;
        if (target < 9) p.getInventory().selected = target;
        else select(st -> st == items.get(target), 0);
    }

    // ------------------------------------------------------------------ categories

    private static Set<Item> throwawayCache;
    private static List<? extends String> throwawaySrc;

    public static Set<Item> throwawayItems() {
        List<? extends String> src = BotConfig.get(BotConfig.THROWAWAY, List.of("minecraft:cobblestone", "minecraft:dirt"));
        if (throwawayCache == null || src != throwawaySrc) {
            Set<Item> s = new HashSet<>();
            for (String id : src) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) s.add(BuiltInRegistries.ITEM.get(rl));
            }
            throwawayCache = s;
            throwawaySrc = src;
        }
        return throwawayCache;
    }

    public static boolean isThrowaway(ItemStack s) {
        return throwawayItems().contains(s.getItem());
    }

    public static int throwawayCount() {
        return count(Inv::isThrowaway);
    }

    public static boolean isGoodFood(ItemStack s) {
        if (!s.isEdible()) return false;
        Item i = s.getItem();
        return i != Items.ROTTEN_FLESH && i != Items.SPIDER_EYE && i != Items.POISONOUS_POTATO && i != Items.PUFFERFISH
                && i != Items.SUSPICIOUS_STEW && i != Items.CHORUS_FRUIT;
    }

    public static int foodValue(ItemStack s) {
        FoodProperties fp = s.getItem().getFoodProperties();
        return fp == null ? 0 : fp.getNutrition();
    }

    public static boolean isWeapon(ItemStack s) {
        return s.getItem() instanceof SwordItem || s.getItem() instanceof AxeItem;
    }

    public static float weaponScore(ItemStack s) {
        if (s.getItem() instanceof SwordItem sw) return sw.getDamage() + 2;
        if (s.getItem() instanceof AxeItem ax) return ax.getAttackDamage();
        return 0;
    }

    public static void selectBestWeapon() {
        LocalPlayer p = player();
        List<ItemStack> items = p.getInventory().items;
        int best = -1;
        float score = 0;
        for (int i = 0; i < items.size(); i++) {
            float sc = weaponScore(items.get(i));
            if (sc > score) { score = sc; best = i; }
        }
        if (best < 0) return;
        final ItemStack target = items.get(best);
        select(st -> st == target, 0);
    }

    public static String id(Item i) {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(i);
        return rl.getPath();
    }
}
