package com.architecturelab.order.adapter.inbound.web

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router

@Configuration
class OrderRouter(
    private val orderHandler: OrderHandler
) {

    @Bean
    fun orderRoutes(): RouterFunction<ServerResponse> =
        router {

            POST(
                "/orders",
                orderHandler::createOrder
            )
        }
}