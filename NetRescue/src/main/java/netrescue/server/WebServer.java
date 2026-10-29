package netrescue.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import netrescue.ds.HashMap;
import netrescue.io.Json;

/**
 * Tiny HTTP server built on the JDK's own com.sun.net.httpserver (no libraries).
 * Serves the web page from /web inside the jar, and answers /api/... calls by
 * handing them to Api. It listens on 127.0.0.1 only, so nothing outside this
 * computer can reach it.
 */
public class WebServer {
    private final HttpServer http;
    private final Api api;

    public WebServer(int port, Api api) throws IOException {
        this.api = api;
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        // worker threads get a large stack so deep recursion (Tarjan's DFS on big networks) is safe
        ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(null, r, "netrescue-http", 256L * 1024 * 1024);
            t.setDaemon(true);
            return t;
        });
        http.setExecutor(pool);
        http.createContext("/", this::handle);
    }

    public void start() { http.start(); }
    public void stop() { http.stop(0); }
    public int port() { return http.getAddress().getPort(); }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                HashMap<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
                String body = "";
                if (ex.getRequestMethod().equalsIgnoreCase("POST")) {
                    try (InputStream in = ex.getRequestBody()) { body = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
                }
                Api.Response r;
                try {
                    r = api.handle(ex.getRequestMethod().toUpperCase(), path, q, body);
                } catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException e) {
                    r = Api.Response.error(400, e.getMessage());
                } catch (RuntimeException | StackOverflowError e) {
                    e.printStackTrace();
                    r = Api.Response.error(500, "internal error: " + e);
                }
                send(ex, r.status, r.contentType, r.body.getBytes(StandardCharsets.UTF_8), r.downloadName);
            } else {
                serveStatic(ex, path);
            }
        } finally {
            ex.close();
        }
    }

    private void serveStatic(HttpExchange ex, String path) throws IOException {
        if (path.equals("/")) path = "/index.html";
        if (path.contains("..")) { send(ex, 400, "text/plain", "bad path".getBytes(StandardCharsets.UTF_8), null); return; }
        try (InputStream in = WebServer.class.getResourceAsStream("/web" + path)) {
            if (in == null) { send(ex, 404, "text/plain", ("not found: " + path).getBytes(StandardCharsets.UTF_8), null); return; }
            send(ex, 200, contentType(path), in.readAllBytes(), null);
        }
    }

    private static String contentType(String p) {
        if (p.endsWith(".html")) return "text/html; charset=utf-8";
        if (p.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (p.endsWith(".css")) return "text/css; charset=utf-8";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    private static void send(HttpExchange ex, int status, String type, byte[] body, String downloadName) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        if (downloadName != null) ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + downloadName + "\"");
        ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) try (OutputStream out = ex.getResponseBody()) { out.write(body); }
    }

    static HashMap<String, String> parseQuery(String raw) {
        HashMap<String, String> m = new HashMap<>();
        if (raw == null || raw.isEmpty()) return m;
        for (String part : raw.split("&")) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            String k = eq < 0 ? part : part.substring(0, eq);
            String v = eq < 0 ? "" : part.substring(eq + 1);
            m.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return m;
    }

    static String errorJson(String msg) { return new Json().obj().put("error", msg == null ? "error" : msg).end().toString(); }
}
