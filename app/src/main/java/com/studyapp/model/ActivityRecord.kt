package com.studyapp.model

import java.util.Date

/**
 * 活跃记录数据模型
 * @param id 记录ID
 * @param userId 用户ID
 * @param username 用户名
 * @param activityDate 活跃日期
 * @param loginCount 当日登录次数
 * @param studyDuration 当日学习时长（分钟）
 * @param courseCount 当日学习课程数量
 * @param quizCount 当日完成测验数量
 * @param activityScore 活跃度分数
 */
data class ActivityRecord(
    val id: Int,
    val userId: Int,
    val username: String,
    val activityDate: Date = Date(),
    val loginCount: Int = 0,
    val studyDuration: Double = 0.0,
    val courseCount: Int = 0,
    val quizCount: Int = 0,
    val activityScore: Double = 0.0
) {
    /**
     * 获取格式化的活跃日期
     */
    fun getFormattedActivityDate(): String {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
        return formatter.format(activityDate)
    }

    /**
     * 获取格式化的学习时长
     */
    fun getFormattedStudyDuration(): String {
        return when {
            studyDuration < 60 -> "${studyDuration.toInt()}分钟"
            studyDuration < 1440 -> { // 24小时
                val hours = (studyDuration / 60).toInt()
                val minutes = (studyDuration % 60).toInt()
                "${hours}小时${minutes}分钟"
            }
            else -> {
                val days = (studyDuration / 1440).toInt()
                val hours = ((studyDuration % 1440) / 60).toInt()
                "${days}天${hours}小时"
            }
        }
    }

    /**
     * 计算活跃度分数（基于多个指标）
     * 权重：登录次数(20%) + 学习时长(40%) + 课程数量(20%) + 测验数量(20%)
     */
    fun calculateActivityScore(): Double {
        val loginScore = loginCount * 2.0  // 每次登录得2分
        val durationScore = studyDuration * 0.1  // 每分钟学习得0.1分
        val courseScore = courseCount * 5.0  // 每门课程得5分
        val quizScore = quizCount * 10.0  // 每次测验得10分

        return loginScore + durationScore + courseScore + quizScore
    }

    /**
     * 获取格式化的活跃度分数
     */
    fun getFormattedActivityScore(): String {
        return String.format("%.1f分", activityScore)
    }

    /**
     * 获取活跃度等级
     */
    fun getActivityLevel(): String {
        return when {
            activityScore >= 200 -> "极高"
            activityScore >= 100 -> "高"
            activityScore >= 50 -> "中等"
            activityScore >= 20 -> "一般"
            else -> "低"
        }
    }

    /**
     * 获取活跃度等级颜色（用于UI）
     */
    fun getActivityLevelColor(): String {
        return when {
            activityScore >= 200 -> "#4CAF50" // 绿色
            activityScore >= 100 -> "#8BC34A" // 浅绿
            activityScore >= 50 -> "#FFC107" // 黄色
            activityScore >= 20 -> "#FF9800" // 橙色
            else -> "#9E9E9E" // 灰色
        }
    }
}