package com.architecturelab.order.adapter.inbound.web.response

import com.architecturelab.order.domain.model.Order

data class OrderResponse(
    val orderId: String,
    val sku: String,
    val quantity: Int,
    val status: String
){
companion object {
    fun Order.toResponse() =
        OrderResponse(
            orderId = orderId.toString(),
            sku = sku,
            quantity = quantity,
            status = status
        )
}}