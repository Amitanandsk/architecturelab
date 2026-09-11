package com.architecturelab.order.common.helper

import com.architecturelab.observability.ReactorTraceContext
import com.architecturelab.order.exception.ApplicationException
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

class StepMeasurement( private val serviceName : String){


       private val logger = LoggerFactory.getLogger("StepMeasurement")
       fun <T : Any> measureStep(
           stepName: String,
           action: () -> Mono<T>
       ): Mono<T> {
           return ReactorTraceContext.currentTraceId()
               .flatMap { traceId ->
                   Mono.defer {
                       val start = System.nanoTime()

                       action()
                           .doOnSuccess {
                               val latencyMs = (System.nanoTime() - start) / 1_000_000
                               logger.info(
                                   "event=step_completed service={} step={} outcome=success latencyMs={} traceId={}",
                                   serviceName,
                                   stepName,
                                   latencyMs,
                                   traceId
                               )
                           }
                           .doOnError { error ->
                               val latencyMs = (System.nanoTime() - start) / 1_000_000

                               val applicationError =
                                   error as? ApplicationException

                               logger.warn(
                                   "event=step_failed service={} step={} outcome=error errorCode={} errorType={} retryable={} latencyMs={} traceId={}",
                                   serviceName,
                                   stepName,
                                   applicationError?.errorCode
                                       ?: "UNCLASSIFIED_ERROR",
                                   error.javaClass.simpleName
                                       ?: "",
                                   applicationError?.retryable
                                       ?: false,

                                   latencyMs,
                                   traceId
                               )
                           }
                   }
               }
       }


   }
