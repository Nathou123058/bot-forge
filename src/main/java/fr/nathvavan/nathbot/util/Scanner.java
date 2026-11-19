package fr.nathvavan.nathbot.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Recherche rapide de blocs dans les chunks charges. */
public final class Scanner {
    private Scanner() {}

    public static List<BlockPos> find(Predicate<BlockState> pred, BlockPos center, int chunkRadius, int max, boolean exposedOnly) {
        List<BlockPos> out = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level == null) return out;
        int ccx = center.getX() >> 4, ccz = center.getZ() >> 4;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int cx = ccx - chunkRadius; cx <= ccx + chunkRadius; cx++) {
            for (int cz = ccz - chunkRadius; cz <= ccz + chunkRadius; cz++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                LevelChunkSection[] secs = ch.getSections();
                for (int i = 0; i < secs.length; i++) {
                    LevelChunkSection sec = secs[i];
                    if (sec == null || sec.hasOnlyAir()) continue;
                    if (!sec.maybeHas(pred)) continue;
                    int baseY = level.getSectionYFromSectionIndex(i) << 4;
                    for (int y = 0; y < 16; y++)
                        for (int z = 0; z < 16; z++)
                            for (int x = 0; x < 16; x++) {
                                BlockState s = sec.getBlockState(x, y, z);
                                if (!pred.test(s)) continue;
                                m.set((cx << 4) + x, baseY + y, (cz << 4) + z);
                                if (exposedOnly && !exposed(level, m)) continue;
                                out.add(m.immutable());
                            }
                }
            }
        }
        out.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        if (out.size() > max) return new ArrayList<>(out.subList(0, max));
        return out;
    }

    public static boolean exposed(Level level, BlockPos p) {
        for (Direction d : Direction.values()) {
            BlockState n = level.getBlockState(p.relative(d));
            if (n.isAir() || !n.getFluidState().isEmpty() || !n.canOcclude()) return true;
        }
        return false;
    }

    /** Y a-t-il au moins un bloc de ce type dans les chunks charges ? (s'arrete au premier trouve) */
    public static boolean any(Predicate<BlockState> pred, BlockPos center, int chunkRadius) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return false;
        int ccx = center.getX() >> 4, ccz = center.getZ() >> 4;
        for (int cx = ccx - chunkRadius; cx <= ccx + chunkRadius; cx++) {
            for (int cz = ccz - chunkRadius; cz <= ccz + chunkRadius; cz++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (LevelChunkSection sec : ch.getSections()) {
                    if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(pred)) continue;
                    for (int y = 0; y < 16; y++)
                        for (int z = 0; z < 16; z++)
                            for (int x = 0; x < 16; x++)
                                if (pred.test(sec.getBlockState(x, y, z))) return true;
                }
            }
        }
        return false;
    }

    public static BlockPos nearest(Predicate<BlockState> pred, BlockPos center, int chunkRadius) {
        List<BlockPos> l = find(pred, center, chunkRadius, 1, false);
        return l.isEmpty() ? null : l.get(0);
    }
}
