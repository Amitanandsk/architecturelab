package com.architecturelab.order.model

data class CreateOrderResponse(
    val orderId: String,
    val status: String,
    val sku: String,
    val quantity: Int
)
