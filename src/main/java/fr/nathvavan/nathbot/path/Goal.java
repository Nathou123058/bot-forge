package fr.nathvavan.nathbot.path;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Objectif de deplacement (position des pieds du joueur). */
public interface Goal {
    double COST_WALK = 4.633;
    double COST_UP = 6.0;
    double COST_DOWN = 2.0;

    boolean isAt(int x, int y, int z);

    double heuristic(int x, int y, int z);

    String describe();

    default boolean isAt(BlockPos p) { return isAt(p.getX(), p.getY(), p.getZ()); }

    static double h(double dx, double dy, double dz) {
        double hor = Math.sqrt(dx * dx + dz * dz) * COST_WALK;
        double ver = dy > 0 ? dy * COST_UP : -dy * COST_DOWN;
        return hor + ver;
    }

    // ------------------------------------------------------------------ implementations

    record Block(int x, int y, int z) implements Goal {
        public Block(BlockPos p) { this(p.getX(), p.getY(), p.getZ()); }
        public boolean isAt(int a, int b, int c) { return a == x && b == y && c == z; }
        public double heuristic(int a, int b, int c) { return h(x - a, y - b, z - c); }
        public String describe() { return "aller en " + x + " " + y + " " + z; }
    }

    record XZ(int x, int z) implements Goal {
        public boolean isAt(int a, int b, int c) { return a == x && c == z; }
        public double heuristic(int a, int b, int c) { return h(x - a, 0, z - c); }
        public String describe() { return "aller en X=" + x + " Z=" + z; }
    }

    record Y(int y) implements Goal {
        public boolean isAt(int a, int b, int c) { return b == y; }
        public double heuristic(int a, int b, int c) { return h(0, y - b, 0); }
        public String describe() { return "aller a la couche Y=" + y; }
    }

    /** Etre a moins de r blocs d'une position. */
    record Near(int x, int y, int z, int r) implements Goal {
        public Near(BlockPos p, int r) { this(p.getX(), p.getY(), p.getZ(), r); }
        public boolean isAt(int a, int b, int c) {
            int dx = a - x, dy = b - y, dz = c - z;
            return dx * dx + dy * dy + dz * dz <= r * r;
        }
        public double heuristic(int a, int b, int c) { return Math.max(0, h(x - a, y - b, z - c) - r * COST_WALK); }
        public String describe() { return "s'approcher de " + x + " " + y + " " + z; }
    }

    /** Les pieds OU la tete dans ce bloc : sert a miner un bloc en "marchant dedans". */
    record TwoBlocks(int x, int y, int z) implements Goal {
        public TwoBlocks(BlockPos p) { this(p.getX(), p.getY(), p.getZ()); }
        public boolean isAt(int a, int b, int c) { return a == x && c == z && (b == y || b == y - 1); }
        public double heuristic(int a, int b, int c) { return h(x - a, Math.min(y - b, y - 1 - b), z - c); }
        public String describe() { return "atteindre le bloc " + x + " " + y + " " + z; }
    }

    /**
     * Etre assez pres pour interagir avec un bloc (a portee de main) sans etre dedans.
     */
    record Reach(int x, int y, int z, double dist) implements Goal {
        public Reach(BlockPos p, double d) { this(p.getX(), p.getY(), p.getZ(), d); }
        public boolean isAt(int a, int b, int c) {
            if (a == x && c == z && (b == y || b + 1 == y)) return false;
            double dx = a + 0.5 - (x + 0.5), dy = b + 1.6 - (y + 0.5), dz = c + 0.5 - (z + 0.5);
            return dx * dx + dy * dy + dz * dz <= dist * dist;
        }
        public double heuristic(int a, int b, int c) { return Math.max(0, h(x - a, y - b, z - c) - (dist - 1) * COST_WALK); }
        public String describe() { return "se placer a portee de " + x + " " + y + " " + z; }
    }

    /** Fuir une position : etre a plus de r blocs. */
    record Away(int x, int z, int r) implements Goal {
        public boolean isAt(int a, int b, int c) { return (a - x) * (a - x) + (c - z) * (c - z) >= r * r; }
        public double heuristic(int a, int b, int c) {
            double d = Math.sqrt((a - x) * (a - x) + (c - z) * (c - z));
            return Math.max(0, r - d) * COST_WALK;
        }
        public String describe() { return "s'eloigner de " + x + " " + z; }
    }

    /** Plusieurs objectifs : le plus proche gagne. */
    final class Any implements Goal {
        public final List<Goal> goals;
        public Any(List<? extends Goal> g) { goals = new ArrayList<>(g); }
        public boolean isAt(int a, int b, int c) {
            for (Goal g : goals) if (g.isAt(a, b, c)) return true;
            return false;
        }
        public double heuristic(int a, int b, int c) {
            double m = Double.MAX_VALUE;
            for (Goal g : goals) m = Math.min(m, g.heuristic(a, b, c));
            return m;
        }
        public String describe() { return goals.size() + " objectifs"; }
    }
}
