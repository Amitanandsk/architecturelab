package com.architecturelab.order.application

import com.architecturelab.observability.ReactorTraceContext
import com.architecturelab.observability.TraceConstants
import com.architecturelab.order.common.helper.StepMeasurement
import com.architecturelab.order.exception.ApplicationException
import com.architecturelab.order.exception.DependencyTimeoutException
import com.architecturelab.order.exception.InventoryUnavailableException
import com.architecturelab.order.exception.OrderProcessingTimeoutException
import com.architecturelab.order.exception.OrderValidationException
import com.architecturelab.order.exception.UnexpectedApplicationException
import com.architecturelab.order.model.CreateOrderRequest
import com.architecturelab.order.model.CreateOrderResponse
import com.architecturelab.order.model.SavedOrder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeoutException

@Service
class CreateOrderService(val stepMeasurement: StepMeasurement) {
    val log = LoggerFactory.getLogger(this::class.java)

    /*fun create(request: CreateOrderRequest): Mono<CreateOrderResponse> {
        return Mono.just(
            CreateOrderResponse(
                orderId = UUID.randomUUID().toString(),
                status = "CREATED",
                sku = request.sku,
                quantity = request.quantity
            )
        )
    }*/


    fun createOrder(request: CreateOrderRequest): Mono<CreateOrderResponse> {

        return Mono.deferContextual { context ->

            val traceId =
                context.getOrDefault<String>(
                    TraceConstants.TRACE_ID,
                    "missing-trace-id"
                ) ?: "missing-trace-id"


            if(request.sku == "UNEXPECTED-ERROR"){
              Mono.error(
                IllegalStateException(
                    "Simulated unexpected failure"
                )
            )
        }
        else {
            validateRequest(request)
            .flatMap { validRequest ->
                persistOrder(validRequest)
            }
            .flatMap { savedOrder ->
                checkInventory(savedOrder)
            }
            .map { savedOrder ->
                CreateOrderResponse(
                    orderId = savedOrder.orderId,
                    status = "CREATED",
                    sku = savedOrder.sku,
                    quantity = savedOrder.quantity
                )
            }}
            .timeout(Duration.ofMillis(2000))
            .onErrorMap(TimeoutException::class.java, {error -> OrderProcessingTimeoutException(error) })
            .onErrorMap({error ->
                when(error) {
                is ApplicationException -> error
                else -> UnexpectedApplicationException(error)
            }   })

            .doOnError { error ->

                        log.warn(
                        "event=order_creation_failed service=order-service error={} traceId={}",
                        error.javaClass.simpleName,
                        traceId
                    )

                    }
                }

    }


    private fun validateRequest(
    request: CreateOrderRequest
): Mono<CreateOrderRequest> {
    return stepMeasurement.measureStep("validate_request") {
        if (request.quantity <= 0) {
            Mono.error(OrderValidationException("Quantity must be greater than zero"))
        } else {
            Mono.just(request)
        }
    }
}



    private fun persistOrder(
        request: CreateOrderRequest
    ): Mono<SavedOrder> {
               return stepMeasurement.measureStep("persist_order") {

                   val delay =
                       if (request.sku == "SLOW-ORDER") {
                           Duration.ofMillis(2500)
                       } else {
                           Duration.ofMillis(100)
                       }
                    Mono.delay(delay)
                        .map {
                            SavedOrder(
                                orderId = UUID.randomUUID().toString(),
                                sku = request.sku,
                                quantity = request.quantity
                            )
                        }
                }
    }


    private fun checkInventory(
        savedOrder: SavedOrder
    ): Mono<SavedOrder> {
        return ReactorTraceContext.currentTraceId()
            .flatMap { traceId ->
               stepMeasurement.measureStep("inventory_check") {
                   val delay =
                       if (savedOrder.sku == "TIMEOUT-INVENTORY") {
                           Duration.ofMillis(500)
                       } else {
                           Duration.ofMillis(200)
                       }

                   Mono.delay(delay)
                        .flatMap {
                            val random = Math.random()
                            when {
                                savedOrder.sku == "FAIL-INVENTORY" -> {
                                    Mono.error(InventoryUnavailableException())
                                }

                                savedOrder.sku == "FLAKY-INVENTORY" && random < 0.6 -> {
                                    Mono.error(InventoryUnavailableException())
                                }

                                else -> {
                                    Mono.just(savedOrder)
                                }
                            }
                        }
                       .timeout(Duration.ofMillis(250))
                       .onErrorMap(TimeoutException::class.java){ error ->
                           DependencyTimeoutException(
                               dependency = "inventory-service",
                               cause = error
                           )
                       }
                        .retryWhen(

                            Retry.backoff(2, Duration.ofMillis(100))
                                .filter { error -> (error as? ApplicationException)?.retryable==true }
                                .doAfterRetry {
                                    log.info(
                                        "event=order_retry service=order-service attempt={} reason={} traceId={}",
                                        it.totalRetries() + 1,
                                        it.failure().javaClass.simpleName,
                                        traceId
                                    )
                                }
                                .onRetryExhaustedThrow { _, retrySignal  ->  retrySignal .failure()}
                        )
                }
            }
    }


}
