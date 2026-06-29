package com.architecturelab.order.config

import com.architecturelab.observability.TraceIdGenerator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ObservabilityConfig {
    @Bean
    fun traceIdGenerator(): TraceIdGenerator = TraceIdGenerator()
}
