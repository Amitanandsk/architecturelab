package com.architecturelab.order.application

import com.architecturelab.order.application.port.outbound.OrderRepositoryPort
import com.architecturelab.order.domain.model.Order
import com.architecturelab.order.exception.OrderNotFoundException
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.UUID

@Service
class GetOrderService(
    private val orderRepositoryPort: OrderRepositoryPort
) {

    fun getOrder(
        orderId: UUID
    ): Mono<Order> {

        return orderRepositoryPort
            .findById(orderId)
            .switchIfEmpty(
                Mono.error(
                    OrderNotFoundException(orderId)
                ))
    }
}