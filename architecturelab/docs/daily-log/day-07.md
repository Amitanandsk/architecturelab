# Day 7 — Reactive Event-Loop Blocking, Scheduler Isolation, and Scheduler Selection

## Goal

Understand and prove how Project Reactor and Spring WebFlux behave when:

- work stays non-blocking,
- blocking work runs directly on the Netty event loop,
- unavoidable blocking work is isolated on `boundedElastic`,
- execution moves between schedulers using `subscribeOn` and `publishOn`,
- CPU-bound and blocking-I/O workloads require different capacity models.

The goal was to prove behavior with thread logs and k6 measurements, not just memorize Reactor rules.

---

# Core Question

What happens if blocking code enters a WebFlux request pipeline?

Working hypothesis:

```text
blocking event-loop thread
→ shared event-loop capacity drops
→ requests queue
→ latency rises
→ throughput falls
→ timeouts/failures may appear later
```

Day 7 proved this experimentally.

---

# Step 1 — Prove Blocking Runs on the Event Loop

Test SKU:

```text
BLOCKING-EVENT-LOOP
```

Validation deliberately used:

```kotlin
Thread.sleep(200)
```

Observed:

```text
event=blocking_operation
operation=validation
thread=reactor-http-nio-3
```

Validation latency was approximately:

```text
210 ms
```

This proved the blocking call executed directly on a Netty event-loop thread.

---

# Why `Thread.sleep()` Is Dangerous

Conceptually:

```text
reactor-http-nio
        ↓
Thread.sleep(200)
        ↓
thread unavailable
        ↓
cannot process other requests
```

Under concurrency:

```text
event-loop workers blocked
        ↓
incoming work waits
        ↓
queueing latency increases
        ↓
tail latency increases
        ↓
throughput falls
```

A small blocking call can create system-wide queueing latency much larger than its own duration.

---

# Non-Blocking Delay Mental Model

Using:

```kotlin
Mono.delay(Duration.ofMillis(200))
```

conceptually means:

```text
schedule timer
    ↓
return control
    ↓
thread becomes available
    ↓
timer completes later
    ↓
pipeline resumes
```

No application worker thread needs to sit idle for the whole wait.

---

# Step 2 — Event-Loop Blocking Under Load

Test:

```text
50 VUs
30 seconds
```

Result:

```text
Total Requests : 1,194
Throughput     : 38.23 RPS
Error Rate     : 0%

Average        : 1.28 s
p50            : 1.22 s
p90            : 1.63 s
p95            : 1.66 s
p99            : 1.91 s
Max            : 2.33 s
```

The important lesson:

```text
200 ms blocking
≠
only 200 ms extra request latency
```

Queueing amplified the impact into 1–2 second latency under concurrency.

---

# Operational Lesson

All requests still returned HTTP 200.

```text
Error rate = 0%
```

But:

```text
p50 ≈ 1.22 s
p95 ≈ 1.66 s
p99 ≈ 1.91 s
```

Therefore:

> A service can remain functionally available while becoming operationally unhealthy.

Error rate alone is not enough. Latency and SLOs matter.

---

# Step 3 — Isolate Blocking Work

Test SKU:

```text
BLOCKING-ISOLATED
```

Implementation:

```kotlin
Mono.fromCallable {
    Thread.sleep(200)
    request
}
.subscribeOn(Schedulers.boundedElastic())
```

Observed thread:

```text
thread=boundedElastic-1
```

The Netty event loop was no longer blocked by this operation.

Important:

> `boundedElastic` does not make blocking code non-blocking.

It isolates blocking work from the event loop.

---

# Step 4 — Equivalent 200-ms Comparison

A third scenario was added:

```text
NONBLOCKING-DELAY
```

using:

```kotlin
Mono.delay(Duration.ofMillis(200))
    .thenReturn(request)
```

Now all three scenarios contained approximately the same extra 200-ms wait.

Only the execution model changed.

---

# Final 50-VU Comparison

| Metric | Non-Blocking `Mono.delay` | Blocking on `boundedElastic` | Blocking on Event Loop |
|---|---:|---:|---:|
| Virtual Users | 50 | 50 | 50 |
| Duration | 30 sec | 30 sec | 30 sec |
| Total Requests | 2,900 | 2,900 | 1,194 |
| Throughput | 95.61 RPS | 95.32 RPS | 38.23 RPS |
| Error Rate | 0% | 0% | 0% |
| Average Latency | 521.49 ms | 521.40 ms | 1.28 s |
| p50 | 516.45 ms | 517.19 ms | 1.22 s |
| p90 | 531.42 ms | 530.46 ms | 1.63 s |
| p95 | 537.03 ms | 533.03 ms | 1.66 s |
| p99 | 547.33 ms | 555.53 ms | 1.91 s |
| Max | 561.17 ms | 580.87 ms | 2.33 s |

Detailed report:

```text
docs/performance-results/order-service/
day-07_2026-09-22_reactive-blocking-comparison_50vu.md
```

---

# Performance Conclusion

```text
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

Event-loop blocking retained only about 40% of the throughput of the healthy implementations.

Isolating the same blocking work improved throughput by roughly 2.5× compared with blocking the event loop.

---

# Why Non-Blocking and boundedElastic Look Similar at 50 VUs

At this load:

```text
Mono.delay
≈ 95.61 RPS

boundedElastic + Thread.sleep
≈ 95.32 RPS
```

But they are not architecturally equivalent.

## Non-Blocking

```text
wait
→ no application worker held for full wait
```

## boundedElastic

```text
wait
→ boundedElastic worker remains occupied
```

The current test did not saturate boundedElastic.

Therefore both looked similar.

This does not prove isolated blocking scales like true non-blocking I/O.

---

# Step 5 — `subscribeOn` vs `publishOn`

Test SKU:

```text
SCHEDULER-TEST
```

Implementation concept:

```kotlin
Mono.fromCallable {
    log.info("stage=source thread={}", Thread.currentThread().name)
    request
}
.subscribeOn(Schedulers.boundedElastic())
.map {
    log.info("stage=before_publishOn thread={}", Thread.currentThread().name)
    it
}
.publishOn(Schedulers.parallel())
.map {
    log.info("stage=after_publishOn thread={}", Thread.currentThread().name)
    it
}
```

Observed:

```text
stage=source
thread=boundedElastic-1

stage=before_publishOn
thread=boundedElastic-1

stage=after_publishOn
thread=parallel-1
```

Mental model:

```text
subscribeOn
→ influences where source/subscription work begins

publishOn
→ changes where downstream work continues
```

---

# Scheduler Selection by Work Type

| Work Type | Typical Reactor Choice |
|---|---|
| Non-blocking HTTP / WebClient | Stay reactive |
| Non-blocking DB driver | Stay reactive |
| Blocking JDBC / legacy SDK / filesystem | `boundedElastic` |
| CPU-heavy work | `parallel` or dedicated CPU scheduler |
| Timer/delay | `Mono.delay` |
| Netty event-loop handling | Keep short; never block |

Schedulers should be chosen according to the nature of the work, not simply because code is slow.

---

# Blocking I/O vs CPU-Heavy Work

## Blocking I/O

Examples:

```text
blocking JDBC
legacy synchronous SDK
filesystem operation
blocking HTTP client
```

The thread spends much of its time waiting.

Typical isolation:

```text
boundedElastic
```

## CPU-Heavy Work

Examples:

```text
encryption
compression
ranking
large calculations
large transformations
```

The thread actively consumes CPU.

Useful parallelism is limited by available CPU capacity.

Typical choice:

```text
Schedulers.parallel()
```

or a dedicated bounded CPU scheduler when workload isolation is required.

---

# Dedicated CPU Scheduler Principle

A dedicated CPU scheduler can isolate expensive CPU workloads.

Example:

```kotlin
val fraudScheduler =
    Schedulers.newParallel(
        "fraud-cpu",
        1
    )
```

Purpose:

```text
isolation
bounded concurrency
predictable blast radius
independent capacity control
```

It does not create additional CPU capacity.

All runnable CPU-heavy threads still compete for the pod's actual CPU.

---

# CPU Capacity Mental Model

For CPU-bound work:

```text
CPU demand
≈ request rate × CPU time per operation
```

Example:

```text
50 operations/sec
×
20 ms CPU/op
=
1 CPU core worth of demand
```

Thread-pool size is not CPU capacity.

Schedulers control concurrency and isolation.

The pod CPU limit determines actual compute capacity.

---

# Saturation Signals

Do not diagnose saturation from one metric.

Look for combinations:

```text
throughput stops increasing
+
p95 rises
+
p99 rises
+
queue grows
+
CPU approaches limit
+
CPU throttling rises
+
timeouts/errors eventually appear
```

A key pattern:

```text
more traffic
→ little extra throughput
→ rapidly increasing latency
```

is strong evidence of saturation.

---

# Memory / RAM Matters

Reactive programming does not make memory unlimited.

Memory is consumed by:

```text
JVM heap
thread stacks
queued tasks
in-flight requests
Netty buffers
Kafka buffers
caches
metrics
class metadata
GC structures
```

Dangerous overload loop:

```text
CPU saturated
    ↓
queue grows
    ↓
memory grows
    ↓
GC increases
    ↓
less CPU available
    ↓
processing slows
    ↓
queue grows further
    ↓
possible OOM
```

Therefore bounded concurrency and bounded queues are important overload controls.

---

# Experiment Discipline

The strongest Day 7 comparison kept equivalent work:

```text
same ~200ms extra wait
same 50 VUs
same service
same request flow
```

and changed only:

```text
execution model
```

This made the architectural conclusion much stronger.

---

# Best Practices Learned

> **Keep Netty event-loop work short and non-blocking.**

> **A small blocking operation on a shared event loop can create much larger queueing latency under concurrency.**

> **Latency degradation can happen long before error rate increases.**

> **Use true non-blocking APIs whenever available.**

> **When blocking work is unavoidable, isolate it explicitly from the event loop.**

> **`boundedElastic` protects the event loop but does not remove blocking or capacity limits.**

> **Choose schedulers according to workload type, not merely because code is slow.**

> **Use `subscribeOn` to control source/subscription execution and `publishOn` to establish downstream execution boundaries.**

> **CPU-heavy work and blocking-I/O work require different scheduling and capacity models.**

> **A scheduler provides concurrency and isolation, not additional CPU.**

> **All schedulers inside one pod share the same CPU and memory budget.**

> **Bound concurrency and queues so sustained overload does not become uncontrolled latency and memory growth.**

> **Validate architecture claims with controlled experiments and equivalent workloads.**

---

# Day 7 Result

Day 7 reactive learning is complete.

```text
[x] Proved blocking execution on reactor-http-nio
[x] Measured event-loop starvation under 50 VUs
[x] Observed queueing amplification
[x] Proved 0% errors can coexist with unhealthy latency
[x] Isolated blocking work using boundedElastic
[x] Verified boundedElastic thread execution
[x] Added true non-blocking Mono.delay comparison
[x] Ran clean three-way 50-VU comparison
[x] Created consolidated performance evidence report
[x] Learned subscribeOn behavior
[x] Learned publishOn behavior
[x] Verified scheduler transitions through logs
[x] Distinguished blocking I/O from CPU-heavy work
[x] Learned parallel vs boundedElastic scheduler purpose
[x] Learned dedicated CPU scheduler rationale
[x] Connected scheduler sizing to pod CPU capacity
[x] Connected overload/queues to RAM and OOM risk
```

## Final Day 7 Conclusion

```text
Reactive scalability depends on protecting shared event-loop capacity.

True non-blocking waiting:
→ event loop remains available

Unavoidable blocking work isolated on boundedElastic:
→ event loop protected
→ blocking capacity still exists and must be managed

Blocking directly on event loop:
→ shared processing capacity lost
→ queueing
→ throughput collapse
→ high p95/p99 latency

Schedulers are workload-isolation and execution tools.
They do not create additional CPU or memory.
```

## Next

Move to the next ArchitectureLab phase and continue applying these reactive rules when integrating real infrastructure such as Redis, databases, external services, and Kafka.
