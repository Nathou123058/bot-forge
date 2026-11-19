package fr.nathvavan.nathbot.bot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.*;

/** Points de passage sauvegardes (maison, mort, portails...). */
public final class Waypoints {
    private Waypoints() {}

    public static final class Wp {
        public String name;
        public int x, y, z;
        public String dim;
        public long created;

        public BlockPos pos() { return new BlockPos(x, y, z); }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<Wp> list;
    public static volatile String active; // waypoint suivi par la boussole du HUD

    private static File file() {
        File d = new File(Minecraft.getInstance().gameDirectory, "nathbot");
        if (!d.exists()) d.mkdirs();
        return new File(d, "waypoints.json");
    }

    private static synchronized List<Wp> data() {
        if (list == null) {
            list = new ArrayList<>();
            File f = file();
            if (f.exists()) {
                try (Reader r = new FileReader(f)) {
                    List<Wp> l = GSON.fromJson(r, new TypeToken<List<Wp>>() {}.getType());
                    if (l != null) list.addAll(l);
                } catch (Exception ignored) {}
            }
        }
        return list;
    }

    private static synchronized void save() {
        try (Writer w = new FileWriter(file())) {
            GSON.toJson(data(), w);
        } catch (Exception ignored) {}
    }

    public static String currentDim() {
        var l = Minecraft.getInstance().level;
        return l == null ? "?" : l.dimension().location().toString();
    }

    public static synchronized void set(String rawName, BlockPos p) {
        String name = rawName.replaceAll("[^A-Za-z0-9_\\-]", "_");
        data().removeIf(w -> w.name.equalsIgnoreCase(name) && w.dim.equals(currentDim()));
        Wp w = new Wp();
        w.name = name;
        w.x = p.getX();
        w.y = p.getY();
        w.z = p.getZ();
        w.dim = currentDim();
        w.created = System.currentTimeMillis();
        data().add(w);
        save();
    }

    public static synchronized boolean remove(String name) {
        boolean r = data().removeIf(w -> w.name.equalsIgnoreCase(name));
        if (r) save();
        return r;
    }

    public static synchronized Wp get(String name) {
        String dim = currentDim();
        for (Wp w : data()) if (w.name.equalsIgnoreCase(name) && w.dim.equals(dim)) return w;
        for (Wp w : data()) if (w.name.equalsIgnoreCase(name)) return w;
        return null;
    }

    public static synchronized List<Wp> all() {
        return new ArrayList<>(data());
    }
}
