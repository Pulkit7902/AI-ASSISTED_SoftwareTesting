package pipeline;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** LLM adapter. The only implementation talks to the Anthropic Messages API over plain HTTPS. */
interface Llm {
    record Completion(String text, int inTokens, int outTokens, double seconds) {}

    Completion complete(String prompt) throws Exception;

    String resolvedModel();

    final class Anthropic implements Llm {
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        private final String apiKey, model;
        private final int maxTokens;
        private final Double temperature;
        private String resolved;

        Anthropic(String model, int maxTokens, Double temperature) {
            this.apiKey = System.getenv("ANTHROPIC_API_KEY"); // never written to disk
            if (apiKey == null || apiKey.isBlank()) throw new Fatal("set ANTHROPIC_API_KEY");
            this.model = model; this.maxTokens = maxTokens; this.temperature = temperature;
            this.resolved = model;
        }

        @Override public String resolvedModel() { return resolved; }

        @Override
        public Completion complete(String prompt) throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            if (temperature != null) body.put("temperature", temperature);
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                    .timeout(Duration.ofMinutes(5))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8)).build();
            for (int attempt = 0; ; attempt++) {
                try {
                    long t0 = System.nanoTime();
                    HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (r.statusCode() / 100 != 2)
                        throw new java.io.IOException("HTTP " + r.statusCode() + ": " + Env.tail(r.body(), 300));
                    Map<String, Object> j = Json.obj(Json.parse(r.body()));
                    StringBuilder text = new StringBuilder();
                    for (Object o : Json.list(j.get("content"))) {
                        Map<String, Object> blk = Json.obj(o);
                        if ("text".equals(blk.get("type"))) text.append((String) blk.get("text"));
                    }
                    if (j.get("model") instanceof String m) resolved = m;
                    Map<String, Object> u = Json.obj(j.get("usage"));
                    return new Completion(text.toString(), ((Number) u.get("input_tokens")).intValue(),
                            ((Number) u.get("output_tokens")).intValue(), (System.nanoTime() - t0) / 1e9);
                } catch (Exception e) { // rate limits, overload, transient network
                    if (attempt == 3) throw e;
                    System.err.println("   retry after error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    Thread.sleep((long) Math.pow(2, attempt) * 5000L);
                }
            }
        }
    }
}
