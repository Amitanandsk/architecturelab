package com.architecturelab.order.exception

class OrderPersistenceUnavailableException(
    retryable: Boolean,
    cause: Throwable
) : ApplicationException(
    errorCode = "ORDER_PERSISTENCE_UNAVAILABLE",
    safeMessage = "Order persistence is temporarily unavailable",
    retryable = retryable,
    cause = cause
)