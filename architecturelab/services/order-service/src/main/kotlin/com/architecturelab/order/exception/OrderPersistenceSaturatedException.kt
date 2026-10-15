package com.architecturelab.order.exception

class OrderPersistenceSaturatedException(
    cause: Throwable
) : ApplicationException(
    errorCode = "ORDER_PERSISTENCE_SATURATED",
    safeMessage = "Order service is temporarily busy",
    retryable = false,
    cause = cause
)