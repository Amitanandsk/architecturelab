# ArchitectureLab Instructions

This is a Principal Engineer Architecture Lab, not a business product.

Main goal:
- Learn and implement non-functional engineering patterns deeply.
- Focus on scalability, observability, reliability, resilience, Redis, Elasticsearch, Kafka, gRPC, authorization, load testing, and performance.

Rules:
- Keep business logic thin.
- Prefer Kotlin + Spring WebFlux + Project Reactor.
- Do not use blocking calls in reactive flows.
- Do not use `.block()` in application code.
- Use Gradle Kotlin DSL.
- Use Java 17.
- Use multi-module Gradle structure.
- Each service must be independently runnable.
- Each service should have its own Dockerfile.
- Shared modules should contain only cross-cutting technical code.
- Do not put business domain logic in shared modules.
- Use structured logging.
- Propagate traceId/correlationId.
- Add tests for important behavior.
- Keep changes small and explain tradeoffs.
