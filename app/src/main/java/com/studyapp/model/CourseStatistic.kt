package com.studyapp.model

import java.util.Date

/**
 * 课程统计数据模型
 * 用于显示教师上传课程的统计信息
 *
 * @param id 课程ID
 * @param courseName 课程名称
 * @param teacherName 教师姓名
 * @param enrolledStudents 选课学生总数
 * @param averageWatchDuration 平均观看时长（分钟）
 * @param totalWatchTime 总观看时长（分钟）
 * @param clickRate 点击率（百分比）
 * @param completionRate 完成率（百分比）
 * @param totalViews 总观看次数
 * @param uploadDate 上传日期
 */
data class CourseStatistic(
    val id: Int,
    val courseName: String,
    val teacherName: String,
    val enrolledStudents: Int,
    val averageWatchDuration: Double, // 分钟
    val totalWatchTime: Double, // 分钟
    val clickRate: Double, // 百分比
    val completionRate: Double, // 百分比
    val totalViews: Int,
    val uploadDate: Date = Date()
) {
    /**
     * 获取格式化的平均观看时长
     */
    fun getFormattedAverageDuration(): String {
        return when {
            averageWatchDuration < 1 -> ((averageWatchDuration * 60).toInt()).toString() + "秒"
            averageWatchDuration < 60 -> String.format("%.1f分钟", averageWatchDuration)
            else -> {
                val hours = (averageWatchDuration / 60).toInt()
                val minutes = (averageWatchDuration % 60).toInt()
                hours.toString() + "小时" + minutes.toString() + "分钟"
            }
        }
    }

    /**
     * 获取格式化的总观看时长
     */
    fun getFormattedTotalWatchTime(): String {
        return when {
            totalWatchTime < 60 -> totalWatchTime.toInt().toString() + "分钟"
            totalWatchTime < 1440 -> { // 24小时
                val hours = (totalWatchTime / 60).toInt()
                val minutes = (totalWatchTime % 60).toInt()
                hours.toString() + "小时" + minutes.toString() + "分钟"
            }
            else -> {
                val days = (totalWatchTime / 1440).toInt()
                val hours = ((totalWatchTime % 1440) / 60).toInt()
                days.toString() + "天" + hours.toString() + "小时"
            }
        }
    }

    /**
     * 获取格式化的点击率
     */
    fun getFormattedClickRate(): String {
        return String.format("%.1f%%", clickRate)
    }

    /**
     * 获取格式化的完成率
     */
    fun getFormattedCompletionRate(): String {
        return String.format("%.1f%%", completionRate)
    }

    /**
     * 获取简化的统计摘要
     */
    fun getSummary(): String {
        return enrolledStudents.toString() + "名学生 · 平均观看" + getFormattedAverageDuration() + " · 完成率" + getFormattedCompletionRate()
    }
}