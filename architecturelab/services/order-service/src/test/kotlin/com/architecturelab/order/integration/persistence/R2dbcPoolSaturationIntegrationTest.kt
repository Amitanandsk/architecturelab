package com.architecturelab.order.integration.persistence

import com.architecturelab.order.integration.support.PostgresTestConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.annotation.DirtiesContext
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.r2dbc.pool.initial-size=0",
        "spring.r2dbc.pool.max-size=2",
        "spring.r2dbc.pool.max-acquire-time=300ms",
        "spring.r2dbc.pool.acquire-retry=0"
    ]
)
@Import(PostgresTestConfiguration::class)
@DirtiesContext(
    classMode = DirtiesContext.ClassMode.AFTER_CLASS
)
class R2dbcPoolSaturationIntegrationTest {

    @Autowired
    lateinit var databaseClient: DatabaseClient


    private fun slowQuery(
        value: Int
    ): Mono<Int> =
        databaseClient
            .sql(
                """
            SELECT :value AS result
            FROM pg_sleep(2)
            """.trimIndent()
            )
            .bind("value", value)
            .map { row, _ ->
                row.get(
                    "result",
                    Int::class.java
                )!!
            }
            .one()


    @Test
    fun `should fail connection acquisition when pool is saturated`() {

        val concurrentQueries =
            Flux.mergeDelayError(
                3,
                slowQuery(1),
                slowQuery(2),
                slowQuery(3)
            )

        StepVerifier
            .create(concurrentQueries)
            .expectErrorSatisfies { error ->

                println(
                    "TYPE    = ${error::class.qualifiedName}"
                )

                println(
                    "MESSAGE = ${error.message}"
                )

                println(
                    "CAUSE   = ${error.cause?.javaClass?.name}"
                )
            }
            .verify()
    }
}