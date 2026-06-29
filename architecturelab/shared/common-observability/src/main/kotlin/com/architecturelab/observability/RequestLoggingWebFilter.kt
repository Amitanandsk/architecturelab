package com.architecturelab.observability

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
class RequestLoggingWebFilter(
    private val traceIdGenerator: TraceIdGenerator = TraceIdGenerator()
) : WebFilter {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val traceId = exchange.request.headers.getFirst(TraceConstants.TRACE_ID_HEADER)
            ?: traceIdGenerator.generate()

        exchange.response.headers.add(TraceConstants.TRACE_ID_HEADER, traceId)
        exchange.attributes[TraceConstants.TRACE_ID] = traceId

        return chain.filter(exchange)
            .doFirst {
                MDC.put(TraceConstants.TRACE_ID, traceId)
                logger.info("request_started method={} path={} traceId={}", exchange.request.method, exchange.request.path, traceId)
            }
            .doFinally {
                logger.info("request_finished status={} traceId={}", exchange.response.statusCode?.value(), traceId)
                MDC.remove(TraceConstants.TRACE_ID)
            }
    }
}
