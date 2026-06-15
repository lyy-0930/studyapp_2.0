package com.studyapp.model

import java.util.Date

/**
 * 用户数据模型
 * @param id 用户ID
 * @param username 用户名
 * @param role 角色（student, teacher, admin）
 * @param email 邮箱
 * @param registrationDate 注册日期
 * @param lastLoginDate 最后登录日期
 * @param isOnline 是否在线
 * @param totalStudyTime 总学习时长（分钟）
 * @param loginCount 登录次数
 * @param courseCompletionRate 课程平均完成率（百分比）
 */
data class User(
    val id: Int,
    val username: String,
    val role: String,
    val email: String,
    val registrationDate: Date = Date(),
    var lastLoginDate: Date = Date(),
    var isOnline: Boolean = false,
    var totalStudyTime: Double = 0.0,
    var loginCount: Int = 0,
    var courseCompletionRate: Double = 0.0
) {
    /**
     * 获取格式化的注册日期
     */
    fun getFormattedRegistrationDate(): String {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
        return formatter.format(registrationDate)
    }

    /**
     * 获取格式化的最后登录日期
     */
    fun getFormattedLastLoginDate(): String {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
        return formatter.format(lastLoginDate)
    }

    /**
     * 获取格式化的总学习时长
     */
    fun getFormattedTotalStudyTime(): String {
        return when {
            totalStudyTime < 60 -> "${totalStudyTime.toInt()}分钟"
            totalStudyTime < 1440 -> { // 24小时
                val hours = (totalStudyTime / 60).toInt()
                val minutes = (totalStudyTime % 60).toInt()
                "${hours}小时${minutes}分钟"
            }
            else -> {
                val days = (totalStudyTime / 1440).toInt()
                val hours = ((totalStudyTime % 1440) / 60).toInt()
                "${days}天${hours}小时"
            }
        }
    }

    /**
     * 获取在线状态文本
     */
    fun getOnlineStatusText(): String {
        return if (isOnline) "在线" else "离线"
    }

    /**
     * 获取在线状态颜色资源（用于UI）
     */
    fun getOnlineStatusColor(): String {
        return if (isOnline) "#4CAF50" else "#9E9E9E" // 绿色表示在线，灰色表示离线
    }

    /**
     * 获取格式化的完成率
     */
    fun getFormattedCompletionRate(): String {
        return String.format("%.1f%%", courseCompletionRate)
    }
}