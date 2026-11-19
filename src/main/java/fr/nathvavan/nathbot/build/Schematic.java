package fr.nathvavan.nathbot.build;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.*;

/** Un plan de construction : des blocs a des positions relatives. */
public final class Schematic {
    public final String id;
    public final String name;
    public final String description;
    public final String category;
    public final LinkedHashMap<BlockPos, BlockState> blocks;

    public Schematic(String id, String name, String description, String category, Map<BlockPos, BlockState> blocks) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.blocks = new LinkedHashMap<>(blocks);
    }

    public int[] size() {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : blocks.keySet()) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        if (blocks.isEmpty()) return new int[]{0, 0, 0};
        return new int[]{maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1};
    }

    /** Rotation de 0, 90, 180 ou 270 degres autour de l'origine. */
    public Schematic rotated(int quarter) {
        int q = ((quarter % 4) + 4) % 4;
        if (q == 0) return this;
        Rotation rot = switch (q) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            default -> Rotation.COUNTERCLOCKWISE_90;
        };
        LinkedHashMap<BlockPos, BlockState> m = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            m.put(e.getKey().rotate(rot), e.getValue().rotate(rot));
        }
        return new Schematic(id, name, description, category, m);
    }

    /** Partie d'un bloc double qu'on ne pose pas (haut de porte, tete de lit...). */
    public static boolean isSecondaryPart(BlockState s) {
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return true;
        return s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
    }

    /** Objet necessaire pour poser ce bloc en survie. */
    public static Item itemFor(BlockState s) {
        Block b = s.getBlock();
        if (b == Blocks.WATER) return Items.WATER_BUCKET;
        if (b == Blocks.LAVA) return Items.LAVA_BUCKET;
        if (b == Blocks.FARMLAND || b == Blocks.DIRT_PATH) return Items.DIRT;
        if (b == Blocks.FIRE) return Items.FLINT_AND_STEEL;
        return b.asItem();
    }

    public Map<Item, Integer> materials() {
        Map<Item, Integer> m = new TreeMap<>(Comparator.comparing(i -> i.getDescription().getString()));
        for (BlockState s : blocks.values()) {
            if (s.isAir() || isSecondaryPart(s)) continue;
            Item i = itemFor(s);
            if (i == Items.AIR) continue;
            int n = 1;
            if (s.hasProperty(BlockStateProperties.SLAB_TYPE) && s.getValue(BlockStateProperties.SLAB_TYPE) == net.minecraft.world.level.block.state.properties.SlabType.DOUBLE) n = 2;
            m.merge(i, n, Integer::sum);
        }
        return m;
    }

    public int solidCount() {
        int n = 0;
        for (BlockState s : blocks.values()) if (!s.isAir()) n++;
        return n;
    }
}
