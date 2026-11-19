package fr.nathvavan.nathbot.web;

import fr.nathvavan.nathbot.BotConfig;
import fr.nathvavan.nathbot.NathBot;
import fr.nathvavan.nathbot.util.BotLog;
import net.minecraft.Util;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mini serveur HTTP local (127.0.0.1 uniquement) qui sert le panneau web et une API JSON.
 * Ecrit a la main, sans dependance externe.
 */
public final class WebServer {
    private WebServer() {}

    private static ServerSocket server;
    private static Thread acceptThread;
    private static ExecutorService pool;
    private static int port;

    public static synchronized void start() {
        if (server != null) return;
        if (!BotConfig.get(BotConfig.WEB_ENABLED, true)) return;
        port = BotConfig.get(BotConfig.WEB_PORT, 8765);
        try {
            server = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            NathBot.LOG.warn("Impossible d'ouvrir le port {} pour le panneau web : {}", port, e.getMessage());
            server = null;
            return;
        }
        pool = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "NathBot-Web");
            t.setDaemon(true);
            return t;
        });
        acceptThread = new Thread(() -> {
            while (server != null && !server.isClosed()) {
                try {
                    Socket s = server.accept();
                    s.setSoTimeout(10000);
                    pool.submit(() -> handle(s));
                } catch (IOException ignored) {}
            }
        }, "NathBot-WebAccept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        NathBot.LOG.info("Panneau web NathBot : http://localhost:{}", port);
    }

    public static synchronized void stop() {
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {}
        server = null;
        if (pool != null) pool.shutdownNow();
        pool = null;
    }

    public static void restart() {
        stop();
        start();
    }

    public static String url() {
        return "http://localhost:" + BotConfig.get(BotConfig.WEB_PORT, 8765) + "/";
    }

    public static boolean running() { return server != null; }

    public static void openInBrowser() {
        if (!running()) start();
        if (!running()) {
            BotLog.error("Le panneau web n'a pas pu demarrer (port " + BotConfig.get(BotConfig.WEB_PORT, 8765) + " occupe ?).");
            return;
        }
        Util.getPlatform().openUri(url());
        BotLog.info("Panneau web : " + url());
    }

    // ------------------------------------------------------------------ HTTP

    private static void handle(Socket s) {
        try (s; InputStream in = new BufferedInputStream(s.getInputStream()); OutputStream out = s.getOutputStream()) {
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String target = parts[1];
            Map<String, String> headers = new HashMap<>();
            String h;
            while ((h = readLine(in)) != null && !h.isEmpty()) {
                int i = h.indexOf(':');
                if (i > 0) headers.put(h.substring(0, i).trim().toLowerCase(Locale.ROOT), h.substring(i + 1).trim());
            }
            // protection : uniquement localhost (anti DNS-rebinding)
            String host = headers.getOrDefault("host", "");
            String hostName = host.contains(":") ? host.substring(0, host.lastIndexOf(':')) : host;
            if (!hostName.equals("localhost") && !hostName.equals("127.0.0.1") && !hostName.equals("[::1]")) {
                send(out, 403, "text/plain", "Forbidden".getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] body = new byte[0];
            int len = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            if (len > 0 && len < 1_000_000) {
                body = in.readNBytes(len);
            }
            String path = target;
            Map<String, String> query = new HashMap<>();
            int q = target.indexOf('?');
            if (q >= 0) {
                path = target.substring(0, q);
                for (String kv : target.substring(q + 1).split("&")) {
                    int e = kv.indexOf('=');
                    if (e > 0) query.put(URLDecoder.decode(kv.substring(0, e), StandardCharsets.UTF_8), URLDecoder.decode(kv.substring(e + 1), StandardCharsets.UTF_8));
                }
            }
            if (method.equals("POST") && !"1".equals(headers.get("x-nathbot"))) {
                // une page web etrangere ne peut pas ajouter cet en-tete sans autorisation CORS
                send(out, 403, "text/plain", "Missing header".getBytes(StandardCharsets.UTF_8));
                return;
            }
            query.put("__dev", headers.getOrDefault("x-nathbot-dev", ""));
            Api.Response r = Api.route(method, path, query, new String(body, StandardCharsets.UTF_8));
            send(out, r.code(), r.type(), r.body());
        } catch (Exception e) {
            NathBot.LOG.debug("Erreur web", e);
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) break;
        }
        if (c == -1 && sb.length() == 0) return null;
        return sb.toString();
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws IOException {
        String status = switch (code) {
            case 200 -> "OK";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            default -> "Error";
        };
        String head = "HTTP/1.1 " + code + " " + status + "\r\n" +
                "Content-Type: " + type + "\r\n" +
                "Content-Length: " + body.length + "\r\n" +
                "Cache-Control: no-store\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }
}
