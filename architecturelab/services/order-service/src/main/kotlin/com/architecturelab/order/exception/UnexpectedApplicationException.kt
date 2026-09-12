package com.architecturelab.order.exception

class UnexpectedApplicationException(
    cause: Throwable
) : ApplicationException(
    errorCode = "INTERNAL_ERROR",
    safeMessage = "An unexpected error occurred while processing the request",
    retryable = false,
    cause = cause
)