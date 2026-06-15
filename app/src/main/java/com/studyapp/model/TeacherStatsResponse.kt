package com.studyapp.model


/**
 * 教师统计数据响应模型
 * 对应后端API: GET /teacher/stats?teacherId={id}
 *
 * @param success 请求是否成功
 * @param message 响应消息
 * @param data 统计数据
 */
data class TeacherStatsResponse(
    val success: Boolean,
    val message: String,
    val data: TeacherStatsData?
)

/**
 * 教师统计数据主体
 */
data class TeacherStatsData(
    val teacherId: Int,
    val teacherName: String,
    val totalCourses: Int,
    val totalStudents: Int,
    val totalWatchTime: Int,  // 总观看时长（分钟）
    val averageWatchDuration: Double,  // 平均观看时长（分钟）
    val averageProgress: Double,  // 平均完成率（百分比）
    val averageClickCount: Double,  // 平均点击次数
    val averageQuizAccuracy: Double = 0.0,  // 平均答题正确率
    val courses: List<TeacherCourseStats>
)

/**
 * 教师单门课程统计数据
 */
data class TeacherCourseStats(
    val courseId: Int,
    val courseName: String,
    val description: String?,
    val teacherName: String,
    val enrolledStudents: Int,  // 选课学生数
    val averageWatchTime: Double,  // 平均观看时长（分钟）
    val averageProgress: Double,  // 平均完成率（百分比）
    val totalWatchTime: Int,  // 总观看时长（分钟）
    val averageClickCount: Double,  // 平均点击次数
    val totalClickCount: Int,  // 总点击次数
    val averageQuizAccuracy: Double = 0.0,  // 答题正确率
    val quizAttemptCount: Int = 0,  // 答题人数
    val createdAt: String  // 课程创建时间，ISO格式
) {
    /**
     * 获取格式化的平均观看时长
     */
    fun getFormattedAverageWatchTime(): String {
        return when {
            averageWatchTime < 1 -> "${(averageWatchTime * 60).toInt()}秒"
            averageWatchTime < 60 -> String.format("%.1f分钟", averageWatchTime)
            else -> {
                val hours = (averageWatchTime / 60).toInt()
                val minutes = (averageWatchTime % 60).toInt()
                "${hours}小时${minutes}分钟"
            }
        }
    }

    /**
     * 获取格式化的总观看时长
     */
    fun getFormattedTotalWatchTime(): String {
        return when {
            totalWatchTime < 60 -> "${totalWatchTime}分钟"
            totalWatchTime < 1440 -> { // 24小时
                val hours = totalWatchTime / 60
                val minutes = totalWatchTime % 60
                "${hours}小时${minutes}分钟"
            }
            else -> {
                val days = totalWatchTime / 1440
                val hours = (totalWatchTime % 1440) / 60
                "${days}天${hours}小时"
            }
        }
    }

    /**
     * 获取格式化的平均完成率
     */
    fun getFormattedAverageProgress(): String {
        return String.format("%.1f%%", averageProgress)
    }

    /**
     * 获取统计摘要
     */
    fun getSummary(): String {
        return "${enrolledStudents}名学生 · 平均观看${getFormattedAverageWatchTime()} · 完成率${getFormattedAverageProgress()}"
    }
}
