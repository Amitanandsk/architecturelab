# Performance Experiment — Order Service Baseline — 50 VUs

## 1. Experiment Information

**Learning Day:** Day 6  
**Date:** 2026-09-16  
**Service:** order-service  
**Scenario:** Normal successful order creation  
**Experiment Type:** Baseline / Load Comparison  
**Test Tool:** k6  
**Load:** 50 Virtual Users  
**Duration:** 30 seconds  
**Code Under Test:** `aa859e7f`

---

# 2. Objective

Measure how the current `order-service` behaves when concurrency increases from 10 VUs to 50 VUs while keeping the code, request payload, delays, and environment unchanged.

The experiment should answer:

- Does throughput scale as concurrency increases?
- Do p50 and p95 remain stable?
- Does p99 begin to increase?
- Does the error rate remain at 0%?
- Is there evidence that the service is approaching saturation?

---

# 3. Hypothesis

Increasing concurrency from 10 VUs to 50 VUs should increase throughput significantly.

If the service is not saturated:

- throughput should scale approximately with concurrency,
- p50 and p95 should remain relatively stable,
- error rate should remain 0%.

Tail latency may begin to increase due to runtime or machine-level contention.

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

Current characteristics:

- Kotlin
- Spring Boot
- Spring WebFlux
- Project Reactor
- Reactor Context trace propagation
- Step-level latency logging
- Reactive timeout/error handling
- Retry support
- No real database
- No Redis
- No Kafka
- Simulated persistence and inventory latency

---

# 5. Test Scenario

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

Expected application path:

```text
validate_request
→ persist_order
→ inventory_check
→ HTTP 200
```

`PROD-1` is deterministic and should not trigger retries or failures.

---

# 6. Workload Configuration

```text
Virtual Users : 50
Duration      : 30 seconds
Think Time    : None
Load Model    : Constant VUs
```

Each VU repeatedly performs:

```text
send request
→ wait for response
→ send next request
```

This experiment controls concurrency, not a fixed RPS.

---

# 7. Success Criteria

```text
HTTP responses          : 200
Checks                  : 100% successful
HTTP request failures   : 0%
```

No latency threshold is enforced yet because the objective is still baseline and scaling analysis.

---

# 8. Results

| Metric | 10 VUs | 50 VUs |
|---|---:|---:|
| Total Requests | 961 | **4,790** |
| Throughput | 31.74 RPS | **158.03 RPS** |
| Error Rate | 0% | **0%** |
| Successful Checks | 100% | **100%** |
| Average Latency | 312.14 ms | **315.13 ms** |
| p50 Latency | 311.21 ms | **311.16 ms** |
| p90 Latency | 314.51 ms | **315.15 ms** |
| p95 Latency | 322.59 ms | **323.22 ms** |
| p99 Latency | 332.15 ms | **475.97 ms** |
| Max Latency | 335.43 ms | **952.53 ms** |

---

# 9. Raw k6 Summary

```text
checks_total.......: 4790
checks_succeeded...: 100.00%
checks_failed......: 0.00%

http_req_duration:
avg   = 315.13 ms
min   = 304.29 ms
med   = 311.16 ms
p(90) = 315.15 ms
p(95) = 323.22 ms
p(99) = 475.97 ms
max   = 952.53 ms
count = 4790

http_req_failed:
0.00%

http_reqs:
4790
158.028634 requests/sec

iteration_duration:
avg   = 315.52 ms
min   = 304.29 ms
med   = 311.39 ms
p(90) = 315.64 ms
p(95) = 323.30 ms
p(99) = 476.01 ms
max   = 952.71 ms
```

---

# 10. Throughput Analysis

Concurrency increased:

```text
10 VUs → 50 VUs
```

This is a 5× increase.

Baseline throughput:

```text
10 VUs
→ 31.74 RPS
```

If throughput scaled perfectly linearly:

```text
31.74 × 5
≈ 158.7 RPS
```

Observed:

```text
158.03 RPS
```

Therefore throughput scaled almost proportionally with the increase in concurrency.

At this load, there is no evidence of broad throughput saturation.

---

# 11. Latency Analysis

Median latency remained essentially unchanged:

```text
10 VUs → 311.21 ms
50 VUs → 311.16 ms
```

p95 also remained almost unchanged:

```text
10 VUs → 322.59 ms
50 VUs → 323.22 ms
```

This means the majority of requests continue to complete with approximately the same latency even after concurrency increased by 5×.

However, tail latency increased:

```text
p99:
332.15 ms
→ 475.97 ms
```

Maximum latency increased significantly:

```text
335.43 ms
→ 952.53 ms
```

This shows that a small percentage of requests experienced much higher latency.

---

# 12. Tail Latency Observation

The key Day 6 observation at 50 VUs is:

```text
p50 stable
p95 stable
p99 increased
max increased significantly
```

This may indicate occasional scheduling, runtime, logging, JVM, OS, or local-machine contention.

However, the current data is not sufficient to identify the cause.

We should not yet conclude that:

```text
Reactor event loops are saturated
CPU is saturated
GC is causing the spike
logging is the bottleneck
```

Those require system-level measurements.

---

# 13. Error Analysis

```text
Total Requests = 4,790
Successful     = 4,790
Failed         = 0
Error Rate     = 0%
```

No application errors were observed.

The normal success path remained reliable under 50 concurrent VUs.

---

# 14. Saturation Evidence

At 50 VUs:

```text
Throughput scaling       → healthy
p50                      → stable
p95                      → stable
Error rate               → 0%
p99                      → increased
Maximum latency          → increased significantly
```

This does not yet prove saturation.

It does show the first tail-latency degradation that should be monitored as load increases.

---

# 15. Comparison with 10-VU Baseline

```text
10 VUs
31.74 RPS
p50 = 311.21 ms
p95 = 322.59 ms
p99 = 332.15 ms
max = 335.43 ms
errors = 0%

             ↓ 5× concurrency

50 VUs
158.03 RPS
p50 = 311.16 ms
p95 = 323.22 ms
p99 = 475.97 ms
max = 952.53 ms
errors = 0%
```

The service converted the additional concurrency into almost proportional throughput while preserving normal-request latency.

Tail latency is the first area showing degradation.

---

# 16. Conclusion

At **50 concurrent virtual users**, `order-service` processed:

```text
4,790 requests
158.03 RPS
0% failures
```

with:

```text
p50 = 311.16 ms
p95 = 323.22 ms
p99 = 475.97 ms
```

Throughput scaled almost linearly compared with the 10-VU baseline.

The majority of requests remained stable because p50 and p95 changed very little.

However, p99 and maximum latency increased, indicating occasional high-latency requests.

There is not yet enough evidence to identify the cause or conclude that the service is saturated.

---

# 17. What This Experiment Does NOT Prove

This test does not prove:

```text
maximum service capacity
maximum sustainable RPS
CPU saturation
event-loop saturation
GC pressure
production readiness
behavior with real persistence
behavior with real downstream services
behavior during long-duration load
```

Additional tests and runtime metrics are required.

---

# 18. Decision

Do not optimize yet.

Increase concurrency again while keeping all other variables unchanged.

Continue observing:

```text
throughput scaling
p50
p95
p99
maximum latency
error rate
```

---

# 19. Next Experiment

Change only:

```text
Virtual Users:
50 → 100
```

Keep unchanged:

```text
same application code
same Git commit
same endpoint
same payload
same simulated delays
same logging
same machine
same k6 script behavior
same duration
```

The next experiment should determine whether throughput continues to scale and whether tail latency degradation becomes more pronounced.

---

# 20. Principal Engineer Takeaways

> **Increasing concurrency does not automatically imply saturation. Look for throughput flattening, increasing latency, growing tail latency, errors, or resource saturation.**

> **p50 and p95 can remain healthy while p99 starts degrading. Tail latency can therefore provide an earlier warning than averages.**

> **Do not diagnose the cause of latency degradation using latency measurements alone. Correlate with CPU, memory, GC, event-loop behavior, connection pools, queues, logging, and downstream metrics.**

> **When comparing performance experiments, keep the implementation and environment unchanged and vary one major factor at a time.**

> **Performance conclusions should state what the evidence proves and explicitly avoid claiming what was not measured.**