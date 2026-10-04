package com.architecturelab.order.exception

class InvalidOrderIdException :
    ApplicationException(
        errorCode = "INVALID_ORDER_ID",
        safeMessage = "Order id is invalid",
        retryable = false
    )