package com.studyapp

import android.app.Application
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自定义Application类，用于全局异常处理
 */
class StudyApp : Application() {

    companion object {
        private const val TAG = "StudyApp"
    }

    override fun onCreate() {
        super.onCreate()

        // 设置默认的未捕获异常处理器
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 记录异常信息
            Log.e(TAG, "未捕获异常: ${throwable.message}", throwable)

            // 将异常堆栈写入文件（可选）
            writeCrashLogToFile(throwable)

            // 显示友好的错误信息（可选，可以在这里启动一个错误报告Activity）

            // 调用默认的异常处理器（会导致应用崩溃）
            defaultHandler?.uncaughtException(thread, throwable)
        }

        Log.d(TAG, "StudyApp初始化完成，全局异常处理器已设置")
    }

    /**
     * 将崩溃日志写入文件（便于调试）
     */
    private fun writeCrashLogToFile(throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val fileName = "crash_log_$timestamp.txt"

            val logDir = File(filesDir, "crash_logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val logFile = File(logDir, fileName)

            val stringWriter = StringWriter()
            val printWriter = PrintWriter(stringWriter)
            throwable.printStackTrace(printWriter)
            printWriter.flush()

            val stackTrace = stringWriter.toString()

            val logContent = """
                崩溃时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}
                异常类型: ${throwable.javaClass.name}
                异常信息: ${throwable.message}

                堆栈跟踪:
                $stackTrace

                设备信息:
                Android SDK: ${android.os.Build.VERSION.SDK_INT}
                设备型号: ${android.os.Build.MODEL}
                品牌: ${android.os.Build.BRAND}
                产品: ${android.os.Build.PRODUCT}

                """.trimIndent()

            FileOutputStream(logFile).use { fos ->
                fos.write(logContent.toByteArray())
            }

            Log.d(TAG, "崩溃日志已保存到: ${logFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "保存崩溃日志失败", e)
        }
    }
}