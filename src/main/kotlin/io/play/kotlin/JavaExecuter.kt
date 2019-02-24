package io.play.kotlin

import org.http4k.format.Jackson
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.util.*

object JavaExecuter{

    open class ExecutionResult(open val errors: Map<String, List<ErrorHandler.ErrorDescriptor>> = emptyMap())
    data class JavaExecutionResult(val text: String, val exception: ExceptionDescriptor? = null, override val errors: Map<String, List<ErrorHandler.ErrorDescriptor>> = emptyMap()): ExecutionResult(errors)
    data class StackTraceElement(val className: String, val methodName: String, val fileName: String, val lineNumber: Int)
    data class ExceptionDescriptor(val message: String, val fullName: String, val stackTrace: List<StackTraceElement>, val cause: ExceptionDescriptor)

    data class ProgramOutput(val standardOutput: String, val errorOutput: String){
        fun asExecutionResult(): ExecutionResult{
            return JavaExecutionResult(text = "<outStream>$standardOutput\n</outStream>")
        }
    }

    fun execute(args: Array<String>): ExecutionResult {
        return Runtime.getRuntime().exec(args).use {
            outputStream.close()
            val standardOut = InputStreamReader(this.inputStream).buffered()
            val standardError = InputStreamReader(this.errorStream).buffered()
            val errorText = StringBuilder()
            val standardText = StringBuilder()
            val standardThread = appendTo(standardText, standardOut)
            standardThread.start()
            val errorThread = appendTo(errorText, standardError)
            errorThread.start()
            interruptAfter(60000, this, listOf(standardThread, errorThread))
            try {
                waitFor()
                standardThread.join(10000)
                errorThread.join(10000)
            } finally {
                try {
                    standardOut.close()
                    standardError.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            if(errorText.toString().isNotEmpty())error(errorText.toString())
            ProgramOutput(standardText.toString(), errorText.toString()).asExecutionResult()
        }
    }

    private fun <T> Process.use(body: Process.() -> T) = try{ body() } finally { destroy() }

    private fun interruptAfter(delay: Long, process: Process, threads: List<Thread>){
        Timer(true).schedule(object : TimerTask() {
            override fun run() {
                threads.forEach { it.interrupt() }
                process.destroy()
            }
        }, delay)
    }

    private fun appendTo(string: StringBuilder, from: BufferedReader) = Thread {
        try {
            while (true) {
                val line = from.readLine()
                if (Thread.interrupted() || line == null) break
                string.appendln(line)
            }
        } catch (e: Throwable) {
            if (!Thread.interrupted()) {
                e.printStackTrace()
            }
        }
    }
}