package com.architecturelab.observability

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono


class RequestLoggingWebFilter(
    private val traceIdGenerator: TraceIdGenerator = TraceIdGenerator(),
    private val serviceName: String
) : WebFilter {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {

        val startTime = System.nanoTime()
        val traceId = exchange.request.headers.getFirst(TraceConstants.TRACE_ID_HEADER)
            ?: traceIdGenerator.generate()

        exchange.response.headers.add(TraceConstants.TRACE_ID_HEADER, traceId)
        exchange.attributes[TraceConstants.TRACE_ID] = traceId

        return chain.filter(exchange)
            .contextWrite { context -> context.put(TraceConstants.TRACE_ID_HEADER,traceId) }
        .doFirst {
                MDC.put(TraceConstants.TRACE_ID, traceId)
                logger.info("event={} service={} method={} path={} traceId={}","request_started", serviceName,exchange.request.method, exchange.request.path, traceId)
            }
            .doFinally {


                val latencyMs = (System.nanoTime() - startTime) / 1_000_000
                val status = exchange.response.statusCode?.value()

                logger.info(
                    "event={} service={} method={} path={} status={} latencyMs={} traceId={}",
                    "request_completed",
                    serviceName,
                    exchange.request.method,
                    exchange.request.path.value(),
                    status,
                    latencyMs,
                    traceId
                )
                MDC.remove(TraceConstants.TRACE_ID)
            }
    }
}
