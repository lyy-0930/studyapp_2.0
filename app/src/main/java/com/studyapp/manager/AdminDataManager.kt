package com.studyapp.manager

import com.studyapp.model.User
import com.studyapp.model.ActivityRecord
import com.studyapp.model.LearningRecord
import java.util.Date
import kotlin.random.Random

/**
 * 管理员数据管理器
 * 负责生成管理员界面所需的模拟数据
 */
object AdminDataManager {

    // 预定义的用户名池
    private val usernamePool = listOf(
        "张三", "李四", "王五", "赵六", "钱七", "孙八", "周九", "吴十",
        "陈明", "林芳", "黄伟", "刘洋", "张华", "李娜", "王磊", "赵静",
        "钱勇", "孙梅", "周强", "吴艳", "陈刚", "林霞", "黄杰", "刘芳"
    )

    // 预定义的课程名池
    private val courseNamePool = listOf(
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

    // 预定义的邮箱后缀
    private val emailSuffixes = listOf("@qq.com", "@163.com", "@gmail.com", "@outlook.com")

    /**
     * 生成模拟用户数据
     * @param count 用户数量（默认20）
     * @return 用户列表
     */
    fun generateUsers(count: Int = 20): List<User> {
        val users = mutableListOf<User>()
        val roles = listOf("student", "teacher")

        for (i in 1..count) {
            val usernameIndex = i % usernamePool.size
            val username = usernamePool[usernameIndex] + (i / usernamePool.size + 1).toString()

            val roleIndex = i % roles.size
            val role = roles[roleIndex]

            val emailSuffixIndex = i % emailSuffixes.size
            val email = "${username.lowercase()}${emailSuffixes[emailSuffixIndex]}"

            // 随机生成注册日期（最近365天内）
            val registrationDate = Date(System.currentTimeMillis() - Random.nextLong(365L * 24 * 60 * 60 * 1000))

            // 随机生成最后登录日期（最近7天内，如果在线则是现在）
            val isOnline = Random.nextBoolean() && Random.nextDouble() > 0.7 // 30%的用户在线
            val lastLoginDate = if (isOnline) {
                Date() // 现在
            } else {
                Date(System.currentTimeMillis() - Random.nextLong(7L * 24 * 60 * 60 * 1000))
            }

            val totalStudyTime = Random.nextDouble() * 1000.0 // 0-1000分钟
            val loginCount = Random.nextInt(1, 100)
            val courseCompletionRate = 20.0 + Random.nextDouble() * 70.0 // 20-90%

            val user = User(
                id = i,
                username = username,
                role = role,
                email = email,
                registrationDate = registrationDate,
                lastLoginDate = lastLoginDate,
                isOnline = isOnline,
                totalStudyTime = totalStudyTime,
                loginCount = loginCount,
                courseCompletionRate = courseCompletionRate
            )

            users.add(user)
        }

        return users
    }

    /**
     * 生成活跃记录数据
     * @param users 用户列表
     * @param days 生成最近多少天的数据（默认30）
     * @return 活跃记录列表
     */
    fun generateActivityRecords(users: List<User>, days: Int = 30): List<ActivityRecord> {
        val records = mutableListOf<ActivityRecord>()
        var recordId = 1

        // 为每个用户生成最近days天的活跃记录
        for (user in users) {
            // 随机决定用户有多少天的活跃记录（1-days天）
            val recordDays = Random.nextInt(1, days + 1)

            for (dayOffset in 0 until recordDays) {
                val activityDate = Date(System.currentTimeMillis() - dayOffset * 24L * 60 * 60 * 1000)

                val loginCount = Random.nextInt(0, 5) // 0-4次登录
                val studyDuration = Random.nextDouble() * 120.0 // 0-120分钟
                val courseCount = Random.nextInt(0, 4) // 0-3门课程
                val quizCount = Random.nextInt(0, 3) // 0-2个测验

                val activityRecord = ActivityRecord(
                    id = recordId++,
                    userId = user.id,
                    username = user.username,
                    activityDate = activityDate,
                    loginCount = loginCount,
                    studyDuration = studyDuration,
                    courseCount = courseCount,
                    quizCount = quizCount
                )

                // 计算活跃度分数
                activityRecord.calculateActivityScore()

                records.add(activityRecord)
            }
        }

        return records.sortedByDescending { it.activityDate }
    }

    /**
     * 生成学习记录数据
     * @param users 用户列表
     * @param recordCount 总记录数量（默认100）
     * @return 学习记录列表
     */
    fun generateLearningRecords(users: List<User>, recordCount: Int = 100): List<LearningRecord> {
        val records = mutableListOf<LearningRecord>()

        for (i in 1..recordCount) {
            // 随机选择一个用户
            val userIndex = i % users.size
            val user = users[userIndex]

            // 随机选择一门课程
            val courseIndex = i % courseNamePool.size
            val courseName = courseNamePool[courseIndex]

            // 随机生成学习日期（最近90天内）
            val studyDate = Date(System.currentTimeMillis() - Random.nextLong(90L * 24 * 60 * 60 * 1000))

            val studyDuration = 5.0 + Random.nextDouble() * 55.0 // 5-60分钟
            val completionRate = 10.0 + Random.nextDouble() * 80.0 // 10-90%
            val quizScore = Random.nextDouble() * 20.0 // 0-20分
            val quizTotal = 20.0 // 总分20分
            val masteryLevel = 10.0 + Random.nextDouble() * 80.0 // 10-90%

            val learningRecord = LearningRecord(
                id = i,
                userId = user.id,
                username = user.username,
                courseId = courseIndex + 1,
                courseName = courseName,
                studyDate = studyDate,
                studyDuration = studyDuration,
                completionRate = completionRate,
                quizScore = quizScore,
                quizTotal = quizTotal,
                masteryLevel = masteryLevel
            )

            records.add(learningRecord)
        }

        return records.sortedByDescending { it.studyDate }
    }

    /**
     * 获取实时在线用户数
     * @param users 用户列表
     * @return 在线用户数
     */
    fun getOnlineUserCount(users: List<User>): Int {
        return users.count { it.isOnline }
    }

    /**
     * 获取在线用户列表
     * @param users 用户列表
     * @return 在线用户列表
     */
    fun getOnlineUsers(users: List<User>): List<User> {
        return users.filter { it.isOnline }.sortedByDescending { it.lastLoginDate }
    }

    /**
     * 计算用户活跃度排名
     * @param activityRecords 活跃记录列表
     * @param days 统计最近多少天的活跃度（默认7）
     * @return 按活跃度降序排列的用户统计列表
     */
    fun calculateActivityRanking(activityRecords: List<ActivityRecord>, days: Int = 7): List<UserActivityStats> {
        // 计算最近days天的起始时间
        val cutoffDate = Date(System.currentTimeMillis() - days * 24L * 60 * 60 * 1000)

        // 过滤出最近days天的记录
        val recentRecords = activityRecords.filter { it.activityDate.after(cutoffDate) }

        // 按用户分组并计算总活跃度
        val userStatsMap = mutableMapOf<Int, UserActivityStats>()

        for (record in recentRecords) {
            val stats = userStatsMap.getOrPut(record.userId) {
                UserActivityStats(
                    userId = record.userId,
                    username = record.username,
                    role = "student",
                    totalActivityScore = 0.0,
                    totalStudyDuration = 0.0,
                    totalLoginCount = 0,
                    totalCourseCount = 0,
                    totalQuizCount = 0
                )
            }

            stats.totalActivityScore += record.activityScore
            stats.totalStudyDuration += record.studyDuration
            stats.totalLoginCount += record.loginCount
            stats.totalCourseCount += record.courseCount
            stats.totalQuizCount += record.quizCount
        }

        // 转换为列表并按活跃度降序排序
        return userStatsMap.values.sortedByDescending { it.totalActivityScore }
    }

    /**
     * 计算学生学习情况统计
     * @param learningRecords 学习记录列表
     * @param users 用户列表
     * @return 学生学习情况统计列表
     */
    fun calculateLearningStats(learningRecords: List<LearningRecord>, users: List<User>): List<StudentLearningStats> {
        val studentStatsMap = mutableMapOf<Int, StudentLearningStats>()

        // 初始化所有学生的统计
        for (user in users.filter { it.role == "student" }) {
            studentStatsMap[user.id] = StudentLearningStats(
                userId = user.id,
                username = user.username,
                totalStudyDuration = 0.0,
                averageCompletionRate = 0.0,
                averageQuizAccuracy = 0.0,
                completedCourseCount = 0,
                totalLearningRecords = 0
            )
        }

        // 统计学习记录
        for (record in learningRecords) {
            val stats = studentStatsMap[record.userId] ?: continue

            stats.totalStudyDuration += record.studyDuration
            stats.averageCompletionRate = (stats.averageCompletionRate * stats.totalLearningRecords + record.completionRate) / (stats.totalLearningRecords + 1)
            stats.averageQuizAccuracy = (stats.averageQuizAccuracy * stats.totalLearningRecords + record.getQuizAccuracy()) / (stats.totalLearningRecords + 1)
            stats.completedCourseCount = if (record.completionRate >= 80.0) stats.completedCourseCount + 1 else stats.completedCourseCount
            stats.totalLearningRecords++
        }

        return studentStatsMap.values.sortedByDescending { it.totalStudyDuration }
    }

    /**
     * 计算课程掌握度与正确率
     * @param learningRecords 学习记录列表
     * @return 课程统计列表
     */
    fun calculateCourseMasteryStats(learningRecords: List<LearningRecord>): List<CourseMasteryStats> {
        val courseStatsMap = mutableMapOf<Int, CourseMasteryStats>()

        for (record in learningRecords) {
            val stats = courseStatsMap.getOrPut(record.courseId) {
                CourseMasteryStats(
                    courseId = record.courseId,
                    courseName = record.courseName,
                    totalStudents = 0,
                    averageMasteryLevel = 0.0,
                    averageQuizAccuracy = 0.0,
                    averageCompletionRate = 0.0,
                    totalLearningRecords = 0
                )
            }

            stats.totalStudents = maxOf(stats.totalStudents, record.userId)
            stats.averageMasteryLevel = (stats.averageMasteryLevel * stats.totalLearningRecords + record.masteryLevel) / (stats.totalLearningRecords + 1)
            stats.averageQuizAccuracy = (stats.averageQuizAccuracy * stats.totalLearningRecords + record.getQuizAccuracy()) / (stats.totalLearningRecords + 1)
            stats.averageCompletionRate = (stats.averageCompletionRate * stats.totalLearningRecords + record.completionRate) / (stats.totalLearningRecords + 1)
            stats.totalLearningRecords++
        }

        return courseStatsMap.values.sortedByDescending { it.averageMasteryLevel }
    }

    /**
     * 用户活跃度统计类
     */
    data class UserActivityStats(
        val userId: Int,
        val username: String,
        val role: String,
        var totalActivityScore: Double,
        var totalStudyDuration: Double,
        var totalLoginCount: Int,
        var totalCourseCount: Int,
        var totalQuizCount: Int
    ) {
        /**
         * 获取格式化的活跃度分数
         */
        fun getFormattedActivityScore(): String {
            return String.format("%.1f分", totalActivityScore)
        }

        /**
         * 获取格式化的学习时长
         */
        fun getFormattedStudyDuration(): String {
            return when {
                totalStudyDuration < 60 -> "${totalStudyDuration.toInt()}分钟"
                totalStudyDuration < 1440 -> { // 24小时
                    val hours = (totalStudyDuration / 60).toInt()
                    val minutes = (totalStudyDuration % 60).toInt()
                    "${hours}小时${minutes}分钟"
                }
                else -> {
                    val days = (totalStudyDuration / 1440).toInt()
                    val hours = ((totalStudyDuration % 1440) / 60).toInt()
                    "${days}天${hours}小时"
                }
            }
        }
    }

    /**
     * 学生学习情况统计类
     */
    data class StudentLearningStats(
        val userId: Int,
        val username: String,
        var totalStudyDuration: Double,
        var averageCompletionRate: Double,
        var averageQuizAccuracy: Double,
        var completedCourseCount: Int,
        var totalLearningRecords: Int
    ) {
        /**
         * 获取格式化的学习时长
         */
        fun getFormattedStudyDuration(): String {
            return when {
                totalStudyDuration < 60 -> "${totalStudyDuration.toInt()}分钟"
                totalStudyDuration < 1440 -> { // 24小时
                    val hours = (totalStudyDuration / 60).toInt()
                    val minutes = (totalStudyDuration % 60).toInt()
                    "${hours}小时${minutes}分钟"
                }
                else -> {
                    val days = (totalStudyDuration / 1440).toInt()
                    val hours = ((totalStudyDuration % 1440) / 60).toInt()
                    "${days}天${hours}小时"
                }
            }
        }

        /**
         * 获取格式化的平均完成率
         */
        fun getFormattedAverageCompletionRate(): String {
            return String.format("%.1f%%", averageCompletionRate)
        }

        /**
         * 获取格式化的平均正确率
         */
        fun getFormattedAverageQuizAccuracy(): String {
            return String.format("%.1f%%", averageQuizAccuracy)
        }
    }

    /**
     * 课程掌握度统计类
     */
    data class CourseMasteryStats(
        val courseId: Int,
        val courseName: String,
        var totalStudents: Int,
        var averageMasteryLevel: Double,
        var averageQuizAccuracy: Double,
        var averageCompletionRate: Double,
        var totalLearningRecords: Int
    ) {
        /**
         * 获取格式化的平均掌握程度
         */
        fun getFormattedAverageMasteryLevel(): String {
            return String.format("%.1f%%", averageMasteryLevel)
        }

        /**
         * 获取格式化的平均正确率
         */
        fun getFormattedAverageQuizAccuracy(): String {
            return String.format("%.1f%%", averageQuizAccuracy)
        }

        /**
         * 获取格式化的平均完成率
         */
        fun getFormattedAverageCompletionRate(): String {
            return String.format("%.1f%%", averageCompletionRate)
        }

        /**
         * 获取掌握程度等级
         */
        fun getMasteryLevel(): String {
            return when {
                averageMasteryLevel >= 90 -> "精通"
                averageMasteryLevel >= 70 -> "良好"
                averageMasteryLevel >= 50 -> "中等"
                averageMasteryLevel >= 30 -> "基础"
                else -> "入门"
            }
        }

        /**
         * 获取掌握程度等级颜色
         */
        fun getMasteryLevelColor(): String {
            return when {
                averageMasteryLevel >= 90 -> "#4CAF50" // 绿色
                averageMasteryLevel >= 70 -> "#8BC34A" // 浅绿
                averageMasteryLevel >= 50 -> "#FFC107" // 黄色
                averageMasteryLevel >= 30 -> "#FF9800" // 橙色
                else -> "#F44336" // 红色
            }
        }
    }
}