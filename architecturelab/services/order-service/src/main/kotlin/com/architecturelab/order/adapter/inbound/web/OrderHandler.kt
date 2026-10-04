package com.architecturelab.order.adapter.inbound.web

import com.architecturelab.order.application.CreateOrderService
import com.architecturelab.order.adapter.inbound.web.request.CreateOrderRequest
import com.architecturelab.order.adapter.inbound.web.response.CreateOrderResponse
import com.architecturelab.order.adapter.inbound.web.response.OrderResponse.Companion.toResponse
import com.architecturelab.order.application.GetOrderService
import com.architecturelab.order.exception.InvalidOrderIdException
import com.architecturelab.web.ApiResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyToMono
import reactor.core.publisher.Mono
import java.util.UUID

@Component
class OrderHandler(
    private val createOrderService: CreateOrderService,
    private val getOrderService: GetOrderService
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

    fun getOrder(
        request: ServerRequest
    ): Mono<ServerResponse> {

        return   Mono.fromCallable {
            UUID.fromString(
            request.pathVariable("orderId")
        ) }
            .onErrorMap(IllegalArgumentException::class.java) {
                InvalidOrderIdException()
            }
            .flatMap {  getOrderService.getOrder(it)}
            .map { it.toResponse() }
            .flatMap { response ->
                ServerResponse
                    .ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(ApiResponse(response))
            }
    }

}