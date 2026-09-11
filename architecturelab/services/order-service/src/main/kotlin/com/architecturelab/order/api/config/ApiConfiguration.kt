package com.architecturelab.order.api.config

import com.architecturelab.order.api.GlobalErrorWebExceptionHandler
import com.architecturelab.order.common.helper.StepMeasurement
import org.springframework.beans.factory.annotation.Value
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

    @Bean
    fun stepMeasurement(@Value("\${spring.application.name}")
                        serviceName: String
    ): StepMeasurement =
        StepMeasurement(serviceName)
}