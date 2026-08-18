package com.architecturelab.order.api

import com.architecturelab.observability.TraceConstants
import com.architecturelab.order.application.CreateOrderService
import com.architecturelab.order.model.CreateOrderRequest
import com.architecturelab.order.model.CreateOrderResponse
import com.architecturelab.web.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/orders")
class OrderController(
    private val createOrderService: CreateOrderService
) {

    @PostMapping
fun createOrder(@RequestBody request: CreateOrderRequest,exchange: ServerWebExchange): Mono<ApiResponse<CreateOrderResponse>> {
       /* val traceId =
            exchange.attributes[TraceConstants.TRACE_ID] as String*/
    return  Mono.deferContextual { context ->
        createOrderService.createOrder(request)
    }.map(::ApiResponse)
}
}
