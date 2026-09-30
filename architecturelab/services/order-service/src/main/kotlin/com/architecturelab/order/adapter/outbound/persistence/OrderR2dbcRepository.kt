package com.architecturelab.order.adapter.outbound.persistence

import com.architecturelab.order.adapter.outbound.persistence.r2dbc.OrderPersistenceEntity
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import java.util.UUID

interface OrderR2dbcRepository :
    ReactiveCrudRepository<OrderPersistenceEntity, UUID>
// this can extend custom interface also like OrderCustomRepository apart from inbuilt ReactiveCrudRepository
// and can include derive query function also like findByStatus