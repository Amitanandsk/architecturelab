package com.architecturelab.order.application.port.outbound

import com.architecturelab.order.domain.model.Order
import reactor.core.publisher.Mono
import java.util.UUID

interface OrderRepositoryPort {

    fun create(order: Order): Mono<Order>

    fun findById(orderId: UUID): Mono<Order>
}