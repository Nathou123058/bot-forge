package fr.nathvavan.nathbot.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Set;

/**
 * "World View" : toutes les questions que le bot pose au monde
 * (peut-on marcher ici ? tenir debout ? casser ? combien de temps ?).
 * Lecture seule, utilisable depuis le thread de calcul de chemin.
 */
public final class WV {
    private WV() {}

    public static final double INF = 1_000_000;

    /** Blocs proteges que le bot ne casse jamais pour passer. */
    public static volatile Set<Long> protectedPositions = Set.of();

    public static Level level() {
        return Minecraft.getInstance().level;
    }

    public static boolean loaded(BlockPos p) {
        Level l = level();
        if (l == null) return false;
        if (p.getY() < l.getMinBuildHeight() || p.getY() >= l.getMaxBuildHeight()) return false;
        try {
            return l.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static BlockState st(BlockPos p) {
        Level l = level();
        if (l == null) return Blocks.BEDROCK.defaultBlockState();
        try {
            return l.getBlockState(p);
        } catch (Throwable t) {
            return Blocks.BEDROCK.defaultBlockState();
        }
    }

    public static boolean isLava(BlockPos p) {
        return st(p).getFluidState().is(FluidTags.LAVA);
    }

    public static boolean isWater(BlockPos p) {
        return st(p).getFluidState().is(FluidTags.WATER);
    }

    public static boolean isAir(BlockPos p) {
        return st(p).isAir();
    }

    private static VoxelShape shape(BlockState s, BlockPos p) {
        try {
            return s.getCollisionShape(level(), p);
        } catch (Throwable t) {
            return net.minecraft.world.phys.shapes.Shapes.block();
        }
    }

    /** Blocs dangereux a traverser. */
    public static boolean dangerousToEnter(BlockState s) {
        Block b = s.getBlock();
        return b instanceof BaseFireBlock || b == Blocks.COBWEB || b == Blocks.SWEET_BERRY_BUSH || b == Blocks.WITHER_ROSE
                || b == Blocks.POWDER_SNOW || b == Blocks.NETHER_PORTAL || b == Blocks.END_PORTAL || b == Blocks.END_GATEWAY
                || b == Blocks.CACTUS || b == Blocks.MAGMA_BLOCK || b instanceof CampfireBlock || s.getFluidState().is(FluidTags.LAVA);
    }

    /** Le joueur peut-il occuper cette case (pieds ou tete) sans casser ? */
    public static boolean passable(BlockPos p) {
        BlockState s = st(p);
        if (s.isAir()) return true;
        if (dangerousToEnter(s)) return false;
        return shape(s, p).isEmpty();
    }

    /** Variante : on autorise explicitement le portail (pour entrer dedans). */
    public static boolean passableOrPortal(BlockPos p) {
        BlockState s = st(p);
        if (s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.END_PORTAL)) return true;
        return passable(p);
    }

    /** Peut-on se tenir debout sur ce bloc ? */
    public static boolean standable(BlockPos p) {
        BlockState s = st(p);
        if (s.isAir()) return false;
        Block b = s.getBlock();
        if (b == Blocks.MAGMA_BLOCK || b == Blocks.CACTUS || b instanceof CampfireBlock || b == Blocks.POINTED_DRIPSTONE
                || b == Blocks.SWEET_BERRY_BUSH || b == Blocks.POWDER_SNOW) return false;
        if (s.getFluidState().is(FluidTags.LAVA)) return false;
        if (b instanceof FenceBlock || b instanceof WallBlock || b instanceof FenceGateBlock) return false;
        VoxelShape sh = shape(s, p);
        if (sh.isEmpty()) return false;
        double top = sh.max(Direction.Axis.Y);
        return top >= 0.45 && top <= 1.0;
    }

    /** On peut poser un bloc a cet endroit. */
    public static boolean replaceable(BlockPos p) {
        BlockState s = st(p);
        if (s.isAir()) return true;
        if (s.getFluidState().is(FluidTags.LAVA)) return false;
        try {
            return s.canBeReplaced();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Bloc solide contre lequel on peut poser. */
    public static boolean solidSupport(BlockPos p) {
        BlockState s = st(p);
        if (s.isAir() || !s.getFluidState().isEmpty() && s.canBeReplaced()) return false;
        if (s.canBeReplaced()) return false;
        return !interactive(s);
    }

    /** Blocs qui ouvrent une interface quand on clique droit (on evite de poser contre). */
    public static boolean interactive(BlockState s) {
        Block b = s.getBlock();
        return b instanceof BaseEntityBlock || b instanceof CraftingTableBlock || b instanceof DoorBlock || b instanceof TrapDoorBlock
                || b instanceof FenceGateBlock || b instanceof ButtonBlock || b instanceof LeverBlock || b instanceof BedBlock
                || b instanceof AnvilBlock || b instanceof NoteBlock || b instanceof RepeaterBlock || b instanceof ComparatorBlock
                || b instanceof LoomBlock || b instanceof StonecutterBlock || b instanceof GrindstoneBlock || b instanceof CartographyTableBlock
                || b instanceof EnchantmentTableBlock || b instanceof RespawnAnchorBlock || b instanceof CakeBlock;
    }

    /** Blocs que le bot ne casse jamais tout seul (coffres, lits, etc.). */
    public static boolean protectedBlock(BlockPos p, BlockState s) {
        if (protectedPositions.contains(p.asLong())) return true;
        Block b = s.getBlock();
        if (b == Blocks.BEDROCK || b == Blocks.END_PORTAL_FRAME || b == Blocks.BARRIER || b == Blocks.END_PORTAL
                || b == Blocks.NETHER_PORTAL || b == Blocks.COMMAND_BLOCK || b == Blocks.REINFORCED_DEEPSLATE) return true;
        if (b instanceof SpawnerBlock) return false;
        return b instanceof BaseEntityBlock || b instanceof BedBlock || b instanceof CraftingTableBlock || b instanceof DoorBlock;
    }

    public static boolean fallingBlock(BlockState s) {
        return s.getBlock() instanceof FallingBlock;
    }

    /** Nombre de ticks pour casser le bloc avec le meilleur outil disponible. */
    public static double breakTicks(BlockPos p, BlockState s, List<ItemStack> tools) {
        float hardness;
        try {
            hardness = s.getDestroySpeed(level(), p);
        } catch (Throwable t) {
            return INF;
        }
        if (hardness < 0) return INF;
        if (hardness == 0) return 1;
        float best = 1f;
        boolean harvest = !s.requiresCorrectToolForDrops();
        boolean bestHarvest = harvest;
        for (ItemStack st : tools) {
            if (st.isEmpty()) continue;
            float sp = st.getDestroySpeed(s);
            if (sp > 1f) {
                int eff = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, st);
                if (eff > 0) sp += eff * eff + 1;
            }
            boolean h = harvest || st.isCorrectToolForDrops(s);
            if ((h && !bestHarvest) || (h == bestHarvest && sp > best)) {
                best = sp;
                bestHarvest = h;
            }
        }
        double perTick = best / hardness / (bestHarvest ? 30.0 : 100.0);
        if (perTick >= 1) return 1;
        return Math.ceil(1.0 / perTick);
    }

    /** Cout pour degager un bloc (0 si deja libre, INF si interdit). */
    public static double breakCost(BlockPos p, boolean allowBreak, List<ItemStack> tools) {
        BlockState s = st(p);
        if (s.isAir()) return 0;
        if (dangerousToEnter(s)) return INF;
        if (shape(s, p).isEmpty()) return 0;
        if (!allowBreak) return INF;
        if (protectedBlock(p, s)) return INF;
        // Jamais creuser a cote de la lave (sinon elle coule sur nous)
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            BlockPos n = p.relative(d);
            if (isLava(n)) return INF;
        }
        double t = breakTicks(p, s, tools);
        if (t >= 600) return INF;
        double c = t + 2;
        BlockState above = st(p.above());
        if (fallingBlock(above)) c += 15;
        if (!above.getFluidState().isEmpty()) c += 30;
        return c;
    }

    public static boolean isLeaves(BlockState s) {
        return s.is(BlockTags.LEAVES);
    }

    public static FluidState fluid(BlockPos p) {
        return st(p).getFluidState();
    }
}
