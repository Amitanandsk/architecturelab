package com.architecturelab.order.exception

class OrderProcessingTimeoutException(
    cause: Throwable? = null
) : ApplicationException(
    errorCode = "ORDER_PROCESSING_TIMEOUT",
    safeMessage = "Order service could not complete the request within the allowed time",
    retryable = false,
    cause = cause
)