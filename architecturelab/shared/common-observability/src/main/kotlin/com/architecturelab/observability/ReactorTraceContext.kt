package com.architecturelab.observability

import reactor.core.publisher.Mono


object ReactorTraceContext {
    fun currentTraceId(): Mono<String> {
        return Mono.deferContextual { context ->
            val traceId =
                if (context.hasKey(TraceConstants.TRACE_ID)) {
                    context.get<String>(TraceConstants.TRACE_ID)
                } else {
                    "missing-trace-id"
                }

            Mono.just(traceId)
        }
    }

}