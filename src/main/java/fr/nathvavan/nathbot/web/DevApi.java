package fr.nathvavan.nathbot.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.command.Commands;
import fr.nathvavan.nathbot.dev.DevConfig;
import fr.nathvavan.nathbot.dev.DevMode;
import fr.nathvavan.nathbot.dev.Scripts;
import fr.nathvavan.nathbot.util.BotLog;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * API de developpement en direct (pour Claude Code ou toi), jeu ouvert :
 *   GET  /api/dev/state                 etat complet (verrou, scripts, reglages, tache)
 *   POST /api/dev/lock?minutes=20       met le bot en pause, impossible a relancer (corps = raison)
 *   POST /api/dev/unlock                libere le bot : "Le bot est repret !" dans le chat (corps = message)
 *   POST /api/dev/reload                recharge scripts, constructions, recettes
 *   POST /api/dev/eval                  execute du JavaScript (corps = code), renvoie le resultat
 *   POST /api/dev/cmd                   execute une commande NathBot (corps = commande)
 *   POST /api/dev/config?key=..&value=  change un reglage et l'enregistre
 * Toutes exigent l'en-tete X-NathBot-Dev (contenu de .minecraft/nathbot/dev-token.txt).
 */
final class DevApi {
    private DevApi() {}

    private static <T> T onGame(Supplier<T> s, int seconds) throws Exception {
        return Minecraft.getInstance().submit(s).get(seconds, TimeUnit.SECONDS);
    }

    private static Api.Response json(Object o) { return Api.Response.json(o); }

    static Api.Response route(String method, String path, Map<String, String> q, String body) throws Exception {
        String given = q.getOrDefault("__dev", "");
        if (given.isEmpty()) given = q.getOrDefault("token", "");
        if (!DevMode.token().equals(given)) return Api.Response.text(401, "Jeton dev manquant ou faux (voir .minecraft/nathbot/dev-token.txt)");
        boolean post = method.equals("POST");
        switch (path) {
            case "/api/dev/state" -> { return json(onGame(DevApi::state, 3)); }
            case "/api/dev/lock" -> {
                if (!post) return Api.Response.text(405, "POST");
                int min = parse(q.getOrDefault("minutes", "20"), 20);
                return json(onGame(() -> { DevMode.lock(body, min); return state(); }, 3));
            }
            case "/api/dev/unlock" -> {
                if (!post) return Api.Response.text(405, "POST");
                return json(onGame(() -> { DevMode.unlock(body); return state(); }, 3));
            }
            case "/api/dev/reload" -> {
                if (!post) return Api.Response.text(405, "POST");
                return json(onGame(() -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("scripts", Scripts.reloadAll());
                    try { o.addProperty("schematics", fr.nathvavan.nathbot.build.SchematicIO.reload()); } catch (Throwable ignored) {}
                    fr.nathvavan.nathbot.util.RecipeBook.invalidate();
                    o.add("state", state());
                    return o;
                }, 10));
            }
            case "/api/dev/eval" -> {
                if (!post) return Api.Response.text(405, "POST");
                return Api.Response.text(200, onGame(() -> Scripts.eval(body), 5));
            }
            case "/api/dev/cmd" -> {
                if (!post) return Api.Response.text(405, "POST");
                String c = body.trim();
                long before = BotLog.counter();
                onGame(() -> {
                    DevMode.bypass = true;
                    try { Commands.execute(c); } finally { DevMode.bypass = false; }
                    return true;
                }, 5);
                JsonObject o = new JsonObject();
                JsonArray out = new JsonArray();
                List<String> lines = BotLog.lines();
                long added = BotLog.counter() - before;
                for (int i = (int) Math.max(0, lines.size() - added); i < lines.size(); i++) out.add(lines.get(i));
                o.add("log", out);
                o.addProperty("status", Bot.I.statusText());
                return json(o);
            }
            case "/api/dev/config" -> {
                if (post) {
                    String k = q.getOrDefault("key", ""), v = q.getOrDefault("value", body);
                    return Api.Response.text(200, onGame(() -> DevConfig.set(k, v), 3));
                }
                return json(onGame(DevApi::config, 3));
            }
            default -> { return Api.Response.text(404, "Introuvable"); }
        }
    }

    private static int parse(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static JsonObject config() {
        JsonObject o = new JsonObject();
        for (var e : DevConfig.all().entrySet()) o.add(e.getKey(), new JsonPrimitive(String.valueOf(e.getValue())));
        return o;
    }

    private static JsonObject state() {
        JsonObject o = new JsonObject();
        o.addProperty("locked", DevMode.locked());
        o.addProperty("reason", DevMode.reason());
        o.addProperty("secondsLeft", DevMode.secondsLeft());
        o.addProperty("status", Bot.I.statusText());
        o.addProperty("paused", Bot.I.paused);
        o.addProperty("inGame", Minecraft.getInstance().player != null);
        o.addProperty("gameDir", Minecraft.getInstance().gameDirectory.getAbsolutePath());
        o.addProperty("scriptsDir", Scripts.dir().getAbsolutePath());
        o.addProperty("queue", Scripts.queueSize());
        JsonArray sc = new JsonArray();
        for (var s : Scripts.info()) {
            JsonObject x = new JsonObject();
            x.addProperty("file", s.file());
            x.addProperty("ok", s.ok());
            x.addProperty("error", s.error());
            x.addProperty("handlers", s.handlers());
            x.addProperty("commands", s.commands());
            sc.add(x);
        }
        o.add("scripts", sc);
        JsonArray cmds = new JsonArray();
        for (String[] c : Scripts.commands()) cmds.add(c[0]);
        o.add("scriptCommands", cmds);
        o.add("config", config());
        return o;
    }

    /** Fichiers statiques de .minecraft/nathbot/web (images, js, css...) modifiables en direct. */
    static Api.Response webFile(String name) throws Exception {
        if (name.contains("..") || name.contains("\\") || name.startsWith("/")) return Api.Response.text(400, "Nom invalide");
        File f = new File(new File(DevMode.dir(), "web"), name);
        if (!f.isFile()) return Api.Response.text(404, "Introuvable");
        String n = name.toLowerCase(Locale.ROOT);
        String type = n.endsWith(".html") ? "text/html; charset=utf-8" : n.endsWith(".js") ? "text/javascript; charset=utf-8"
                : n.endsWith(".css") ? "text/css; charset=utf-8" : n.endsWith(".json") ? "application/json; charset=utf-8"
                : n.endsWith(".png") ? "image/png" : n.endsWith(".svg") ? "image/svg+xml" : "application/octet-stream";
        return new Api.Response(200, type, Files.readAllBytes(f.toPath()));
    }

    @SuppressWarnings("unused")
    private static byte[] utf8(String s) { return s.getBytes(StandardCharsets.UTF_8); }
}
