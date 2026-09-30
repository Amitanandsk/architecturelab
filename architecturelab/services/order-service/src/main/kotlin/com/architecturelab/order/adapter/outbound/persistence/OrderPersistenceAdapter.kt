package com.architecturelab.order.adapter.outbound.persistence

import com.architecturelab.order.adapter.outbound.persistence.r2dbc.OrderPersistenceEntity
import com.architecturelab.order.application.port.outbound.OrderRepositoryPort
import com.architecturelab.order.domain.model.Order
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.UUID

@Component
class OrderPersistenceAdapter(
    private val repository: OrderR2dbcRepository
) : OrderRepositoryPort {

    override fun save(
        order: Order
    ): Mono<Order> {

        val entity = order.toEntity()

        return repository
            .save(entity)
            .map { it.toDomain() }
    }

    override fun findById(
        orderId: UUID
    ): Mono<Order> {

        return repository
            .findById(orderId)
            .map { it.toDomain() }
    }
}

private fun Order.toEntity() =
    OrderPersistenceEntity(
        orderId = orderId,
        sku = sku,
        quantity = quantity,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

private fun OrderPersistenceEntity.toDomain() =
    Order(
        orderId = orderId,
        sku = sku,
        quantity = quantity,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )