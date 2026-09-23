package dev.example.avatar;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AvatarReleaseTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void publishesOnlyAfterOptimization() throws Exception {
    List<String> calls = new ArrayList<>();
    AvatarRelease.Gateway gateway = (method, path, body, id) -> {
      calls.add(method + " " + path);
      if (path.contains("upload")) return json.readTree("{\"url\":\"uploaded\"}");
      if (path.contains("smart_crop")) return json.readTree("{\"url\":\"cropped\"}");
      if (path.contains("resize")) return json.readTree("{\"url\":\"optimized\"}");
      assertEquals("user-7", body.get("user_id"));
      assertEquals("optimized", ((java.util.Map<?, ?>) body.get("metadata")).get("avatar"));
      assertEquals("release-42:publish", body.get("idempotency_key"));
      return json.readTree("{}");
    };
    AvatarRelease service = new AvatarRelease(gateway, 1024);
    var result = service.release(new AvatarRelease.ReleaseRequest("user-7", "avatar.png", "YWJj", "release-42"));
    assertEquals("published", result.state());
    assertEquals("optimized", result.avatar());
    assertEquals(List.of("POST /v1/image/upload", "POST /v1/image/smart_crop", "POST /v1/image/resize", "PATCH /v1/auth/user/update/user-7"), calls);
  }

  @Test
  void rejectsOversizedInputBeforeAnyWrite() {
    List<String> calls = new ArrayList<>();
    AvatarRelease service = new AvatarRelease((method, path, body, id) -> { calls.add(path); return json.createObjectNode(); }, 2);
    assertThrows(IllegalArgumentException.class, () -> service.release(new AvatarRelease.ReleaseRequest("user-7", "avatar.png", "YWJj", "release-42")));
    assertTrue(calls.isEmpty());
  }
}
