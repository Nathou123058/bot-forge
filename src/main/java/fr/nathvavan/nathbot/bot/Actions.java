package fr.nathvavan.nathbot.bot;

import fr.nathvavan.nathbot.util.Inv;
import fr.nathvavan.nathbot.util.Rot;
import fr.nathvavan.nathbot.util.WV;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/** Actions de base : casser, poser, utiliser, attaquer. */
public final class Actions {
    private Actions() {}

    public static final double REACH = 4.4;
    private static int placeCooldown = 0;
    /** Si non nul : lacet envoye au serveur au moment de poser (orientation des blocs). */
    public static Float forcedYaw = null;
    private static int useCooldown = 0;

    static void tickCooldowns() {
        if (placeCooldown > 0) placeCooldown--;
        if (useCooldown > 0) useCooldown--;
    }

    private static Minecraft mc() { return Minecraft.getInstance(); }

    public static boolean inReach(BlockPos pos) {
        LocalPlayer p = mc().player;
        return p.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= REACH * REACH;
    }

    /** Point a viser : centre de la face visible la plus proche. */
    public static Vec3 aimPoint(BlockPos pos) {
        LocalPlayer p = mc().player;
        Vec3 eye = p.getEyePosition();
        Vec3 best = Vec3.atCenterOf(pos);
        double bd = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (!WV.passable(n) && !WV.isWater(n) && !n.equals(Bot.feet()) && !n.equals(Bot.feet().above())) continue;
            Vec3 c = Vec3.atCenterOf(pos).add(d.getStepX() * 0.49, d.getStepY() * 0.49, d.getStepZ() * 0.49);
            double dd = c.distanceToSqr(eye);
            if (dd < bd) { bd = dd; best = c; }
        }
        return best;
    }

    public static Direction faceToward(BlockPos pos) {
        Vec3 eye = mc().player.getEyePosition();
        Vec3 c = Vec3.atCenterOf(pos);
        return Direction.getNearest(eye.x - c.x, eye.y - c.y, eye.z - c.z);
    }

    /**
     * Casse un bloc (un tick de minage). Renvoie true quand le bloc est casse / deja libre.
     */
    public static boolean breakBlock(BlockPos pos) {
        LocalPlayer p = mc().player;
        BlockState s = WV.st(pos);
        if (s.isAir() || WV.passable(pos)) {
            return true;
        }
        if (!inReach(pos)) return false;
        Inv.selectBestTool(s);
        boolean aimed = Rot.lookAt(aimPoint(pos));
        Bot.I.markBreaking(pos);
        if (!aimed) return false;
        if (mc().screen == null) {
            HitResult hr = mc().hitResult;
            Bot.I.requestAttackKey();
            if (hr instanceof BlockHitResult bhr && hr.getType() == HitResult.Type.BLOCK && !bhr.getBlockPos().equals(pos)) {
                // un autre bloc gene : le vanilla va le casser, c'est ce qu'on veut aussi
                return false;
            }
        } else {
            mc().gameMode.continueDestroyBlock(pos, faceToward(pos));
            p.swing(InteractionHand.MAIN_HAND);
        }
        return false;
    }

    /** Pose un bloc a la position cible (cherche un support autour). */
    public static boolean place(BlockPos target, Predicate<ItemStack> item) {
        if (!WV.replaceable(target)) return false;
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        LocalPlayer p = mc().player;
        Vec3 eye = p.getEyePosition();
        Direction bestDir = null;
        double bd = Double.MAX_VALUE;
        for (Direction d : order) {
            BlockPos sup = target.relative(d);
            if (!WV.solidSupport(sup)) continue;
            Vec3 hit = Vec3.atCenterOf(target).add(d.getStepX() * 0.5, d.getStepY() * 0.5, d.getStepZ() * 0.5);
            double dd = hit.distanceToSqr(eye);
            if (dd < bd) { bd = dd; bestDir = d; }
        }
        if (bestDir == null) return false;
        return placeAgainst(target.relative(bestDir), bestDir.getOpposite(), item);
    }

    public static boolean hasSupport(BlockPos target) {
        for (Direction d : Direction.values()) if (WV.solidSupport(target.relative(d))) return true;
        return false;
    }

    /** Pose un bloc en cliquant sur la face 'face' du bloc support. */
    public static boolean placeAgainst(BlockPos support, Direction face, Predicate<ItemStack> item) {
        LocalPlayer p = mc().player;
        if (placeCooldown > 0) return false;
        if (!Inv.select(item, 1)) return false;
        Vec3 hit = Vec3.atCenterOf(support).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        if (p.getEyePosition().distanceToSqr(hit) > REACH * REACH) return false;
        boolean aimed = Rot.lookAt(hit);
        if (!aimed && fr.nathvavan.nathbot.BotConfig.get(fr.nathvavan.nathbot.BotConfig.SMOOTH_ROTATION, true)) return false;
        if (forcedYaw != null) {
            p.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(forcedYaw, p.getXRot(), p.onGround()));
        } else Rot.syncNow();
        BlockHitResult bhr = new BlockHitResult(hit, face, support, false);
        InteractionResult r = mc().gameMode.useItemOn(p, InteractionHand.MAIN_HAND, bhr);
        if (forcedYaw != null) Rot.syncNow();
        if (r.consumesAction()) p.swing(InteractionHand.MAIN_HAND);
        placeCooldown = 2;
        return r.consumesAction();
    }

    /** Clic droit sur un bloc (ouvrir un coffre, un four, utiliser un objet sur un bloc...). */
    public static boolean useOnBlock(BlockPos pos, Direction face) {
        LocalPlayer p = mc().player;
        if (useCooldown > 0) return false;
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        if (p.getEyePosition().distanceToSqr(hit) > REACH * REACH) return false;
        Rot.lookAt(hit);
        Rot.syncNow();
        InteractionResult r = mc().gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
        if (r.consumesAction()) p.swing(InteractionHand.MAIN_HAND);
        useCooldown = 5;
        return r.consumesAction();
    }

    /** Utilise l'objet en main (seau, oeil de l'Ender, nourriture...). */
    public static void useItem() {
        if (useCooldown > 0) return;
        Rot.syncNow();
        mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND);
        useCooldown = 5;
    }

    public static boolean canAttack() {
        return mc().player.getAttackStrengthScale(0.5f) >= 0.95f;
    }

    /** Attaque une entite si a portee et si l'arme est chargee. */
    public static boolean attack(Entity e) {
        LocalPlayer p = mc().player;
        if (p.distanceTo(e) > 3.6) return false;
        Rot.lookAt(e.getBoundingBox().getCenter());
        if (!canAttack()) return false;
        Rot.syncNow();
        mc().gameMode.attack(p, e);
        p.swing(InteractionHand.MAIN_HAND);
        return true;
    }
}
