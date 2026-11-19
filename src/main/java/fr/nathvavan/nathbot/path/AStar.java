package fr.nathvavan.nathbot.path;

import fr.nathvavan.nathbot.util.WV;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Pathfinding A* maison sur la grille de blocs.
 * Mouvements : marcher, diagonale, monter (saut), descendre / tomber, pilier (poser sous soi),
 * creuser vers le bas, casser les blocs genants, poser un pont.
 */
public final class AStar {
    public static final class Ctx {
        public boolean allowBreak = true;
        public boolean allowPlace = true;
        public boolean allowDigDown = true;
        public int maxFall = 3;
        public int throwaway = 0;
        public List<ItemStack> tools = List.of();
        public long timeoutMs = 2500;
        public int maxNodes = 400_000;
        public boolean allowPortal = false;
    }

    static final double PLACE_COST = 20;
    static final double JUMP_COST = 2.5;
    static final double PILLAR_COST = 22;

    private static final class Node {
        final int x, y, z;
        double g = Double.MAX_VALUE, h, f;
        Node parent;
        Path.Move move;
        boolean place;
        boolean closed;

        Node(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }

    private final Ctx ctx;
    private final Goal goal;
    private final BlockPos start;
    private final HashMap<Long, Node> nodes = new HashMap<>();
    private final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
    private volatile boolean cancelled;

    public AStar(BlockPos start, Goal goal, Ctx ctx) {
        this.start = start;
        this.goal = goal;
        this.ctx = ctx;
    }

    public void cancel() { cancelled = true; }

    private Node node(int x, int y, int z) {
        long k = BlockPos.asLong(x, y, z);
        Node n = nodes.get(k);
        if (n == null) {
            n = new Node(x, y, z);
            n.h = goal.heuristic(x, y, z);
            nodes.put(k, n);
        }
        return n;
    }

    public Path compute() {
        long t0 = System.currentTimeMillis();
        Node s = node(start.getX(), start.getY(), start.getZ());
        s.g = 0;
        s.f = s.h;
        open.add(s);
        Node best = s;
        double bestScore = s.h;
        int expanded = 0;
        while (!open.isEmpty() && !cancelled) {
            Node n = open.poll();
            if (n.closed) continue;
            n.closed = true;
            expanded++;
            if (goal.isAt(n.x, n.y, n.z)) return build(n, false, expanded, t0);
            double score = n.h + n.g * 0.05;
            if (score < bestScore) { bestScore = score; best = n; }
            if (expanded > ctx.maxNodes || (expanded & 255) == 0 && System.currentTimeMillis() - t0 > ctx.timeoutMs) break;
            expand(n);
        }
        if (best == s || cancelled) return null;
        return build(best, true, expanded, t0);
    }

    private Path build(Node end, boolean partial, int expanded, long t0) {
        LinkedList<Path.Step> steps = new LinkedList<>();
        Node n = end;
        while (n.parent != null) {
            steps.addFirst(new Path.Step(new BlockPos(n.parent.x, n.parent.y, n.parent.z), new BlockPos(n.x, n.y, n.z), n.move, n.place));
            n = n.parent;
        }
        return new Path(new ArrayList<>(steps), partial, goal, expanded, System.currentTimeMillis() - t0);
    }

    private void add(Node from, int x, int y, int z, double cost, Path.Move move, boolean place) {
        if (cost >= WV.INF) return;
        Node n = node(x, y, z);
        if (n.closed) return;
        double g = from.g + cost;
        if (g < n.g) {
            n.g = g;
            n.f = g + n.h;
            n.parent = from;
            n.move = move;
            n.place = place;
            open.add(n);
        }
    }

    private static BlockPos p(int x, int y, int z) { return new BlockPos(x, y, z); }

    private double brk(int x, int y, int z) {
        return WV.breakCost(p(x, y, z), ctx.allowBreak, ctx.tools);
    }

    private void expand(Node n) {
        int x = n.x, y = n.y, z = n.z;
        boolean inWater = WV.isWater(p(x, y, z));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int nx = x + d[0], nz = z + d[1];
            if (!WV.loaded(p(nx, y, nz))) continue;
            traverse(n, nx, y, nz);
            ascend(n, nx, y, nz);
            descend(n, nx, y, nz);
        }
        if (!inWater) {
            int[][] diag = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
            for (int[] d : diag) diagonal(n, x + d[0], y, z + d[1], d[0], d[1]);
        }
        pillar(n, x, y, z, inWater);
        digDown(n, x, y, z);
        if (inWater) swimUp(n, x, y, z);
    }

    private void traverse(Node n, int nx, int y, int nz) {
        double b1 = brk(nx, y + 1, nz);
        if (b1 >= WV.INF) return;
        double b2 = brk(nx, y, nz);
        if (b2 >= WV.INF) return;
        double c = Goal.COST_WALK + b1 + b2;
        BlockPos floor = p(nx, y - 1, nz);
        boolean place = false;
        if (!WV.standable(floor)) {
            if (WV.isWater(p(nx, y, nz)) || WV.isWater(floor)) {
                c += 4; // nage
                if (!WV.isWater(floor) && !WV.passable(floor)) return;
                if (WV.isWater(floor) && ctx.allowPlace && ctx.throwaway > 0) { c += PLACE_COST - 4; place = true; }
            } else {
                if (!ctx.allowPlace || ctx.throwaway <= 0) return;
                if (!WV.replaceable(floor) || WV.isLava(floor)) return;
                c += PLACE_COST;
                place = true;
            }
        }
        if (WV.isWater(p(nx, y, nz))) c *= 1.6;
        add(n, nx, y, nz, c, Path.Move.TRAVERSE, place);
    }

    private void ascend(Node n, int nx, int y, int nz) {
        if (!WV.standable(p(nx, y, nz)) && !WV.isWater(p(n.x, n.y, n.z))) {
            return;
        }
        if (!WV.standable(p(nx, y, nz))) return;
        double b0 = brk(n.x, y + 2, n.z);
        if (b0 >= WV.INF) return;
        double b1 = brk(nx, y + 2, nz);
        if (b1 >= WV.INF) return;
        double b2 = brk(nx, y + 1, nz);
        if (b2 >= WV.INF) return;
        add(n, nx, y + 1, nz, Goal.COST_WALK + JUMP_COST + b0 + b1 + b2, Path.Move.ASCEND, false);
    }

    private void descend(Node n, int nx, int y, int nz) {
        double b1 = brk(nx, y + 1, nz);
        if (b1 >= WV.INF) return;
        double b2 = brk(nx, y, nz);
        if (b2 >= WV.INF) return;
        // y-1 : soit libre (chute), soit cassable (escalier qui descend)
        BlockPos below1 = p(nx, y - 1, nz);
        if (WV.standable(below1)) return; // c'est un "traverse"
        double base = Goal.COST_WALK + b1 + b2;
        if (!WV.passable(below1)) {
            // escalier : casser y-1 et se poser sur y-2
            double b3 = brk(nx, y - 1, nz);
            if (b3 >= WV.INF) return;
            if (!WV.standable(p(nx, y - 2, nz))) return;
            add(n, nx, y - 1, nz, base + b3 + Goal.COST_DOWN, Path.Move.DESCEND, false);
            return;
        }
        if (b1 > 0 || b2 > 0) {
            // on ne tombe pas apres avoir creuse (trop risque) sauf 1 bloc
            if (WV.standable(p(nx, y - 2, nz))) add(n, nx, y - 1, nz, base + Goal.COST_DOWN, Path.Move.DESCEND, false);
            return;
        }
        boolean water = false;
        int limit = 40;
        for (int k = 1; k <= limit; k++) {
            BlockPos feet = p(nx, y - k, nz);
            if (!WV.loaded(feet)) return;
            if (WV.isWater(feet)) water = true;
            if (WV.isLava(feet)) return;
            if (!WV.passable(feet) && !WV.isWater(feet)) return;
            BlockPos fl = p(nx, y - k - 1, nz);
            if (WV.standable(fl) || (WV.isWater(feet) && k > 1)) {
                if (k > ctx.maxFall && !water) return;
                if (WV.isLava(fl)) return;
                Path.Move mv = k == 1 ? Path.Move.DESCEND : Path.Move.FALL;
                add(n, nx, y - k, nz, base + Goal.COST_DOWN * k + (k > 1 ? 2 : 0), mv, false);
                return;
            }
            if (k > ctx.maxFall && !water && !WV.isWater(fl)) {
                // continuer seulement si de l'eau est en dessous
                boolean waterBelow = false;
                for (int j = k + 1; j <= limit; j++) {
                    BlockPos q = p(nx, y - j, nz);
                    if (WV.isWater(q)) { waterBelow = true; break; }
                    if (!WV.passable(q)) break;
                }
                if (!waterBelow) return;
            }
        }
    }

    private void diagonal(Node n, int nx, int y, int nz, int dx, int dz) {
        if (!WV.loaded(p(nx, y, nz))) return;
        if (!WV.passable(p(nx, y, nz)) || !WV.passable(p(nx, y + 1, nz))) return;
        if (!WV.standable(p(nx, y - 1, nz))) return;
        if (!WV.passable(p(n.x + dx, y, n.z)) || !WV.passable(p(n.x + dx, y + 1, n.z))) return;
        if (!WV.passable(p(n.x, y, n.z + dz)) || !WV.passable(p(n.x, y + 1, n.z + dz))) return;
        add(n, nx, y, nz, Goal.COST_WALK * Math.sqrt(2), Path.Move.DIAGONAL, false);
    }

    private void pillar(Node n, int x, int y, int z, boolean inWater) {
        if (!ctx.allowPlace || ctx.throwaway <= 0 || inWater) return;
        if (!WV.standable(p(x, y - 1, z))) return;
        double b = brk(x, y + 2, z);
        if (b >= WV.INF) return;
        if (!WV.replaceable(p(x, y, z)) && !WV.passable(p(x, y, z))) return;
        add(n, x, y + 1, z, PILLAR_COST + b, Path.Move.PILLAR, true);
    }

    private void swimUp(Node n, int x, int y, int z) {
        if (WV.passable(p(x, y + 2, z)) || WV.isWater(p(x, y + 2, z))) {
            if (WV.isWater(p(x, y + 1, z)) || WV.passable(p(x, y + 1, z)))
                add(n, x, y + 1, z, Goal.COST_WALK * 1.5, Path.Move.ASCEND, false);
        }
    }

    private void digDown(Node n, int x, int y, int z) {
        if (!ctx.allowDigDown || !ctx.allowBreak) return;
        BlockPos below = p(x, y - 1, z);
        if (WV.passable(below)) return;
        double b = brk(x, y - 1, z);
        if (b >= WV.INF) return;
        BlockPos floor = p(x, y - 2, z);
        if (!WV.standable(floor) || WV.isLava(floor)) return;
        // securite : pas de lave autour de la case d'arrivee
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (WV.isLava(p(x + d[0], y - 1, z + d[1])) || WV.isLava(p(x + d[0], y - 2, z + d[1]))) return;
        }
        add(n, x, y - 1, z, b + 3, Path.Move.DIG_DOWN, false);
    }
}
