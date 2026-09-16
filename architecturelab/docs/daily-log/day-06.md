# Day 6 — Performance Baseline with k6

## Goal

Establish a repeatable performance baseline for `order-service` before adding real infrastructure such as Redis, Kafka, databases, or external services.

The focus was not to find the absolute maximum capacity of the service.

The goals were to learn how to:

- create a controlled load test,
- measure throughput and latency,
- interpret p50 / p95 / p99,
- distinguish concurrency from throughput,
- compare multiple load levels,
- identify early signs of saturation,
- avoid over-interpreting one anomalous run,
- preserve reproducible performance evidence.

---

# Tools Used

- k6
- Kotlin
- Spring Boot
- Spring WebFlux
- Project Reactor
- Git
- Markdown performance reports

---

# Test Scenario

Endpoint:

```text
POST /orders
```

Payload:

```json
{
  "sku": "PROD-1",
  "quantity": 1
}
```

`PROD-1` was deterministic:

```text
PROD-1
→ validation success
→ persistence success
→ inventory success
→ HTTP 200
```

No retry, timeout, or intentional failure was expected.

---

# Current Order Flow

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

Current latency is synthetic.

Approximate simulated flow:

```text
validation
≈ negligible

persistence
≈ 100 ms

inventory
≈ 200 ms
```

Therefore total request latency is expected to be around:

```text
~300 ms + framework/logging overhead
```

---

# k6 Baseline Script

The reusable k6 test was created under:

```text
infra/k6/order-baseline.js
```

The test uses constant virtual users.

Conceptually:

```text
VU sends request
    ↓
waits for response
    ↓
immediately sends next request
```

There is no artificial think time.

Important:

> A constant-VU test controls concurrency, not requests per second.

Throughput emerges from:

```text
concurrency
+
request latency
```

---

# Test 1 — 10 VUs

Configuration:

```text
Virtual Users : 10
Duration      : 30 seconds
```

Results:

| Metric | Result |
|---|---:|
| Total Requests | 961 |
| Throughput | 31.74 RPS |
| Error Rate | 0% |
| Average Latency | 312.14 ms |
| p50 | 311.21 ms |
| p90 | 314.51 ms |
| p95 | 322.59 ms |
| p99 | 332.15 ms |
| Max | 335.43 ms |

Observation:

```text
Latency distribution was narrow.
No visible saturation.
0% failures.
```

---

# Test 2 — 50 VUs

Configuration:

```text
Virtual Users : 50
Duration      : 30 seconds
```

Results:

| Metric | Result |
|---|---:|
| Total Requests | 4,790 |
| Throughput | 158.03 RPS |
| Error Rate | 0% |
| Average Latency | 315.13 ms |
| p50 | 311.16 ms |
| p90 | 315.15 ms |
| p95 | 323.22 ms |
| p99 | 475.97 ms |
| Max | 952.53 ms |

Observation:

Throughput scaled almost proportionally:

```text
10 VUs  → 31.74 RPS
50 VUs  → 158.03 RPS
```

p50 and p95 remained stable.

However:

```text
p99 increased
max latency increased significantly
```

This looked like a possible early tail-latency signal.

Important decision:

> Do not conclude saturation from one anomalous run.

The cause could have been temporary JVM activity, OS scheduling, logging contention, local machine activity, load-generator activity, or other runtime noise.

---

# Test 3 — 100 VUs

Configuration:

```text
Virtual Users : 100
Duration      : 30 seconds
```

Results:

| Metric | Result |
|---|---:|
| Total Requests | 9,636 |
| Throughput | 318.09 RPS |
| Error Rate | 0% |
| Average Latency | 312.06 ms |
| p50 | 311.15 ms |
| p90 | 314.87 ms |
| p95 | 323.05 ms |
| p99 | 330.48 ms |
| Max | 363.27 ms |

Observation:

Throughput again scaled almost linearly.

```text
10 VUs   → 31.74 RPS
50 VUs   → 158.03 RPS
100 VUs  → 318.09 RPS
```

The original 10-VU baseline predicts approximately:

```text
31.74 × 10
≈ 317.4 RPS
```

Actual at 100 VUs:

```text
318.09 RPS
```

This is almost perfectly proportional.

Latency remained stable:

```text
p50:
10 VUs   → 311.21 ms
50 VUs   → 311.16 ms
100 VUs  → 311.15 ms

p95:
10 VUs   → 322.59 ms
50 VUs   → 323.22 ms
100 VUs  → 323.05 ms
```

The 50-VU p99 spike was not reproduced.

Therefore it should not currently be treated as evidence of saturation.

---

# Final Comparison

| Metric | 10 VUs | 50 VUs | 100 VUs |
|---|---:|---:|---:|
| Throughput | 31.74 RPS | 158.03 RPS | 318.09 RPS |
| Error Rate | 0% | 0% | 0% |
| Average | 312.14 ms | 315.13 ms | 312.06 ms |
| p50 | 311.21 ms | 311.16 ms | 311.15 ms |
| p95 | 322.59 ms | 323.22 ms | 323.05 ms |
| p99 | 332.15 ms | 475.97 ms | 330.48 ms |
| Max | 335.43 ms | 952.53 ms | 363.27 ms |

---

# Main Result

Under the current synthetic non-blocking Reactor workload:

```text
10 VUs
→ 31.74 RPS

50 VUs
→ 158.03 RPS

100 VUs
→ 318.09 RPS
```

with:

```text
0% errors
stable p50
stable p95
stable p99 at 100 VUs
```

No broad saturation was visible through 100 concurrent VUs.

---

# Concurrency vs Throughput

One of the most important Day 6 lessons:

```text
10 VUs
does NOT mean
10 requests/sec
```

A VU repeatedly:

```text
sends request
→ waits for response
→ sends next request
```

For the 100-VU test:

```text
throughput ≈ 318 requests/sec
latency    ≈ 0.312 sec
```

Approximate concurrency:

```text
318 × 0.312
≈ 99
```

This closely matches:

```text
100 VUs
```

This demonstrated the relationship between concurrency, throughput, and latency.

---

# Why p95 and p99 Matter

Average latency alone can hide slow requests.

Example:

```text
95% requests may be healthy
while
1% of requests become extremely slow
```

Therefore performance analysis should include:

```text
average
p50
p95
p99
max
error rate
throughput
```

The 50-VU test demonstrated why p99 is useful.

It exposed a tail spike that average and p95 mostly hid.

The 100-VU test then demonstrated another important lesson:

> One anomalous tail result is not enough to diagnose saturation.

---

# Saturation Signals to Watch

Future performance experiments should look for combinations such as:

```text
throughput stops scaling
+
p95 rises
+
p99 rises sharply
+
errors appear
+
resource utilization increases
```

Potential saturation sources include:

- CPU,
- memory,
- GC,
- Reactor event loop,
- connection pools,
- database connections,
- queues,
- logging,
- downstream dependencies,
- network limits.

Latency numbers alone cannot identify the exact cause.

---

# Important Limitation of Current Test

Current persistence and inventory latency are simulated using non-blocking Reactor operations such as:

```text
Mono.delay(...)
```

This means:

```text
100 waiting requests
does not mean
100 blocked threads
```

The test demonstrates useful reactive concurrency behavior.

However, it does not establish production capacity because there is currently no real:

```text
database
database connection pool
Redis
Kafka
WebClient downstream service
network dependency
CPU-heavy work
external system constraint
```

Therefore:

> The test proves the behavior of the current lab implementation only.

---

# Why We Stopped at 100 VUs

Running 200 / 500 / 1000 VUs was not necessary at this stage.

Because dependencies are still synthetic and non-blocking, a larger test may mostly discover limits of:

```text
developer laptop
OS scheduling
console logging
JVM environment
k6 running locally
```

rather than a meaningful architecture bottleneck.

Higher-load testing becomes more valuable when real resource constraints are introduced.

Examples:

```text
real database
Redis
real inventory-service call
Kafka
connection pool
rate limiter
bulkhead
CPU-heavy operation
```

---

# Performance Evidence Convention

Performance reports are stored in Git.

Recommended naming pattern:

```text
day-XX_YYYY-MM-DD_<component>_<scenario>_<load>.md
```

Examples:

```text
day-06_2026-09-16_order-service_baseline_10vu.md
day-06_2026-09-16_order-service_baseline_50vu.md
day-06_2026-09-16_order-service_baseline_100vu.md
```

Each important report should record:

```text
learning day
date
service/component
scenario
load
code-under-test Git SHA
test configuration
results
analysis
conclusion
limitations
next experiment
PE takeaway
```

---

# Why Record the Git SHA

The report should identify the exact code version under test.

Example:

```text
Code Under Test: a7c42e1
```

This allows future comparison:

```text
baseline implementation
a7c42e1
p95 = 323 ms

later Redis implementation
f32de90
p95 = ...
```

The purpose is reproducibility and traceability.

---

# Experiment Discipline Learned

## Change One Major Variable at a Time

Example:

```text
10 VUs
→ 50 VUs
→ 100 VUs
```

Everything else remained unchanged.

This helps establish causality.

If code, load, machine, timeout, logging, payload, and dependencies all change together, it becomes difficult to know why performance changed.

## Do Not Optimize Before Measuring

Wrong:

```text
Add Redis because Redis is fast.
```

Better:

```text
Measure baseline
        ↓
identify bottleneck
        ↓
introduce architectural change
        ↓
rerun same experiment
        ↓
compare evidence
```

## Do Not Overstate Performance Claims

Correct:

> No broad saturation was visible through 100 concurrent VUs under the current synthetic non-blocking workload.

Incorrect:

> The service can handle production traffic.

The experiment did not prove production readiness.

---

# Principal Engineer Best Practices

> **Never optimize without first establishing a measurable baseline.**

> **Performance claims should be limited to exactly what the experiment proves.**

> **Concurrency and throughput are different concepts. Throughput emerges from concurrency and latency unless arrival rate is explicitly controlled.**

> **Use p95 and p99 to understand tail behavior; averages alone are insufficient.**

> **A single anomalous performance result is a signal to investigate, not proof of a bottleneck.**

> **Saturation should be identified using multiple signals: throughput, latency, errors, queues, and resource metrics.**

> **Keep performance experiments reproducible by recording workload, environment, code-under-test Git SHA, and test configuration.**

> **Change one major experiment variable at a time when trying to understand cause and effect.**

> **Synthetic non-blocking delays are useful for learning concurrency behavior but do not establish production capacity.**

---

# Day 6 Result

Day 6 is complete.

Completed:

```text
[x] Installed and ran k6
[x] Created reusable order-service baseline script
[x] Used deterministic successful workload
[x] Ran 10-VU baseline
[x] Ran 50-VU comparison
[x] Ran 100-VU comparison
[x] Measured throughput
[x] Measured p50 / p95 / p99
[x] Measured error rate
[x] Learned concurrency vs throughput
[x] Learned tail-latency interpretation
[x] Re-evaluated anomalous p99 using another experiment
[x] Learned saturation evidence discipline
[x] Created reusable performance-report convention
[x] Added Git traceability for performance evidence
[x] Documented experiment limitations
```

## Final Day 6 Conclusion

```text
Current synthetic non-blocking order-service
scaled approximately linearly from:

10 VUs
→ 31.74 RPS

50 VUs
→ 158.03 RPS

100 VUs
→ 318.09 RPS

with 0% errors and stable normal latency.

No broad saturation was demonstrated through 100 VUs.

This is a baseline for the current lab implementation,
not a production-capacity claim.
```

## Next

Move to the next ArchitectureLab topic and retain this baseline for future before/after comparisons when real infrastructure and performance constraints are introduced.
