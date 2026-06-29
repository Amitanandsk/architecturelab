package com.architecturelab.observability

import java.util.UUID

class TraceIdGenerator {
    fun generate(): String = UUID.randomUUID().toString()
}
