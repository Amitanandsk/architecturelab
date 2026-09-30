package com.architecturelab.order.adapter.outbound.persistence.r2dbc

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("orders")
data class OrderPersistenceEntity(

    @Id
    @Column("id")
    val orderId: UUID,

    val sku: String,

    val quantity: Int,

    val status: String,

    @Column("created_at")
    val createdAt: Instant,

    @Column("updated_at")
    val updatedAt: Instant
)