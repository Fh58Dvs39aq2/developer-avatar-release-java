package dev.example.avatar;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class AvatarRelease {
  public record ReleaseRequest(String userId, String filename, String imageBase64, String releaseId) { }
  public record ReleaseResult(String releaseId, String userId, String avatar, String state) { }

  interface Gateway { JsonNode send(String method, String path, Map<String, ?> body, String id) throws Exception; }

  private final Gateway gateway;
  private final long maxBytes;

  public AvatarRelease(Gateway gateway, @Value("${avatar.max-bytes}") long maxBytes) {
    this.gateway = gateway;
    this.maxBytes = maxBytes;
  }

  public static void main(String[] args) { SpringApplication.run(AvatarRelease.class, args); }

  @PostMapping("/avatars/releases")
  public ReleaseResult release(@RequestBody ReleaseRequest input) throws Exception {
    if (input.userId() == null || input.userId().isBlank() || input.releaseId() == null || input.releaseId().isBlank()
        || input.filename() == null || input.filename().isBlank() || input.imageBase64() == null)
      throw new IllegalArgumentException("userId, releaseId, filename and imageBase64 are required");
    byte[] bytes;
    try { bytes = Base64.getDecoder().decode(input.imageBase64()); }
    catch (IllegalArgumentException e) { throw new IllegalArgumentException("imageBase64 must be valid base64"); }
    if (bytes.length == 0 || bytes.length > maxBytes) throw new IllegalArgumentException("Avatar exceeds configured upload bounds");
    String id = input.releaseId();
    JsonNode uploaded = gateway.send("POST", "/v1/image/upload", Map.of("file", input.imageBase64(), "filename", input.filename()), id + ":upload");
    JsonNode cropped = gateway.send("POST", "/v1/image/smart_crop", Map.of("image", image(uploaded), "aspect", "1:1", "idempotency_key", id + ":crop"), id + ":crop");
    JsonNode optimized = gateway.send("POST", "/v1/image/resize", Map.of("image", image(cropped), "width", 256, "height", 256, "fit", "cover", "format", "webp", "store", true, "idempotency_key", id + ":resize"), id + ":resize");
    String avatar = image(optimized);
    gateway.send("PATCH", "/v1/auth/user/update/" + InfraiClient.segment(input.userId()), Map.of("user_id", input.userId(), "metadata", Map.of("avatar", avatar), "idempotency_key", id + ":publish"), id + ":publish");
    return new ReleaseResult(id, input.userId(), avatar, "published");
  }

  private static String image(JsonNode data) {
    for (String field : new String[] {"url", "image", "id"}) {
      JsonNode value = data.path(field);
      if (value.isTextual() && !value.asText().isBlank()) return value.asText();
    }
    throw new IllegalStateException("Image response has no reference");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
    return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
  }

  @ExceptionHandler(InfraiClient.Rejection.class)
  ResponseEntity<Map<String, String>> rejected(InfraiClient.Rejection error) {
    int status = error.status >= 400 && error.status < 500 ? error.status : 502;
    return ResponseEntity.status(HttpStatus.valueOf(status)).body(Map.of("code", error.code, "error", error.getMessage()));
  }
}

@Configuration(proxyBeanMethods = false)
class AvatarConfiguration {
  @Bean
  AvatarRelease.Gateway gateway(@Value("${avatar.base-url}") String baseUrl) {
    String key = System.getenv("INFRAI_API_KEY");
    if (key == null || key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY");
    InfraiClient client = new InfraiClient(baseUrl, key);
    return client::send;
  }
}
