package com.architecturelab.order.integration.persistence

import com.architecturelab.order.adapter.outbound.persistence.OrderPersistenceAdapter
import com.architecturelab.order.domain.model.Order
import com.architecturelab.order.integration.support.PostgresTestConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Import(PostgresTestConfiguration::class)
class OrderPersistenceAdapterIntegrationTest {

    @Autowired
    lateinit var orderPersistenceAdapter: OrderPersistenceAdapter


    @Test
    fun `should create and find order in postgres`() {

        val now = Instant.now()

        val order =
            Order(
                orderId = UUID.randomUUID(),
                sku = "PERSIST-TEST-1",
                quantity = 2,
                status = "CREATED",
                createdAt = now,
                updatedAt = now
            )

        StepVerifier
            .create(
                orderPersistenceAdapter.create(order)
            )
            .assertNext { savedOrder ->

                assertEquals(
                    order.orderId,
                    savedOrder.orderId
                )

                assertEquals(
                    order.sku,
                    savedOrder.sku
                )

                assertEquals(
                    order.quantity,
                    savedOrder.quantity
                )
            }
            .verifyComplete()

        StepVerifier
            .create(
                orderPersistenceAdapter.findById(order.orderId)
            )
            .assertNext { foundOrder ->

                assertEquals(
                    order.orderId,
                    foundOrder.orderId
                )

                assertEquals(
                    order.sku,
                    foundOrder.sku
                )

                assertEquals(
                    order.quantity,
                    foundOrder.quantity
                )
            }
            .verifyComplete()
    }
}