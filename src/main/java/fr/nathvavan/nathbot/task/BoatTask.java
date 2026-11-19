package fr.nathvavan.nathbot.task;

import fr.nathvavan.nathbot.bot.Actions;
import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.bot.BotInput;
import fr.nathvavan.nathbot.feature.Features;
import fr.nathvavan.nathbot.path.Goal;
import fr.nathvavan.nathbot.util.Brain;
import fr.nathvavan.nathbot.util.Inv;
import fr.nathvavan.nathbot.util.Rot;
import fr.nathvavan.nathbot.util.Scanner;
import fr.nathvavan.nathbot.util.WV;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Traverser une grande etendue d'eau en bateau : en fabrique un si besoin, le pose,
 * monte dedans, navigue vers la destination, descend sur la rive et recupere le bateau.
 */
public class BoatTask extends Task {
    private final int tx, tz;
    private int tries = 0;
    private int wait = 0;
    private int ride = 0;
    private int stall = 0;
    private int recover = 0;
    private boolean sailed = false;
    private boolean attacked = false;
    private boolean collected = false;
    private Vec3 last;

    /** Dernier tick ou une BoatTask etait active (evite de lancer un bateau dans un bateau). */
    private static long lastActive = -1000;

    public BoatTask(int tx, int tz) {
        this.tx = tx;
        this.tz = tz;
    }

    @Override
    public String name() { return "Bateau vers X=" + tx + " Z=" + tz; }

    public static boolean isBoat(ItemStack s) { return s.getItem() instanceof BoatItem; }

    private static void markActive() {
        var mc = Minecraft.getInstance();
        if (mc.level != null) lastActive = mc.level.getGameTime();
    }

    @Override
    protected boolean shouldInterruptChild(Task child) {
        markActive();
        return false;
    }

    /** Est-ce que ca vaut le coup de prendre un bateau pour aller la-bas ? */
    public static boolean worthIt(int tx, int tz) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || p.isPassenger() || !Features.isOn("auto_boat")) return false;
        if (mc.level.getGameTime() - lastActive < 40) return false;
        double dx = tx - p.getX(), dz = tz - p.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 40) return false;
        // il faut un bateau ou de quoi en faire un
        if (Inv.count(BoatTask::isBoat) == 0 && Inv.count(s -> s.is(ItemTags.PLANKS)) < 5 && Inv.count(s -> s.is(ItemTags.LOGS)) < 2) return false;
        int water = 0, total = 0, firstWater = -1;
        int max = (int) Math.min(64, dist);
        for (int d = 2; d <= max; d += 2) {
            int x = Mth.floor(p.getX() + dx / dist * d), z = Mth.floor(p.getZ() + dz / dist * d);
            if (mc.level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) break;
            int y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            total++;
            if (mc.level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER)) {
                water++;
                if (firstWater < 0) firstWater = d;
            }
        }
        return total >= 10 && firstWater >= 0 && firstWater <= 8 && water >= total * 0.6;
    }

    private BlockPos waterSpot(int radius) {
        BlockPos feet = Bot.feet();
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (int x = -radius; x <= radius; x++)
            for (int z = -radius; z <= radius; z++)
                for (int y = -2; y <= 1; y++) {
                    BlockPos b = feet.offset(x, y, z);
                    if (!WV.isWater(b) || !WV.st(b.above()).isAir()) continue;
                    // on prefere l'eau dans la direction de la destination
                    double d = b.distSqr(feet) + 0.02 * (Math.pow(b.getX() - tx, 2) + Math.pow(b.getZ() - tz, 2)) / 100.0;
                    if (d < bd) { bd = d; best = b; }
                }
        return best;
    }

    private Boat nearestBoat(LocalPlayer p, double r) {
        List<Boat> l = mc.level.getEntitiesOfClass(Boat.class, p.getBoundingBox().inflate(r), b -> b.isAlive() && b.getPassengers().isEmpty());
        Boat best = null;
        double bd = Double.MAX_VALUE;
        for (Boat b : l) if (b.distanceToSqr(p) < bd) { bd = b.distanceToSqr(p); best = b; }
        return best;
    }

    @Override
    protected Status update() {
        markActive();
        LocalPlayer p = mc.player;
        if (p.getVehicle() instanceof Boat b) return sail(p, b);
        if (sailed) return recover(p);
        if (++tries > 40) return Status.FAILED;
        if (Inv.count(BoatTask::isBoat) == 0) {
            Brain.think("eau", "Beaucoup d'eau devant : je fabrique un bateau.");
            run(new GatherTask(Ingredient.of(ItemTags.BOATS), 1));
            return Status.RUNNING;
        }
        Boat existing = nearestBoat(p, 3.5);
        if (existing != null) {
            if (wait-- > 0) return Status.RUNNING;
            Rot.lookAt(existing.position());
            mc.gameMode.interact(p, existing, InteractionHand.MAIN_HAND);
            wait = 10;
            return Status.RUNNING;
        }
        BlockPos w = waterSpot(3);
        if (w == null) {
            BlockPos far = Scanner.nearest(s -> s.getFluidState().is(FluidTags.WATER) && s.getFluidState().isSource(), Bot.feet(), 2);
            if (far == null) return Status.FAILED;
            run(new GotoTask(new Goal.Near(far, 2), 3));
            return Status.RUNNING;
        }
        if (wait-- > 0) return Status.RUNNING;
        if (!Inv.select(BoatTask::isBoat, 4)) return Status.RUNNING;
        if (!Rot.lookAt(new Vec3(w.getX() + 0.5, w.getY() + 0.9, w.getZ() + 0.5))) {
            wait = 0;
            return Status.RUNNING;
        }
        Brain.think("eau", "Je pose mon bateau sur l'eau et je monte dedans.");
        Actions.useItem();
        wait = 8;
        return Status.RUNNING;
    }

    private Status sail(LocalPlayer p, Boat b) {
        sailed = true;
        BotInput in = Bot.I.in;
        in.active = true;
        double dx = tx + 0.5 - b.getX(), dz = tz + 0.5 - b.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        Vec3 pos = b.position();
        if (last != null && pos.distanceToSqr(last) < 0.0025) stall++;
        else stall = Math.max(0, stall - 2);
        last = pos;
        boolean onLand = !b.isInWater() && b.onGround() && ride > 40;
        if (dist < 4 || stall > 50 || onLand || ++ride > 20 * 240) {
            if (stall > 50 || onLand) Brain.think("eau", "J'ai touche la rive, je descends du bateau.");
            else Brain.think("eau", "Arrive, je descends du bateau.");
            in.sneak = true; // descendre
            return Status.RUNNING;
        }
        float desired = (float) (Mth.atan2(dz, dx) * 180 / Math.PI) - 90f;
        float diff = Mth.wrapDegrees(desired - b.getYRot());
        in.strafe = diff > 6 ? -1 : diff < -6 ? 1 : 0;   // a droite / a gauche
        in.forward = Math.abs(diff) < 70 ? 1 : 0;
        Rot.setRotation(desired, 8f);
        status = (int) dist + " blocs";
        return Status.RUNNING;
    }

    private Status recover(LocalPlayer p) {
        if (++recover > 120) return Status.SUCCESS;
        Boat b = nearestBoat(p, 4);
        if (b == null) {
            if (attacked && !collected) {
                collected = true;
                run(new CollectDropsTask(5, 40, BoatTask::isBoat));
                return Status.RUNNING;
            }
            if (recover > 20 || collected) return Status.SUCCESS;
            return Status.RUNNING;
        }
        if (recover == 1) Brain.think("eau", "Je casse le bateau pour le reprendre.");
        if (p.distanceTo(b) > 3.4) return Status.SUCCESS; // trop loin : tant pis
        Rot.lookAt(b.getBoundingBox().getCenter());
        if (recover % 4 == 0) {
            Rot.syncNow();
            mc.gameMode.attack(p, b);
            p.swing(InteractionHand.MAIN_HAND);
            attacked = true;
        }
        return Status.RUNNING;
    }
}
