package com.architecturelab.order.common.helper

import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono


    private val logger = LoggerFactory.getLogger("StepMeasurement")
     fun <T: Any > measureStep(
        stepName: String,
        traceId: String,
        action:()-> Mono<T>
    ): Mono<T> {
        return Mono.defer {
            val start = System.nanoTime()

            action().doFinally {
                val latencyMs = (System.nanoTime() - start) / 1_000_000
                logger.info(
                    "event=order_step_completed service=order-service step={} latencyMs={} traceId={}",
                    stepName,
                    latencyMs,
                    traceId
                )
            }
        }
    }
