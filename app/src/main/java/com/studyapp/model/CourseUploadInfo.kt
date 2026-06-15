package com.studyapp.model

import android.net.Uri
import java.util.Date

/**
 * 课程上传信息数据类
 * 用于存储上传课程时输入的表单数据和文件信息
 *
 * @param courseTitle 课程标题
 * @param courseDescription 课程描述
 * @param videoUri 视频文件URI（可选，选择文件后设置）
 * @param videoFileName 视频文件名
 * @param videoFileSize 视频文件大小（字节）
 * @param category 课程分类（可选）
 * @param difficulty 难度等级（可选）
 * @param teacherName 教师姓名
 * @param uploadTime 上传时间
 * @param ossUrl 阿里云OSS访问URL（上传成功后设置）
 * @param ossPath 阿里云OSS存储路径（上传成功后设置）
 */
data class CourseUploadInfo(
    val courseTitle: String = "",
    val courseDescription: String = "",
    val videoUri: Uri? = null,
    val videoFileName: String? = null,
    val videoFileSize: Long = 0L,
    val category: String = "未分类",
    val difficulty: String = "初级",
    val teacherName: String = "",
    val uploadTime: Date = Date(),
    val ossUrl: String? = null,
    val ossPath: String? = null
) {
    /**
     * 检查表单是否填写完整（基本验证）
     */
    fun isFormValid(): Boolean {
        return courseTitle.isNotBlank() &&
               courseDescription.isNotBlank() &&
               videoUri != null
    }

    /**
     * 获取文件大小的人类可读格式
     */
    fun getFormattedFileSize(): String {
        return when {
            videoFileSize < 1024 -> "$videoFileSize B"
            videoFileSize < 1024 * 1024 -> String.format("%.1f KB", videoFileSize / 1024.0)
            videoFileSize < 1024 * 1024 * 1024 -> String.format("%.1f MB", videoFileSize / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", videoFileSize / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * 获取简化的课程信息（用于显示）
     */
    fun getSummary(): String {
        return "《$courseTitle》\n$courseDescription\n分类：$category · 难度：$difficulty"
    }
}