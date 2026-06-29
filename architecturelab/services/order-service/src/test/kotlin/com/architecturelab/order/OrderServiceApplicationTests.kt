package com.architecturelab.order

import com.architecturelab.order.model.CreateOrderRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.web.reactive.server.WebTestClient.bindToApplicationContext
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest
@SpringJUnitConfig
class OrderServiceApplicationTests(
    @Autowired private val applicationContext: ApplicationContext
) {

    @Test
    fun contextLoads() {
    }

    @Test
    fun createsOrderResponseAndPropagatesTraceId() {
        val webTestClient = bindToApplicationContext(applicationContext).build()

        webTestClient.post()
            .uri("/orders")
            .header("X-Trace-Id", "trace-123")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(CreateOrderRequest(sku = "BOOK-1", quantity = 2))
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("X-Trace-Id", "trace-123")
            .expectBody()
            .jsonPath("$.data.status").isEqualTo("CREATED")
            .jsonPath("$.data.sku").isEqualTo("BOOK-1")
            .jsonPath("$.data.quantity").isEqualTo(2)
    }
}
