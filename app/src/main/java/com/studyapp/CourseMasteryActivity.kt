package com.studyapp

import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.adapter.CourseMasteryAdapter
import com.studyapp.manager.AdminDataManager
import com.studyapp.manager.ApiService
import com.studyapp.model.User
import kotlinx.coroutines.launch

/**
 * 课程掌握度与正确率Activity
 */
class CourseMasteryActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var sortButton: TextView
    private lateinit var titleTextView: TextView
    private lateinit var totalCoursesTextView: TextView
    private lateinit var averageMasteryTextView: TextView
    private lateinit var averageAccuracyTextView: TextView
    private lateinit var averageCompletionTextView: TextView
    private lateinit var masteryDistributionTextView: TextView
    private lateinit var coursesRecyclerView: RecyclerView

    private lateinit var courseMasteryAdapter: CourseMasteryAdapter
    private var users: List<User> = emptyList()
    private var courseStats: List<AdminDataManager.CourseMasteryStats> = emptyList()
    private var sortBy: SortBy = SortBy.MASTERY_LEVEL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_mastery)

        // 初始化视图
        initViews()

        // 生成模拟数据
        generateAndDisplayData()

        // 设置返回按钮
        backButton.setOnClickListener {
            finish()
        }

        // 设置排序按钮
        sortButton.setOnClickListener {
            showSortDialog()
        }
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        sortButton = findViewById(R.id.sortButton)
        titleTextView = findViewById(R.id.titleTextView)
        totalCoursesTextView = findViewById(R.id.totalCoursesTextView)
        averageMasteryTextView = findViewById(R.id.averageMasteryTextView)
        averageAccuracyTextView = findViewById(R.id.averageAccuracyTextView)
        averageCompletionTextView = findViewById(R.id.averageCompletionTextView)
        masteryDistributionTextView = findViewById(R.id.masteryDistributionTextView)
        coursesRecyclerView = findViewById(R.id.coursesRecyclerView)

        // 设置RecyclerView
        courseMasteryAdapter = CourseMasteryAdapter()
        coursesRecyclerView.layoutManager = LinearLayoutManager(this)
        coursesRecyclerView.adapter = courseMasteryAdapter
    }

    /**
     * 生成并显示数据（从API获取真实数据）
     */
    private fun generateAndDisplayData() {
        loadCourseMasteryData()
    }

    /**
     * 加载课程掌握度统计数据（通用方法，供筛选和刷新使用）
     */
    private fun loadCourseMasteryData() {
        lifecycleScope.launch {
            try {
                val apiService = ApiService.getInstance(this@CourseMasteryActivity)
                val result = apiService.getCourseMasteryStats(limit = 20)

                if (result.isSuccess) {
                    val courseMasteryResponse = result.getOrNull()
                    if (courseMasteryResponse != null) {
                        // 转换API数据为适配器需要的格式
                        courseStats = convertToCourseMasteryStats(courseMasteryResponse.courses)

                        // 根据当前排序方式排序
                        val sortedStats = when (sortBy) {
                            SortBy.MASTERY_LEVEL -> courseStats.sortedByDescending { it.averageMasteryLevel }
                            SortBy.ACCURACY -> courseStats.sortedByDescending { it.averageQuizAccuracy }
                            SortBy.COMPLETION -> courseStats.sortedByDescending { it.averageCompletionRate }
                            SortBy.STUDENT_COUNT -> courseStats.sortedByDescending { it.totalStudents }
                        }
                        courseStats = sortedStats

                        // 更新统计UI（使用真实统计数据）
                        updateStatistics(courseMasteryResponse.stats)

                        // 更新课程列表
                        courseMasteryAdapter.submitList(courseStats)

                        // 显示成功提示（可选）
                        // android.widget.Toast.makeText(this@CourseMasteryActivity, "数据加载成功", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        showError("数据解析失败")
                        fallbackToMockData()
                    }
                } else {
                    val error = result.exceptionOrNull()
                    showError("加载失败: ${error?.message ?: "未知错误"}")
                    // 如果API失败，回退到模拟数据
                    fallbackToMockData()
                }
            } catch (e: Exception) {
                Log.e("CourseMastery", "网络请求异常", e)
                showError("网络请求异常: ${e.message}")
                fallbackToMockData()
            }
        }
    }

    /**
     * 将API响应转换为CourseMasteryStats列表
     */
    private fun convertToCourseMasteryStats(apiCourses: List<ApiService.CourseMasteryItem>): List<AdminDataManager.CourseMasteryStats> {
        return apiCourses.map { item ->
            AdminDataManager.CourseMasteryStats(
                courseId = item.courseId,
                courseName = item.courseName,
                totalStudents = item.totalStudents,
                averageMasteryLevel = item.averageProgress, // 使用平均进度作为掌握程度
                averageQuizAccuracy = item.averageQuizAccuracy,
                averageCompletionRate = item.averageCompletionRate,
                totalLearningRecords = item.totalLearningRecords
            )
        }
    }

    /**
     * 显示错误提示
     */
    private fun showError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    /**
     * 回退到模拟数据（当API失败时）
     */
    private fun fallbackToMockData() {
        // 生成模拟用户数据
        users = AdminDataManager.generateUsers()

        // 生成模拟学习记录数据
        val learningRecordsList = AdminDataManager.generateLearningRecords(users, 200)

        // 计算课程掌握度统计
        val allCourseStats = AdminDataManager.calculateCourseMasteryStats(learningRecordsList)

        // 根据当前排序方式排序
        courseStats = when (sortBy) {
            SortBy.MASTERY_LEVEL -> allCourseStats.sortedByDescending { it.averageMasteryLevel }
            SortBy.ACCURACY -> allCourseStats.sortedByDescending { it.averageQuizAccuracy }
            SortBy.COMPLETION -> allCourseStats.sortedByDescending { it.averageCompletionRate }
            SortBy.STUDENT_COUNT -> allCourseStats.sortedByDescending { it.totalStudents }
        }

        // 更新统计UI
        updateStatistics()

        // 更新课程列表
        courseMasteryAdapter.submitList(courseStats)

        // 提示使用模拟数据
        android.widget.Toast.makeText(this, "使用模拟数据", android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * 更新统计信息
     * @param stats 可选的API统计数据，如果提供则使用API数据，否则使用courseStats计算
     */
    private fun updateStatistics(stats: ApiService.CourseMasteryStats? = null) {
        if (stats != null) {
            // 使用API提供的统计数据
            totalCoursesTextView.text = stats.totalCourses.toString()

            // 平均掌握度（使用平均进度）
            averageMasteryTextView.text = String.format("%.1f%%", stats.averageProgress)

            // 平均正确率（将点击次数转换为百分比，类似学习统计中的逻辑）
            val quizAccuracyPercentage = Math.min(100.0, stats.averageClickCount * 5)
            averageAccuracyTextView.text = String.format("%.1f%%", quizAccuracyPercentage)

            // 平均完成率
            averageCompletionTextView.text = String.format("%.1f%%", stats.averageCompletionRate)

            // 掌握度分布
            val masteryDistribution = stats.masteryDistribution
            masteryDistributionTextView.text = "精通：${masteryDistribution.proficient}门，" +
                    "良好：${masteryDistribution.good}门，" +
                    "中等：${masteryDistribution.medium}门，" +
                    "基础：${masteryDistribution.basic}门，" +
                    "入门：${masteryDistribution.beginner}门"
        } else {
            // 使用courseStats计算统计信息（模拟数据或转换后的数据）
            totalCoursesTextView.text = courseStats.size.toString()

            // 计算平均掌握度
            val averageMastery = if (courseStats.isNotEmpty()) {
                courseStats.map { it.averageMasteryLevel }.average()
            } else {
                0.0
            }
            averageMasteryTextView.text = String.format("%.1f%%", averageMastery)

            // 计算平均正确率
            val averageAccuracy = if (courseStats.isNotEmpty()) {
                courseStats.map { it.averageQuizAccuracy }.average()
            } else {
                0.0
            }
            averageAccuracyTextView.text = String.format("%.1f%%", averageAccuracy)

            // 计算平均完成率
            val averageCompletion = if (courseStats.isNotEmpty()) {
                courseStats.map { it.averageCompletionRate }.average()
            } else {
                0.0
            }
            averageCompletionTextView.text = String.format("%.1f%%", averageCompletion)

            // 计算掌握度分布
            val masteryCounts = calculateMasteryDistribution()
            masteryDistributionTextView.text = "精通：${masteryCounts["精通"] ?: 0}门，" +
                    "良好：${masteryCounts["良好"] ?: 0}门，" +
                    "中等：${masteryCounts["中等"] ?: 0}门，" +
                    "基础：${masteryCounts["基础"] ?: 0}门，" +
                    "入门：${masteryCounts["入门"] ?: 0}门"
        }
    }

    /**
     * 计算掌握度分布
     */
    private fun calculateMasteryDistribution(): Map<String, Int> {
        val distribution = mutableMapOf<String, Int>()

        for (stats in courseStats) {
            val level = stats.getMasteryLevel()
            distribution[level] = distribution.getOrDefault(level, 0) + 1
        }

        return distribution
    }

    /**
     * 显示排序对话框
     */
    private fun showSortDialog() {
        val items = arrayOf("按掌握度排序", "按正确率排序", "按完成率排序", "按学习学生数排序")
        val currentSelection = when (sortBy) {
            SortBy.MASTERY_LEVEL -> 0
            SortBy.ACCURACY -> 1
            SortBy.COMPLETION -> 2
            SortBy.STUDENT_COUNT -> 3
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("排序方式")
            .setSingleChoiceItems(items, currentSelection) { dialog, which ->
                // 更新排序方式
                sortBy = when (which) {
                    0 -> SortBy.MASTERY_LEVEL
                    1 -> SortBy.ACCURACY
                    2 -> SortBy.COMPLETION
                    3 -> SortBy.STUDENT_COUNT
                    else -> SortBy.MASTERY_LEVEL
                }

                // 重新排序数据
                courseStats = when (sortBy) {
                    SortBy.MASTERY_LEVEL -> courseStats.sortedByDescending { it.averageMasteryLevel }
                    SortBy.ACCURACY -> courseStats.sortedByDescending { it.averageQuizAccuracy }
                    SortBy.COMPLETION -> courseStats.sortedByDescending { it.averageCompletionRate }
                    SortBy.STUDENT_COUNT -> courseStats.sortedByDescending { it.totalStudents }
                }

                // 更新UI
                courseMasteryAdapter.submitList(courseStats)

                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 刷新数据
     */
    private fun refreshData() {
        // 重新生成数据
        generateAndDisplayData()

        // 显示刷新提示
        android.widget.Toast.makeText(this, "数据已刷新", android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * 排序方式枚举
     */
    private enum class SortBy {
        MASTERY_LEVEL,
        ACCURACY,
        COMPLETION,
        STUDENT_COUNT
    }
}