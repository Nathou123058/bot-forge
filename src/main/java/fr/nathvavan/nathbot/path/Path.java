package fr.nathvavan.nathbot.path;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Chemin calcule : une suite de mouvements. */
public final class Path {
    public enum Move { TRAVERSE, DIAGONAL, ASCEND, DESCEND, FALL, PILLAR, DIG_DOWN }

    public record Step(BlockPos from, BlockPos to, Move move, boolean placeFloor) {
        /** Blocs a degager pour faire ce mouvement (du haut vers le bas). */
        public List<BlockPos> toBreak() {
            List<BlockPos> l = new ArrayList<>(3);
            switch (move) {
                case TRAVERSE, DIAGONAL -> { l.add(to.above()); l.add(to); }
                case ASCEND -> { l.add(from.above(2)); l.add(to.above()); l.add(to); }
                case DESCEND -> { l.add(to.above(2)); l.add(to.above()); l.add(to); }
                case FALL -> { l.add(new BlockPos(to.getX(), from.getY() + 1, to.getZ())); l.add(new BlockPos(to.getX(), from.getY(), to.getZ())); }
                case PILLAR -> l.add(from.above(2));
                case DIG_DOWN -> l.add(to);
            }
            return l;
        }
    }

    public final List<Step> steps;
    public final boolean partial;
    public final Goal goal;
    public final int expanded;
    public final long millis;

    public Path(List<Step> steps, boolean partial, Goal goal, int expanded, long millis) {
        this.steps = steps;
        this.partial = partial;
        this.goal = goal;
        this.expanded = expanded;
        this.millis = millis;
    }

    public BlockPos end() {
        return steps.isEmpty() ? null : steps.get(steps.size() - 1).to;
    }
}
