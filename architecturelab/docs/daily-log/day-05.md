# Day 5 — Reactive Error Classification, HTTP Mapping, Timeout Semantics, and Cancellation

## Goal
Build a clean, scalable reactive error-handling model for `order-service` using Kotlin, Project Reactor, Spring WebFlux, Spring Boot, Clean Architecture principles, centralized HTTP error translation, and no annotation-based exception mapping.

The Day 5 objective was to make failures meaningful across four dimensions:

1. **Application semantics** — what actually failed?
2. **Resilience semantics** — should this failure be retried?
3. **Transport semantics** — what HTTP status should the caller receive?
4. **Observability semantics** — how should success, failure, retry, timeout, and cancellation be logged?

## Final Error Architecture

```text
Technical / Framework Error
        |
        v
Infrastructure / Dependency Boundary
        |
        | translate if needed
        v
ApplicationException
        |
        v
Application / Use Case
        |
        | propagate unless business fallback exists
        v
GlobalErrorWebExceptionHandler
        |
        v
HttpErrorMapper
        |
        v
ApiErrorResponse
        |
        v
HTTP status + safe JSON response
```

> Application errors describe **what failed**. The HTTP adapter decides **how that failure is represented over HTTP**.

## Application Exception Model

```kotlin
abstract class ApplicationException(
    val errorCode: String,
    val safeMessage: String,
    val retryable: Boolean = false,
    cause: Throwable? = null
) : RuntimeException(safeMessage, cause)
```

The base exception intentionally does not contain `HttpStatus`, `ServerWebExchange`, `ResponseEntity`, `traceId`, or JSON response details.

### OrderValidationException

```kotlin
class OrderValidationException(
    message: String
) : ApplicationException(
    errorCode = "INVALID_ORDER_REQUEST",
    safeMessage = message,
    retryable = false
)
```

Behavior:

```text
invalid request
→ OrderValidationException
→ no retry
→ HTTP 400
```

### InventoryUnavailableException

```kotlin
class InventoryUnavailableException(
    cause: Throwable? = null
) : ApplicationException(
    errorCode = "INVENTORY_UNAVAILABLE",
    safeMessage = "Inventory service is temporarily unavailable",
    retryable = true,
    cause = cause
)
```

Behavior:

```text
inventory unavailable
→ InventoryUnavailableException
→ retry locally
→ preserve same exception after retry exhaustion
→ HTTP 503
```

### DependencyTimeoutException

```kotlin
class DependencyTimeoutException(
    val dependency: String,
    cause: Throwable? = null
) : ApplicationException(
    errorCode = "DEPENDENCY_TIMEOUT",
    safeMessage = "A required downstream service timed out",
    retryable = true,
    cause = cause
)
```

Behavior:

```text
dependency attempt timeout
→ Reactor TimeoutException
→ DependencyTimeoutException
→ retry locally
→ HTTP 504
```

### OrderProcessingTimeoutException

```kotlin
class OrderProcessingTimeoutException(
    cause: Throwable? = null
) : ApplicationException(
    errorCode = "ORDER_PROCESSING_TIMEOUT",
    safeMessage = "Order service could not complete the request within the allowed time",
    retryable = false,
    cause = cause
)
```

Behavior:

```text
whole create-order workflow exceeds deadline
→ OrderProcessingTimeoutException
→ HTTP 503
```

### UnexpectedApplicationException

```kotlin
class UnexpectedApplicationException(
    cause: Throwable
) : ApplicationException(
    errorCode = "INTERNAL_ERROR",
    safeMessage = "An unexpected error occurred while processing the request",
    retryable = false,
    cause = cause
)
```

Behavior:

```text
unexpected Throwable
→ UnexpectedApplicationException
→ HTTP 500
```

## Public API Error Contract

```kotlin
data class ApiErrorResponse(
    val errorCode: String,
    val message: String,
    val traceId: String
)
```

The public response does not expose stack traces, internal class names, raw framework exceptions, dependency hosts, causes, or package names.

## HTTP Mapping Model

```kotlin
data class HttpError(
    val status: HttpStatus,
    val errorCode: String,
    val message: String
)
```

Final mapping:

| Application Failure | Error Code | Retry | HTTP |
|---|---|---:|---:|
| `OrderValidationException` | `INVALID_ORDER_REQUEST` | No | 400 |
| `InventoryUnavailableException` | `INVENTORY_UNAVAILABLE` | Yes, locally | 503 |
| `DependencyTimeoutException` | `DEPENDENCY_TIMEOUT` | Yes, locally | 504 |
| `OrderProcessingTimeoutException` | `ORDER_PROCESSING_TIMEOUT` | No | 503 |
| `UnexpectedApplicationException` | `INTERNAL_ERROR` | No | 500 |

## Global Reactive HTTP Error Handler

A centralized reactive HTTP error boundary was introduced:

```text
GlobalErrorWebExceptionHandler
implements ErrorWebExceptionHandler
```

Its responsibility:

```text
receive propagated ApplicationException
→ HttpErrorMapper
→ obtain traceId from ServerWebExchange
→ build ApiErrorResponse
→ write HTTP status + JSON response
```

It does not perform retry, business fallback, database access, compensation, or business rules.

## Retry Classification

Initial retry logic:

```kotlin
.filter { error ->
    error is RuntimeException
}
```

This was too broad.

Final direction:

```kotlin
.filter { error ->
    (error as? ApplicationException)?.retryable == true
}
```

Examples:

```text
OrderValidationException
retryable=false
→ no retry

InventoryUnavailableException
retryable=true
→ retry

DependencyTimeoutException
retryable=true
→ retry
```

Retry remains scoped to the inventory dependency operation.

## Retry Exhaustion

Reactor originally produced a retry wrapper. Retry exhaustion was changed to preserve the meaningful application failure:

```kotlin
.onRetryExhaustedThrow { _, retrySignal ->
    retrySignal.failure()
}
```

This prevents upper layers from needing to understand Reactor retry internals.

## Dependency Timeout

Deterministic lab trigger:

```text
TIMEOUT-INVENTORY
```

Simulated dependency latency: 500 ms  
Per-attempt timeout: 250 ms

```kotlin
.timeout(Duration.ofMillis(250))
.onErrorMap(TimeoutException::class.java) { error ->
    DependencyTimeoutException(
        dependency = "inventory-service",
        cause = error
    )
}
```

Flow:

```text
inventory attempt
→ 250ms deadline
→ TimeoutException
→ DependencyTimeoutException
→ retry
```

## Overall Order Workflow Timeout

End-to-end create-order timeout:

```text
2000 ms
```

Deterministic lab trigger:

```text
SLOW-ORDER
```

Simulated persistence delay:

```text
2500 ms
```

Flow:

```text
validate request
→ persist starts
→ overall 2000ms deadline expires
→ upstream persistence cancelled
→ OrderProcessingTimeoutException
→ HTTP 503
```

This established the distinction between dependency-attempt timeout and whole-workflow deadline.

## Unexpected Error Classification

Deterministic lab trigger:

```text
UNEXPECTED-ERROR
```

It produces an `IllegalStateException`, which is normalized at the application boundary:

```kotlin
.onErrorMap { error ->
    when (error) {
        is ApplicationException -> error
        else -> UnexpectedApplicationException(error)
    }
}
```

Final behavior:

```text
IllegalStateException
→ UnexpectedApplicationException
→ HTTP 500
```

The original cause remains available internally.

## Reactor Operator Responsibilities

### `doOnError`
Use for side-effect observation/logging only.

### `onErrorMap`
Use to translate one failure type into another.

### `onErrorResume`
Use only when the workflow intentionally recovers using another publisher.

Do not use `onErrorResume` just to log and rethrow the same error.

## StepMeasurement Improvements

The original implementation used `doFinally` and incorrectly logged `step_completed` for failures.

The measurement model was changed to explicit terminal outcomes.

### Success

```text
event=step_completed
service=order-service
step=persist_order
outcome=success
latencyMs=...
traceId=...
```

### Failure

```text
event=step_failed
service=order-service
step=inventory_check
outcome=error
errorCode=INVENTORY_UNAVAILABLE
errorType=InventoryUnavailableException
retryable=true
latencyMs=...
traceId=...
```

### Cancellation

```text
event=step_cancelled
service=order-service
step=persist_order
outcome=cancelled
latencyMs=...
traceId=...
```

## Exactly One Terminal Outcome

During the overall timeout experiment, a completed validation step was initially logged as both success and cancelled.

A per-subscription guard was added:

```kotlin
val terminalLogged = AtomicBoolean(false)
```

Each terminal callback uses:

```kotlin
terminalLogged.compareAndSet(false, true)
```

for `doOnSuccess`, `doOnError`, and `doOnCancel`.

The guard controls observability semantics only; it does not prevent Reactor cancellation.

Final rule:

```text
one logical step
→ exactly one terminal observability event
```

## StepMeasurement Placement

The logical inventory measurement wraps the entire retry policy:

```text
StepMeasurement
    |
    +-- inventory attempt
    +-- timeout
    +-- retry
    +-- backoff
    +-- retry
    +-- final result
```

Therefore `inventory_check latency` represents the end-to-end cost of the dependency operation, including retries and backoff.

Individual retry attempts are logged separately.

## Deterministic Lab Failure Modes

```text
FAIL-INVENTORY
→ dependency unavailable

TIMEOUT-INVENTORY
→ dependency timeout

SLOW-ORDER
→ overall workflow deadline

UNEXPECTED-ERROR
→ unclassified unexpected exception
```

These remain intentionally in ArchitectureLab for repeatable resilience experiments and can be isolated later when real dependencies and persistence are introduced.

# Test Evidence

## Test 1 — Invalid Request

Input:

```json
{
  "sku": "PROD-1",
  "quantity": 0
}
```

Observed:

```text
event=step_failed
service=order-service
step=validate_request
outcome=error
errorCode=INVALID_ORDER_REQUEST
errorType=OrderValidationException
retryable=false
```

HTTP: `400`

Response:

```json
{
  "errorCode": "INVALID_ORDER_REQUEST",
  "message": "Quantity must be greater than zero",
  "traceId": "98ee5adf-3c05-4a17-96af-a60786b3168b"
}
```

No retry occurred.

## Test 2 — Inventory Unavailable

Input:

```json
{
  "sku": "FAIL-INVENTORY",
  "quantity": 10
}
```

Observed:

```text
retry attempt=1 reason=InventoryUnavailableException
retry attempt=2 reason=InventoryUnavailableException

event=step_failed
step=inventory_check
errorCode=INVENTORY_UNAVAILABLE
errorType=InventoryUnavailableException
retryable=true
```

HTTP: `503`

Response:

```json
{
  "errorCode": "INVENTORY_UNAVAILABLE",
  "message": "Inventory service is temporarily unavailable",
  "traceId": "5e3806cf-ca78-443b-b7c9-b2d6fcecd965"
}
```

## Test 3 — Dependency Timeout

Input:

```json
{
  "sku": "TIMEOUT-INVENTORY",
  "quantity": 10
}
```

Observed:

```text
retry attempt=1 reason=DependencyTimeoutException
retry attempt=2 reason=DependencyTimeoutException

event=step_failed
step=inventory_check
outcome=error
errorCode=DEPENDENCY_TIMEOUT
errorType=DependencyTimeoutException
retryable=true
```

HTTP: `504`

Response:

```json
{
  "errorCode": "DEPENDENCY_TIMEOUT",
  "message": "A required downstream service timed out",
  "traceId": "ff5ebcad-f7ec-4a0b-a270-287b43dd21a5"
}
```

Observed inventory logical latency: ~1215 ms  
Observed request latency: ~1607 ms

## Test 4 — Overall Order Deadline

Input:

```json
{
  "sku": "SLOW-ORDER",
  "quantity": 10
}
```

Final observed behavior:

```text
event=step_completed
step=validate_request
outcome=success

event=step_cancelled
step=persist_order
outcome=cancelled
latencyMs=2007

event=order_creation_failed
error=OrderProcessingTimeoutException

event=request_completed
status=503
latencyMs=2015
```

Trace ID:

```text
2200d487-f5fd-489f-a2aa-c82d58b37ac5
```

This proved:

```text
outer workflow deadline
→ cancellation of in-flight upstream step
→ explicit cancellation observability
→ HTTP 503
```

## Test 5 — Unexpected Error

Input:

```json
{
  "sku": "UNEXPECTED-ERROR",
  "quantity": 77
}
```

Original internal failure:

```text
IllegalStateException
```

Mapped to:

```text
UnexpectedApplicationException
```

HTTP: `500`

Safe response:

```json
{
  "errorCode": "INTERNAL_ERROR",
  "message": "An unexpected error occurred while processing the request",
  "traceId": "1b12aeca-2246-4056-a61a-239be980becd"
}
```

The internal error detail was not leaked to the API consumer.

# Final Failure Matrix

```text
INVALID INPUT
OrderValidationException
retry=false
HTTP 400

DEPENDENCY UNAVAILABLE
InventoryUnavailableException
retry=true
HTTP 503

DEPENDENCY TIMEOUT
DependencyTimeoutException
retry=true
HTTP 504

WHOLE WORKFLOW DEADLINE
OrderProcessingTimeoutException
retry=false
HTTP 503

UNKNOWN APPLICATION FAILURE
UnexpectedApplicationException
retry=false
HTTP 500
```


# Day 5 Result

Day 5 implementation is complete.

```text
[x] Application exception taxonomy
[x] Clean separation from HTTP
[x] Semantic retry classification
[x] Retry exhaustion preserves meaningful failure
[x] Centralized reactive HTTP error adapter
[x] Stable safe API error response
[x] Trace ID returned to client
[x] Validation -> 400
[x] Dependency unavailable -> 503
[x] Dependency timeout -> 504
[x] Whole order deadline -> 503
[x] Unexpected application fault -> 500
[x] Success/failure/cancellation step observability
[x] Exactly one terminal step outcome
[x] Retry attempt vs logical-step outcome separation
[x] Deterministic failure scenarios
```

