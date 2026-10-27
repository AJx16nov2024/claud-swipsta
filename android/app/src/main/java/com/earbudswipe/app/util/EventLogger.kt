package com.earbudswipe.app.util

import com.earbudswipe.app.model.DiagnosticLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedDeque

object EventLogger {
    private const val MAX_LOGS = 100
    private val logDeque = ConcurrentLinkedDeque<DiagnosticLog>()

    private val _logsFlow = MutableStateFlow<List<DiagnosticLog>>(emptyList())
    val logsFlow: StateFlow<List<DiagnosticLog>> = _logsFlow.asStateFlow()

    fun log(
        event: String,
        source: String,
        details: String,
        targetPackage: String,
        accepted: Boolean,
        rejectionReason: String? = null
    ) {
        val entry = DiagnosticLog(
            event = event,
            source = source,
            details = details,
            targetPackage = targetPackage,
            accepted = accepted,
            rejectionReason = rejectionReason
        )

        logDeque.addFirst(entry)
        while (logDeque.size > MAX_LOGS) {
            logDeque.removeLast()
        }
        _logsFlow.value = logDeque.toList()
    }

    fun clear() {
        logDeque.clear()
        _logsFlow.value = emptyList()
    }
}
