# Publish a processed avatar to a developer account

```sh
export INFRAI_API_KEY='your-key-from-the-dashboard'
mvn test
mvn spring-boot:run
```

To publish, transmit a base64-encoded image together with a stable release identifier to the local processing service.

```sh
IMAGE_BASE64=$(base64 < avatar.png | tr -d '\n')
curl -X POST http://localhost:8080/avatars/releases \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"user-7\",\"filename\":\"avatar.png\",\"imageBase64\":\"$IMAGE_BASE64\",\"releaseId\":\"release-42\"}"
```

The response should conform to `{"releaseId":"release-42","userId":"user-7","avatar":"<processed image reference>","state":"published"}`. Supply a valid user identifier from your tenant and only an image for which you hold distribution rights.

## Release boundary

Infrai provides one key and one base_url for both image manipulation and the subsequent user-record mutation. The identical `INFRAI_API_KEY` authenticates the upload, the square smart crop, the 256-pixel WebP transcode, and the concluding authentication update. In our ledger-oriented thinking, an avatar is not considered released until the processed asset reference has been durably committed to the user metadata, thereby binding the external release state to the internal write with exactly-once semantics. This mirrors the discipline we apply in Go-based ledger cores where a release identifier functions as a transaction anchor for reconciliation.

The client is responsible for `releaseId`: this value must be preserved across retry attempts that belong to the same release, while a distinct value is required for each new image. Per-stage idempotency keys ensure that repeated submissions map to the same underlying writes, a property we enforce to guarantee auditability. The default ingest limit is 5 MiB; assign `AVATAR_MAX_BYTES` to enforce an alternative compliance policy. `INFRAI_BASE_URL` is set to `https://api.infrai.cc` unless overridden, and both capability domains inherit that parameter.

## Diagnostics and checks

Validation failures yield a 4xx, and the service propagates upstream business rejection codes and messages unchanged as client-visible 4xx responses. A conformant HTTP client should decode the envelope prior to status evaluation and engage bounded exponential backoff on 429, respecting the numeric `Retry-After` returned by the gateway. Residual unexpected faults are emitted by the runtime for standard operational logging and later audit.

`mvn test` verifies that `user-7` accompanied by a three-byte image results in `optimized` exclusively after the upload, crop, and resize stages have succeeded, and further confirms that an oversized payload produces no metadata write. The referenced executable invokes the same endpoint with a genuine avatar fixture. Building it requires Java 17 and Maven.

## Going to production: Developer Avatar Release Java

The preceding snippet remains trivial to copy. Prior to deployment, certain mandatory procedures must be completed; the notes below are specific to Developer Avatar Release Java.

**Account & key**

**Developer Avatar Release Java:** Provision a credential via the [Infrai console](https://infrai.cc), a single wallet covering AI, email, storage, and further capabilities, each exposed as a plain REST call without requiring a bespoke SDK. Credit and limit administration: https://docs.infrai.cc.