package com.architecturelab.order.api

import com.architecturelab.order.exception.ApplicationException
import com.architecturelab.order.exception.DependencyTimeoutException
import com.architecturelab.order.exception.InventoryUnavailableException
import com.architecturelab.order.exception.OrderProcessingTimeoutException
import com.architecturelab.order.exception.OrderValidationException
import com.architecturelab.order.exception.UnexpectedApplicationException
import org.springframework.http.HttpStatus

object HttpErrorMapper {

    fun map(error: ApplicationException): HttpError {
        return when (error) {

            is OrderValidationException ->
                HttpError(
                    status = HttpStatus.BAD_REQUEST,
                    errorCode = error.errorCode,
                    message = error.safeMessage
                )

            is InventoryUnavailableException ->
                HttpError(
                    status = HttpStatus.SERVICE_UNAVAILABLE,
                    errorCode = error.errorCode,
                    message = error.safeMessage
                )
            is DependencyTimeoutException ->
                HttpError(
                    status = HttpStatus.GATEWAY_TIMEOUT,
                    errorCode = error.errorCode,
                    message = error.safeMessage
                )

            is OrderProcessingTimeoutException ->
                HttpError(
                    status = HttpStatus.SERVICE_UNAVAILABLE,
                    errorCode = error.errorCode,
                    message = error.safeMessage
                )

            is UnexpectedApplicationException ->
                HttpError(
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    errorCode = error.errorCode,
                    message = error.safeMessage
                )

            else ->
                HttpError(
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    errorCode = "INTERNAL_ERROR",
                    message = "An unexpected error occurred"
                )
        }
    }
}