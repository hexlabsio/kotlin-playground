package io.hexlabs.kotlin.playground.model

import io.hexlabs.kotlin.playground.JavaExecutor

open class ExecutionResult(open val errors: Map<String, List<ErrorDescriptor>> = emptyMap())

data class JavaExecutionResult(val text: String, val exception: JavaExecutor.ExceptionDescriptor? = null, override val errors: Map<String, List<ErrorDescriptor>> = emptyMap()): ExecutionResult(errors)
