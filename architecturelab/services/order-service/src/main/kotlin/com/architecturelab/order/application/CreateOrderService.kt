package com.architecturelab.order.application

import com.architecturelab.observability.ReactorTraceContext
import com.architecturelab.order.common.helper.measureStep
import com.architecturelab.order.model.CreateOrderRequest
import com.architecturelab.order.model.CreateOrderResponse
import com.architecturelab.order.model.SavedOrder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.util.UUID

@Service
class CreateOrderService {
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
        return validateRequest(request)
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
            }
            .timeout(Duration.ofMillis(10000))

            .onErrorResume { error ->
                ReactorTraceContext.currentTraceId()
                    .flatMap { traceId ->
                        log.warn(
                        "event=order_creation_failed service=order-service error={} traceId={}",
                        error.javaClass.simpleName,
                        traceId
                    )
                        Mono.just(
                            CreateOrderResponse(
                                orderId = UUID.randomUUID().toString(),
                                status = "FAILED",
                                sku = request.sku,
                                quantity = request.quantity
                            )
                        )
                    }
            }
    }


    private fun validateRequest(
    request: CreateOrderRequest
): Mono<CreateOrderRequest> {
    return measureStep("validate_request") {
        if (request.quantity <= 0) {
            Mono.error(IllegalArgumentException("Quantity must be greater than zero"))
        } else {
            Mono.just(request)
        }
    }
}



    private fun persistOrder(
        request: CreateOrderRequest
    ): Mono<SavedOrder> {
               return measureStep("persist_order") {
                    Mono.delay(Duration.ofMillis(100))
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
                measureStep("inventory_check") {
                    Mono.delay(Duration.ofMillis(200))
                        .flatMap {
                            val random = Math.random()
                            when {
                                savedOrder.sku == "FAIL-INVENTORY" -> {
                                    Mono.error(RuntimeException("Inventory service failed"))
                                }

                                random < 0.6 -> {
                                    Mono.error(RuntimeException("Inventory service failed"))
                                }

                                else -> {
                                    Mono.just(savedOrder)
                                }
                            }
                        }
                        .retryWhen(

                            Retry.backoff(2, Duration.ofMillis(100))
                                .filter { error -> error is RuntimeException }
                                .doAfterRetry {
                                    log.info(
                                        "event=order_retry service=order-service attempt={} reason={} traceId={}",
                                        it.totalRetries() + 1,
                                        it.failure().javaClass.simpleName,
                                        traceId
                                    )
                                }
                        )
                }
            }
    }


}
