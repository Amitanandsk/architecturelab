package com.architecturelab.order.config

import com.architecturelab.observability.RequestLoggingWebFilter
import com.architecturelab.observability.TraceIdGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ObservabilityConfig( @Value("\${spring.application.name:unknown-service}")
                           private val serviceName: String){


    @Bean
    fun traceIdGenerator(): TraceIdGenerator = TraceIdGenerator()


    @Bean
    fun requestLoggingWebFilter(): RequestLoggingWebFilter {
        return RequestLoggingWebFilter(traceIdGenerator = traceIdGenerator(), serviceName = serviceName)
    }

}
