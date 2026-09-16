# Performance Experiment — Order Service Baseline — 10 VUs

## 1. Experiment Information

**Learning Day:** Day 6  
**Date:** 2026-09-16  
**Service:** order-service  
**Scenario:** Normal successful order creation  
**Experiment Type:** Baseline  
**Test Tool:** k6  
**Load:** 10 Virtual Users  
**Duration:** 30 seconds  
**Code Commit:** `<add git rev-parse --short HEAD output>`

---

# 2. Objective

Establish the first repeatable performance baseline for the current `order-service` implementation before introducing additional infrastructure or performance optimizations.

The experiment should answer:

- What throughput does the current service produce with 10 concurrent virtual users?
- What are p50, p95, and p99 request latencies?
- Are requests completing successfully?
- Is tail latency already increasing significantly?
- Is there any visible evidence of saturation at this load?

This baseline will later be compared against higher concurrency and architectural changes such as Redis, resilience mechanisms, messaging, gRPC, and other optimizations.

---

# 3. Hypothesis

At 10 concurrent virtual users:

- Normal `PROD-1` order creation should succeed consistently.
- HTTP error rate should remain 0%.
- Latency should remain relatively stable.
- No significant saturation should be visible at this low concurrency.

---

# 4. System Under Test

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

Current implementation characteristics:

- Kotlin
- Spring Boot
- Spring WebFlux
- Project Reactor
- Reactor Context trace propagation
- Step-level latency logging
- Reactive timeout/error handling
- Retry support
- No real database yet
- No Redis
- No Kafka
- Simulated persistence and inventory latency

---

# 5. Test Scenario

Request:

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

`PROD-1` is deterministic and follows the successful path.

Expected:

```text
validate_request
→ persist_order
→ inventory_check
→ HTTP 200
```

No intentional failure, retry, or timeout is expected.

---

# 6. Workload Configuration

```text
Virtual Users : 10
Duration      : 30 seconds
Think Time    : None
Load Model    : Constant VUs
```

Each virtual user repeatedly performs:

```text
send request
→ wait for response
→ send next request
```

Therefore this experiment controls **concurrency**, not a fixed requests-per-second arrival rate.

---

# 7. Success Criteria

```text
HTTP responses: 200
Checks: 100% successful
HTTP request failures: 0%
```

No latency SLO is enforced yet because this experiment establishes the initial baseline.

---

# 8. Results

| Metric | Result |
|---|---:|
| Virtual Users | 10 |
| Test Duration | ~30 sec |
| Total Requests | 961 |
| Throughput | **31.74 RPS** |
| HTTP Error Rate | **0%** |
| Successful Checks | **100%** |
| Average Latency | **312.14 ms** |
| Median / p50 | **311.21 ms** |
| p90 Latency | **314.51 ms** |
| p95 Latency | **322.59 ms** |
| p99 Latency | **332.15 ms** |
| Maximum Latency | **335.43 ms** |

---

# 9. k6 Execution Results

```text
checks_total.......: 961
checks_succeeded...: 100.00%
checks_failed......: 0.00%

http_req_duration:
avg   = 312.14 ms
min   = 306.17 ms
med   = 311.21 ms
p(90) = 314.51 ms
p(95) = 322.59 ms
p(99) = 332.15 ms
max   = 335.43 ms

http_req_failed:
0.00%

http_reqs:
961
31.735125 requests/sec

iteration_duration:
avg   = 312.72 ms
med   = 311.40 ms
p(95) = 322.94 ms
p(99) = 343.47 ms
max   = 372.45 ms
```

---

# 10. Latency Interpretation

The latency distribution is narrow:

```text
p50 = 311.21 ms
p95 = 322.59 ms
p99 = 332.15 ms
max = 335.43 ms
```

Difference between p50 and p99:

```text
~21 ms
```

This means that at this workload there is currently no significant tail-latency amplification.

Most requests are completing in roughly the same latency range.

---

# 11. Why Request Latency Is Around 312 ms

The result is consistent with the current simulated architecture.

Approximate request composition:

```text
validation
≈ very small

persistence
≈ 100 ms

inventory
≈ 200 ms

HTTP + Reactor + logging/framework overhead
≈ small additional latency
```

Therefore:

```text
~100 ms
+
~200 ms
+
framework overhead
≈ 312 ms
```

The external k6 measurement therefore aligns with the internal architecture.

---

# 12. Throughput Interpretation

Measured:

```text
10 concurrent VUs
average latency ≈ 312 ms
throughput ≈ 31.74 RPS
```

The workload uses constant virtual users rather than a fixed arrival rate.

Therefore throughput emerges from:

```text
concurrency
+
request completion time
```

If requests become faster, the same 10 VUs can generate more RPS.

If requests become slower, the same 10 VUs generate fewer RPS.

Important:

> 10 virtual users does not mean 10 requests per second.

---

# 13. Error Analysis

```text
Total requests = 961
Successful      = 961
Failed          = 0
Error rate      = 0%
```

No:

- application failures
- retries
- dependency timeouts
- workflow timeouts

were expected for this normal success scenario.

---

# 14. Saturation Evidence

At this workload, the test does not show obvious evidence of saturation.

Observed:

```text
Stable p50
Stable p95
Stable p99
0% failures
Narrow latency spread
```

No conclusion can yet be made about:

- CPU saturation
- memory pressure
- GC pressure
- event-loop saturation
- maximum throughput
- production capacity

Those require additional experiments and metrics.

---

# 15. Conclusion

At **10 concurrent virtual users**, the current order-service completed:

```text
961 requests
31.74 RPS
0% failures
```

with:

```text
p50 = 311.21 ms
p95 = 322.59 ms
p99 = 332.15 ms
```

Latency remained stable and tightly distributed.

No significant saturation is visible at this workload.

The measured latency is consistent with the current simulated persistence and inventory delays.

---

# 16. What This Experiment Does NOT Prove

This test does **not** prove that order-service can sustain:

```text
50 concurrent users
100 concurrent users
100 RPS
500 RPS
1000 RPS
production traffic
long-running traffic
multiple service replicas
real database traffic
real inventory-service traffic
```

It establishes only the first controlled baseline.

---

# 17. Decision

Do not optimize the system yet.

Increase concurrency while keeping the remaining experiment variables unchanged.

This allows us to determine when:

```text
throughput stops scaling proportionally
p95 increases
p99 increases
errors appear
```

and therefore identify the beginning of saturation.

---

# 18. Next Experiment

Change only:

```text
Virtual Users:
10 → 50
```

Keep unchanged:

```text
duration
endpoint
payload
service implementation
simulated delays
logging
timeout settings
machine
k6 test structure
```

Compare:

```text
throughput
average
p50
p95
p99
max
error rate
```

---

# 19. Principal Engineer Takeaways

> **Never optimize without first establishing a measurable baseline.**

> **Change one major experiment variable at a time when trying to establish causality.**

> **Concurrency and throughput are different: throughput emerges from concurrency and latency unless the workload explicitly controls arrival rate.**

> **p95 and p99 are essential because averages can hide tail-latency degradation.**

> **Performance claims should be limited to exactly what the experiment proves.**

> **A valuable performance result should be reproducible and traceable to the exact code version that generated it.**