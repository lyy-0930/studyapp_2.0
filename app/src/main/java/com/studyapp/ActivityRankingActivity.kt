package com.studyapp

import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.adapter.ActivityRankingAdapter
import com.studyapp.manager.AdminDataManager
import com.studyapp.manager.ApiService
import com.studyapp.model.User
import kotlinx.coroutines.launch

/**
 * 活跃率排行榜Activity
 */
class ActivityRankingActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var filterButton: TextView
    private lateinit var titleTextView: TextView
    private lateinit var statPeriodTextView: TextView
    private lateinit var totalUsersTextView: TextView
    private lateinit var averageActivityTextView: TextView
    private lateinit var topActivityTextView: TextView
    private lateinit var activityDistributionTextView: TextView
    private lateinit var rankingRecyclerView: RecyclerView

    private lateinit var activityRankingAdapter: ActivityRankingAdapter
    private var users: List<User> = emptyList()
    private var activityRecords: List<AdminDataManager.UserActivityStats> = emptyList()
    private var statDays: Int = 7 // 默认统计最近7天

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activity_ranking)

        // 初始化视图
        initViews()

        // 生成模拟数据
        generateAndDisplayData()

        // 设置返回按钮
        backButton.setOnClickListener {
            finish()
        }

        // 设置筛选按钮
        filterButton.setOnClickListener {
            showFilterDialog()
        }
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        filterButton = findViewById(R.id.filterButton)
        titleTextView = findViewById(R.id.titleTextView)
        statPeriodTextView = findViewById(R.id.statPeriodTextView)
        totalUsersTextView = findViewById(R.id.totalUsersTextView)
        averageActivityTextView = findViewById(R.id.averageActivityTextView)
        topActivityTextView = findViewById(R.id.topActivityTextView)
        activityDistributionTextView = findViewById(R.id.activityDistributionTextView)
        rankingRecyclerView = findViewById(R.id.rankingRecyclerView)

        // 设置RecyclerView
        activityRankingAdapter = ActivityRankingAdapter()
        rankingRecyclerView.layoutManager = LinearLayoutManager(this)
        rankingRecyclerView.adapter = activityRankingAdapter
    }

    /**
     * 生成并显示数据（从API获取真实数据）
     */
    private fun generateAndDisplayData() {
        loadActivityRankingData()
    }

    /**
     * 将API响应转换为UserActivityStats列表
     */
    private fun convertToUserActivityStats(response: ApiService.ActivityRankingResponse): List<AdminDataManager.UserActivityStats> {
        return response.ranking.map { item ->
            AdminDataManager.UserActivityStats(
                userId = item.userId,
                username = item.username,
                role = item.role,
                totalActivityScore = item.activityScore,
                totalStudyDuration = item.details.totalWatchTime.toDouble(), // 分钟
                totalLoginCount = 0, // API没有提供登录次数，设为0
                totalCourseCount = item.details.studyRecordsCount, // 使用学习记录数作为课程数
                totalQuizCount = item.details.totalClickCount // 使用点击次数作为测验数
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

        // 生成模拟活跃记录数据
        val activityRecordsList = AdminDataManager.generateActivityRecords(users, 30)

        // 计算活跃度排名
        activityRecords = AdminDataManager.calculateActivityRanking(activityRecordsList, statDays)

        // 更新统计UI
        updateStatistics()

        // 更新排行榜列表
        activityRankingAdapter.submitList(activityRecords)

        // 提示使用模拟数据
        android.widget.Toast.makeText(this, "使用模拟数据", android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * 更新统计信息
     * @param stats 可选的API统计数据，如果提供则使用API数据，否则使用activityRecords计算
     */
    private fun updateStatistics(stats: ApiService.ActivityRankingStats? = null) {
        // 更新统计时间
        statPeriodTextView.text = "最近${statDays}天"

        if (stats != null) {
            // 使用API提供的统计数据
            totalUsersTextView.text = stats.totalUsers.toString()
            averageActivityTextView.text = String.format("%.1f分", stats.averageActivityScore)
            topActivityTextView.text = String.format("%.1f分", stats.maxActivityScore)

            // 使用API提供的活跃度分布数据
            activityDistributionTextView.text = "高活跃度：${stats.activityDistribution.high}人，中等：${stats.activityDistribution.medium}人，低活跃度：${stats.activityDistribution.low}人"
        } else {
            // 使用activityRecords计算统计信息（模拟数据或转换后的数据）
            totalUsersTextView.text = activityRecords.size.toString()

            val averageActivity = if (activityRecords.isNotEmpty()) {
                activityRecords.sumOf { it.totalActivityScore } / activityRecords.size
            } else {
                0.0
            }
            averageActivityTextView.text = String.format("%.1f分", averageActivity)

            val topActivity = if (activityRecords.isNotEmpty()) {
                activityRecords.maxOf { it.totalActivityScore }
            } else {
                0.0
            }
            topActivityTextView.text = String.format("%.1f分", topActivity)

            // 计算活跃度分布
            val highCount = activityRecords.count { it.totalActivityScore >= 100 }
            val mediumCount = activityRecords.count { it.totalActivityScore in 50.0..99.9 }
            val lowCount = activityRecords.count { it.totalActivityScore < 50 }

            activityDistributionTextView.text = "高活跃度：${highCount}人，中等：${mediumCount}人，低活跃度：${lowCount}人"
        }
    }

    /**
     * 加载活跃度排行榜数据（通用方法，供筛选和刷新使用）
     */
    private fun loadActivityRankingData() {
        lifecycleScope.launch {
            try {
                val apiService = ApiService.getInstance(this@ActivityRankingActivity)
                val result = apiService.getActivityRanking(days = statDays, limit = 20)

                if (result.isSuccess) {
                    val activityRankingResponse = result.getOrNull()
                    if (activityRankingResponse != null) {
                        // 转换API数据为适配器需要的格式
                        activityRecords = convertToUserActivityStats(activityRankingResponse)

                        // 更新统计UI（使用真实统计数据）
                        updateStatistics(activityRankingResponse.stats)

                        // 更新排行榜列表
                        activityRankingAdapter.submitList(activityRecords)

                        // 显示成功提示（可选）
                        // android.widget.Toast.makeText(this@ActivityRankingActivity, "数据加载成功", android.widget.Toast.LENGTH_SHORT).show()
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
                Log.e("ActivityRanking", "网络请求异常", e)
                showError("网络请求异常: ${e.message}")
                fallbackToMockData()
            }
        }
    }

    /**
     * 显示筛选对话框
     */
    private fun showFilterDialog() {
        val items = arrayOf("最近3天", "最近7天", "最近30天", "最近90天")
        val currentSelection = when (statDays) {
            3 -> 0
            7 -> 1
            30 -> 2
            90 -> 3
            else -> 1
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择统计时间")
            .setSingleChoiceItems(items, currentSelection) { dialog, which ->
                // 更新统计天数
                statDays = when (which) {
                    0 -> 3
                    1 -> 7
                    2 -> 30
                    3 -> 90
                    else -> 7
                }

                // 重新调用API获取新时间范围的数据
                loadActivityRankingData()

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
}