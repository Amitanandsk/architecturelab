package com.architecturelab.order.observability

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

@Component
class OrderErrorMetrics(private val  meterRegistry: MeterRegistry) {

    fun record(
        errorCode: String,
        status: HttpStatus
    ) {
        meterRegistry
            .counter(
                "order.errors",
                "error_code", errorCode,
                "status", status.value().toString()
            )
            .increment()
    }

}