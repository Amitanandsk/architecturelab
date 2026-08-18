# Day 4 — Reactor Context and Trace Propagation

## Goal
Remove manual `traceId` propagation from business method signatures and use Reactor Context for request-scoped tracing across asynchronous/reactive execution.

## Completed Checklist
- [x] Request filter generates or reuses a trace ID
- [x] Trace ID is returned in the response header
- [x] Trace ID is written into Reactor Context
- [x] HTTP header key and Reactor Context key are kept separate
- [x] Added `ReactorTraceContext`
- [x] Controller no longer extracts `traceId`
- [x] Removed `traceId` from business/service method parameters
- [x] Refactored `persistOrder`
- [x] Refactored `checkInventory`
- [x] Refactored `measureStep`
- [x] Refactored retry and fallback logging
- [x] Verified same trace ID across multiple Reactor threads
- [x] Verified same trace ID through retry exhaustion and fallback

## Request Filter
Conceptually:

```kotlin
val traceId =
    exchange.request.headers.getFirst(TraceConstants.TRACE_ID_HEADER)
        ?: traceIdGenerator.generate()

exchange.response.headers.add(
    TraceConstants.TRACE_ID_HEADER,
    traceId
)

return chain.filter(exchange)
    .contextWrite { context ->
        context.put(TraceConstants.TRACE_ID, traceId)
    }
```

### Key convention
```text
HTTP header:
TraceConstants.TRACE_ID_HEADER
Example: X-Trace-Id

Reactor Context:
TraceConstants.TRACE_ID
Example: traceId
```

## Reactor Context Helper

```kotlin
object ReactorTraceContext {

    fun currentTraceId(): Mono<String> =
        Mono.deferContextual { context ->
            val traceId =
                if (context.hasKey(TraceConstants.TRACE_ID)) {
                    context.get<String>(TraceConstants.TRACE_ID)
                } else {
                    "missing-trace-id"
                }

            Mono.just(traceId)
        }
}
```

`Mono.deferContextual` reads context when the reactive flow is subscribed/executed.

## Controller Refactor

### Before
```kotlin
return Mono.deferContextual { context ->
    createOrderService.createOrder(
        request,
        context.get(TraceConstants.TRACE_ID_HEADER)
    )
}.map(::ApiResponse)
```

### After
```kotlin
return createOrderService
    .createOrder(request)
    .map(::ApiResponse)
```

The controller no longer participates in observability propagation.

## Business Method Refactor

### Before
```kotlin
persistOrder(request, traceId)
checkInventory(savedOrder, traceId)
measureStep("persist_order", traceId) { ... }
```

### After
```kotlin
persistOrder(request)
checkInventory(savedOrder)
measureStep("persist_order") { ... }
```

## Successful Trace Propagation Evidence

```text
request_started      reactor-http-nio-3
validate_request     reactor-http-nio-3
persist_order        parallel-1
retry                parallel-4
request_completed    parallel-5
inventory_check      parallel-5
```

All logs used:

```text
traceId=37905f59-1521-4e73-af0f-08ece8e3fb78
```

This proves the trace ID survived asynchronous thread changes.

## Failure Path Evidence

```text
request_started      reactor-http-nio-3
validate_request     reactor-http-nio-3
persist_order        parallel-1
retry #1             parallel-4
retry #2             parallel-6
order_creation_failed parallel-7
request_completed    parallel-7
inventory_check      parallel-7
```

All logs used:

```text
traceId=aec3f2f9-a007-4450-9ae7-902389c54248
```

Failure log:

```text
event=order_creation_failed
service=order-service
error=RetryExhaustedException
traceId=aec3f2f9-a007-4450-9ae7-902389c54248
```

## Bug Found

Initially the failure log printed:

```text
traceId=MonoDeferContextual
```

Cause: `ReactorTraceContext.currentTraceId()` returns `Mono<String>`, not `String`.

Correct pattern:

```kotlin
ReactorTraceContext.currentTraceId()
    .flatMap { traceId ->
        log.warn(
            "event=order_creation_failed service=order-service error={} traceId={}",
            error.javaClass.simpleName,
            traceId
        )

        Mono.just(fallbackResponse)
    }
```

## Principal Engineer Lessons

### 1. Business APIs should carry business data
> Business method signatures should primarily carry business data. Cross-cutting request metadata such as trace IDs should propagate through appropriate infrastructure/context mechanisms rather than being threaded manually through every application method.

### 2. Reactive execution is not bound to one thread
> In reactive systems, request execution is not bound to one thread. Thread-local assumptions therefore break down across asynchronous boundaries. Reactor Context provides subscription-scoped propagation for cross-cutting metadata such as trace IDs without polluting business method signatures.

### 3. Cross-cutting concerns belong at infrastructure boundaries
Tracing, correlation IDs, authentication context, tenant metadata, and similar concerns should normally be introduced at infrastructure boundaries rather than manually propagated through domain APIs.

For this experiment:

```text
RequestLoggingWebFilter
        ↓
Reactor Context
        ↓
application flow
```

### 4. Context keys are contracts
A context key written by infrastructure and read by downstream code must match exactly.

Keep these separate:

```text
HTTP transport key -> X-Trace-Id
Internal context key -> traceId
```

### 5. Error paths need the same observability discipline as success paths
Trace propagation is incomplete if it works only for successful requests.

It must remain correct through:

```text
success
retry
timeout
fallback
exception
cancellation
```

### 6. Do not treat `Mono<T>` as `T`
`Mono<String>` is not a `String`.

Reactive values should stay inside operators such as:

```text
map
flatMap
deferContextual
onErrorResume
```

### 7. Observability should have low coupling to business code
The system should not depend on every developer remembering:

```text
"Pass traceId into the next method."
```

Infrastructure should make correct propagation the default.

### 8. Prove behavior with experiments
Do not assume propagation works because the code looks correct.

Useful evidence is:

```text
different execution threads
+
same traceId
+
success path
+
failure/retry path
```

## Step Latency Observation

Failed inventory request:

```text
validate_request      ~3 ms
persist_order        ~132 ms
inventory_check     ~1055 ms
request total       ~1393 ms
```

The inventory measurement includes retry/backoff cost, showing that resilience policies consume end-to-end latency budget.

## Current Confidence
- Reactor Context fundamentals: 8/10
- `contextWrite`: 8/10
- `deferContextual`: 7/10
- Trace propagation across thread changes: 8/10
- Removing observability metadata from business APIs: 9/10
- Reactive failure-path context handling: 7/10

## Day 4 Result
Day 4 is complete.

```text
HTTP Request
     |
RequestLoggingWebFilter
     |
     | traceId
     v
Reactor Context
     |
     +---------------------------+
     |            |              |
 validation   persistence     inventory
     |            |              |
     +------------+--------------+
                  |
            same traceId
```

## Next Step — Day 5
### Error Classification and Correct API Semantics

Topics:
- validation vs infrastructure failures
- custom exception types
- correct HTTP status codes
- `400` invalid request
- `503` unavailable dependency
- `504` timeout
- `500` unexpected failure
- structured `errorCode` / `errorType`
- avoid returning HTTP `200` for every failed workflow
