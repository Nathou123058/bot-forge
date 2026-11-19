package fr.nathvavan.nathbot.task;

import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.path.Goal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Ramasse les objets au sol autour du joueur. */
public class CollectDropsTask extends Task {
    private final double radius;
    private final int maxTicks;
    private final Predicate<ItemStack> filter;
    private final Set<Integer> skipped = new HashSet<>();
    private ItemEntity current;

    public CollectDropsTask(double radius, int maxTicks) {
        this(radius, maxTicks, s -> true);
    }

    public CollectDropsTask(double radius, int maxTicks, Predicate<ItemStack> filter) {
        this.radius = radius;
        this.maxTicks = maxTicks;
        this.filter = filter;
    }

    @Override
    public String name() { return "Ramasser les objets"; }

    @Override
    protected Status update() {
        if (ticks > maxTicks) return Status.SUCCESS;
        if (fr.nathvavan.nathbot.util.Inv.freeSlots() == 0) return Status.SUCCESS;
        List<ItemEntity> items = mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(mc.player.blockPosition()).inflate(radius),
                e -> e.isAlive() && filter.test(e.getItem()) && !skipped.contains(e.getId()));
        if (items.isEmpty()) return Status.SUCCESS;
        items.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
        current = items.get(0);
        BlockPos p = BlockPos.containing(current.getX(), current.getY() + 0.2, current.getZ());
        GotoTask g = new GotoTask(new Goal.Near(p, 1), 2);
        g.quiet = true;
        run(g);
        return Status.RUNNING;
    }

    @Override
    protected boolean shouldInterruptChild(Task child) {
        return current != null && !current.isAlive() || ticks > maxTicks;
    }

    @Override
    protected void onChildDone(Task child, Status result) {
        if (current != null && current.isAlive()) {
            if (result == Status.FAILED || current.distanceTo(mc.player) < 1.5) skipped.add(current.getId());
        }
        current = null;
    }

    static BlockPos feet() { return Bot.feet(); }
}
