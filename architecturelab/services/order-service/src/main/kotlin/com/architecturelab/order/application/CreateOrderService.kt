package com.architecturelab.order.application

import com.architecturelab.observability.ReactorTraceContext
import com.architecturelab.observability.TraceConstants
import com.architecturelab.order.application.port.outbound.OrderRepositoryPort
import com.architecturelab.order.common.helper.StepMeasurement
import com.architecturelab.order.exception.ApplicationException
import com.architecturelab.order.exception.DependencyTimeoutException
import com.architecturelab.order.exception.InventoryUnavailableException
import com.architecturelab.order.exception.OrderProcessingTimeoutException
import com.architecturelab.order.exception.OrderValidationException
import com.architecturelab.order.exception.UnexpectedApplicationException
import com.architecturelab.order.model.CreateOrderRequest
import com.architecturelab.order.model.CreateOrderResponse
import com.architecturelab.order.domain.model.Order
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.retry.Retry
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeoutException

@Service
class CreateOrderService(
    val stepMeasurement: StepMeasurement,
    val orderRepositoryPort: OrderRepositoryPort) {
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
                    orderId = savedOrder.orderId.toString(),
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

            when (request.sku) {
                "SCHEDULER-TEST" ->
                    Mono.fromCallable {

                        log.info(
                            "event=scheduler_test stage=source thread={}",
                            Thread.currentThread().name
                        )

                        request
                    }
                        .subscribeOn(Schedulers.boundedElastic())

                        .map {
                            log.info(
                                "event=scheduler_test stage=before_publishOn thread={}",
                                Thread.currentThread().name
                            )

                            it
                        }

                        .publishOn(Schedulers.parallel())

                        .map {
                            log.info(
                                "event=scheduler_test stage=after_publishOn thread={}",
                                Thread.currentThread().name
                            )

                            it
                        }

                "BLOCKING-EVENT-LOOP" -> {

                    Mono.fromCallable {

                        log.warn(
                            "event=blocking_operation operation=validation thread={}",
                            Thread.currentThread().name
                        )

                        Thread.sleep(200)

                        request
                    }
                }

                "NONBLOCKING-DELAY" ->
                    Mono.delay(Duration.ofMillis(200))
                        .thenReturn(request)

                "BLOCKING-ISOLATED" -> {

                    Mono.fromCallable {

                        log.warn(
                            "event=blocking_operation_isolated operation=validation thread={}",
                            Thread.currentThread().name
                        )

                        Thread.sleep(200)

                        request
                    }
                        .subscribeOn(Schedulers.boundedElastic())
                }

                else -> {
                    if (request.quantity <= 0) {
                        Mono.error(
                            OrderValidationException(
                                "Quantity must be greater than zero"
                            )
                        )
                    } else {
                        Mono.just(request)
                    }
                }
            }
        }
    }


    private fun persistOrder(
        request: CreateOrderRequest
    ): Mono<Order> {

        return stepMeasurement.measureStep("persist_order") {

            val now = Instant.now()

            val order =
                Order(
                    orderId = UUID.randomUUID(),
                    sku = request.sku,
                    quantity = request.quantity,
                    status = "CREATED",
                    createdAt = now,
                    updatedAt = now
                )

            orderRepositoryPort.save(order)
        }
    }


    private fun checkInventory(
        order: Order
    ): Mono<Order> {
        return ReactorTraceContext.currentTraceId()
            .flatMap { traceId ->
               stepMeasurement.measureStep("inventory_check") {
                   val delay =
                       if (order.sku == "TIMEOUT-INVENTORY") {
                           Duration.ofMillis(500)
                       } else {
                           Duration.ofMillis(200)
                       }

                   Mono.delay(delay)
                        .flatMap {
                            val random = Math.random()
                            when {
                                order.sku == "FAIL-INVENTORY" -> {
                                    Mono.error(InventoryUnavailableException())
                                }

                                order.sku == "FLAKY-INVENTORY" && random < 0.6 -> {
                                    Mono.error(InventoryUnavailableException())
                                }

                                else -> {
                                    Mono.just(order)
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
