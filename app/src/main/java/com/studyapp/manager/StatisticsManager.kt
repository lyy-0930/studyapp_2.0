package com.studyapp.manager

import com.studyapp.model.CourseStatistic
import com.studyapp.model.Course
import java.util.Date
import kotlin.random.Random

/**
 * 统计数据管理器
 * 负责生成模拟的课程统计数据
 */
object StatisticsManager {

    /**
     * 根据真实课程列表生成统计数据
     * @param courses 课程列表
     * @param teacherName 教师用户名（用于随机种子）
     * @return 课程统计列表
     */
    fun generateStatisticsFromCourses(courses: List<Course>, teacherName: String): List<CourseStatistic> {
        val statistics = mutableListOf<CourseStatistic>()

        for (course in courses) {
            // 真实数据：暂时没有学习记录，所有统计数据为0
            // TODO: 从后端数据库获取真实的选课人数、观看时长等数据
            val enrolledStudents = 0 // 真实选课人数（暂无数据）
            val averageWatchDuration = 0.0 // 平均观看时长（分钟）
            val totalWatchTime = 0.0 // 总观看时长（分钟）
            val clickRate = 0.0 // 点击率（百分比）
            val completionRate = 0.0 // 完成率（百分比）
            val totalViews = 0 // 总观看次数

            val statistic = CourseStatistic(
                id = course.id,
                courseName = course.name,
                teacherName = course.teacher,
                enrolledStudents = enrolledStudents,
                averageWatchDuration = averageWatchDuration,
                totalWatchTime = totalWatchTime,
                clickRate = clickRate,
                completionRate = completionRate,
                totalViews = totalViews,
                uploadDate = Date() // 使用当前日期，实际应从课程中获取
            )

            statistics.add(statistic)
        }

        // 按课程ID排序
        return statistics.sortedBy { it.id }
    }

    /**
     * 根据教师用户名生成模拟的课程统计数据
     * @param teacherName 教师用户名，用于生成可重复的随机数据
     * @return 该教师的课程统计列表
     */
    fun generateTeacherStatistics(teacherName: String): List<CourseStatistic> {
        // 使用教师用户名的哈希值作为随机种子，确保同一教师的数据可重复
        val seed = teacherName.hashCode()
        val random = Random(seed)

        // 为每位教师生成3-8门课程的统计数据
        val courseCount = 3 + random.nextInt(6) // 3到8门课
        val statistics = mutableListOf<CourseStatistic>()

        // 预定义的课程名称池
        val courseNamePool = listOf(
            "Android开发基础",
            "Kotlin编程实战",
            "Jetpack Compose入门",
            "移动应用架构设计",
            "Android性能优化",
            "Material Design设计规范",
            "网络编程与API集成",
            "数据库与数据持久化",
            "测试驱动开发",
            "项目实战：在线学习平台"
        )

        // 为每门课程生成统计信息
        for (i in 1..courseCount) {
            // 选择课程名称（基于种子选择，确保可重复）
            val courseNameIndex = Math.abs((seed + i) % courseNamePool.size)
            val courseName = courseNamePool[courseNameIndex]

            // 生成统计数据
            val enrolledStudents = 10 + random.nextInt(91) // 10-100名学生
            val averageWatchDuration = 5.0 + random.nextDouble() * 55.0 // 5-60分钟
            val totalWatchTime = averageWatchDuration * enrolledStudents * 0.7 // 假设70%的学生观看了
            val clickRate = 30.0 + random.nextDouble() * 60.0 // 30-90%点击率
            val completionRate = 20.0 + random.nextDouble() * 70.0 // 20-90%完成率
            val totalViews = (enrolledStudents * (1.5 + random.nextDouble() * 2.5)).toInt() // 每个学生观看1.5-4次

            // 创建统计对象
            val prefix = if (teacherName.isNotEmpty()) teacherName.substring(0, 1).uppercase() else "T"
            val statistic = CourseStatistic(
                id = i,
                courseName = "${courseName} ($prefix${i})",
                teacherName = teacherName,
                enrolledStudents = enrolledStudents,
                averageWatchDuration = averageWatchDuration,
                totalWatchTime = totalWatchTime,
                clickRate = clickRate,
                completionRate = completionRate,
                totalViews = totalViews,
                uploadDate = Date(System.currentTimeMillis() - random.nextLong(365L * 24 * 60 * 60 * 1000)) // 一年内随机日期
            )

            statistics.add(statistic)
        }

        // 按选课学生数降序排序
        return statistics.sortedByDescending { it.enrolledStudents }
    }

    /**
     * 计算教师的总体统计数据
     * @param statistics 课程统计列表
     * @return 总体统计信息
     */
    fun calculateOverallStatistics(statistics: List<CourseStatistic>): OverallStatistics {
        if (statistics.isEmpty()) {
            return OverallStatistics(
                totalCourses = 0,
                totalStudents = 0,
                totalWatchTime = 0.0,
                averageWatchDuration = 0.0,
                averageClickRate = 0.0,
                averageCompletionRate = 0.0
            )
        }

        val totalCourses = statistics.size
        val totalStudents = statistics.sumOf { it.enrolledStudents }
        val totalWatchTime = statistics.sumOf { it.totalWatchTime }
        val averageWatchDuration = statistics.map { it.averageWatchDuration }.average()
        val averageClickRate = statistics.map { it.clickRate }.average()
        val averageCompletionRate = statistics.map { it.completionRate }.average()

        return OverallStatistics(
            totalCourses = totalCourses,
            totalStudents = totalStudents,
            totalWatchTime = totalWatchTime,
            averageWatchDuration = averageWatchDuration,
            averageClickRate = averageClickRate,
            averageCompletionRate = averageCompletionRate
        )
    }

    /**
     * 总体统计数据类
     */
    data class OverallStatistics(
        val totalCourses: Int,
        val totalStudents: Int,
        val totalWatchTime: Double,
        val averageWatchDuration: Double,
        val averageClickRate: Double,
        val averageCompletionRate: Double,
        val averageQuizAccuracy: Double = 0.0
    ) {
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
         * 获取格式化的平均观看时长
         */
        fun getFormattedAverageDuration(): String {
            return String.format("%.1f分钟", averageWatchDuration)
        }

        /**
         * 获取格式化的平均点击率（显示为平均点击次数）
         */
        fun getFormattedAverageClickRate(): String {
            return String.format("%.1f次点击", averageClickRate)
        }

        /**
         * 获取格式化的平均完成率
         */
        fun getFormattedAverageCompletionRate(): String {
            return String.format("%.1f%%", averageCompletionRate)
        }

        /**
         * 获取格式化的平均答题正确率
         */
        fun getFormattedAverageQuizAccuracy(): String {
            return String.format("%.1f%%", averageQuizAccuracy)
        }
    }
}