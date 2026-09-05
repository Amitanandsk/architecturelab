package com.architecturelab.order.exception

class OrderValidationException(
 message: String
) : ApplicationException(
 errorCode = "INVALID_ORDER_REQUEST",
 safeMessage = message,
 retryable = false
)