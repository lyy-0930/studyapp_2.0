package com.studyapp.model

import java.util.Date

/**
 * 学习记录数据模型
 * @param id 记录ID
 * @param userId 用户ID
 * @param username 用户名
 * @param courseId 课程ID
 * @param courseName 课程名称
 * @param studyDate 学习日期
 * @param studyDuration 学习时长（分钟）
 * @param completionRate 本次学习完成率（百分比）
 * @param quizScore 测验分数（如果有）
 * @param quizTotal 测验总分
 * @param masteryLevel 掌握程度（0-100）
 */
data class LearningRecord(
    val id: Int,
    val userId: Int,
    val username: String,
    val courseId: Int,
    val courseName: String,
    val studyDate: Date = Date(),
    val studyDuration: Double = 0.0,
    val completionRate: Double = 0.0,
    val quizScore: Double = 0.0,
    val quizTotal: Double = 0.0,
    val masteryLevel: Double = 0.0
) {
    /**
     * 获取格式化的学习日期
     */
    fun getFormattedStudyDate(): String {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
        return formatter.format(studyDate)
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
     * 获取格式化的完成率
     */
    fun getFormattedCompletionRate(): String {
        return String.format("%.1f%%", completionRate)
    }

    /**
     * 获取测验正确率
     */
    fun getQuizAccuracy(): Double {
        return if (quizTotal > 0) (quizScore / quizTotal) * 100.0 else 0.0
    }

    /**
     * 获取格式化的测验正确率
     */
    fun getFormattedQuizAccuracy(): String {
        return String.format("%.1f%%", getQuizAccuracy())
    }

    /**
     * 获取格式化的掌握程度
     */
    fun getFormattedMasteryLevel(): String {
        return when {
            masteryLevel >= 90 -> "精通"
            masteryLevel >= 70 -> "良好"
            masteryLevel >= 50 -> "中等"
            masteryLevel >= 30 -> "基础"
            else -> "入门"
        }
    }

    /**
     * 获取掌握程度颜色（用于UI）
     */
    fun getMasteryLevelColor(): String {
        return when {
            masteryLevel >= 90 -> "#4CAF50" // 绿色
            masteryLevel >= 70 -> "#8BC34A" // 浅绿
            masteryLevel >= 50 -> "#FFC107" // 黄色
            masteryLevel >= 30 -> "#FF9800" // 橙色
            else -> "#F44336" // 红色
        }
    }
}