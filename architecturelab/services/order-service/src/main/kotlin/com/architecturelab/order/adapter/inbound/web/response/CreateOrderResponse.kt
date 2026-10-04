package com.architecturelab.order.adapter.inbound.web.response

data class CreateOrderResponse(
    val orderId: String,
    val status: String,
    val sku: String,
    val quantity: Int
)