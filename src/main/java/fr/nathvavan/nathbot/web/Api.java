package fr.nathvavan.nathbot.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.nathvavan.nathbot.BotConfig;
import fr.nathvavan.nathbot.NathBot;
import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.bot.Waypoints;
import fr.nathvavan.nathbot.build.Schematic;
import fr.nathvavan.nathbot.build.Schematics;
import fr.nathvavan.nathbot.command.Commands;
import fr.nathvavan.nathbot.feature.Feature;
import fr.nathvavan.nathbot.feature.Features;
import fr.nathvavan.nathbot.path.Path;
import fr.nathvavan.nathbot.util.BotLog;
import fr.nathvavan.nathbot.util.Inv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** API JSON du panneau web. */
final class Api {
    private Api() {}

    record Response(int code, String type, byte[] body) {
        static Response json(Object o) {
            return new Response(200, "application/json; charset=utf-8", new Gson().toJson(o).getBytes(StandardCharsets.UTF_8));
        }

        static Response text(int code, String s) {
            return new Response(code, "text/plain; charset=utf-8", s.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static <T> T onGame(Supplier<T> s) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        return mc.submit(s).get(3, TimeUnit.SECONDS);
    }

    static Response route(String method, String path, Map<String, String> q, String body) throws Exception {
        if (path.startsWith("/api/dev/")) return DevApi.route(method, path, q, body);
        if (path.startsWith("/web/")) return DevApi.webFile(path.substring(5));
        switch (path) {
            case "/", "/index.html" -> {
                // une copie modifiee dans .minecraft/nathbot/web/index.html remplace celle du mod (modifiable en direct)
                java.io.File custom = new java.io.File(fr.nathvavan.nathbot.dev.DevMode.dir(), "web/index.html");
                if (custom.isFile()) return new Response(200, "text/html; charset=utf-8", java.nio.file.Files.readAllBytes(custom.toPath()));
                try (InputStream in = NathBot.class.getResourceAsStream("/assets/nathbot/web/index.html")) {
                    if (in == null) return Response.text(404, "index.html introuvable");
                    return new Response(200, "text/html; charset=utf-8", in.readAllBytes());
                }
            }
            case "/api/status" -> { return Response.json(onGame(Api::status)); }
            case "/api/log" -> {
                JsonObject o = new JsonObject();
                JsonArray a = new JsonArray();
                for (String l : BotLog.lines()) a.add(l);
                o.add("lines", a);
                o.addProperty("counter", BotLog.counter());
                return Response.json(o);
            }
            case "/api/features" -> { return Response.json(onGame(Api::features)); }
            case "/api/schematics" -> { return Response.json(onGame(Api::schematics)); }
            case "/api/schematic" -> { return Response.json(onGame(() -> schematic(q.getOrDefault("id", "")))); }
            case "/api/waypoints" -> { return Response.json(onGame(Api::waypoints)); }
            case "/api/map" -> {
                int r = Math.max(16, Math.min(96, Integer.parseInt(q.getOrDefault("r", "48"))));
                return Response.json(onGame(() -> map(r)));
            }
            case "/api/help" -> {
                JsonArray a = new JsonArray();
                for (Commands.Help h : Commands.HELP) {
                    JsonObject o = new JsonObject();
                    o.addProperty("usage", h.usage());
                    o.addProperty("desc", h.desc());
                    o.addProperty("cat", h.category());
                    a.add(o);
                }
                return Response.json(a);
            }
            case "/api/settings" -> { return Response.json(onGame(Api::settings)); }
            case "/api/brain" -> { return Response.json(onGame(Api::brain)); }
            case "/api/items" -> {
                String qq = q.getOrDefault("q", "");
                return Response.json(onGame(() -> items(qq)));
            }
            case "/api/plan" -> {
                String id = q.getOrDefault("item", "");
                int n = Math.max(1, Math.min(2304, parseInt(q.getOrDefault("n", "1"), 1)));
                return Response.json(onGame(() -> plan(id, n)));
            }
            case "/api/cmd" -> {
                if (!method.equals("POST")) return Response.text(405, "POST uniquement");
                String cmd = body.trim();
                if (cmd.length() > 300) return Response.text(400, "Trop long");
                if (!cmd.isEmpty()) Bot.I.commandQueue.add(cmd);
                return Response.text(200, "ok");
            }
            default -> { return Response.text(404, "Introuvable"); }
        }
    }

    // ------------------------------------------------------------------

    private static JsonObject status() {
        Minecraft mc = Minecraft.getInstance();
        JsonObject o = new JsonObject();
        LocalPlayer p = mc.player;
        o.addProperty("inGame", p != null && mc.level != null);
        o.addProperty("version", NathBot.VERSION);
        o.addProperty("status", Bot.I.statusText());
        o.addProperty("active", Bot.I.task() != null);
        o.addProperty("paused", Bot.I.paused);
        o.addProperty("logCounter", BotLog.counter());
        o.addProperty("devLock", fr.nathvavan.nathbot.dev.DevMode.locked());
        o.addProperty("devReason", fr.nathvavan.nathbot.dev.DevMode.reason());
        if (p == null || mc.level == null) return o;
        o.addProperty("name", p.getGameProfile().getName());
        o.addProperty("x", p.getX());
        o.addProperty("y", p.getY());
        o.addProperty("z", p.getZ());
        o.addProperty("yaw", p.getYRot());
        o.addProperty("dim", mc.level.dimension().location().getPath());
        o.addProperty("health", p.getHealth());
        o.addProperty("maxHealth", p.getMaxHealth());
        o.addProperty("food", p.getFoodData().getFoodLevel());
        o.addProperty("xp", p.experienceLevel);
        o.addProperty("armor", p.getArmorValue());
        o.addProperty("time", mc.level.getDayTime() % 24000);
        o.addProperty("biome", mc.level.getBiome(p.blockPosition()).unwrapKey().map(k -> k.location().getPath()).orElse("?"));
        o.addProperty("gamemode", mc.gameMode.getPlayerMode().getName());
        o.addProperty("selected", p.getInventory().selected);
        JsonArray inv = new JsonArray();
        for (int i = 0; i < 36; i++) inv.add(stackJson(p.getInventory().items.get(i)));
        o.add("inventory", inv);
        JsonArray armor = new JsonArray();
        for (ItemStack s : p.getInventory().armor) armor.add(stackJson(s));
        o.add("armorItems", armor);
        o.add("offhand", stackJson(p.getOffhandItem()));
        var s = Bot.I.stats;
        JsonObject st = new JsonObject();
        st.addProperty("mined", s.blocksMined);
        st.addProperty("distance", (int) s.distance);
        st.addProperty("kills", s.kills);
        st.addProperty("deaths", s.deaths);
        st.addProperty("tasks", s.tasksDone);
        st.addProperty("seconds", s.seconds());
        st.addProperty("speed", Math.round(s.speed * 10) / 10.0);
        JsonObject byType = new JsonObject();
        s.minedCopy().forEach(byType::addProperty);
        st.add("minedByType", byType);
        o.add("stats", st);
        Path path = Bot.I.renderPath;
        if (path != null) {
            JsonArray pa = new JsonArray();
            for (int i = Math.max(0, Bot.I.renderIdx - 1); i < path.steps.size() && pa.size() < 400; i++) {
                BlockPos b = path.steps.get(i).to();
                JsonArray c = new JsonArray();
                c.add(b.getX());
                c.add(b.getY());
                c.add(b.getZ());
                pa.add(c);
            }
            o.add("path", pa);
        }
        return o;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static JsonObject brain() {
        JsonObject o = new JsonObject();
        o.addProperty("counter", fr.nathvavan.nathbot.util.Brain.counter());
        JsonArray th = new JsonArray();
        for (var t : fr.nathvavan.nathbot.util.Brain.all()) {
            JsonObject x = new JsonObject();
            x.addProperty("t", t.time());
            x.addProperty("k", t.kind());
            x.addProperty("m", t.text());
            th.add(x);
        }
        o.add("thoughts", th);
        JsonArray chain = new JsonArray();
        if (Bot.I.task() != null) for (String part : Bot.I.task().describe().split(" > ")) chain.add(part);
        o.add("chain", chain);
        o.addProperty("paused", Bot.I.paused);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            o.addProperty("free", Inv.freeSlots());
            o.addProperty("sorting", fr.nathvavan.nathbot.util.InvManager.busy());
            o.addProperty("recipes", fr.nathvavan.nathbot.util.RecipeBook.recipeCount());
            o.addProperty("dropIndex", fr.nathvavan.nathbot.util.DropIndex.progressText());
            o.addProperty("air", mc.player.getAirSupply());
            o.addProperty("inWater", mc.player.isInWater());
            o.addProperty("boat", mc.player.getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat);
        }
        return o;
    }

    private static String norm(String s) {
        return java.text.Normalizer.normalize(s.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static JsonArray items(String q) {
        JsonArray a = new JsonArray();
        String k = norm(q.trim());
        if (k.length() < 2) return a;
        java.util.List<Item> exact = new java.util.ArrayList<>(), other = new java.util.ArrayList<>();
        for (Item i : BuiltInRegistries.ITEM) {
            if (i == net.minecraft.world.item.Items.AIR) continue;
            String id = BuiltInRegistries.ITEM.getKey(i).toString();
            String name;
            try { name = norm(i.getDescription().getString()); } catch (Throwable t) { name = ""; }
            if (name.startsWith(k) || id.contains(":" + k)) exact.add(i);
            else if (name.contains(k) || id.contains(k)) other.add(i);
            if (exact.size() >= 40) break;
        }
        exact.addAll(other);
        for (Item i : exact) {
            if (a.size() >= 40) break;
            JsonObject o = new JsonObject();
            o.addProperty("id", BuiltInRegistries.ITEM.getKey(i).toString());
            o.addProperty("name", i.getDescription().getString());
            o.addProperty("have", Inv.count(i));
            a.add(o);
        }
        return a;
    }

    private static JsonObject plan(String id, int n) {
        JsonObject o = new JsonObject();
        Item it = Commands.parseItem(id);
        if (it == null) {
            o.addProperty("error", "Objet inconnu : " + id);
            return o;
        }
        o.addProperty("id", BuiltInRegistries.ITEM.getKey(it).toString());
        o.addProperty("name", it.getDescription().getString());
        o.addProperty("have", Inv.count(it));
        fr.nathvavan.nathbot.task.Planner pl = new fr.nathvavan.nathbot.task.Planner();
        o.addProperty("possible", pl.plan(it).ok());
        JsonArray lines = new JsonArray();
        for (String l : pl.explain(it, n)) lines.add(l);
        o.add("lines", lines);
        return o;
    }

    private static JsonObject stackJson(ItemStack s) {
        JsonObject o = new JsonObject();
        if (s.isEmpty()) return o;
        o.addProperty("rid", BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
        o.addProperty("id", BuiltInRegistries.ITEM.getKey(s.getItem()).getPath());
        o.addProperty("name", s.getHoverName().getString());
        o.addProperty("count", s.getCount());
        if (s.isDamageableItem()) o.addProperty("dur", (s.getMaxDamage() - s.getDamageValue()) / (double) s.getMaxDamage());
        return o;
    }

    private static JsonArray features() {
        JsonArray a = new JsonArray();
        for (Feature f : Features.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", f.id);
            o.addProperty("name", f.name);
            o.addProperty("desc", f.description);
            o.addProperty("cat", f.category);
            o.addProperty("on", f.enabled());
            a.add(o);
        }
        return a;
    }

    private static JsonArray schematics() {
        JsonArray a = new JsonArray();
        for (Schematic s : Schematics.all().values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", s.id);
            o.addProperty("name", s.name);
            o.addProperty("desc", s.description);
            o.addProperty("cat", s.category);
            int[] sz = s.size();
            o.addProperty("size", sz[0] + "x" + sz[1] + "x" + sz[2]);
            o.addProperty("blocks", s.solidCount());
            JsonArray mats = new JsonArray();
            for (Map.Entry<Item, Integer> e : s.materials().entrySet()) {
                JsonObject m = new JsonObject();
                m.addProperty("name", e.getKey().getDescription().getString());
                m.addProperty("need", e.getValue());
                m.addProperty("have", Minecraft.getInstance().player == null ? 0 : Inv.count(e.getKey()));
                mats.add(m);
            }
            o.add("materials", mats);
            a.add(o);
        }
        return a;
    }

    private static JsonObject schematic(String id) {
        Schematic s = Schematics.get(id);
        JsonObject o = new JsonObject();
        if (s == null) return o;
        JsonArray blocks = new JsonArray();
        var level = Minecraft.getInstance().level;
        int n = 0;
        for (Map.Entry<BlockPos, BlockState> e : s.blocks.entrySet()) {
            BlockState st = e.getValue();
            if (st.isAir()) continue;
            if (++n > 20000) break;
            int col;
            try {
                MapColor mc = st.getMapColor(level, BlockPos.ZERO);
                col = mc == MapColor.NONE ? 0x9a9a9a : mc.col;
            } catch (Throwable t) {
                col = 0x9a9a9a;
            }
            JsonArray b = new JsonArray();
            b.add(e.getKey().getX());
            b.add(e.getKey().getY());
            b.add(e.getKey().getZ());
            b.add(col);
            blocks.add(b);
        }
        o.add("blocks", blocks);
        o.addProperty("name", s.name);
        return o;
    }

    private static JsonArray waypoints() {
        JsonArray a = new JsonArray();
        for (Waypoints.Wp w : Waypoints.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", w.name);
            o.addProperty("x", w.x);
            o.addProperty("y", w.y);
            o.addProperty("z", w.z);
            o.addProperty("dim", w.dim.replace("minecraft:", ""));
            o.addProperty("active", w.name.equals(Waypoints.active));
            a.add(o);
        }
        return a;
    }

    private static JsonObject map(int r) {
        Minecraft mc = Minecraft.getInstance();
        JsonObject o = new JsonObject();
        if (mc.player == null || mc.level == null) return o;
        BlockPos c = mc.player.blockPosition();
        int size = r * 2 + 1;
        JsonArray rows = new JsonArray();
        boolean nether = mc.level.dimensionType().hasCeiling();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dz = -r; dz <= r; dz++) {
            StringBuilder sb = new StringBuilder(size * 6);
            for (int dx = -r; dx <= r; dx++) {
                int x = c.getX() + dx, z = c.getZ() + dz;
                int col = 0x000000;
                if (mc.level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null) {
                    int y;
                    if (nether) {
                        y = c.getY() + 2;
                        while (y > c.getY() - 20 && mc.level.getBlockState(m.set(x, y, z)).isAir()) y--;
                        y++;
                    } else y = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    BlockState st = mc.level.getBlockState(m.set(x, y - 1, z));
                    MapColor mcol = st.getMapColor(mc.level, m);
                    col = mcol == MapColor.NONE ? 0x202020 : mcol.col;
                    // ombrage selon la hauteur
                    int hN = nether ? y : mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1);
                    double f = y > hN ? 1.12 : y < hN ? 0.85 : 1.0;
                    int rr = (int) Math.min(255, ((col >> 16) & 255) * f), gg = (int) Math.min(255, ((col >> 8) & 255) * f), bb = (int) Math.min(255, (col & 255) * f);
                    col = (rr << 16) | (gg << 8) | bb;
                }
                sb.append(String.format("%06x", col));
            }
            rows.add(sb.toString());
        }
        o.add("rows", rows);
        o.addProperty("r", r);
        o.addProperty("cx", c.getX());
        o.addProperty("cz", c.getZ());
        JsonArray ents = new JsonArray();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || Math.abs(e.getX() - c.getX()) > r || Math.abs(e.getZ() - c.getZ()) > r) continue;
            String kind = e instanceof Player ? "player" : e instanceof Enemy ? "hostile" : e instanceof net.minecraft.world.entity.animal.Animal ? "animal" : null;
            if (kind == null) continue;
            JsonObject eo = new JsonObject();
            eo.addProperty("x", e.getX());
            eo.addProperty("z", e.getZ());
            eo.addProperty("k", kind);
            eo.addProperty("n", e.getName().getString());
            ents.add(eo);
        }
        o.add("entities", ents);
        return o;
    }

    private static JsonObject settings() {
        JsonObject o = new JsonObject();
        o.addProperty("casser", BotConfig.get(BotConfig.ALLOW_BREAK, true));
        o.addProperty("poser", BotConfig.get(BotConfig.ALLOW_PLACE, true));
        o.addProperty("sprint", BotConfig.get(BotConfig.ALLOW_SPRINT, true));
        o.addProperty("creuser_bas", BotConfig.get(BotConfig.ALLOW_DIG_DOWN, true));
        o.addProperty("legit", BotConfig.get(BotConfig.LEGIT_MINING, false));
        o.addProperty("commandes", BotConfig.get(BotConfig.BUILD_WITH_COMMANDS, false));
        o.addProperty("recolte_auto", BotConfig.get(BotConfig.AUTO_GATHER_MATERIALS, true));
        o.addProperty("rotation_douce", BotConfig.get(BotConfig.SMOOTH_ROTATION, true));
        o.addProperty("chute", BotConfig.get(BotConfig.MAX_FALL, 3));
        o.addProperty("manger_sous", BotConfig.get(BotConfig.EAT_BELOW_FOOD, 14));
        o.addProperty("panique", BotConfig.get(BotConfig.PANIC_HEALTH, 5));
        o.addProperty("port", BotConfig.get(BotConfig.WEB_PORT, 8765));
        return o;
    }

    static List<String> unused() { return List.of(); }
}
