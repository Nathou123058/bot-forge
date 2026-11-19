package fr.nathvavan.nathbot.build;

import fr.nathvavan.nathbot.util.BotLog;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

/**
 * Lecture / ecriture de constructions au format "structure" de Minecraft (.nbt),
 * le meme que les blocs de structure. Dossier : .minecraft/nathbot/schematics/
 */
public final class SchematicIO {
    private SchematicIO() {}

    public static BlockPos pos1, pos2;

    public static File dir() {
        File d = new File(Minecraft.getInstance().gameDirectory, "nathbot/schematics");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static int reload() {
        Schematics.clearUser();
        File[] files = dir().listFiles((f, n) -> n.toLowerCase(Locale.ROOT).endsWith(".nbt"));
        int n = 0;
        if (files == null) return 0;
        for (File f : files) {
            try (InputStream in = new FileInputStream(f)) {
                CompoundTag tag = NbtIo.readCompressed(in);
                String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
                Schematic s = fromTag(id, tag);
                if (s != null) {
                    Schematics.putUser(s);
                    n++;
                }
            } catch (Exception e) {
                BotLog.warn("Fichier illisible : " + f.getName() + " (" + e.getMessage() + ")");
            }
        }
        return n;
    }

    static Schematic fromTag(String id, CompoundTag tag) {
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        if (palette.isEmpty() && tag.contains("palettes")) {
            ListTag ps = tag.getList("palettes", Tag.TAG_LIST);
            if (!ps.isEmpty()) palette = ps.getList(0);
        }
        List<BlockState> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
        }
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        LinkedHashMap<BlockPos, BlockState> m = new LinkedHashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            ListTag p = b.getList("pos", Tag.TAG_INT);
            int st = b.getInt("state");
            if (st < 0 || st >= states.size()) continue;
            BlockState s = states.get(st);
            if (s.is(net.minecraft.world.level.block.Blocks.STRUCTURE_VOID)) continue;
            m.put(new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2)), s);
        }
        if (m.isEmpty()) return null;
        return new Schematic(id, id.replace('_', ' '), "Construction importee (" + m.size() + " blocs)", "Mes constructions", m);
    }

    /** Sauvegarde la zone pos1-pos2 dans un fichier .nbt. */
    public static String saveSelection(String name) {
        if (pos1 == null || pos2 == null) return "Selection incomplete : utilise /nb pos1 et /nb pos2.";
        var level = Minecraft.getInstance().level;
        int x1 = Math.min(pos1.getX(), pos2.getX()), y1 = Math.min(pos1.getY(), pos2.getY()), z1 = Math.min(pos1.getZ(), pos2.getZ());
        int x2 = Math.max(pos1.getX(), pos2.getX()), y2 = Math.max(pos1.getY(), pos2.getY()), z2 = Math.max(pos1.getZ(), pos2.getZ());
        long vol = (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
        if (vol > 500_000) return "Zone trop grande (" + vol + " blocs, max 500000).";
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> idx = new HashMap<>();
        ListTag blocks = new ListTag();
        for (int y = y1; y <= y2; y++)
            for (int x = x1; x <= x2; x++)
                for (int z = z1; z <= z2; z++) {
                    BlockState s = level.getBlockState(new BlockPos(x, y, z));
                    int i = idx.computeIfAbsent(s, k -> { palette.add(k); return palette.size() - 1; });
                    CompoundTag b = new CompoundTag();
                    ListTag p = new ListTag();
                    p.add(IntTag.valueOf(x - x1));
                    p.add(IntTag.valueOf(y - y1));
                    p.add(IntTag.valueOf(z - z1));
                    b.put("pos", p);
                    b.putInt("state", i);
                    blocks.add(b);
                }
        CompoundTag root = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(x2 - x1 + 1));
        size.add(IntTag.valueOf(y2 - y1 + 1));
        size.add(IntTag.valueOf(z2 - z1 + 1));
        root.put("size", size);
        ListTag pal = new ListTag();
        for (BlockState s : palette) pal.add(NbtUtils.writeBlockState(s));
        root.put("palette", pal);
        root.put("blocks", blocks);
        root.put("entities", new ListTag());
        root.putInt("DataVersion", 3465);
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        File f = new File(dir(), id + ".nbt");
        try (OutputStream out = new FileOutputStream(f)) {
            NbtIo.writeCompressed(root, out);
        } catch (Exception e) {
            return "Erreur d'ecriture : " + e.getMessage();
        }
        reload();
        return "Construction '" + id + "' sauvegardee (" + blocks.size() + " blocs) dans " + f.getPath();
    }
}
