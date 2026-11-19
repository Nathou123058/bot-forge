package fr.nathvavan.nathbot.dev;

import fr.nathvavan.nathbot.bot.Bot;
import fr.nathvavan.nathbot.command.Commands;
import fr.nathvavan.nathbot.util.BotLog;
import net.minecraft.client.Minecraft;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Scripts JavaScript en direct : chaque fichier .js de .minecraft/nathbot/scripts est charge
 * au demarrage et RECHARGE AUTOMATIQUEMENT des qu'il est modifie, sans fermer Minecraft.
 *
 * Anti-lag : chaque appel de script est limite a 50 ms ; au-dela il est coupe net.
 */
public final class Scripts {
    private Scripts() {}

    record Handler(String file, String event, Scriptable scope, Function fn, int every) {}

    record Cmd(String file, String name, String desc, Scriptable scope, Function fn) {}

    public record Info(String file, boolean ok, String error, int handlers, int commands) {}

    static final ConcurrentLinkedDeque<String> QUEUE = new ConcurrentLinkedDeque<>();
    private static final List<Handler> HANDLERS = new ArrayList<>();
    private static final Map<String, Cmd> COMMANDS = new LinkedHashMap<>();
    private static final Map<String, Long> LOADED = new HashMap<>();
    private static final Map<String, String> ERRORS = new LinkedHashMap<>();
    private static final Map<String, Scriptable> SCOPES = new HashMap<>();
    private static final Map<Function, Integer> FAILS = new HashMap<>();

    private static long deadline = Long.MAX_VALUE;
    private static boolean started = false;
    private static int tickCount = 0;
    private static int lastQueueStart = -100;
    private static ScriptableObject shared;
    private static Scriptable consoleScope;

    private static final ContextFactory FACTORY = new ContextFactory() {
        @Override
        protected Context makeContext() {
            Context cx = super.makeContext();
            cx.setOptimizationLevel(-1); // interprete : necessaire pour le controle du temps
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setInstructionObserverThreshold(2000);
            return cx;
        }

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            if (System.nanoTime() > deadline) throw new Error("script trop long, arrete pour ne pas faire ramer le jeu");
        }
    };

    public static File dir() {
        File d = new File(DevMode.dir(), "scripts");
        d.mkdirs();
        return d;
    }

    private static Context enter(long budgetMs) {
        deadline = System.nanoTime() + budgetMs * 1_000_000L;
        return FACTORY.enterContext();
    }

    private static void exit() {
        deadline = Long.MAX_VALUE;
        Context.exit();
    }

    // ---------------------------------------------------------------- chargement

    private static void init() {
        started = true;
        File d = dir();
        File ex = new File(d, "exemple.js");
        if (!ex.exists() && Objects.requireNonNull(d.list()).length == 0) {
            try {
                Files.writeString(ex.toPath(), """
                        // Exemple de script NathBot : recharge tout seul des que tu sauvegardes ce fichier.
                        // En jeu, tape : /nb bonjour
                        // Toute l'API est decrite dans nathbot/NATHBOT-DEV.md (dossier de l'instance).
                        bot.command("bonjour", "Dit bonjour et donne la position", function (args) {
                          bot.say("Bonjour ! Je suis en " + Math.round(bot.x()) + " " + Math.round(bot.y()) + " " + Math.round(bot.z())
                            + ", vie " + bot.health() + ", " + bot.free() + " cases libres.");
                        });
                        """, StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }
        Context cx = enter(2000);
        try {
            shared = cx.initStandardObjects();
            consoleScope = newScope(cx, "console");
        } finally {
            exit();
        }
        reloadAll();
    }

    private static Scriptable newScope(Context cx, String file) {
        Scriptable scope = cx.newObject(shared);
        scope.setPrototype(shared);
        scope.setParentScope(null);
        ScriptableObject.putProperty(scope, "bot", Context.javaToJS(new ScriptApi(file), scope));
        return scope;
    }

    private static File[] files() {
        File[] f = dir().listFiles((d, n) -> n.endsWith(".js"));
        if (f == null) return new File[0];
        Arrays.sort(f);
        return f;
    }

    /** Recharge tous les scripts (commande "scripts recharger" ou API dev). */
    public static synchronized int reloadAll() {
        if (!started) { init(); return LOADED.size(); }
        for (String f : new ArrayList<>(LOADED.keySet())) unload(f);
        ERRORS.clear();
        for (File f : files()) load(f);
        return LOADED.size();
    }

    private static void unload(String file) {
        HANDLERS.removeIf(h -> h.file.equals(file));
        COMMANDS.values().removeIf(c -> c.file.equals(file));
        LOADED.remove(file);
        SCOPES.remove(file);
        ERRORS.remove(file);
    }

    private static void load(File f) {
        String name = f.getName();
        unload(name);
        LOADED.put(name, f.lastModified());
        String code;
        try {
            code = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ERRORS.put(name, "lecture impossible : " + e.getMessage());
            return;
        }
        Context cx = enter(500);
        try {
            Scriptable scope = newScope(cx, name);
            SCOPES.put(name, scope);
            cx.evaluateString(scope, code, name, 1, null);
        } catch (Throwable t) {
            ERRORS.put(name, describe(t));
            BotLog.error("Script " + name + " : " + describe(t));
        } finally {
            exit();
        }
    }

    private static String describe(Throwable t) {
        if (t instanceof RhinoException re) return "ligne " + re.lineNumber() + " : " + re.details();
        return t.getClass().getSimpleName() + " : " + t.getMessage();
    }

    /** Surveille le dossier et recharge les fichiers modifies. */
    private static void watch() {
        Set<String> seen = new HashSet<>();
        for (File f : files()) {
            seen.add(f.getName());
            Long t = LOADED.get(f.getName());
            if (t == null || t != f.lastModified()) {
                boolean isNew = t == null;
                load(f);
                if (!ERRORS.containsKey(f.getName())) BotLog.ok("Script " + f.getName() + (isNew ? " charge." : " recharge."));
            }
        }
        for (String old : new ArrayList<>(LOADED.keySet())) {
            if (!seen.contains(old)) {
                unload(old);
                BotLog.info("Script " + old + " retire.");
            }
        }
    }

    // ---------------------------------------------------------------- enregistrement (appele par les scripts)

    static synchronized void addHandler(String file, String event, Function fn, int every) {
        Scriptable scope = SCOPES.getOrDefault(file, consoleScope);
        HANDLERS.add(new Handler(file, event.toLowerCase(Locale.ROOT), scope, fn, every));
    }

    static synchronized void addCommand(String file, String name, String desc, Function fn) {
        Scriptable scope = SCOPES.getOrDefault(file, consoleScope);
        COMMANDS.put(name.toLowerCase(Locale.ROOT), new Cmd(file, name, desc, scope, fn));
    }

    // ---------------------------------------------------------------- execution

    private static boolean call(String file, Scriptable scope, Function fn, Object[] args, long budgetMs) {
        Context cx = enter(budgetMs);
        try {
            Object[] jsArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) jsArgs[i] = args[i] instanceof String[] a ? cx.newArray(scope, (Object[]) a) : Context.javaToJS(args[i], scope);
            fn.call(cx, scope, scope, jsArgs);
            return true;
        } catch (Throwable t) {
            int n = FAILS.merge(fn, 1, Integer::sum);
            ERRORS.put(file, describe(t));
            if (n <= 3) BotLog.error("Script " + file + " : " + describe(t));
            if (n == 3) BotLog.warn("Script " + file + " : trop d'erreurs, cette fonction est coupee jusqu'au prochain rechargement.");
            return false;
        } finally {
            exit();
        }
    }

    /** Chaque tick client. */
    public static synchronized void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!started) init();
        tickCount++;
        if (tickCount % 20 == 0) watch();
        if (mc.player == null || mc.level == null || DevMode.locked()) return;
        for (Handler h : new ArrayList<>(HANDLERS)) {
            if (!h.event.equals("tick") || tickCount % h.every != 0) continue;
            if (FAILS.getOrDefault(h.fn, 0) >= 3) continue;
            call(h.file, h.scope, h.fn, new Object[0], 50);
        }
        // file de commandes : une a la fois, quand le bot est libre
        if (!QUEUE.isEmpty() && Bot.I.task() == null && !Bot.I.paused && tickCount - lastQueueStart > 10) {
            lastQueueStart = tickCount;
            String c = QUEUE.poll();
            BotLog.info("File de commandes : " + c + (QUEUE.isEmpty() ? "" : " (" + QUEUE.size() + " apres)"));
            Commands.execute(c);
        }
    }

    public static synchronized void onChat(String text) {
        if (!started || DevMode.locked()) return;
        for (Handler h : new ArrayList<>(HANDLERS)) {
            if (!h.event.equals("chat") || FAILS.getOrDefault(h.fn, 0) >= 3) continue;
            call(h.file, h.scope, h.fn, new Object[]{text}, 50);
        }
    }

    /** Commande ajoutee par un script ? Renvoie true si elle a ete executee. */
    public static synchronized boolean runCommand(String name, String[] args) {
        Cmd c = COMMANDS.get(name.toLowerCase(Locale.ROOT));
        if (c == null) return false;
        call(c.file, c.scope, c.fn, new Object[]{args}, 200);
        return true;
    }

    /** Execute du code JavaScript (console de developpement). Renvoie le resultat en texte. */
    public static synchronized String eval(String code) {
        if (!started) init();
        Context cx = enter(2000);
        try {
            Object r = cx.evaluateString(consoleScope, code, "console", 1, null);
            if (r instanceof Undefined) return "undefined";
            if (r instanceof Wrapper w) r = w.unwrap();
            return Context.toString(r);
        } catch (Throwable t) {
            return "Erreur : " + describe(t);
        } finally {
            exit();
        }
    }

    public static synchronized List<Info> info() {
        List<Info> l = new ArrayList<>();
        for (File f : files()) {
            String n = f.getName();
            int h = 0, c = 0;
            for (Handler x : HANDLERS) if (x.file.equals(n)) h++;
            for (Cmd x : COMMANDS.values()) if (x.file.equals(n)) c++;
            l.add(new Info(n, !ERRORS.containsKey(n), ERRORS.getOrDefault(n, ""), h, c));
        }
        return l;
    }

    public static synchronized List<String[]> commands() {
        List<String[]> l = new ArrayList<>();
        for (Cmd c : COMMANDS.values()) l.add(new String[]{c.name, c.desc, c.file});
        return l;
    }

    public static int queueSize() { return QUEUE.size(); }
}
