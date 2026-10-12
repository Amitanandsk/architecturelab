package com.architecturelab.order.exception

import java.util.UUID

class OrderAlreadyExistsException(
    orderId: UUID
) : ApplicationException(
    errorCode = "ORDER_ALREADY_EXISTS",
    safeMessage = "Order $orderId already exists",
    retryable = false
)