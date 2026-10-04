package com.architecturelab.order.adapter.inbound.web.request

data class CreateOrderRequest(
    val sku: String,
    val quantity: Int
)