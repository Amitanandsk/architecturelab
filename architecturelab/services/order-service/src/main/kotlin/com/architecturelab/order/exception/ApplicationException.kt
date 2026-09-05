package com.architecturelab.order.exception

abstract class ApplicationException(
    val errorCode: String,
    val safeMessage: String,
    val retryable: Boolean = false,
    cause: Throwable? = null
) : RuntimeException(safeMessage, cause)