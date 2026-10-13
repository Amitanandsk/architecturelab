package com.architecturelab.order.integration.persistence

import com.architecturelab.order.adapter.outbound.persistence.OrderPersistenceAdapter
import com.architecturelab.order.domain.model.Order
import com.architecturelab.order.exception.OrderPersistenceUnavailableException
import com.architecturelab.order.integration.support.PostgresTestConfiguration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.testcontainers.postgresql.PostgreSQLContainer
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Import(PostgresTestConfiguration::class)
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class OrderPersistenceAvailabilityIntegrationTest {

    @Autowired
    lateinit var orderPersistenceAdapter: OrderPersistenceAdapter

    @Autowired
    lateinit var postgresContainer: PostgreSQLContainer


    @Test
    fun `should fail when postgres is unavailable`() {

        val now = Instant.now()

        val order =
            Order(
                orderId = UUID.randomUUID(),
                sku = "DB-DOWN-TEST",
                quantity = 2,
                status = "CREATED",
                createdAt = now,
                updatedAt = now
            )

        postgresContainer.stop()

        StepVerifier
            .create(
                orderPersistenceAdapter.create(order)
            )
            .expectErrorSatisfies { error ->

              assertTrue(error is OrderPersistenceUnavailableException)
               val persistenceError = error as OrderPersistenceUnavailableException
                assertEquals("ORDER_PERSISTENCE_UNAVAILABLE",persistenceError.errorCode)
                assertFalse(persistenceError.retryable)
            }
            .verify()
    }
}