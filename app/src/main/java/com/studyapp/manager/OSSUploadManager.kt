package com.studyapp.manager

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 视频上传管理器
 * 支持两种模式：
 *   1. 本地模式（默认）：直接上传到后端 /upload/video，适合开发和内网
 *   2. OSS 模式：通过后端预签名 URL 上传到阿里云 OSS（需要 OSS 可用）
 *
 * 当前使用本地模式，无需阿里云 OSS
 */
class OSSUploadManager private constructor() {

    companion object {
        private const val TAG = "OSSUploadManager"

        @Volatile
        private var instance: OSSUploadManager? = null

        fun getInstance(): OSSUploadManager {
            return instance ?: synchronized(this) {
                instance ?: OSSUploadManager().also { instance = it }
            }
        }
    }

    /**
     * 上传视频文件（本地模式：直接 POST 到后端）
     */
    suspend fun uploadVideo(
        context: Context,
        videoUri: Uri,
        teacherName: String,
        originalFileName: String,
        progressListener: UploadProgressListener? = null
    ): UploadResult {
        return withContext(Dispatchers.IO) {
            try {
                if (!OSSConfig.isValidVideoFile(originalFileName)) {
                    return@withContext UploadResult.error("不支持的文件格式")
                }

                // 复制到临时文件
                val videoFile = uriToFile(context, videoUri, originalFileName)
                if (!videoFile.exists() || videoFile.length() == 0L) {
                    return@withContext UploadResult.error("文件为空")
                }

                progressListener?.onStart()

                // ========== 直接上传到后端 /upload/video ==========
                val (fileUrl, errorMsg) = uploadToServer(context, videoFile, originalFileName)
                if (fileUrl == null) {
                    val msg = errorMsg ?: "上传到服务器失败"
                    Log.e(TAG, msg)
                    return@withContext UploadResult.error(msg)
                }

                Log.d(TAG, "上传成功: $fileUrl")

                progressListener?.onComplete()
                videoFile.delete()
                UploadResult.success(fileUrl, videoFile.name, videoFile.length())

            } catch (e: Exception) {
                Log.e(TAG, "上传失败: ${e.message}", e)
                progressListener?.onError("上传失败: ${e.message}")
                UploadResult.error("上传失败: ${e.message}")
            }
        }
    }

    /**
     * 上传文件到后端 /upload/video/raw（原始流模式，不走 multipart）
     * @return Pair(文件URL, 错误信息) — 成功时文件URL非空，失败时错误信息非空
     */
    private fun uploadToServer(context: Context, file: File, fileName: String): Pair<String?, String?> {
        return try {
            val url = URL("${ApiService.BASE_URL}/upload/video/raw")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "video/mp4")
            conn.setRequestProperty("X-Filename", fileName)
            conn.setChunkedStreamingMode(0)
            conn.connectTimeout = 30 * 60 * 1000
            conn.readTimeout = 30 * 60 * 1000
            ApiService.addAuthToConnection(conn, context)

            try {
                val out = conn.outputStream
                val buffer = ByteArray(8192)
                var bytesRead: Int
                file.inputStream().use { fis ->
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                }
                out.flush()
                out.close()

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    val errMsg = "上传到服务器失败: HTTP $responseCode"
                    Log.e(TAG, errMsg)
                    return Pair(null, errMsg)
                }

                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                if (json.optBoolean("success", false)) {
                    val data = json.optJSONObject("data")
                    val url = data?.optString("fileUrl", "")?.takeIf { it.isNotEmpty() }
                    if (url != null) Pair(url, null) else Pair(null, "服务器未返回视频地址")
                } else {
                    val errMsg = "后端返回错误: ${json.optString("message")}"
                    Log.e(TAG, errMsg)
                    Pair(null, errMsg)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            val errMsg = "上传到服务器异常: ${e.message}"
            Log.e(TAG, errMsg, e)
            Pair(null, errMsg)
        }
    }

    private fun uriToFile(context: Context, uri: Uri, originalFileName: String): File {
        val tempDir = File(context.cacheDir, "oss_upload_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "upload_${System.currentTimeMillis()}_$originalFileName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }
        return tempFile
    }

    data class UploadResult(
        val success: Boolean,
        val fileUrl: String? = null,
        val ossPath: String? = null,
        val fileSize: Long = 0,
        val errorMessage: String? = null
    ) {
        companion object {
            fun success(fileUrl: String, ossPath: String, fileSize: Long) =
                UploadResult(true, fileUrl, ossPath, fileSize)
            fun error(message: String) = UploadResult(false, errorMessage = message)
        }
    }

    interface UploadProgressListener {
        fun onProgress(progress: Int, currentSize: Long, totalSize: Long)
        fun onStart()
        fun onComplete()
        fun onError(error: String)
    }
}
