package com.studyapp.model

/**
 * 视频项数据类
 * 用于存储视频信息
 */
data class VideoItem(
    val id: Long = System.currentTimeMillis(), // 唯一ID，使用时间戳
    val url: String,                          // 视频URL
    val title: String = "未命名视频",           // 视频标题（默认值）
    val description: String = "",             // 视频描述
    val teacher: String = "教师",              // 上传者/教师
    val uploadTime: Long = System.currentTimeMillis() // 上传时间
) {
    /**
     * 获取简短文件名（从URL中提取）
     */
    fun getShortFileName(): String {
        return try {
            // 从URL中提取文件名部分
            val urlParts = url.split("/")
            val fileName = urlParts.lastOrNull() ?: "unknown.mp4"

            // 如果文件名太长，截断显示
            if (fileName.length > 20) {
                fileName.take(20) + "..."
            } else {
                fileName
            }
        } catch (e: Exception) {
            "视频文件"
        }
    }

    /**
     * 获取格式化时间
     */
    fun getFormattedTime(): String {
        return android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", uploadTime).toString()
    }
}