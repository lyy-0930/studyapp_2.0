package com.studyapp.model

/**
 * 班级统计数据响应模型
 * @param totalStudents 总学生数
 * @param activeStudents 活跃学生数（最近7天有学习记录）
 * @param averageWatchTime 平均观看时长（分钟）
 * @param completionRate 平均完成率（百分比）
 * @param topStudents 优秀学生列表（按进度排序）
 */
data class ClassStatsResponse(
    val totalStudents: Int,
    val activeStudents: Int,
    val averageWatchTime: Int,
    val completionRate: Int,
    val topStudents: List<Student>
) {
    /**
     * 获取活跃学生比例
     */
    fun getActiveRatio(): Float {
        return if (totalStudents == 0) 0f else activeStudents.toFloat() / totalStudents
    }

    /**
     * 获取格式化的活跃学生比例文本
     */
    fun getFormattedActiveRatio(): String = "${(getActiveRatio() * 100).toInt()}%"

    /**
     * 获取格式化的平均观看时长文本
     */
    fun getFormattedAverageWatchTime(): String {
        return when {
            averageWatchTime < 60 -> "${averageWatchTime}分钟"
            else -> {
                val hours = averageWatchTime / 60
                val minutes = averageWatchTime % 60
                if (minutes == 0) "${hours}小时" else "${hours}小时${minutes}分钟"
            }
        }
    }

    /**
     * 获取格式化的完成率文本
     */
    fun getFormattedCompletionRate(): String = "$completionRate%"
}