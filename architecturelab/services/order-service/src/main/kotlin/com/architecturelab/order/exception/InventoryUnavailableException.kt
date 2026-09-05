package com.architecturelab.order.exception

class InventoryUnavailableException(
    cause: Throwable? = null
) : ApplicationException(
    errorCode = "INVENTORY_UNAVAILABLE",
    safeMessage = "Inventory service is temporarily unavailable",
    retryable = true,
    cause = cause
)