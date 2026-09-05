package com.architecturelab.order.api

import org.springframework.http.HttpStatus


data class ApiErrorResponse(
    val errorCode: String,
    val message: String,
    val traceId: String,
  //  val timestamp: Instant,
  //  val path: String,
  //  val details: List<ApiErrorDetail> = emptyList()
)

data class HttpError(
    val status: HttpStatus,
    val errorCode: String,
    val message: String
)

data class ApiErrorDetail(
    val field: String,
    val code: String,
    val message: String
)