# Day 3 — Retry Placement, Failure Observability, and Timeout Budget

## Goal
Improve resilience in `order-service` by localizing retry to the unstable inventory step, making inventory failures deterministic for testing, and understanding how retry/backoff interacts with the overall API timeout.

## Completed Checklist
- [x] Added deterministic inventory failure using `sku = FAIL-INVENTORY`
- [x] Kept retry localized inside `checkInventory()`
- [x] Added retry with exponential backoff
- [x] Added retry attempt logging
- [x] Verified `Retry.backoff(2, ...)` gives 2 retries after the original attempt
- [x] Verified inventory can execute up to 3 times
- [x] Verified `persist_order` executes only once even when inventory retries
- [x] Verified fallback executes after retries are exhausted
- [x] Observed `RetryExhaustedException`
- [x] Increased overall timeout from 400 ms to 1200 ms for the retry-exhaustion experiment
- [x] Understood retry placement and latency-budget interaction

## Current Retry Configuration

```kotlin
.retryWhen(
    Retry.backoff(2, Duration.ofMillis(100))
        .filter { error -> error is RuntimeException }
        .doAfterRetry {
            log.info(
                "event=order_retry service=order-service attempt={} reason={} traceId={}",
                it.totalRetries() + 1,
                it.failure().javaClass.simpleName,
                traceId
            )
        }
)
```

## Current Timeout

```kotlin
.timeout(Duration.ofMillis(1200))
```

The timeout was initially 400 ms. It fired before the complete retry sequence could finish, so it was temporarily increased to 1200 ms to observe retry exhaustion clearly.

## Deterministic Failure Test

Request:

```json
{
  "sku": "FAIL-INVENTORY",
  "quantity": 10
}
```

Observed fallback response:

```json
{
  "data": {
    "orderId": "5d42ac0a-4947-467e-8d38-e2ec3ab71060",
    "status": "FAILED",
    "sku": "FAIL-INVENTORY",
    "quantity": 10
  }
}
```

## Retry / Failure Evidence

Retry attempts:

```text
event=order_retry service=order-service attempt=1 reason=RuntimeException traceId=8054458a-a00d-4d82-b738-2e5e23f6045d
event=order_retry service=order-service attempt=2 reason=RuntimeException traceId=8054458a-a00d-4d82-b738-2e5e23f6045d
```

Final retry exhaustion:

```text
2026-08-13T00:12:24.331+05:30 WARN 23620 --- [order-service] [parallel-7] c.a.o.application.CreateOrderService : event=order_creation_failed service=order-service error=RetryExhaustedException traceId=8054458a-a00d-4d82-b738-2e5e23f6045d
```

Persistence evidence:

```text
event=order_step_completed service=order-service step=persist_order latencyMs=109 traceId=63b421b5-8f41-4ede-8978-948e837e7dd7
```

`persist_order` appeared only once while the inventory step retried. This proves the retry is scoped to the inventory operation instead of re-running the entire order workflow.

## Observations

1. `Retry.backoff(2, ...)` means:
    - Initial inventory attempt
    - Retry #1
    - Retry #2
    - Then `RetryExhaustedException` if all attempts fail

   Therefore there can be **3 total inventory executions but only 2 retry logs**.

2. Retry placement matters. When retry wrapped the whole pipeline, an inventory failure could cause earlier steps such as persistence to run again. Localizing retry inside `checkInventory()` prevents that behavior.

3. A 400 ms overall timeout was too small for this experiment because inventory attempts, retry backoff, persistence latency, and framework overhead together could exceed the timeout before retry exhaustion.

4. Retry count, retry backoff, downstream latency, and overall API timeout must be designed together as one latency budget.

## Principal Engineer Learning

### Retry only the operation that is safe and meaningful to retry
A temporary downstream inventory failure should not automatically re-run an earlier order persistence operation. Retrying a write can create duplicate side effects unless that write is idempotent.

### Classify retryable errors
The current experiment retries `RuntimeException`, which is intentionally broad for learning. A production implementation should retry specific transient failures such as `InventoryUnavailableException`, timeout, connection-reset, or selected 5xx responses, while avoiding retries for validation and permanent business errors.

### Timeout and retry are one policy
Retries consume latency budget. A retry policy that mathematically cannot finish inside the API timeout is internally inconsistent. Production settings must be derived from the end-to-end SLO rather than selected independently.

## Current Confidence

- Retry placement: 8/10
- Retry with backoff: 7/10
- Retry exhaustion behavior: 8/10
- Timeout/retry interaction: 7/10
- Failure observability: 6/10
- Reactor resilience reasoning: 7/10

## Next Step — Day 4

Reactor Context and trace propagation:

- Remove unnecessary manual `traceId` propagation
- Understand why normal `ThreadLocal` assumptions are unsafe in reactive flows
- Read `traceId` from Reactor Context across asynchronous execution
- Verify the same traceId survives thread switches
- Continue improving structured failure observability
