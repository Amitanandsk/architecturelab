package com.architecturelab.order.integration.persistence

import com.architecturelab.order.adapter.outbound.persistence.OrderPersistenceAdapter
import com.architecturelab.order.domain.model.Order
import com.architecturelab.order.exception.OrderAlreadyExistsException
import com.architecturelab.order.exception.OrderDataIntegrityException
import com.architecturelab.order.integration.support.PostgresTestConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID
import kotlin.test.assertTrue

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


    @Test
    fun `should return empty when order does not exist`() {

        val unknownOrderId = UUID.randomUUID()

        StepVerifier
            .create(
                orderPersistenceAdapter.findById(unknownOrderId)
            )
            .expectNextCount(0)
            .verifyComplete()
    }

    @Test
    fun `should reject order when quantity violates database constraint`() {

        val now = Instant.now()

        val order =
            Order(
                orderId = UUID.randomUUID(),
                sku = "INVALID-QTY",
                quantity = 0,
                status = "CREATED",
                createdAt = now,
                updatedAt = now
            )

        StepVerifier
            .create(
                orderPersistenceAdapter.create(order)
            )
            .expectError(OrderDataIntegrityException::class.java)
            .verify()
    }

    @Test
    fun `should reject duplicate order id`() {

        val now = Instant.now()

        val order =
            Order(
                orderId = UUID.randomUUID(),
                sku = "DUPLICATE-TEST",
                quantity = 2,
                status = "CREATED",
                createdAt = now,
                updatedAt = now
            )

        StepVerifier
            .create(
                orderPersistenceAdapter.create(order)
            )
            .expectNextCount(1)
            .verifyComplete()

        StepVerifier
            .create(
                orderPersistenceAdapter.create(order)
            )
           // .expectError(OrderAlreadyExistsException::class.java) or write as below
            .expectErrorSatisfies {error ->
                assertTrue(error is OrderAlreadyExistsException)
                assertEquals("ORDER_ALREADY_EXISTS",error.errorCode)
                assertEquals("Order ${order.orderId} already exists",error.safeMessage)
            }
            .verify()
    }
}