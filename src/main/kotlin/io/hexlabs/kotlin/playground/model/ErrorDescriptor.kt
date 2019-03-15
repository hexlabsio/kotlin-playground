package io.hexlabs.kotlin.playground.model

data class ErrorDescriptor(val interval: TextInterval, val message: String, val severity: Severity, val className: String? = null)
