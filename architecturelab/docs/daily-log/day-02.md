## Observations
- `timeout(Duration.ofMillis(400))` was added to protect the order creation pipeline.
- `retryWhen(Retry.backoff(2, Duration.ofMillis(100)))` was added.
- `onErrorResume` executed after retries were exhausted.
- Failure log showed `RetryExhaustedException`.
- Retry attempt logging was added.
- Current risk: retry may still be applied to the full pipeline, which can re-run `persist_order`.

## Key Learning
Retry should not blindly wrap the full workflow. If a downstream dependency fails, retrying the whole pipeline may repeat earlier write operations. In production, this can create duplicate side effects unless those operations are idempotent.