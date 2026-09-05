package com.architecturelab.order.api

import com.architecturelab.observability.TraceConstants
import com.architecturelab.order.exception.ApplicationException
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler
import org.springframework.core.Ordered
import org.springframework.http.MediaType
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

class GlobalErrorWebExceptionHandler(
    private val objectMapper: ObjectMapper
) : ErrorWebExceptionHandler, Ordered {

    override fun getOrder(): Int =
        Ordered.HIGHEST_PRECEDENCE

    override fun handle(
        exchange: ServerWebExchange,
        error: Throwable
    ): Mono<Void> {

        // Response already started; we cannot safely replace it.
        if (exchange.response.isCommitted) {
            return Mono.error(error)
        }

        // This handler currently owns our application errors only.
        if (error !is ApplicationException) {
            return Mono.error(error)
        }

        val httpError =
            HttpErrorMapper.map(error)

        val traceId =
            exchange.getAttribute<String>(
                TraceConstants.TRACE_ID
            ) ?: "missing-trace-id"

        val apiErrorResponse =
            ApiErrorResponse(
                errorCode = httpError.errorCode,
                message = httpError.message,
                traceId = traceId
            )

        exchange.response.statusCode =
            httpError.status

        exchange.response.headers.contentType =
            MediaType.APPLICATION_JSON

        return Mono.fromCallable {
            objectMapper.writeValueAsBytes(apiErrorResponse)
        }.flatMap { bytes ->

            val buffer =
                exchange.response
                    .bufferFactory()
                    .wrap(bytes)

            exchange.response.writeWith(
                Mono.just(buffer)
            )
        }
    }
}