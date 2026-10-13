package com.architecturelab.order.adapter.outbound.persistence

import com.architecturelab.order.adapter.outbound.persistence.r2dbc.OrderPersistenceEntity
import com.architecturelab.order.application.port.outbound.OrderRepositoryPort
import com.architecturelab.order.domain.model.Order
import com.architecturelab.order.exception.OrderAlreadyExistsException
import com.architecturelab.order.exception.OrderDataIntegrityException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.UUID

@Component
class OrderPersistenceAdapter(
    private val entityTemplate: R2dbcEntityTemplate,
    private val repository: OrderR2dbcRepository
) : OrderRepositoryPort {

    override fun create(
        order: Order
    ): Mono<Order> {

        val entity = order.toEntity()

        return entityTemplate
            .insert(entity)
            .map { it.toDomain() }
            .onErrorMap { error ->
                translateCreateError(
                    error = error,
                    orderId = order.orderId) }
    }

    override fun findById(
        orderId: UUID
    ): Mono<Order> {

        return repository
            .findById(orderId)
            .map { it.toDomain() }
    }
}


private fun translateCreateError(
    error: Throwable,
    orderId: UUID
): Throwable =
    when (error) {

        is DuplicateKeyException ->
            OrderAlreadyExistsException(
                orderId = orderId,
                cause = error
            )

        is DataIntegrityViolationException ->
            OrderDataIntegrityException(
                cause = error
            )

        else ->
            error
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