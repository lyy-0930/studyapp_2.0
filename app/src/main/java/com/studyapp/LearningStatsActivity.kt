package com.studyapp

import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.adapter.LearningStatsAdapter
import com.studyapp.manager.AdminDataManager
import com.studyapp.manager.ApiService
import com.studyapp.model.User
import kotlinx.coroutines.launch

/**
 * 学生学习情况统计Activity
 */
class LearningStatsActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var exportButton: TextView
    private lateinit var titleTextView: TextView
    private lateinit var totalStudentsTextView: TextView
    private lateinit var totalStudyTimeTextView: TextView
    private lateinit var averageCompletionRateTextView: TextView
    private lateinit var averageAccuracyTextView: TextView
    private lateinit var courseCompletionTextView: TextView
    private lateinit var studentsRecyclerView: RecyclerView

    private lateinit var learningStatsAdapter: LearningStatsAdapter
    private var users: List<User> = emptyList()
    private var learningRecords: List<AdminDataManager.StudentLearningStats> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning_stats)

        // 初始化视图
        initViews()

        // 生成模拟数据
        generateAndDisplayData()

        // 设置返回按钮
        backButton.setOnClickListener {
            finish()
        }

        // 设置导出按钮
        exportButton.setOnClickListener {
            showExportDialog()
        }
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        exportButton = findViewById(R.id.exportButton)
        titleTextView = findViewById(R.id.titleTextView)
        totalStudentsTextView = findViewById(R.id.totalStudentsTextView)
        totalStudyTimeTextView = findViewById(R.id.totalStudyTimeTextView)
        averageCompletionRateTextView = findViewById(R.id.averageCompletionRateTextView)
        averageAccuracyTextView = findViewById(R.id.averageAccuracyTextView)
        courseCompletionTextView = findViewById(R.id.courseCompletionTextView)
        studentsRecyclerView = findViewById(R.id.studentsRecyclerView)

        // 设置RecyclerView
        learningStatsAdapter = LearningStatsAdapter()
        studentsRecyclerView.layoutManager = LinearLayoutManager(this)
        studentsRecyclerView.adapter = learningStatsAdapter
    }

    /**
     * 生成并显示数据（从API获取真实数据）
     */
    private fun generateAndDisplayData() {
        loadLearningStatsData()
    }

    /**
     * 加载学生学习统计数据（通用方法，供筛选和刷新使用）
     */
    private fun loadLearningStatsData() {
        lifecycleScope.launch {
            try {
                val apiService = ApiService.getInstance(this@LearningStatsActivity)
                val result = apiService.getLearningStats(limit = 20)

                if (result.isSuccess) {
                    val learningStatsResponse = result.getOrNull()
                    if (learningStatsResponse != null) {
                        // 转换API数据为适配器需要的格式
                        learningRecords = convertToStudentLearningStats(learningStatsResponse.students)

                        // 更新统计UI（使用真实统计数据）
                        updateStatistics(learningStatsResponse.stats)

                        // 更新学生列表
                        learningStatsAdapter.submitList(learningRecords)

                        // 显示成功提示（可选）
                        // android.widget.Toast.makeText(this@LearningStatsActivity, "数据加载成功", android.widget.Toast.LENGTH_SHORT).show()
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
                Log.e("LearningStats", "网络请求异常", e)
                showError("网络请求异常: ${e.message}")
                fallbackToMockData()
            }
        }
    }

    /**
     * 将API响应转换为StudentLearningStats列表
     */
    private fun convertToStudentLearningStats(apiStudents: List<ApiService.StudentLearningStatsItem>): List<AdminDataManager.StudentLearningStats> {
        return apiStudents.map { item ->
            // 将点击次数转换为百分比（假设平均点击次数0-20，乘以5得到0-100%）
            val quizAccuracyPercentage = (item.averageClickCount * 5.0).coerceIn(0.0, 100.0)

            AdminDataManager.StudentLearningStats(
                userId = item.studentId,
                username = item.username,
                totalStudyDuration = item.totalWatchTime.toDouble(), // 分钟
                averageCompletionRate = item.averageProgress,
                averageQuizAccuracy = quizAccuracyPercentage, // 转换为百分比
                completedCourseCount = item.completedCourseCount,
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
        val learningRecordsList = AdminDataManager.generateLearningRecords(users, 150)

        // 计算学生学习情况统计
        learningRecords = AdminDataManager.calculateLearningStats(learningRecordsList, users)

        // 更新统计UI
        updateStatistics()

        // 更新学生列表
        learningStatsAdapter.submitList(learningRecords)

        // 提示使用模拟数据
        android.widget.Toast.makeText(this, "使用模拟数据", android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * 更新统计信息
     * @param stats 可选的API统计数据，如果提供则使用API数据，否则使用learningRecords计算
     */
    private fun updateStatistics(stats: ApiService.LearningStats? = null) {
        if (stats != null) {
            // 使用API提供的统计数据
            totalStudentsTextView.text = stats.totalStudents.toString()

            // 总观看时间转换为小时（API提供的是分钟）
            val totalWatchTimeHours = stats.totalWatchTime / 60.0
            totalStudyTimeTextView.text = String.format("%.1f小时", totalWatchTimeHours)

            // 平均进度
            averageCompletionRateTextView.text = String.format("%.1f%%", stats.averageProgress)

            // 平均点击次数（作为互动指标）
            averageAccuracyTextView.text = String.format("%.1f次/人", stats.averageClickCount)

            // 平均每人完成课程数
            courseCompletionTextView.text = String.format("平均每人完成%.1f门课程", stats.averageCompletedCourses)
        } else {
            // 使用learningRecords计算统计信息（模拟数据或转换后的数据）
            totalStudentsTextView.text = learningRecords.size.toString()

            // 计算总学习时长（小时）
            val totalStudyTimeHours = if (learningRecords.isNotEmpty()) {
                learningRecords.sumOf { it.totalStudyDuration } / 60.0 // 转换为小时
            } else {
                0.0
            }
            totalStudyTimeTextView.text = String.format("%.1f小时", totalStudyTimeHours)

            // 计算平均完成率
            val averageCompletionRate = if (learningRecords.isNotEmpty()) {
                learningRecords.map { it.averageCompletionRate }.average()
            } else {
                0.0
            }
            averageCompletionRateTextView.text = String.format("%.1f%%", averageCompletionRate)

            // 计算平均正确率
            val averageAccuracy = if (learningRecords.isNotEmpty()) {
                learningRecords.map { it.averageQuizAccuracy }.average()
            } else {
                0.0
            }
            averageAccuracyTextView.text = String.format("%.1f%%", averageAccuracy)

            // 计算平均每人完成课程数
            val averageCompletedCourses = if (learningRecords.isNotEmpty()) {
                learningRecords.sumOf { it.completedCourseCount } / learningRecords.size.toDouble()
            } else {
                0.0
            }
            courseCompletionTextView.text = String.format("平均每人完成%.1f门课程", averageCompletedCourses)
        }
    }

    /**
     * 显示导出对话框
     */
    private fun showExportDialog() {
        val items = arrayOf("导出为Excel", "导出为PDF", "导出为CSV")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("导出学习统计")
            .setItems(items) { dialog, which ->
                when (which) {
                    0 -> exportAsExcel()
                    1 -> exportAsPDF()
                    2 -> exportAsCSV()
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 导出为Excel（模拟）
     */
    private fun exportAsExcel() {
        // 模拟导出操作
        android.widget.Toast.makeText(this, "正在导出Excel文件...", android.widget.Toast.LENGTH_SHORT).show()

        // 在实际应用中，这里应该实现实际的导出逻辑
        // 例如：生成Excel文件并保存到设备或分享

        // 模拟导出完成
        android.widget.Toast.makeText(this, "Excel文件导出成功！", android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * 导出为PDF（模拟）
     */
    private fun exportAsPDF() {
        // 模拟导出操作
        android.widget.Toast.makeText(this, "正在生成PDF报告...", android.widget.Toast.LENGTH_SHORT).show()

        // 模拟导出完成
        android.widget.Toast.makeText(this, "PDF报告生成成功！", android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * 导出为CSV（模拟）
     */
    private fun exportAsCSV() {
        // 模拟导出操作
        android.widget.Toast.makeText(this, "正在导出CSV数据...", android.widget.Toast.LENGTH_SHORT).show()

        // 模拟导出完成
        android.widget.Toast.makeText(this, "CSV数据导出成功！", android.widget.Toast.LENGTH_SHORT).show()
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
}