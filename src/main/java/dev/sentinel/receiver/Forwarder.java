package dev.sentinel.receiver;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Wraps a verified webhook body in an envelope and posts it to the triage service. */
public final class Forwarder {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final URI triageUrl;

    public Forwarder(String triageBaseUrl) {
        this.triageUrl = URI.create(triageBaseUrl.replaceAll("/+$", "") + "/triage");
    }

    static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** Builds the envelope. The body must already be a JSON object (checked by the caller). */
    static String envelope(String eventType, String deliveryId, String rawJsonBody) {
        return "{\"source\":\"github\",\"event_type\":\"" + jsonEscape(eventType)
                + "\",\"delivery_id\":\"" + jsonEscape(deliveryId)
                + "\",\"payload\":" + rawJsonBody + "}";
    }

    public boolean forward(String eventType, String deliveryId, byte[] body) {
        String json = envelope(eventType, deliveryId, new String(body, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(triageUrl)
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() / 100 == 2;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
