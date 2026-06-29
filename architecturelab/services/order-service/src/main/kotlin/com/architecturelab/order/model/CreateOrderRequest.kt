package com.architecturelab.order.model

data class CreateOrderRequest(
    val sku: String,
    val quantity: Int
)
