package fr.nathvavan.nathbot.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/** Traduction des noms tapes par le joueur ("diamant", "fer", "bois"...) en blocs. */
public final class Targets {
    private Targets() {}

    public record Spec(String label, Set<Block> blocks, Integer bestY) {}

    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        String[][] a = {
                {"diamant", "diamond"}, {"diamants", "diamond"}, {"diamonds", "diamond"},
                {"fer", "iron"}, {"or", "gold"}, {"charbon", "coal"}, {"cuivre", "copper"},
                {"emeraude", "emerald"}, {"lapis", "lapis"}, {"redstone", "redstone"},
                {"quartz", "quartz"}, {"debris", "debris"}, {"netherite", "debris"},
                {"bois", "wood"}, {"buche", "wood"}, {"log", "wood"}, {"logs", "wood"},
                {"pierre", "stone"}, {"roche", "stone"}, {"cobble", "stone"},
                {"terre", "dirt"}, {"sable", "sand"}, {"gravier", "gravel"}, {"argile", "clay"},
                {"obsidienne", "obsidian"}, {"glowstone", "glowstone"}, {"feuilles", "leaves"},
                {"laine", "wool"}, {"ancient_debris", "debris"},
        };
        for (String[] x : a) ALIASES.put(x[0], x[1]);
    }

    public static Spec parse(String raw) {
        String n = raw.toLowerCase(Locale.ROOT).trim().replace("minecraft:", "");
        n = ALIASES.getOrDefault(n, n);
        Set<Block> s = new HashSet<>();
        Integer y = null;
        switch (n) {
            case "diamond" -> { s.add(Blocks.DIAMOND_ORE); s.add(Blocks.DEEPSLATE_DIAMOND_ORE); y = -58; }
            case "iron" -> { s.add(Blocks.IRON_ORE); s.add(Blocks.DEEPSLATE_IRON_ORE); y = 16; }
            case "gold" -> { s.add(Blocks.GOLD_ORE); s.add(Blocks.DEEPSLATE_GOLD_ORE); s.add(Blocks.NETHER_GOLD_ORE); y = -16; }
            case "coal" -> { s.add(Blocks.COAL_ORE); s.add(Blocks.DEEPSLATE_COAL_ORE); y = 45; }
            case "copper" -> { s.add(Blocks.COPPER_ORE); s.add(Blocks.DEEPSLATE_COPPER_ORE); y = 48; }
            case "emerald" -> { s.add(Blocks.EMERALD_ORE); s.add(Blocks.DEEPSLATE_EMERALD_ORE); y = 100; }
            case "lapis" -> { s.add(Blocks.LAPIS_ORE); s.add(Blocks.DEEPSLATE_LAPIS_ORE); y = 0; }
            case "redstone" -> { s.add(Blocks.REDSTONE_ORE); s.add(Blocks.DEEPSLATE_REDSTONE_ORE); y = -58; }
            case "quartz" -> { s.add(Blocks.NETHER_QUARTZ_ORE); y = 15; }
            case "debris" -> { s.add(Blocks.ANCIENT_DEBRIS); y = 15; }
            case "wood" -> BuiltInRegistries.BLOCK.forEach(b -> { if (b.defaultBlockState().is(BlockTags.LOGS)) s.add(b); });
            case "leaves" -> BuiltInRegistries.BLOCK.forEach(b -> { if (b.defaultBlockState().is(BlockTags.LEAVES)) s.add(b); });
            case "wool" -> BuiltInRegistries.BLOCK.forEach(b -> { if (b.defaultBlockState().is(BlockTags.WOOL)) s.add(b); });
            case "stone" -> { s.add(Blocks.STONE); s.add(Blocks.COBBLESTONE); s.add(Blocks.DEEPSLATE); s.add(Blocks.COBBLED_DEEPSLATE); }
            case "dirt" -> { s.add(Blocks.DIRT); s.add(Blocks.GRASS_BLOCK); s.add(Blocks.COARSE_DIRT); }
            case "sand" -> { s.add(Blocks.SAND); s.add(Blocks.RED_SAND); }
            default -> {
                ResourceLocation rl = ResourceLocation.tryParse(n.contains(":") ? n : "minecraft:" + n);
                if (rl != null && BuiltInRegistries.BLOCK.containsKey(rl)) s.add(BuiltInRegistries.BLOCK.get(rl));
                ResourceLocation ore = ResourceLocation.tryParse("minecraft:" + n + "_ore");
                if (ore != null && BuiltInRegistries.BLOCK.containsKey(ore)) s.add(BuiltInRegistries.BLOCK.get(ore));
                ResourceLocation ds = ResourceLocation.tryParse("minecraft:deepslate_" + n + "_ore");
                if (ds != null && BuiltInRegistries.BLOCK.containsKey(ds)) s.add(BuiltInRegistries.BLOCK.get(ds));
            }
        }
        s.remove(Blocks.AIR);
        return new Spec(raw, s, y);
    }
}
