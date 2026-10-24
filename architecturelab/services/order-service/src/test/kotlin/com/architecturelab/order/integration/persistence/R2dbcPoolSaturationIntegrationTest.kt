package com.architecturelab.order.integration.persistence

import com.architecturelab.order.adapter.outbound.persistence.OrderPersistenceAdapter
import com.architecturelab.order.exception.OrderPersistenceSaturatedException
import com.architecturelab.order.integration.support.PostgresTestConfiguration
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.ConnectionFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.annotation.DirtiesContext
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.r2dbc.pool.initial-size=2",
        "spring.r2dbc.pool.max-size=2",
        "spring.r2dbc.pool.max-acquire-time=500ms",
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

    @Autowired
    lateinit var connectionFactory: ConnectionFactory

    @Autowired
    lateinit var orderPersistenceAdapter: OrderPersistenceAdapter

    @Autowired
    lateinit var connectionPool: ConnectionPool


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

        val twoConnections =
            connectionPool
                .warmup()
                .then(
            Mono.zip(
                Mono.from(connectionFactory.create()),
                Mono.from(connectionFactory.create())
            ))
        val operationWhilePoolIsFull =
            Mono.usingWhen(
                twoConnections,

                { connections ->

                    // Both pooled connections are currently checked out.
                    // This call therefore needs a third connection.
                    orderPersistenceAdapter
                        .findById(UUID.randomUUID())
                        .then()
                },

                { connections ->

                    Mono.`when`(
                        Mono.from(connections.t1.close()),
                    Mono.from(connections.t2.close())
                    )
                }
            )

        StepVerifier
            .create(operationWhilePoolIsFull)
            .expectError(
                OrderPersistenceSaturatedException::class.java
            )
            .verify()
    }
}