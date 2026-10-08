package com.architecturelab.order.integration.api

import com.architecturelab.order.application.port.outbound.OrderRepositoryPort
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureWebTestClient
class OrderApiIntegrationTest {

    @Autowired
    lateinit var webTestClient: WebTestClient
    @Autowired
    lateinit var orderRepositoryPort: OrderRepositoryPort

    companion object {

        @Container
        @ServiceConnection
        @JvmField
        val postgres =
            PostgreSQLContainer(
                "postgres:17-alpine"
            )
                .withDatabaseName("architecture_lab")
                .withUsername("architecture_user")
                .withPassword("architecture_password")
    }

    @Test
    fun `should create order and persist it`() {


            webTestClient
                .post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """
                {
                  "sku": "PROD-1",
                  "quantity": 2
                }
                """.trimIndent()
                )
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.data.sku")
                .isEqualTo("PROD-1")
                .jsonPath("$.data.quantity")
                .isEqualTo(2)
                .jsonPath("$.data.status")
                .isEqualTo("CREATED")
                .jsonPath("$.data.orderId")
                .value<String> { orderId ->

                    val savedOrder =
                        orderRepositoryPort
                            .findById(UUID.fromString(orderId))
                            .block()

                    assertNotNull(savedOrder)
                    assertEquals("PROD-1", savedOrder?.sku)
                    assertEquals(2, savedOrder?.quantity)
                    assertEquals("CREATED", savedOrder?.status)
                }
    }


    @Test
    fun `should get existing order`() {

        var createdOrderId: String? = null

        webTestClient
            .post()
            .uri("/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
            {
              "sku": "PROD-2",
              "quantity": 3
            }
            """.trimIndent()
            )
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.data.orderId")
            .value<String> { createdOrderId = it }

        webTestClient
            .get()
            .uri("/orders/$createdOrderId")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.data.orderId")
            .isEqualTo(createdOrderId!!)
            .jsonPath("$.data.sku")
            .isEqualTo("PROD-2")
            .jsonPath("$.data.quantity")
            .isEqualTo(3)
            .jsonPath("$.data.status")
            .isEqualTo("CREATED")
    }

    @Test
    fun `should return 404 when order does not exist`() {

        val unknownOrderId =
            "11111111-1111-1111-1111-111111111111"

        webTestClient
            .get()
            .uri("/orders/$unknownOrderId")
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.errorCode")
            .isEqualTo("ORDER_NOT_FOUND")
            .jsonPath("$.message")
            .isEqualTo("Order $unknownOrderId was not found")
            .jsonPath("$.traceId")
            .exists()
    }

    @Test
    fun `should return 400 when order id is invalid`() {

        webTestClient
            .get()
            .uri("/orders/abc")
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.errorCode")
            .isEqualTo("INVALID_ORDER_ID")
            .jsonPath("$.message")
            .isEqualTo("Order id is invalid")
            .jsonPath("$.traceId")
            .exists()
    }
}