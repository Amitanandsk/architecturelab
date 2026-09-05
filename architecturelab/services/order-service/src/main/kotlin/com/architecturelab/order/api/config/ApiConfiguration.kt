package com.architecturelab.order.api.config

import com.architecturelab.order.api.GlobalErrorWebExceptionHandler
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class ApiConfiguration {

    @Bean
    fun globalErrorWebExceptionHandler(
        objectMapper: ObjectMapper
    ): ErrorWebExceptionHandler =
        GlobalErrorWebExceptionHandler(objectMapper)
}