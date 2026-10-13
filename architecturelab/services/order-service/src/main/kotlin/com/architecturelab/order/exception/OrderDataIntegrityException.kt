package com.architecturelab.order.exception

class OrderDataIntegrityException(
    cause: Throwable? = null
) : ApplicationException(
    errorCode = "ORDER_DATA_INTEGRITY_ERROR",
    safeMessage = "Order could not be persisted because of a data integrity violation",
    retryable = false,
    cause = cause
)