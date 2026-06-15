package com.studyapp.manager

import android.util.Log

/**
 * 阿里云OSS配置类
 * 使用原生 HTTP PUT 请求替代 Aliyun OSS SDK
 *
 * ⚠️ 安全说明：
 * 此文件仅包含公开配置常量（Endpoint、Bucket名等）。
 * AK/SK 已从客户端移除，完全由后端服务管理。
 * 上传时通过后端 /api/oss/presigned-upload 接口获取预签名 URL。
 */
object OSSConfig {
    // 阿里云OSS Endpoint（地域节点）
    const val OSS_ENDPOINT = "https://oss-cn-hangzhou.aliyuncs.com"

    // 阿里云OSS Bucket名称
    const val OSS_BUCKET_NAME = "study-app-android-2026"

    // 视频文件存储路径前缀
    const val VIDEO_PATH_PREFIX = "studyapp/videos/"

    // 课程封面图存储路径前缀
    const val THUMBNAIL_PATH_PREFIX = "studyapp/thumbnails/"

    // 最大文件大小限制（2GB，支持长时间视频）
    const val MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024

    // 允许的视频文件扩展名
    val ALLOWED_VIDEO_EXTENSIONS = arrayOf("mp4", "avi", "mov", "mkv", "wmv", "flv", "m4v")

    /**
     * 获取OSS上传Endpoint（不含协议）
     */
    fun getEndpointHost(): String {
        return OSS_ENDPOINT.removePrefix("https://").removePrefix("http://")
    }

    /**
     * 生成视频文件在OSS上的存储路径
     * 格式：studyapp/videos/{timestamp}_{teacherName}_{originalFilename}
     * @param teacherName 教师用户名
     * @param originalFilename 原始文件名
     * @return OSS存储路径
     */
    fun generateVideoPath(teacherName: String, originalFilename: String): String {
        val timestamp = System.currentTimeMillis()
        val safeTeacherName = teacherName.replace("[^a-zA-Z0-9\\u4e00-\\u9fa5]".toRegex(), "_")
        val safeFilename = originalFilename.replace("[^a-zA-Z0-9\\.\\-]".toRegex(), "_")
        return "${VIDEO_PATH_PREFIX}${timestamp}_${safeTeacherName}_${safeFilename}"
    }

    /**
     * 生成缩略图在OSS上的存储路径
     * @param videoPath 对应的视频路径
     * @return 缩略图存储路径
     */
    fun generateThumbnailPath(videoPath: String): String {
        val videoName = videoPath.substringAfterLast("/")
        val thumbnailName = videoName.replaceAfterLast(".", "jpg")
        return "${THUMBNAIL_PATH_PREFIX}${thumbnailName}"
    }

    /**
     * 验证文件名是否符合要求
     * @param fileName 文件名
     * @return 是否允许上传
     */
    fun isValidVideoFile(fileName: String): Boolean {
        if (fileName.isBlank()) return false
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in ALLOWED_VIDEO_EXTENSIONS
    }

    /**
     * 生成完整的OSS视频URL（假设Bucket已设置为公共读）
     * @param objectPath OSS对象路径，如 "studyapp/videos/12345_teacher_video.mp4"
     * @return 完整的公共访问URL
     */
    fun generatePublicVideoUrl(objectPath: String): String {
        val endpointHost = OSS_ENDPOINT.removePrefix("https://").removePrefix("http://")
        return "https://$OSS_BUCKET_NAME.$endpointHost/$objectPath"
    }

    /**
     * 检查视频URL是否为当前配置的OSS Bucket URL
     * @param videoUrl 视频URL
     * @return 如果是当前配置的OSS URL则返回true
     */
    fun isCurrentOssUrl(videoUrl: String): Boolean {
        val endpointHost = OSS_ENDPOINT.removePrefix("https://").removePrefix("http://")
        return videoUrl.contains("$OSS_BUCKET_NAME.$endpointHost")
    }

    /**
     * 检查配置是否有效（仅验证 Endpoint 和 Bucket 是否已设置）
     * AK/SK 无需在客户端配置，由后端服务管理
     */
    fun isConfigured(): Boolean {
        val endpointHost = OSS_ENDPOINT.removePrefix("https://").removePrefix("http://")
        return OSS_ENDPOINT.isNotBlank() &&
               OSS_ENDPOINT.startsWith("http") &&
               endpointHost.startsWith("oss-cn-") &&
               endpointHost.contains(".aliyuncs.com") &&
               OSS_BUCKET_NAME.isNotBlank() &&
               OSS_BUCKET_NAME != "your-bucket-name" &&
               !OSS_BUCKET_NAME.contains("example")
    }

    /**
     * 获取未配置的字段列表（仅检查 Endpoint 和 Bucket）
     */
    fun getUnconfiguredFields(): List<String> {
        val unconfigured = mutableListOf<String>()

        if (OSS_ENDPOINT.isBlank()) {
            unconfigured.add("OSS_ENDPOINT（为空）")
        } else if (!OSS_ENDPOINT.startsWith("http")) {
            unconfigured.add("OSS_ENDPOINT（必须以http://或https://开头）")
        } else {
            val endpointHost = OSS_ENDPOINT.removePrefix("https://").removePrefix("http://")
            if (!endpointHost.startsWith("oss-cn-")) {
                unconfigured.add("OSS_ENDPOINT（必须以oss-cn-开头，例如：oss-cn-hangzhou.aliyuncs.com）")
            } else if (!endpointHost.contains(".aliyuncs.com")) {
                unconfigured.add("OSS_ENDPOINT（必须包含.aliyuncs.com）")
            }
        }

        if (OSS_BUCKET_NAME.isBlank()) {
            unconfigured.add("OSS_BUCKET_NAME（为空）")
        } else if (OSS_BUCKET_NAME == "your-bucket-name") {
            unconfigured.add("OSS_BUCKET_NAME（请替换为您的Bucket名称）")
        } else if (OSS_BUCKET_NAME.contains("example")) {
            unconfigured.add("OSS_BUCKET_NAME（包含example，请使用真实Bucket名称）")
        }

        return unconfigured
    }
}
