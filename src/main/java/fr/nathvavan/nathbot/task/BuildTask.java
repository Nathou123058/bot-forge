package fr.nathvavan.nathbot.task;

import fr.nathvavan.nathbot.BotConfig;
import fr.nathvavan.nathbot.bot.Actions;
import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.build.Schematic;
import fr.nathvavan.nathbot.path.Goal;
import fr.nathvavan.nathbot.util.BotLog;
import fr.nathvavan.nathbot.util.Inv;
import fr.nathvavan.nathbot.util.Rot;
import fr.nathvavan.nathbot.util.WV;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Construit un plan bloc par bloc.
 * - Mode survie : le bot pose les blocs de son inventaire, va chercher ce qui manque si possible.
 * - Mode creatif : il se donne les blocs.
 * - Mode commandes (cheats) : /setblock instantane avec orientation exacte.
 */
public class BuildTask extends Task {
    private final Schematic schem;
    private final BlockPos origin;
    private final List<BlockPos> order = new ArrayList<>();
    private final Map<BlockPos, BlockState> target = new HashMap<>();
    private final Set<BlockPos> skipped = new HashSet<>();
    private final Map<Item, Integer> missing = new LinkedHashMap<>();
    private final Map<BlockPos, Integer> attempts = new HashMap<>();
    private boolean commands;
    private int cmdIndex = 0;
    private int placed = 0;
    private int passes = 0;
    private BlockPos current;
    private int gatherTries = 0;

    public BuildTask(Schematic schem, BlockPos origin, boolean commands) {
        this.schem = schem;
        this.origin = origin;
        this.commands = commands;
        Set<Long> prot = new HashSet<>();
        for (Map.Entry<BlockPos, BlockState> e : schem.blocks.entrySet()) {
            BlockPos p = origin.offset(e.getKey());
            target.put(p, e.getValue());
            order.add(p);
            if (!e.getValue().isAir()) prot.add(p.asLong());
        }
        // du bas vers le haut, les blocs "accroches" (torches, portes...) a la fin de leur couche
        order.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(p -> attachLater(target.get(p)) ? 1 : 0)
                .thenComparingDouble(p -> p.distSqr(origin)));
        WV.protectedPositions = prot;
    }

    private static boolean attachLater(BlockState s) {
        Block b = s.getBlock();
        return b instanceof TorchBlock || b instanceof LadderBlock || b instanceof DoorBlock || b instanceof ButtonBlock
                || b instanceof CarpetBlock || b instanceof LanternBlock || b instanceof WaterlilyBlock || b instanceof SugarCaneBlock
                || b instanceof LiquidBlock || b instanceof PressurePlateBlock;
    }

    @Override
    public String name() { return "Construire " + schem.name + " (" + placed + " poses)"; }

    private boolean correct(BlockPos p, BlockState want) {
        BlockState cur = WV.st(p);
        if (want.isAir()) return cur.isAir() || cur.canBeReplaced() && cur.getFluidState().isEmpty();
        if (Schematic.isSecondaryPart(want)) return true;
        if (want.getBlock() == Blocks.FARMLAND) return cur.is(Blocks.FARMLAND) || cur.is(Blocks.DIRT) || cur.is(Blocks.GRASS_BLOCK);
        if (want.getBlock() instanceof LiquidBlock) return cur.getFluidState().isSource() && cur.getFluidState().getType().isSame(want.getFluidState().getType());
        return cur.getBlock() == want.getBlock();
    }

    private boolean canUseCommands() {
        return mc.player.hasPermissions(2);
    }

    @Override
    protected Status update() {
        if (commands) {
            if (!canUseCommands()) {
                BotLog.warn("Pas de droits de commandes (cheats) : je passe en mode survie.");
                commands = false;
                return Status.RUNNING;
            }
            int sent = 0;
            while (cmdIndex < order.size() && sent < 12) {
                BlockPos p = order.get(cmdIndex++);
                BlockState s = target.get(p);
                if (WV.st(p) == s) continue;
                String st = BlockStateParser.serialize(s);
                mc.player.connection.sendCommand("setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " " + st);
                sent++;
                placed++;
            }
            status = cmdIndex + "/" + order.size();
            return cmdIndex >= order.size() ? Status.SUCCESS : Status.RUNNING;
        }

        // ---------------- mode survie / creatif
        BlockPos next = findNext();
        if (next == null) {
            if (!skipped.isEmpty() && passes < 3) {
                passes++;
                skipped.clear();
                attempts.clear();
                return Status.RUNNING;
            }
            if (!missing.isEmpty()) {
                StringBuilder sb = new StringBuilder("Construction incomplete. Il manque : ");
                missing.forEach((i, n) -> sb.append(n).append("x ").append(i.getDescription().getString()).append(", "));
                BotLog.warn(sb.toString());
                return Status.FAILED;
            }
            return Status.SUCCESS;
        }
        current = next;
        BlockState want = target.get(next);
        int tries = attempts.merge(next, 1, Integer::sum);
        if (tries > 300) {
            skipped.add(next);
            return Status.RUNNING;
        }
        status = placed + " poses, prochain " + next.toShortString();

        // 1) mauvais bloc -> casser
        BlockState cur = WV.st(next);
        if (!cur.isAir() && !cur.canBeReplaced() && !correct(next, want)) {
            if (WV.protectedBlock(next, cur) && !WV.protectedPositions.contains(next.asLong())) { skipped.add(next); return Status.RUNNING; }
            if (!Actions.inReach(next)) return approach(next);
            Actions.breakBlock(next);
            return Status.RUNNING;
        }
        if (want.isAir()) {
            if (!cur.getFluidState().isEmpty()) { skipped.add(next); }
            return Status.RUNNING;
        }

        // 2) materiaux
        Item item = Schematic.itemFor(want);
        if (item == Items.AIR) { skipped.add(next); return Status.RUNNING; }
        if (!Inv.has(item)) {
            if (mc.player.getAbilities().instabuild) {
                int slot = Inv.find(ItemStack::isEmpty);
                if (slot < 0 || slot >= 9) slot = 8;
                mc.gameMode.handleCreativeModeItemAdd(new ItemStack(item, item.getMaxStackSize()), 36 + slot);
                mc.player.getInventory().items.set(slot, new ItemStack(item, item.getMaxStackSize()));
                return Status.RUNNING;
            }
            int need = countRemaining(item);
            if (BotConfig.get(BotConfig.AUTO_GATHER_MATERIALS, true) && GatherTask.canGather(item) && gatherTries < 20) {
                gatherTries++;
                BotLog.info("Il me manque " + need + "x " + item.getDescription().getString() + ", je vais en chercher.");
                run(new GatherTask(item, Math.min(need, 64 * 4)));
                return Status.RUNNING;
            }
            missing.merge(item, 1, Integer::sum);
            skipped.add(next);
            return Status.RUNNING;
        }

        // 3) support
        if (!Actions.hasSupport(next)) {
            skipped.add(next);
            return Status.RUNNING;
        }
        // 4) ne pas etre dans le bloc
        AABB box = new AABB(next);
        if (mc.player.getBoundingBox().intersects(box)) {
            BlockPos f = Bot.feet();
            run(new GotoTask(new Goal.Away(next.getX(), next.getZ(), 2), 2));
            return Status.RUNNING;
        }
        if (!Actions.inReach(next)) return approach(next);

        // 5) poser
        if (item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET) {
            Inv.select(s -> s.is(item), 2);
            Direction d = supportDir(next);
            if (d == null) { skipped.add(next); return Status.RUNNING; }
            BlockPos sup = next.relative(d);
            Vec3 hit = Vec3.atCenterOf(sup).add(Vec3.atLowerCornerOf(d.getOpposite().getNormal()).scale(0.5));
            if (Rot.lookAt(hit)) {
                Rot.syncNow();
                Actions.useItem();
            }
            return Status.RUNNING;
        }
        Float yaw = placementYaw(want);
        Actions.forcedYaw = yaw;
        boolean ok;
        try {
            ok = placeWithFacing(next, item, yaw);
        } finally {
            Actions.forcedYaw = null;
        }
        if (ok) placed++;
        return Status.RUNNING;
    }

    private boolean placeWithFacing(BlockPos target, Item item, Float yaw) {
        Direction d = supportDir(target);
        if (d == null) return false;
        BlockState want = this.target.get(target);
        // bloc accroche a un mur : cliquer sur le bon mur
        if (want.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && (want.getBlock() instanceof WallTorchBlock || want.getBlock() instanceof LadderBlock)) {
            Direction facing = want.getValue(BlockStateProperties.HORIZONTAL_FACING);
            BlockPos wall = target.relative(facing.getOpposite());
            if (WV.solidSupport(wall)) return Actions.placeAgainst(wall, facing, s -> s.is(item));
        }
        return Actions.placeAgainst(target.relative(d), d.getOpposite(), s -> s.is(item));
    }

    /** Lacet a envoyer au serveur pour que le bloc soit bien oriente. */
    private Float placementYaw(BlockState s) {
        Direction f = null;
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) f = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (f == null) return null;
        Block b = s.getBlock();
        boolean same = b instanceof StairBlock || b instanceof DoorBlock || b instanceof FenceGateBlock || b instanceof BedBlock
                || b instanceof TrapDoorBlock || b instanceof AnvilBlock;
        Direction look = same ? f : f.getOpposite();
        if (b instanceof AnvilBlock) look = f.getClockWise();
        return look.toYRot();
    }

    private Direction supportDir(BlockPos target) {
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        for (Direction d : order) if (WV.solidSupport(target.relative(d))) return d;
        return null;
    }

    private Status approach(BlockPos p) {
        GotoTask g = new GotoTask(new Goal.Reach(p, 3.8), 2);
        g.quiet = true;
        run(g);
        return Status.RUNNING;
    }

    private int countRemaining(Item item) {
        int n = 0;
        for (BlockPos p : order) {
            BlockState s = target.get(p);
            if (!s.isAir() && !Schematic.isSecondaryPart(s) && Schematic.itemFor(s) == item && !correct(p, s)) n++;
        }
        return Math.max(1, n);
    }

    private BlockPos findNext() {
        BlockPos feet = Bot.feet();
        BlockPos best = null;
        int bestY = Integer.MAX_VALUE;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : order) {
            if (skipped.contains(p)) continue;
            BlockState s = target.get(p);
            if (correct(p, s)) continue;
            if (!WV.loaded(p)) continue;
            int y = p.getY();
            if (y > bestY) break; // ordre trie par Y : inutile de regarder plus haut
            double d = p.distSqr(feet);
            if (y < bestY || d < bestD) {
                bestY = y;
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    @Override
    protected void onChildDone(Task child, Status result) {
        if (child instanceof GotoTask && result == Status.FAILED && current != null) skipped.add(current);
    }

    @Override
    protected void onStop() {
        WV.protectedPositions = Set.of();
    }
}
