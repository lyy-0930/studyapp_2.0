package com.studyapp.manager

import android.content.Context
import android.content.SharedPreferences
import com.studyapp.model.VideoItem

/**
 * 视频存储管理器
 * 使用SharedPreferences保存和读取视频列表
 */
object VideoStorageManager {

    private const val PREFS_NAME = "video_storage"
    private const val KEY_VIDEO_DATA = "video_data" // 存储视频数据的键
    private const val FIELD_SEPARATOR = "|||" // 字段分隔符
    private const val ITEM_SEPARATOR = "###"  // 项目分隔符

    /**
     * 获取SharedPreferences实例
     */
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存视频信息
     * @param context Context
     * @param videoUrl 视频URL
     * @param videoTitle 视频标题
     * @param videoDescription 视频描述
     * @param teacher 教师/上传者
     */
    fun saveVideo(
        context: Context,
        videoUrl: String,
        videoTitle: String = "未命名视频",
        videoDescription: String = "",
        teacher: String = "教师"
    ) {
        val sharedPref = getSharedPreferences(context)
        val existingItems = getVideoItems(context).toMutableList()

        // 如果URL已存在，不重复添加
        if (existingItems.any { it.url == videoUrl }) {
            return
        }

        // 创建新的VideoItem
        val newItem = VideoItem(
            url = videoUrl,
            title = videoTitle,
            description = videoDescription,
            teacher = teacher,
            uploadTime = System.currentTimeMillis()
        )

        // 添加到列表开头（最新视频在前）
        existingItems.add(0, newItem)

        // 保存更新后的视频列表
        saveVideoItems(context, existingItems)
    }

    /**
     * 获取所有视频项
     * @return VideoItem列表
     */
    fun getVideoItems(context: Context): List<VideoItem> {
        val sharedPref = getSharedPreferences(context)
        val dataString = sharedPref.getString(KEY_VIDEO_DATA, "")

        return if (dataString.isNullOrEmpty()) {
            emptyList()
        } else {
            parseVideoItems(dataString)
        }
    }

    /**
     * 将视频项列表保存到SharedPreferences
     */
    private fun saveVideoItems(context: Context, items: List<VideoItem>) {
        val sharedPref = getSharedPreferences(context)
        val dataString = serializeVideoItems(items)
        sharedPref.edit()
            .putString(KEY_VIDEO_DATA, dataString)
            .apply()
    }

    /**
     * 序列化视频项列表为字符串
     */
    private fun serializeVideoItems(items: List<VideoItem>): String {
        return items.joinToString(ITEM_SEPARATOR) { item ->
            listOf(
                item.id.toString(),
                item.url,
                item.title,
                item.description,
                item.teacher,
                item.uploadTime.toString()
            ).joinToString(FIELD_SEPARATOR)
        }
    }

    /**
     * 解析字符串为视频项列表
     */
    private fun parseVideoItems(dataString: String): List<VideoItem> {
        return dataString.split(ITEM_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { itemString ->
                try {
                    val fields = itemString.split(FIELD_SEPARATOR)
                    if (fields.size >= 6) {
                        VideoItem(
                            id = fields[0].toLongOrNull() ?: System.currentTimeMillis(),
                            url = fields[1],
                            title = fields[2],
                            description = fields[3],
                            teacher = fields[4],
                            uploadTime = fields[5].toLongOrNull() ?: System.currentTimeMillis()
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * 清空所有视频记录
     */
    fun clearAllVideos(context: Context) {
        val sharedPref = getSharedPreferences(context)
        sharedPref.edit()
            .remove(KEY_VIDEO_DATA)
            .apply()
    }

    /**
     * 获取视频数量
     */
    fun getVideoCount(context: Context): Int {
        return getVideoItems(context).size
    }

    /**
     * 向后兼容：旧版本的saveVideoUrl方法
     */
    @Deprecated("使用saveVideo方法替代")
    fun saveVideoUrl(context: Context, videoUrl: String, videoTitle: String? = null) {
        saveVideo(
            context = context,
            videoUrl = videoUrl,
            videoTitle = videoTitle ?: extractTitleFromUrl(videoUrl),
            videoDescription = "",
            teacher = "教师"
        )
    }

    /**
     * 从URL中提取标题（文件名）- 向后兼容
     */
    private fun extractTitleFromUrl(url: String): String {
        return try {
            val fileName = url.substringAfterLast("/")
            if (fileName.contains(".")) {
                // 移除扩展名
                val nameWithoutExt = fileName.substringBeforeLast(".")
                // 如果包含时间戳和教师名，提取原始文件名部分
                val parts = nameWithoutExt.split("_")
                if (parts.size >= 3) {
                    // 格式：timestamp_teacherName_originalFileName
                    parts.lastOrNull() ?: nameWithoutExt
                } else {
                    nameWithoutExt
                }
            } else {
                fileName
            }
        } catch (e: Exception) {
            "未命名视频"
        }
    }
}