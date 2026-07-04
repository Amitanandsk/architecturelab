# Day 1 — ArchitectureLab Foundation

## Goal
Create the ArchitectureLab multi-module foundation with one working reactive order-service and basic observability logging.

## Completed Checklist
- [x] ArchitectureLab project created
- [x] Gradle multi-module build works
- [x] order-service runs locally
- [x] POST /orders works
- [x] traceId generated
- [x] request start log added
- [x] request completion log added
- [x] service name added in logs
- [x] status code logged
- [x] latencyMs logged
- [x] common-observability used by order-service
- [x] Dockerfile exists
- [ ] docker compose up runs order-service

## Sample Request
```json
{
  "sku": "PROD-1",
  "quantity": 2
}

```
## Sample Response
```json
{
  "data": {
    "orderId": "336bf24b-df9e-4cd8-b72c-3d8a00bc6fe4",
    "status": "CREATED",
    "sku": "PROD-1",
    "quantity": 2
  }
}
```
## Sample Log
```aiignore

event=request_started service=order-service method=POST path=/orders traceId=6938b1e3-71ea-4033-a782-a09b92c18f5e
event=request_completed service=order-service method=POST path=/orders status=200 latencyMs=3 traceId=6938b1e3-71ea-4033-a782-a09b92c18f5e
```

## Learnings

Learning
- WebFlux WebFilter can intercept every request.
- traceId helps correlate logs across services.
- service field is important for multi-service debugging.
- request_completed log is more useful than only request_started because it contains status and latency.

## Next Steps

- [ ] docker compose up runs order-service


## Docker Compose Result
- order-service successfully ran through Docker Compose.
- POST /orders worked from Docker container.
- Container logs showed request_started and request_completed events.
- Observed Docker container timestamp in UTC.

## Final Status
Day 1 completed.