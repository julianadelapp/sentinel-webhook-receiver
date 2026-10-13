package dev.sentinel.receiver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Minimal GitHub-style webhook receiver. Verifies the HMAC signature, then forwards the
 * event to the triage service. Fails closed: it will not start without WEBHOOK_SECRET.
 */
public final class Main {
    private static final int MAX_BODY_BYTES = 1_048_576;

    public static void main(String[] args) throws IOException {
        String secret = System.getenv("WEBHOOK_SECRET");
        if (secret == null || secret.isBlank()) {
            System.err.println("WEBHOOK_SECRET is required");
            System.exit(1);
        }
        String triageUrl = System.getenv().getOrDefault("TRIAGE_URL", "http://localhost:8000");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        Forwarder forwarder = new Forwarder(triageUrl);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/health", ex -> respond(ex, 200, "{\"status\":\"ok\"}"));
        server.createContext("/webhook", ex -> handleWebhook(ex, secret, forwarder));
        server.start();
        System.out.println("sentinel-webhook-receiver listening on :" + port + " -> " + triageUrl);
    }

    private static void handleWebhook(HttpExchange ex, String secret, Forwarder forwarder) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        byte[] body = ex.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            respond(ex, 413, "{\"error\":\"payload too large\"}");
            return;
        }
        String signature = ex.getRequestHeaders().getFirst("X-Hub-Signature-256");
        if (!Signature.verify(secret, body, signature)) {
            respond(ex, 401, "{\"error\":\"invalid signature\"}");
            return;
        }
        String trimmed = new String(body, StandardCharsets.UTF_8).strip();
        if (!trimmed.startsWith("{")) {
            respond(ex, 400, "{\"error\":\"expected a JSON object\"}");
            return;
        }
        String eventType = headerOr(ex, "X-GitHub-Event", "unknown");
        String deliveryId = headerOr(ex, "X-GitHub-Delivery", "");
        boolean ok = forwarder.forward(eventType, deliveryId, body);
        respond(ex, ok ? 202 : 502, ok ? "{\"status\":\"accepted\"}" : "{\"error\":\"triage unavailable\"}");
    }

    private static String headerOr(HttpExchange ex, String name, String fallback) {
        String value = ex.getRequestHeaders().getFirst(name);
        return value == null ? fallback : value;
    }

    private static void respond(HttpExchange ex, int status, String json) throws IOException {
        byte[] out = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, out.length);
        try (var os = ex.getResponseBody()) {
            os.write(out);
        }
    }
}
