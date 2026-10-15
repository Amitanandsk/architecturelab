package com.architecturelab.order.integration.persistence

import com.architecturelab.order.integration.support.PostgresTestConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.r2dbc.core.DatabaseClient
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.TimeoutException

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Import(PostgresTestConfiguration::class)
class PostgresLatencyIntegrationTest{
    @Autowired
    lateinit var databaseClient: DatabaseClient


    @Test
    fun `should execute slow postgres query`() {

        val slowQuery =
            databaseClient
                .sql(
                    """
                SELECT 1 AS result
                FROM pg_sleep(1)
                """.trimIndent()
                )
                .map { row, _ ->
                    row.get("result", Integer::class.java) as Int
                }
                .one()

        StepVerifier
            .create(slowQuery)
            .expectNext(1)
            .verifyComplete()
    }

    @Test
    fun `should timeout when postgres query exceeds application budget`() {

        val slowQuery =
            databaseClient
                .sql(
                    """
                SELECT 1 AS result
                FROM pg_sleep(2)
                """.trimIndent()
                )
                .map { row, _ ->
                    row.get("result", Integer::class.java) as Int
                }
                .one()
                .timeout(
                    Duration.ofMillis(300)
                )

        StepVerifier
            .create(slowQuery)
            .expectError(TimeoutException::class.java)
            .verify()
    }
}