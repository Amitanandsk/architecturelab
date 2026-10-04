package com.architecturelab.order.exception

import java.util.UUID

class OrderNotFoundException(
    orderId: UUID
) : ApplicationException(
    errorCode = "ORDER_NOT_FOUND",
    safeMessage = "Order $orderId was not found",
    retryable = false
)