package com.webview.browser

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {
    private const val LOG_FILE_NAME = "wakelock_logs.txt"
    private const val MAX_LOG_SIZE = 2 * 1024 * 1024L // 2MB

    fun log(context: Context, message: String) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean("enable_wakelock_log", false)) return

        val logFile = File(context.filesDir, LOG_FILE_NAME)
        
        // Check file size and rotate/clear if needed
        if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
            // Simple rotation: delete and start new
            // Or keep last 1MB. For simplicity, we'll just clear it or keep tail.
            // Let's just delete it to keep it simple and under 2MB strictly.
            logFile.delete()
        }

        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] $message\n"
            
            FileWriter(logFile, true).use { writer ->
                writer.append(logEntry)
            }
        } catch (e: Exception) {
            Log.e("LogManager", "Failed to write log", e)
        }
    }

    fun getLogs(context: Context): String {
        val logFile = File(context.filesDir, LOG_FILE_NAME)
        return if (logFile.exists()) {
            logFile.readText()
        } else {
            "No logs found."
        }
    }

    fun clearLogs(context: Context) {
        val logFile = File(context.filesDir, LOG_FILE_NAME)
        if (logFile.exists()) {
            logFile.delete()
        }
    }
}