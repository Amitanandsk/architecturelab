package com.architecturelab.order.exception

class DependencyTimeoutException(
    val dependency: String,
    cause: Throwable? = null
) : ApplicationException(
    errorCode = "DEPENDENCY_TIMEOUT",
    safeMessage = "A required downstream service timed out",
    retryable = true,
    cause = cause
)