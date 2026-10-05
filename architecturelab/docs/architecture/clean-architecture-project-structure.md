# ArchitectureLab — Clean Architecture Project Structure

## Purpose

ArchitectureLab uses a **Clean Architecture / Ports-and-Adapters style**.

The core rule is:

> Business/application logic depends on abstractions it owns. HTTP, databases, Redis, Kafka, and frameworks stay outside the core.

Dependency direction:

```text
External world
    ↓
Inbound Adapter
    ↓
Application / Use Cases
    ↓
Domain

Application
    ↓
Outbound Port
    ↑
Outbound Adapter implements it
    ↓
Database / Redis / Kafka / external service
```

The inner layers do not depend on outer implementation details.

---

## Clean package structure

```text
com.architecturelab.order
│
├── domain/
│   └── model/
│       └── Order.kt
│
├── application/
│   ├── service/
│   │   ├── CreateOrderService.kt
│   │   └── GetOrderService.kt
│   │
│   └── port/
│       ├── inbound/
│       │   ├── CreateOrderUseCase.kt
│       │   └── GetOrderUseCase.kt
│       │
│       └── outbound/
│           └── OrderRepositoryPort.kt
│
├── adapter/
│   ├── inbound/
│   │   └── web/
│   │       ├── OrderRouter.kt
│   │       ├── OrderHandler.kt
│   │       ├── request/
│   │       │   └── CreateOrderRequest.kt
│   │       └── response/
│   │           ├── CreateOrderResponse.kt
│   │           └── ApiResponse.kt
│   │
│   └── outbound/
│       └── persistence/
│           └── r2dbc/
│               ├── OrderPersistenceEntity.kt
│               ├── OrderR2dbcRepository.kt
│               ├── OrderPersistenceAdapter.kt
│               └── OrderPersistenceMapper.kt
│
└── config/
    └── ...
```

---

## Responsibilities

### `domain/`

Contains business/domain concepts.

Examples:

```text
Order
OrderStatus
Money
```

Rules:

- no Spring annotations
- no R2DBC annotations
- no HTTP classes
- no Kafka classes
- no persistence-only fields unless they are genuine domain concepts

The domain model answers:

> What does the business/application understand?

---

### `application/`

Contains use cases and application orchestration.

Examples:

```text
CreateOrderService
GetOrderService
CreateOrderUseCase
OrderRepositoryPort
```

The application layer may depend on:

```text
domain
application-owned interfaces
```

It must NOT depend on:

```text
Spring Data R2DBC
PostgreSQL classes
ServerRequest / ServerResponse
KafkaTemplate
RedisTemplate
WebClient implementation details
```

---

### `application/port/inbound`

Defines what the application can do.

Example:

```kotlin
interface CreateOrderUseCase {
    fun createOrder(command: CreateOrderCommand): Mono<Order>
}
```

The web adapter calls this port.

---

### `application/port/outbound`

Defines what the application needs from external systems.

Example:

```kotlin
interface OrderRepositoryPort {
    fun save(order: Order): Mono<Order>
    fun findById(orderId: UUID): Mono<Order>
}
```

The application owns this contract.

It does not know whether the implementation uses PostgreSQL, MySQL, MongoDB,
an external API, or an in-memory test adapter.

---

### `adapter/inbound/web`

Converts HTTP into application calls and application results back into HTTP.

Contains:

```text
Router
Handler
HTTP request DTO
HTTP response DTO
HTTP mapper
```

It may depend on Spring WebFlux.

It should not contain database logic.

---

### `adapter/outbound/persistence`

Implements persistence ports.

Contains:

```text
R2DBC entity
Spring Data repository
Persistence adapter
Persistence mapper
```

Dependency:

```text
CreateOrderService
    ↓
OrderRepositoryPort
    ↑ implemented by
OrderPersistenceAdapter
    ↓
OrderR2dbcRepository
    ↓
PostgreSQL
```

The application never imports `OrderPersistenceAdapter` or `OrderR2dbcRepository`.

Spring injects the implementation at runtime.

---

## Separate models by responsibility

Do not force one class to represent every layer.

### Domain model

```kotlin
data class Order(
    val orderId: UUID,
    val sku: String,
    val quantity: Int,
    val status: OrderStatus
)
```

### Web request model

```kotlin
data class CreateOrderRequest(
    val sku: String,
    val quantity: Int
)
```

### Web response model

```kotlin
data class CreateOrderResponse(
    val orderId: String,
    val status: String
)
```

### Persistence model

```kotlin
@Table("orders")
data class OrderPersistenceEntity(
    @Id
    @Column("id")
    val orderId: UUID,
    val sku: String,
    val quantity: Int,
    val status: String
)
```

These models may look similar initially, but separate boundaries prevent
HTTP/database concerns from leaking into the domain.

---

## Mapper placement

A small persistence-only mapper may initially stay beside the persistence adapter.

Example:

```kotlin
private fun Order.toEntity() =
    OrderPersistenceEntity(
        orderId = orderId,
        sku = sku,
        quantity = quantity,
        status = status.name
    )
```

When mapping grows or is reused, extract:

```text
OrderPersistenceMapper.kt
```

inside:

```text
adapter/outbound/persistence/r2dbc/
```

Do not put persistence mapping inside the domain layer.

---

## Dependency rule

Allowed:

```text
adapter → application → domain
adapter → application ports
```

Avoid:

```text
domain → Spring
application → R2DBC
application → PostgreSQL
application → web handler
domain → persistence entity
```

The application owns its ports.
Outer adapters implement those ports.

---

## Why this structure matters

It gives us:

```text
framework independence
database replaceability
testability
clear ownership
smaller blast radius of infrastructure changes
better separation of business vs transport vs persistence concerns
```

Example:

Today:

```text
OrderRepositoryPort
    ↑
PostgreSQL R2DBC adapter
```

Tomorrow:

```text
OrderRepositoryPort
    ↑
different SQL adapter
```

The application use case does not need to change if the contract still satisfies its needs.

---

## Naming guidance

There is no single universal package/class naming standard for Clean Architecture.

ArchitectureLab uses these conventions:

```text
*UseCase              inbound application contract
*Service              use-case implementation
*Port                 outbound application contract
*Handler              HTTP functional handler
*Router               HTTP route configuration
*PersistenceEntity    database representation
*R2dbcRepository      Spring Data R2DBC repository
*PersistenceAdapter   implementation of repository port
*Mapper               mapping between layer-specific models
```

Consistency matters more than claiming one naming scheme is universal.

---

## README guidance

Keep the detailed structure in a separate file:

```text
docs/architecture/clean-architecture-structure.md
```

Then add a short README link:

```markdown
## Architecture

See [Clean Architecture Structure](docs/architecture/clean-architecture-project-structure.md).
```

This keeps README concise while preserving a durable architecture reference.

---

## Principal Engineer rule

> The core application should describe business/use-case intent. External technologies should plug into it through explicit adapters and ports, not become the architecture itself.
