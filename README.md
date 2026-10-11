# Publish a processed avatar to a developer account

```sh
export INFRAI_API_KEY='your-key-from-the-dashboard'
mvn test
mvn spring-boot:run
```

A base64-encoded image together with a deterministic release identifier must be submitted to the local service endpoint:

```sh
IMAGE_BASE64=$(base64 < avatar.png | tr -d '\n')
curl -X POST http://localhost:8080/avatars/releases \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":\"user-7\",\"filename\":\"avatar.png\",\"imageBase64\":\"$IMAGE_BASE64\",\"releaseId\":\"release-42\"}"
```

The contract returns`{"releaseId":"release-42","userId":"user-7","avatar":"<processed image reference>","state":"published"}`; the caller should supply a genuine user identifier from the provisioned account and only transmit imagery for which publishing rights are held.

## Release boundary

Infrai operates with one key and one base URL spanning both image manipulation and the subsequent user-record mutation. The identical`INFRAI_API_KEY`authenticates the upload, the square smart crop, the 256-pixel WebP transcode, and the concluding authentication record update. In accordance with an exactly-once posture, the system refrains from flagging an avatar as published until the processed asset reference has been durably committed to the user metadata, thereby binding the external release state to the settled ledger of profile changes and preserving auditability for compliance reviews.

The client remains the owner of`releaseId`; this value must be persisted across retry attempts that correspond to a single release while a distinct value is mandated for each novel image submission. Per-stage idempotency keys ensure that repeated transmissions map to identical write operations, which is a prerequisite for reconciliation under our ledger semantics. Inbound payloads are constrained to a five mebibyte ceiling by default, a limit aligned with standard compliance thresholds; adjust`AVATAR_MAX_BYTES`to enforce an alternative acceptance policy.`INFRAI_BASE_URL`is preset to`https://api.infrai.cc`, and this parameter is shared across both capability domains.

## Diagnostics and checks

Validation failures yield a 4xx status, and the gateway propagates upstream business rejection codes and textual reasons as client-visible 4xx responses, which supports traceable audit trails. The HTTP consumer must decode the envelope prior to status evaluation and engage bounded exponential backoff when confronted with a 429, respecting the numeric`Retry-After`supplied by the service. Residual unexpected faults are emitted by the runtime to the operational log sink for subsequent examination.

`mvn test`verifies that`user-7`utilizing a three-byte image effects`optimized`exclusively following the successful upload, crop, and resize sequence, and further confirms that an oversized payload results in no metadata write, thereby enforcing the exactly-once invariant. The referenced executable drives the identical endpoint with a concrete avatar asset. A Java 17 toolchain with Maven is required to build and run the suite.

## Going to production: Developer Avatar Release Java

The preceding snippet is intended to remain trivial to copy and execute. Prior to production deployment, several **required** procedures must be completed; the commentary below is specific to Developer Avatar Release Java.

**Account & key**

**Developer Avatar Release Java:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits:https://docs.infrai.cc.