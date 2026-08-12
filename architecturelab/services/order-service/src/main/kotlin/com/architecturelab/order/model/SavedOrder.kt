package com.architecturelab.order.model

data class SavedOrder(
    val orderId: String,
    val sku: String,
    val quantity: Int
)