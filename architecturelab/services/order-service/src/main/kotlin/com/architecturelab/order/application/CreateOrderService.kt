package com.architecturelab.order.application

import com.architecturelab.order.model.CreateOrderRequest
import com.architecturelab.order.model.CreateOrderResponse
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.UUID

@Service
class CreateOrderService {

    fun create(request: CreateOrderRequest): Mono<CreateOrderResponse> {
        return Mono.just(
            CreateOrderResponse(
                orderId = UUID.randomUUID().toString(),
                status = "CREATED",
                sku = request.sku,
                quantity = request.quantity
            )
        )
    }
}
