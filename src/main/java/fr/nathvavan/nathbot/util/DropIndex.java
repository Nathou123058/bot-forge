package fr.nathvavan.nathbot.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Quel bloc faut-il casser pour obtenir cet objet ?" pour TOUS les blocs (vanilla + mods).
 *
 * En solo / LAN heberge, on lit les vraies tables de butin du jeu (pierre -> pierre taillee,
 * bambou -> bambou, minerai de fer -> fer brut...). Le calcul se fait sur le fil du serveur,
 * par petits paquets pendant son temps libre, pour ne jamais faire ramer le jeu.
 * Sur un serveur distant (tables de butin invisibles), on suppose qu'un bloc se donne lui-meme
 * sauf exceptions connues. Le bot apprend aussi de ses erreurs (bloc casse mais rien recu).
 */
public final class DropIndex {
    private DropIndex() {}

    private static final int BATCH = 48;
    private static final Map<Item, Set<Block>> DROPS = new ConcurrentHashMap<>();
    private static final Set<Long> FAILED = ConcurrentHashMap.newKeySet();
    private static volatile Object builtFor;
    private static volatile boolean ready = false;
    private static volatile int progress = 0, totalBlocks = 0;

    /** Appele a chaque tick client : lance l'indexation quand un monde est charge. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (builtFor != null) {
                builtFor = null;
                ready = false;
                DROPS.clear();
                FAILED.clear();
            }
            return;
        }
        Object key = mc.getSingleplayerServer() != null ? mc.getSingleplayerServer() : mc.getConnection();
        if (key == builtFor) return;
        builtFor = key;
        ready = false;
        DROPS.clear();
        IntegratedServer srv = mc.getSingleplayerServer();
        if (srv == null) {
            ready = true; // serveur distant : mode "devine"
            return;
        }
        List<Block> blocks = new ArrayList<>();
        BuiltInRegistries.BLOCK.forEach(blocks::add);
        totalBlocks = blocks.size();
        progress = 0;
        schedule(srv, key, blocks, 0);
    }

    private static void schedule(IntegratedServer srv, Object key, List<Block> blocks, int from) {
        srv.execute(() -> {
            if (builtFor != key) return;
            ServerLevel sl = srv.overworld();
            ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
            int end = Math.min(from + BATCH, blocks.size());
            for (int i = from; i < end; i++) {
                Block b = blocks.get(i);
                try {
                    if (b.getLootTable() == BuiltInLootTables.EMPTY) continue;
                    BlockState st = b.defaultBlockState();
                    for (int roll = 0; roll < 3; roll++) {
                        LootParams.Builder lb = new LootParams.Builder(sl)
                                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                                .withParameter(LootContextParams.TOOL, tool);
                        for (ItemStack d : st.getDrops(lb)) {
                            if (!d.isEmpty()) DROPS.computeIfAbsent(d.getItem(), k -> ConcurrentHashMap.newKeySet()).add(b);
                        }
                    }
                } catch (Throwable ignored) {
                    // certains blocs de mods ont besoin d'infos qu'on n'a pas : tant pis
                }
            }
            progress = end;
            if (end < blocks.size()) schedule(srv, key, blocks, end);
            else {
                ready = true;
                BotLog.quiet("Index des blocs : " + DROPS.size() + " objets obtenables en cassant des blocs.");
            }
        });
    }

    private static long k(Block b, Item i) {
        return ((long) BuiltInRegistries.BLOCK.getId(b) << 32) | (BuiltInRegistries.ITEM.getId(i) & 0xffffffffL);
    }

    /** Le bot a casse ce bloc sans recevoir l'objet : on ne le reessaiera plus. */
    public static void markFailed(Block b, Item i) { FAILED.add(k(b, i)); }

    public static boolean ready() { return ready; }

    public static String progressText() {
        return ready ? "pret" : progress + "/" + totalBlocks;
    }

    /** Blocs qui donnent cet objet quand on les casse. */
    public static Set<Block> blocksFor(Item item) {
        Set<Block> out = new HashSet<>();
        boolean exact = ready && Minecraft.getInstance().getSingleplayerServer() != null;
        Set<Block> s = DROPS.get(item);
        if (s != null) out.addAll(s);
        if (!exact) {
            Block self = Block.byItem(item);
            if (self != Blocks.AIR && selfDrops(self.defaultBlockState())) out.add(self);
        }
        out.removeIf(b -> b == Blocks.AIR || FAILED.contains(k(b, item)) || isUnwanted(b));
        return out;
    }

    /** Blocs a ne jamais casser pour recolter (dangereux ou idiots). */
    private static boolean isUnwanted(Block b) {
        BlockState st = b.defaultBlockState();
        return b == Blocks.BEDROCK || b == Blocks.SPAWNER || b == Blocks.END_PORTAL_FRAME || b == Blocks.BUDDING_AMETHYST
                || b == Blocks.REINFORCED_DEEPSLATE || b.defaultDestroyTime() < 0 || b == Blocks.INFESTED_STONE
                || st.is(BlockTags.BEDS) || st.is(BlockTags.SHULKER_BOXES) || b == Blocks.CHEST || b == Blocks.BARREL;
    }

    /** Devine si un bloc se donne lui-meme (serveur distant). */
    private static boolean selfDrops(BlockState st) {
        Block b = st.getBlock();
        if (st.is(Tags.Blocks.ORES) || st.is(Tags.Blocks.GLASS) || st.is(Tags.Blocks.GLASS_PANES) || st.is(BlockTags.LEAVES)
                || st.is(BlockTags.ICE)) return false;
        return b != Blocks.STONE && b != Blocks.DEEPSLATE && b != Blocks.GRASS_BLOCK && b != Blocks.MYCELIUM && b != Blocks.PODZOL
                && b != Blocks.BOOKSHELF && b != Blocks.CLAY && b != Blocks.GLOWSTONE && b != Blocks.MELON && b != Blocks.SNOW_BLOCK
                && b != Blocks.SEA_LANTERN && b != Blocks.DIRT_PATH && b != Blocks.FARMLAND && b != Blocks.TALL_GRASS && b != Blocks.GRASS
                && b != Blocks.GILDED_BLACKSTONE && b != Blocks.TURTLE_EGG && b != Blocks.CAKE;
    }
}
