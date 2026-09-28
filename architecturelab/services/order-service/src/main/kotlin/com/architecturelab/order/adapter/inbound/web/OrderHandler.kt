package com.architecturelab.order.adapter.inbound.web

import com.architecturelab.order.application.CreateOrderService
import com.architecturelab.order.model.CreateOrderRequest
import com.architecturelab.order.model.CreateOrderResponse
import com.architecturelab.web.ApiResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyToMono
import reactor.core.publisher.Mono

@Component
class OrderHandler(
    private val createOrderService: CreateOrderService
) {

    fun createOrder(
        request: ServerRequest
    ): Mono<ServerResponse> {

        return request
            .bodyToMono<CreateOrderRequest>()
            .flatMap(createOrderService::createOrder)
            .map(::ApiResponse)
            .flatMap(::okResponse)
    }

    private fun okResponse(
        response: ApiResponse<CreateOrderResponse>
    ): Mono<ServerResponse> {

        return ServerResponse
            .ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(response)
    }
}