package fr.nathvavan.nathbot.path;

import fr.nathvavan.nathbot.BotConfig;
import fr.nathvavan.nathbot.bot.Actions;
import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.bot.BotInput;
import fr.nathvavan.nathbot.util.Inv;
import fr.nathvavan.nathbot.util.Rot;
import fr.nathvavan.nathbot.util.WV;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Fait suivre un chemin au joueur en simulant clavier / souris. */
public final class PathExecutor {
    public enum Result { RUNNING, DONE, FAILED }

    private final Path path;
    private int idx = 0;
    private int ticksOnStep = 0;
    private int stuck = 0;
    private Vec3 lastPos;
    public String failReason = "";

    public PathExecutor(Path path) {
        this.path = path;
    }

    public Path path() { return path; }

    public int index() { return idx; }

    private Result fail(String why) {
        failReason = why;
        return Result.FAILED;
    }

    public Result tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        BotInput in = Bot.I.in;
        in.active = true;
        List<Path.Step> steps = path.steps;
        if (idx >= steps.size()) return Result.DONE;
        BlockPos feet = Bot.feet();
        boolean grounded = p.onGround() || p.isInWater() || p.onClimbable();

        // etapes deja atteintes (on a pu aller plus vite que prevu)
        for (int j = Math.min(steps.size() - 1, idx + 4); j >= idx; j--) {
            if (steps.get(j).to().equals(feet) && grounded) {
                idx = j + 1;
                ticksOnStep = 0;
                stuck = 0;
                Bot.I.renderIdx = idx;
                if (idx >= steps.size()) return Result.DONE;
                break;
            }
        }
        Path.Step s = steps.get(idx);

        // hors du chemin ?
        if (grounded) {
            double d1 = distXZ(p, s.from()), d2 = distXZ(p, s.to());
            int dy = Math.min(Math.abs(feet.getY() - s.from().getY()), Math.abs(feet.getY() - s.to().getY()));
            if (Math.min(d1, d2) > 2.2 || dy > 2) return fail("sorti du chemin");
        }
        if (++ticksOnStep > 20 * 25) return fail("etape trop longue");
        if (p.isInLava()) {
            in.jump = true;
            in.forward = 1;
        }

        // 1) degager les blocs
        for (BlockPos b : s.toBreak()) {
            if (WV.passable(b) || WV.isWater(b)) continue;
            if (WV.breakCost(b, true, Inv.toolSnapshot()) >= WV.INF) return fail("bloc impossible a casser en " + b.toShortString());
            if (!Actions.inReach(b)) {
                // on s'approche un peu
                moveToward(s.from(), 0.6f);
                return Result.RUNNING;
            }
            Actions.breakBlock(b);
            stuck = 0;
            return Result.RUNNING;
        }
        // attendre la chute du sable / gravier
        if (!mc.level.getEntitiesOfClass(FallingBlockEntity.class, new AABB(s.to()).inflate(1, 3, 1)).isEmpty()) return Result.RUNNING;

        // 2) mouvements speciaux
        switch (s.move()) {
            case PILLAR -> { return pillar(s, feet); }
            case DIG_DOWN -> { return digDown(s, feet); }
            default -> {}
        }

        // 3) poser un pont si besoin
        if (s.placeFloor() && !WV.standable(s.to().below())) {
            if (Inv.throwawayCount() <= 0) return fail("plus de blocs a poser");
            in.sneak = true;
            if (!feet.equals(s.from())) {
                moveToward(s.from(), 0.4f);
                return Result.RUNNING;
            }
            Direction dir = Direction.getNearest(s.to().getX() - s.from().getX(), 0, s.to().getZ() - s.from().getZ());
            BlockPos sup = s.from().below();
            if (WV.solidSupport(sup)) Actions.placeAgainst(sup, dir, Inv::isThrowaway);
            else Actions.place(s.to().below(), Inv::isThrowaway);
            stuck = 0;
            return Result.RUNNING;
        }

        // 4) marcher
        walk(s, feet, p);

        // detection de blocage
        Vec3 pos = p.position();
        if (lastPos != null && pos.distanceToSqr(lastPos) < 0.0004) {
            if (++stuck > 80) return fail("bloque");
            if (stuck > 30 && p.onGround()) in.jump = true;
        } else stuck = 0;
        lastPos = pos;
        return Result.RUNNING;
    }

    private void walk(Path.Step s, BlockPos feet, LocalPlayer p) {
        BotInput in = Bot.I.in;
        Vec3 target = new Vec3(s.to().getX() + 0.5, p.getY(), s.to().getZ() + 0.5);
        double dx = target.x - p.getX(), dz = target.z - p.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        boolean vertical = s.to().getX() == s.from().getX() && s.to().getZ() == s.from().getZ();
        if (!vertical || dist > 0.3) {
            float desired = (float) (Mth.atan2(dz, dx) * 180 / Math.PI) - 90f;
            Rot.setRotation(desired, aimPitch(p));
            float err = Math.abs(Mth.wrapDegrees(desired - p.getYRot()));
            in.forward = err < 70 ? (dist < 0.4 && vertical ? 0.3f : 1f) : 0f;
        }
        switch (s.move()) {
            case ASCEND -> {
                if (vertical) in.jump = true;
                else if ((p.onGround() || p.isInWater()) && (dist < 1.35 || p.horizontalCollision)) in.jump = true;
            }
            case TRAVERSE, DIAGONAL -> {
                if (p.horizontalCollision && p.onGround()) in.jump = true;
            }
            default -> {}
        }
        if (p.isInWater() && s.to().getY() >= feet.getY()) in.jump = true;
        if (BotConfig.get(BotConfig.ALLOW_SPRINT, true) && !p.isInWater() && p.getFoodData().getFoodLevel() > 6
                && (s.move() == Path.Move.TRAVERSE || s.move() == Path.Move.DIAGONAL) && !s.placeFloor() && nextIsStraight()) {
            in.sprint = true;
        }
        // technique de speedrunner : sprint + saut sur les longues lignes droites (~30 % plus rapide)
        if (in.sprint && p.onGround() && !in.jump && fr.nathvavan.nathbot.task.SpeedrunTask.isRunning() && longStraight()) in.jump = true;
    }

    /** Les 4 prochains pas vont tout droit, a plat, avec de la place au-dessus de la tete pour sauter. */
    private boolean longStraight() {
        if (idx + 4 >= path.steps.size()) return false;
        Path.Step a = path.steps.get(idx);
        int dx = a.to().getX() - a.from().getX(), dz = a.to().getZ() - a.from().getZ();
        for (int k = idx; k < idx + 4; k++) {
            Path.Step st = path.steps.get(k);
            if (st.move() != Path.Move.TRAVERSE || st.placeFloor() || !st.toBreak().isEmpty()) return false;
            if (st.to().getX() - st.from().getX() != dx || st.to().getZ() - st.from().getZ() != dz) return false;
            if (st.to().getY() != a.from().getY()) return false;
            if (!WV.passable(st.to().above(2)) || !WV.passable(st.from().above(2))) return false;
        }
        return true;
    }

    private float aimPitch(LocalPlayer p) {
        float pitch = p.getXRot();
        return Mth.clamp(pitch * 0.6f + 10f * 0.4f, -30f, 40f);
    }

    private boolean nextIsStraight() {
        if (idx + 2 >= path.steps.size()) return false;
        Path.Step a = path.steps.get(idx), b = path.steps.get(idx + 1), c = path.steps.get(idx + 2);
        for (Path.Step st : new Path.Step[]{b, c}) {
            if (st.move() != Path.Move.TRAVERSE && st.move() != Path.Move.DIAGONAL) return false;
            if (st.placeFloor()) return false;
        }
        int dx = a.to().getX() - a.from().getX(), dz = a.to().getZ() - a.from().getZ();
        int dx2 = b.to().getX() - b.from().getX(), dz2 = b.to().getZ() - b.from().getZ();
        return dx == dx2 && dz == dz2;
    }

    private void moveToward(BlockPos pos, float speed) {
        LocalPlayer p = Minecraft.getInstance().player;
        Vec3 c = new Vec3(pos.getX() + 0.5, p.getY(), pos.getZ() + 0.5);
        double d = Math.sqrt(c.distanceToSqr(p.position()));
        if (d < 0.15) return;
        Rot.yawTowards(c);
        Bot.I.in.forward = (float) Math.min(speed, Math.max(0.15, d));
    }

    private boolean centered(BlockPos pos, double tol) {
        LocalPlayer p = Minecraft.getInstance().player;
        return distXZ(p, pos) <= tol;
    }

    private Result pillar(Path.Step s, BlockPos feet) {
        LocalPlayer p = Minecraft.getInstance().player;
        BotInput in = Bot.I.in;
        if (Inv.throwawayCount() <= 0) return fail("plus de blocs pour monter");
        if (!centered(s.from(), 0.25) && p.onGround()) {
            moveToward(s.from(), 0.3f);
            return Result.RUNNING;
        }
        Rot.setRotation(p.getYRot(), 90f);
        if (p.onGround() && feet.equals(s.from()) && p.getXRot() > 80) in.jump = true;
        if (p.getY() >= s.from().getY() + 0.98 && WV.replaceable(s.from())) {
            Actions.placeAgainst(s.from().below(), Direction.UP, Inv::isThrowaway);
        }
        return Result.RUNNING;
    }

    private Result digDown(Path.Step s, BlockPos feet) {
        if (!centered(s.from(), 0.25)) {
            moveToward(s.from(), 0.3f);
            return Result.RUNNING;
        }
        BlockPos below = s.from().below();
        if (WV.isLava(below.below())) return fail("lave en dessous !");
        Actions.breakBlock(below);
        return Result.RUNNING;
    }

    private static double distXZ(LocalPlayer p, BlockPos b) {
        double dx = b.getX() + 0.5 - p.getX(), dz = b.getZ() + 0.5 - p.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
