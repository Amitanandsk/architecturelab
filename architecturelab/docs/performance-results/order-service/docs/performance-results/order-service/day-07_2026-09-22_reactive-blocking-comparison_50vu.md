# Day 7 — Reactive Blocking vs Non-Blocking Performance Comparison

## Experiment Information

**Learning Day:** Day 7  
**Date:** 2026-09-22  
**Service:** order-service  
**Experiment Type:** Reactive execution model comparison  
**Load:** 50 Virtual Users  
**Duration:** 30 seconds per scenario  
**Test Tool:** k6  
**Code Under Test:** `<add the Git SHA used for these Day 7 experiments>`

---

# Objective

Measure and compare the performance impact of three different ways of handling the same additional ~200 ms wait inside a Spring WebFlux / Project Reactor request flow:

1. **Non-blocking wait** using `Mono.delay(...)`
2. **Blocking wait isolated** on `Schedulers.boundedElastic()`
3. **Blocking wait directly on the Netty event loop** using `Thread.sleep(...)`

The goal is to prove, with measured evidence, how blocking the WebFlux event loop affects throughput and latency under concurrency.

---

# Experimental Principle

Keep the workload equivalent and change only the execution model of the additional ~200 ms wait.

All scenarios use:

```text
50 VUs
30 seconds
same service
same machine
same endpoint
same normal order flow
same additional ~200 ms waiting work
```

This makes the comparison more defensible because the major variable being changed is:

```text
HOW the waiting work is executed
```

rather than:

```text
HOW MUCH work is executed
```

---

# Common Request Flow

```text
k6
 |
 | POST /orders
 v
Order Service
 |
 +-- validate_request
 |
 +-- persist_order
 |
 +-- inventory_check
 |
 v
HTTP 200
```

The normal order flow already contains approximately:

```text
persist_order      ≈ 100 ms
inventory_check    ≈ 200 ms
```

Each Day 7 scenario adds another approximately:

```text
200 ms
```

inside validation.

Therefore, for a healthy implementation, total request latency should be approximately:

```text
~500–530 ms
```

including framework/scheduler/logging overhead.

---

# Scenario A — Non-Blocking Delay

## Test SKU

```text
NONBLOCKING-DELAY
```

## Implementation Concept

```kotlin
Mono.delay(Duration.ofMillis(200))
    .thenReturn(request)
```

## Execution Model

```text
reactor-http-nio
        ↓
schedule 200 ms timer
        ↓
thread becomes available
        ↓
other requests can execute
        ↓
timer completes
        ↓
pipeline resumes
```

No worker thread is held idle for the full 200 ms wait.

## k6 Result

```text
Total Requests : 2,900
Throughput     : 95.61 RPS
Error Rate     : 0%

Average        : 521.49 ms
Min            : 508.33 ms
p50            : 516.45 ms
p90            : 531.42 ms
p95            : 537.03 ms
p99            : 547.33 ms
Max            : 561.17 ms
```

---

# Scenario B — Blocking Work Isolated on boundedElastic

## Test SKU

```text
BLOCKING-ISOLATED
```

## Implementation Concept

```kotlin
Mono.fromCallable {
    Thread.sleep(200)
    request
}
.subscribeOn(Schedulers.boundedElastic())
```

## Verified Thread

Manual test confirmed:

```text
thread=boundedElastic-1
```

## Execution Model

```text
reactor-http-nio
        ↓
schedule blocking work
        ↓
event loop becomes available
        ↓
boundedElastic worker
        ↓
Thread.sleep(200)
        ↓
pipeline continues
```

The blocking call still exists.

However, it no longer consumes Netty event-loop capacity.

## k6 Result

```text
Total Requests : 2,900
Throughput     : 95.32 RPS
Error Rate     : 0%

Average        : 521.40 ms
Min            : 509.86 ms
p50            : 517.19 ms
p90            : 530.46 ms
p95            : 533.03 ms
p99            : 555.53 ms
Max            : 580.87 ms
```

---

# Scenario C — Blocking Directly on Netty Event Loop

## Test SKU

```text
BLOCKING-EVENT-LOOP
```

## Implementation Concept

```kotlin
Thread.sleep(200)
```

executed directly inside the reactive request pipeline without scheduler isolation.

## Verified Thread

Manual test confirmed:

```text
thread=reactor-http-nio-3
```

## Execution Model

```text
reactor-http-nio worker
        ↓
Thread.sleep(200)
        ↓
event-loop worker unavailable
        ↓
other requests wait
        ↓
queueing increases
        ↓
latency increases
```

## k6 Result

```text
Total Requests : 1,194
Throughput     : 38.23 RPS
Error Rate     : 0%

Average        : 1.28 s
Min            : 621.54 ms
p50            : 1.22 s
p90            : 1.63 s
p95            : 1.66 s
p99            : 1.91 s
Max            : 2.33 s
```

---

# Final Comparison

| Metric | Non-Blocking `Mono.delay` | Blocking on `boundedElastic` | Blocking on Event Loop |
|---|---:|---:|---:|
| Virtual Users | 50 | 50 | 50 |
| Duration | 30 sec | 30 sec | 30 sec |
| Total Requests | 2,900 | 2,900 | 1,194 |
| Throughput | **95.61 RPS** | **95.32 RPS** | **38.23 RPS** |
| Error Rate | 0% | 0% | 0% |
| Average Latency | **521.49 ms** | **521.40 ms** | **1.28 s** |
| p50 | **516.45 ms** | **517.19 ms** | **1.22 s** |
| p90 | **531.42 ms** | **530.46 ms** | **1.63 s** |
| p95 | **537.03 ms** | **533.03 ms** | **1.66 s** |
| p99 | **547.33 ms** | **555.53 ms** | **1.91 s** |
| Max | **561.17 ms** | **580.87 ms** | **2.33 s** |

---

# Throughput Impact

The two healthy execution models produced almost identical throughput:

```text
Non-blocking Mono.delay
→ 95.61 RPS

Blocking isolated on boundedElastic
→ 95.32 RPS
```

Blocking the Netty event loop reduced throughput to:

```text
38.23 RPS
```

Compared with the non-blocking implementation:

```text
38.23 / 95.61
≈ 40%
```

The service retained only about 40% of the throughput.

Equivalently, isolating the blocking operation improved throughput from:

```text
38.23 RPS
→ 95.32 RPS
```

or approximately:

```text
2.5×
```

---

# Latency Impact

Healthy implementations:

```text
Non-blocking p95:
537 ms

boundedElastic p95:
533 ms
```

Blocking event loop:

```text
p95:
1.66 seconds
```

Similarly:

```text
p99:

Non-blocking
547 ms

boundedElastic
556 ms

Event-loop blocking
1.91 sec
```

The additional latency was much greater than the original 200 ms blocking call.

---

# Why a 200 ms Block Produced 1–2 Second Latency

A single request performing:

```text
Thread.sleep(200)
```

does not only affect that request.

Netty event-loop workers are shared across many connections.

Under concurrency:

```text
request A
→ event-loop worker blocked

request B
→ another worker blocked

request C
→ another worker blocked

...
```

As processing capacity becomes unavailable:

```text
incoming work
        ↓
waits for event-loop capacity
        ↓
queueing delay increases
        ↓
request latency increases
```

Therefore:

```text
request latency
=
actual application work
+
blocking time
+
queueing time
```

Queueing amplified the original 200 ms delay into p95 latency of approximately 1.66 seconds.

---

# Concurrency / Throughput / Latency Relationship

For the event-loop blocking test:

```text
Throughput ≈ 38.23 RPS
Average latency ≈ 1.28 sec
```

Approximate concurrency:

```text
38.23 × 1.28
≈ 49
```

This closely matches:

```text
50 configured VUs
```

For the non-blocking test:

```text
95.61 × 0.521
≈ 50
```

Again, this closely matches the configured concurrency.

This reinforces the relationship:

```text
Concurrency ≈ Throughput × Latency
```

for this closed/constant-VU workload.

---

# Important Observation — 0% Errors Does Not Mean Healthy

All three scenarios returned:

```text
0% HTTP failures
100% successful checks
```

Yet the event-loop blocking scenario had:

```text
p50 = 1.22 sec
p95 = 1.66 sec
p99 = 1.91 sec
```

Therefore:

> A service can remain functionally available while becoming operationally unhealthy.

Availability must not be measured only by:

```text
HTTP 200 vs HTTP 500
```

Latency and SLO measurements are also required.

---

# Non-Blocking vs boundedElastic

At 50 concurrent VUs:

```text
Mono.delay
≈ 95.61 RPS

boundedElastic + Thread.sleep
≈ 95.32 RPS
```

The measured performance is nearly identical at this load.

However, the architectures are not equivalent.

## Non-Blocking

```text
waiting operation
→ no application worker held idle for entire wait
```

## boundedElastic

```text
waiting operation
→ boundedElastic worker remains blocked
```

The current test load did not saturate `boundedElastic`.

Therefore both appear similar.

This does NOT prove that blocking work on `boundedElastic` scales exactly like true non-blocking I/O.

At higher blocking concurrency:

```text
boundedElastic workers occupied
        ↓
scheduler capacity consumed
        ↓
queueing can begin
```

Scheduler isolation moves the blocking capacity problem away from the event loop; it does not eliminate the underlying blocking behavior.

---

# Decision Guide

## Prefer True Non-Blocking APIs

Examples:

```text
Reactive database driver
Reactive WebClient
Async/non-blocking SDK
```

Conceptually:

```text
start I/O
→ release execution thread
→ resume when result is ready
```

This is the preferred reactive architecture.

## Use boundedElastic When Blocking Is Unavoidable

Examples:

```text
legacy synchronous SDK
blocking JDBC driver
blocking filesystem API
legacy blocking HTTP client
```

Pattern:

```kotlin
Mono.fromCallable {
    blockingCall()
}
.subscribeOn(Schedulers.boundedElastic())
```

The purpose is to protect the event loop.

## Avoid Blocking the Event Loop

Examples:

```text
Thread.sleep
blocking JDBC
blocking REST client
synchronous filesystem work
blocking SDK calls
```

directly on:

```text
reactor-http-nio-*
```

can create:

```text
event-loop starvation
→ queueing
→ tail latency
→ timeouts
→ eventual failures
```

---

# What This Experiment Proves

Under an identical 50-VU workload with an additional approximately 200 ms wait:

- True non-blocking waiting sustained approximately **95.61 RPS**.
- Isolated blocking work on `boundedElastic` sustained approximately **95.32 RPS**.
- Blocking the Netty event loop reduced throughput to approximately **38.23 RPS**.
- Event-loop blocking increased p95 latency from approximately **0.53 sec** to **1.66 sec**.
- Event-loop blocking increased p99 latency to approximately **1.91 sec**.
- All requests still returned successfully, showing that latency degradation can precede availability failure.

The experiment demonstrates the performance impact of event-loop starvation and the value of scheduler isolation.

---

# What This Experiment Does NOT Prove

This experiment does not establish:

- maximum capacity of `boundedElastic`,
- maximum service throughput,
- behavior with a real blocking JDBC driver,
- behavior with a real database,
- behavior under very long-duration load,
- production readiness,
- exact scheduler saturation thresholds,
- exact number of Netty workers required for a given workload.

Those require separate experiments.

---

# Principal Engineer Takeaways

> **Non-blocking means an operation may wait without requiring a thread to remain occupied during the wait.**

> **Blocking a shared event-loop worker removes processing capacity from many connections, so a small blocking operation can create much larger queueing latency under concurrency.**

> **Event-loop starvation can dramatically reduce throughput before HTTP errors appear.**

> **A service can be available according to error rate while already violating latency SLOs.**

> **If blocking work is unavoidable, isolate it at an explicit architectural boundary rather than allowing it to execute on the event loop.**

> **`boundedElastic` protects the event loop; it does not magically make blocking code non-blocking.**

> **True non-blocking I/O remains preferable when the dependency supports it.**

> **When comparing architectural choices, keep workload and business work equivalent and change only the mechanism being evaluated.**

> **Performance conclusions should be based on measured and reproducible evidence rather than framework slogans such as “WebFlux is faster.”**

---

# Final Day 7 Performance Conclusion

```text
Same workload:
50 VUs
30 seconds
same ~200 ms additional wait

NON-BLOCKING
Mono.delay
→ 95.61 RPS
→ p95 537 ms

BLOCKING ISOLATED
boundedElastic + Thread.sleep
→ 95.32 RPS
→ p95 533 ms

BLOCKING EVENT LOOP
reactor-http-nio + Thread.sleep
→ 38.23 RPS
→ p95 1.66 sec
```

The experiment clearly demonstrates that the location and execution model of blocking work can have a much larger system-wide impact than the blocking duration itself.
