package dev.example.avatar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class InfraiClient {
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private final String baseUrl;
  private final String key;

  public InfraiClient(String baseUrl, String key) {
    this.baseUrl = baseUrl;
    this.key = key;
  }

  public JsonNode send(String method, String path, Map<String, ?> body, String requestId) throws Exception {
    String payload = json.writeValueAsString(body);
    for (int attempt = 0; attempt < 4; attempt++) {
      HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
          .timeout(Duration.ofSeconds(30))
          .header("Authorization", "Bearer " + key)
          .header("Content-Type", "application/json")
          .header("Idempotency-Key", requestId)
          .method(method, HttpRequest.BodyPublishers.ofString(payload)).build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode envelope = json.readTree(response.body());
      if (response.statusCode() == 429 && attempt < 3) {
        long backoff = Math.min(8000, 500L << attempt);
        String retryAfter = response.headers().firstValue("Retry-After").orElse("");
        try { backoff = Math.max(backoff, 1000L * Long.parseLong(retryAfter)); }
        catch (NumberFormatException ignored) { }
        Thread.sleep(backoff);
        continue;
      }
      if (!envelope.path("ok").asBoolean(false)) {
        JsonNode error = envelope.path("error");
        throw new Rejection(response.statusCode(), error.path("code").asText("REQUEST_REJECTED"), error.path("message").asText("Request rejected"));
      }
      if (response.statusCode() >= 500) throw new IllegalStateException("Upstream HTTP " + response.statusCode());
      return envelope.path("data");
    }
    throw new IllegalStateException("Retry budget exhausted");
  }

  public static String segment(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  public static class Rejection extends RuntimeException {
    public final int status;
    public final String code;
    public Rejection(int status, String code, String message) {
      super(message);
      this.status = status;
      this.code = code;
    }
  }
}
