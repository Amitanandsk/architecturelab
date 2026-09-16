# Performance Experiment — Order Service Baseline — 100 VUs

## 1. Experiment Information

**Learning Day:** Day 6
**Date:** 2026-09-16
**Service:** order-service
**Scenario:** Normal successful order creation
**Experiment Type:** Baseline / Load Scaling
**Test Tool:** k6
**Load:** 100 Virtual Users
**Duration:** 30 seconds
**Code Under Test:** `aa859e7f`

---

# 2. Objective

Determine whether `order-service` continues scaling as concurrency increases from 50 to 100 VUs while keeping application code, payload, delays, environment, and test behavior unchanged.

Key questions:

* Does throughput continue scaling approximately proportionally?
* Do p50 and p95 remain stable?
* Does the p99 increase observed at 50 VUs continue?
* Do errors begin appearing?
* Is there evidence of service saturation?

---

# 3. Workload

```text
Virtual Users : 100
Duration      : 30 seconds
Think Time    : None
Load Model    : Constant VUs
```

Request:

```json
{
  "sku": "PROD-1",
  "quantity": 1
}
```

Expected flow:

```text
POST /orders
    ↓
validate_request
    ↓
persist_order
    ↓
inventory_check
    ↓
HTTP 200
```

---

# 4. Results

| Metric          |    10 VUs |     50 VUs |        100 VUs |
| --------------- | --------: | ---------: | -------------: |
| Total Requests  |       961 |      4,790 |      **9,636** |
| Throughput      | 31.74 RPS | 158.03 RPS | **318.09 RPS** |
| Error Rate      |        0% |         0% |         **0%** |
| Average Latency | 312.14 ms |  315.13 ms |  **312.06 ms** |
| p50             | 311.21 ms |  311.16 ms |  **311.15 ms** |
| p90             | 314.51 ms |  315.15 ms |  **314.87 ms** |
| p95             | 322.59 ms |  323.22 ms |  **323.05 ms** |
| p99             | 332.15 ms |  475.97 ms |  **330.48 ms** |
| Max             | 335.43 ms |  952.53 ms |  **363.27 ms** |

---

# 5. Raw k6 Summary

```text
Total Requests : 9,636
Throughput     : 318.093504 RPS
Error Rate     : 0%

avg            : 312.06 ms
min            : 302.53 ms
median         : 311.15 ms
p90            : 314.87 ms
p95            : 323.05 ms
p99            : 330.48 ms
max            : 363.27 ms
```

All 9,636 checks succeeded.

---

# 6. Throughput Analysis

Concurrency increased by 10× compared with the original baseline:

```text
10 VUs
→
100 VUs
```

Initial throughput:

```text
31.74 RPS
```

Expected throughput if scaling remained approximately linear:

```text
31.74 × 10
≈ 317.4 RPS
```

Observed:

```text
318.09 RPS
```

Throughput therefore remained almost perfectly proportional to concurrency.

There is currently no evidence of throughput flattening through 100 VUs.

---

# 7. Latency Analysis

Normal request latency remained remarkably stable.

```text
p50:
10 VUs  = 311.21 ms
50 VUs  = 311.16 ms
100 VUs = 311.15 ms
```

```text
p95:
10 VUs  = 322.59 ms
50 VUs  = 323.22 ms
100 VUs = 323.05 ms
```

Increasing concurrency by 10× did not materially increase p50 or p95 latency.

---

# 8. Re-evaluation of the 50-VU Tail Spike

The 50-VU test showed:

```text
p99 = 475.97 ms
max = 952.53 ms
```

At 100 VUs:

```text
p99 = 330.48 ms
max = 363.27 ms
```

Therefore, the 50-VU tail spike did not continue as concurrency increased.

It should not currently be interpreted as evidence of saturation.

Possible explanations include transient:

* OS scheduling
* JVM runtime activity
* logging contention
* machine activity
* load-generator activity
* other environmental variation

No specific cause has been proven.

Repeated tests and runtime metrics would be required to diagnose it.

---

# 9. Error Analysis

```text
Total Requests = 9,636
Successful     = 9,636
Failed         = 0
Error Rate     = 0%
```

No retries, timeouts, or application errors were expected for the deterministic `PROD-1` path.

---

# 10. Concurrency / Throughput Relationship

Measured:

```text
Throughput ≈ 318 requests/sec
Latency    ≈ 0.312 sec
```

Approximate in-flight concurrency:

```text
318 × 0.312
≈ 99
```

This closely matches the configured:

```text
100 VUs
```

The experiment therefore demonstrates the relationship between:

```text
Concurrency
Throughput
Latency
```

For this constant-VU workload, throughput emerges from the number of concurrent users and how quickly requests complete.

---

# 11. Saturation Assessment

At 100 VUs:

```text
Throughput     → scaling proportionally
p50            → stable
p95            → stable
p99            → stable
Errors         → 0%
```

No broad saturation signal is currently visible.

However, this conclusion applies only to the current simulated ArchitectureLab implementation.

---

# 12. Important Limitation

Current persistence and inventory operations use simulated non-blocking latency.

The test does not yet include real constraints such as:

```text
database connection pools
database locks
network connections
real downstream services
Redis connections
Kafka brokers
CPU-heavy processing
external API limits
```

Therefore this result should not be interpreted as production capacity.

It primarily demonstrates the concurrency characteristics of the current reactive/non-blocking implementation.

---

# 13. Conclusion

At 100 concurrent virtual users, `order-service` processed:

```text
9,636 requests
318.09 RPS
0% errors
```

with:

```text
p50 = 311.15 ms
p95 = 323.05 ms
p99 = 330.48 ms
```

Throughput continued to scale almost linearly while normal and tail latency remained stable.

The p99 spike observed during the 50-VU run was not reproduced at higher concurrency and therefore should not currently be treated as a saturation signal.

No visible broad saturation has been demonstrated through 100 VUs.

---

# 14. What This Experiment Does NOT Prove

It does not establish:

* maximum capacity,
* production readiness,
* real database scalability,
* connection-pool behavior,
* CPU saturation point,
* memory/GC limits,
* long-duration stability,
* real downstream-service capacity,
* sustainable production RPS.

Those require separate experiments.

---

# 15. Principal Engineer Takeaways

> **Performance anomalies must be reproducible before they are treated as architectural evidence.**

> **A single p99 spike is a signal to investigate, not proof of saturation.**

> **Throughput, concurrency, and latency must be analyzed together rather than as isolated metrics.**

> **Stable p50/p95/p99 while throughput scales proportionally is strong evidence that broad saturation has not yet been reached under the tested workload.**

> **Synthetic non-blocking dependencies demonstrate concurrency behavior but do not establish real production capacity.**

> **Performance conclusions must always state the exact workload and limitations under which they were proven.**
