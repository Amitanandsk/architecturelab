package com.architecturelab.order.domain.model

import java.time.Instant
import java.util.UUID

data class Order(
    val orderId: UUID,
    val sku: String,
    val quantity: Int,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant
)