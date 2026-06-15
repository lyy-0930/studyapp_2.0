package com.studyapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.adapter.CourseStatisticAdapter
import com.studyapp.manager.StatisticsManager
import com.studyapp.manager.ApiService
import com.studyapp.model.CourseStatistic
import com.studyapp.model.TeacherStatsResponse
import com.studyapp.model.TeacherStatsData
import com.studyapp.model.TeacherCourseStats
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

/**
 * 教师数据统计界面
 * 显示教师上传课程的详细统计数据
 */
class TeacherStatisticsActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var teacherNameTextView: TextView
    private lateinit var totalCoursesTextView: TextView
    private lateinit var totalStudentsTextView: TextView
    private lateinit var totalWatchTimeTextView: TextView
    private lateinit var averageDurationTextView: TextView
    private lateinit var averageClickRateTextView: TextView
    private lateinit var averageCompletionRateTextView: TextView
    private lateinit var statisticsRecyclerView: RecyclerView
    private lateinit var statisticsDescriptionTextView: TextView
    private lateinit var overallStatsCard: androidx.cardview.widget.CardView
    private lateinit var detailStatsTitle: TextView

    private lateinit var courseStatisticAdapter: CourseStatisticAdapter
    private var courseStatistics: List<CourseStatistic> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_statistics)

        // 初始化视图
        initViews()

        // 获取传递的教师信息
        val teacherName = intent.getStringExtra("username") ?: "教师"
        val role = intent.getStringExtra("role") ?: "teacher"

        // 设置教师姓名
        teacherNameTextView.text = "教师：$teacherName"

        // 生成模拟统计数据
        generateAndDisplayStatistics(teacherName)

        // 设置返回按钮
        backButton.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // 当从视频上传页面返回时，刷新数据
        val teacherName = intent.getStringExtra("username") ?: "教师"
        generateAndDisplayStatistics(teacherName)
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        teacherNameTextView = findViewById(R.id.teacherNameTextView)
        totalCoursesTextView = findViewById(R.id.totalCoursesTextView)
        totalStudentsTextView = findViewById(R.id.totalStudentsTextView)
        totalWatchTimeTextView = findViewById(R.id.totalWatchTimeTextView)
        averageDurationTextView = findViewById(R.id.averageDurationTextView)
        averageClickRateTextView = findViewById(R.id.averageClickRateTextView)
        averageCompletionRateTextView = findViewById(R.id.averageCompletionRateTextView)
        statisticsRecyclerView = findViewById(R.id.statisticsRecyclerView)
        statisticsDescriptionTextView = findViewById(R.id.statisticsDescriptionTextView)
        overallStatsCard = findViewById(R.id.overallStatsCard)
        detailStatsTitle = findViewById(R.id.detailStatsTitle)

        // 设置RecyclerView
        courseStatisticAdapter = CourseStatisticAdapter()
        statisticsRecyclerView.layoutManager = LinearLayoutManager(this)
        statisticsRecyclerView.adapter = courseStatisticAdapter
    }

    /**
     * 从后端API获取教师统计数据
     */
    private suspend fun fetchTeacherStatsFromApi(teacherName: String): TeacherStatsResponse? {
        return try {
            Log.d("TeacherStatsAPI", "调用教师统计API，用户名: $teacherName")
            val url = URL("${ApiService.BASE_URL}/teacher/stats?username=${java.net.URLEncoder.encode(teacherName, "UTF-8")}")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            try {
                val responseCode = conn.responseCode
                Log.d("TeacherStatsAPI", "HTTP $responseCode")
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val body = conn.inputStream.bufferedReader().readText()
                    parseTeacherStatsResponse(body)
                } else null
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e("TeacherStatsAPI", "获取教师统计数据异常: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    private fun parseTeacherStatsResponse(jsonStr: String): TeacherStatsResponse {
        val root = JSONObject(jsonStr)
        val dataObj = root.optJSONObject("data")
        val data = if (dataObj != null) {
            val coursesArray = dataObj.getJSONArray("courses")
            val courses = mutableListOf<TeacherCourseStats>()
            for (i in 0 until coursesArray.length()) {
                val c = coursesArray.getJSONObject(i)
                courses.add(TeacherCourseStats(
                    courseId = c.getInt("courseId"),
                    courseName = c.getString("courseName"),
                    description = c.optString("description"),
                    teacherName = c.optString("teacherName", ""),
                    enrolledStudents = c.getInt("enrolledStudents"),
                    averageWatchTime = c.optDouble("averageWatchTime", 0.0),
                    averageProgress = c.optDouble("averageProgress", 0.0),
                    totalWatchTime = c.getInt("totalWatchTime"),
                    averageClickCount = c.optDouble("averageClickCount", 0.0),
                    totalClickCount = c.optInt("totalClickCount", 0),
                    averageQuizAccuracy = c.optDouble("averageQuizAccuracy", 0.0),
                    quizAttemptCount = c.optInt("quizAttemptCount", 0),
                    createdAt = c.optString("createdAt", "")
                ))
            }
            TeacherStatsData(
                teacherId = dataObj.getInt("teacherId"),
                teacherName = dataObj.optString("teacherName", ""),
                totalCourses = dataObj.getInt("totalCourses"),
                totalStudents = dataObj.getInt("totalStudents"),
                totalWatchTime = dataObj.getInt("totalWatchTime"),
                averageWatchDuration = dataObj.optDouble("averageWatchDuration", 0.0),
                averageProgress = dataObj.optDouble("averageProgress", 0.0),
                averageClickCount = dataObj.optDouble("averageClickCount", 0.0),
                averageQuizAccuracy = dataObj.optDouble("averageQuizAccuracy", 0.0),
                courses = courses
            )
        } else null
        return TeacherStatsResponse(
            success = root.getBoolean("success"),
            message = root.optString("message", ""),
            data = data
        )
    }

    /**
     * 将TeacherCourseStats转换为CourseStatistic
     */
    private fun TeacherCourseStats.toCourseStatistic(): CourseStatistic {
        // 使用真实的平均点击次数作为点击率
        val clickRate = this.averageClickCount
        return CourseStatistic(
            id = this.courseId,
            courseName = this.courseName,
            teacherName = this.teacherName,
            enrolledStudents = this.enrolledStudents,
            averageWatchDuration = this.averageWatchTime,
            totalWatchTime = this.totalWatchTime.toDouble(),
            clickRate = clickRate,
            completionRate = this.averageProgress,
            totalViews = this.enrolledStudents * 2, // 估计：每个学生平均观看2次
            uploadDate = Date() // 暂时使用当前日期，实际应从createdAt解析
        )
    }

    /**
     * 将TeacherStatsData转换为OverallStatistics
     */
    private fun TeacherStatsData.toOverallStatistics(): StatisticsManager.OverallStatistics {
        // 使用真实的平均点击次数作为点击率
        val clickRate = this.averageClickCount
        return StatisticsManager.OverallStatistics(
            totalCourses = this.totalCourses,
            totalStudents = this.totalStudents,
            totalWatchTime = this.totalWatchTime.toDouble(),
            averageWatchDuration = this.averageWatchDuration,
            averageClickRate = clickRate,
            averageCompletionRate = this.averageProgress
        )
    }

    /**
     * 生成并显示统计数据
     * 从后端API获取真实数据，如果没有上传课程则显示提示
     */
    private fun generateAndDisplayStatistics(teacherName: String) {
        Log.d("TeacherStatistics", "开始生成统计，教师姓名: '$teacherName' (长度: ${teacherName.length})")

        // 显示加载状态
        statisticsDescriptionTextView.text = "正在加载统计数据..."
        statisticsDescriptionTextView.setTextColor(getColor(R.color.darker_gray))
        overallStatsCard.visibility = android.view.View.GONE
        detailStatsTitle.visibility = android.view.View.GONE

        lifecycleScope.launch {
            // 从后端API获取数据
            Log.d("TeacherStatistics", "正在获取教师统计数据，教师姓名: $teacherName")
            val apiResponse = fetchTeacherStatsFromApi(teacherName)
            Log.d("TeacherStatistics", "API响应: success=${apiResponse?.success}, message=${apiResponse?.message}")

            if (apiResponse?.success == true && apiResponse.data != null) {
                // API请求成功，有数据
                val teacherStatsData = apiResponse.data
                Log.d("TeacherStatistics", "获取到教师数据: teacherId=${teacherStatsData.teacherId}, totalCourses=${teacherStatsData.totalCourses}")

                if (teacherStatsData.totalCourses > 0) {
                    // 有课程数据，显示统计数据
                    // 转换课程统计数据
                    courseStatistics = teacherStatsData.courses.map { it.toCourseStatistic() }

                    // 转换总体统计数据
                    val overallStats = teacherStatsData.toOverallStatistics()

                    // 更新总体统计UI
                    totalCoursesTextView.text = overallStats.totalCourses.toString()
                    totalStudentsTextView.text = overallStats.totalStudents.toString()
                    totalWatchTimeTextView.text = overallStats.getFormattedTotalWatchTime()
                    averageDurationTextView.text = overallStats.getFormattedAverageDuration()
                    averageClickRateTextView.text = overallStats.getFormattedAverageClickRate()
                    averageCompletionRateTextView.text = overallStats.getFormattedAverageCompletionRate()

                    // 显示统计区域
                    overallStatsCard.visibility = android.view.View.VISIBLE
                    detailStatsTitle.visibility = android.view.View.VISIBLE
                    statisticsDescriptionTextView.text = "以下是您上传课程的统计数据分析"
                    statisticsDescriptionTextView.setTextColor(getColor(R.color.darker_gray))

                    // 更新课程统计列表
                    courseStatisticAdapter.submitList(courseStatistics)
                } else {
                    // 没有课程数据，显示提示信息
                    courseStatistics = emptyList()

                    // 隐藏总体统计区域
                    overallStatsCard.visibility = android.view.View.GONE
                    detailStatsTitle.visibility = android.view.View.GONE

                    // 更新描述文本为提示信息
                    statisticsDescriptionTextView.text = "您尚未上传任何教学视频，请先上传视频以查看统计数据。"
                    statisticsDescriptionTextView.setTextColor(getColor(R.color.tech_red_alert))

                    // 清空课程统计列表
                    courseStatisticAdapter.submitList(emptyList())

                    // 显示提示Toast
                    showToast("未上传教学视频，暂无统计数据")
                }
            } else {
                // API请求失败或无数据
                Log.w("TeacherStatistics", "API请求失败: apiResponse=${apiResponse?.let { "success=${it.success}, message='${it.message}', data=${it.data != null}" } ?: "null"}")
                courseStatistics = emptyList()

                // 隐藏总体统计区域
                overallStatsCard.visibility = android.view.View.GONE
                detailStatsTitle.visibility = android.view.View.GONE

                // 显示错误信息
                val errorMessage = apiResponse?.message ?: "获取统计数据失败"
                statisticsDescriptionTextView.text = "数据加载失败: $errorMessage"
                statisticsDescriptionTextView.setTextColor(getColor(R.color.tech_red_alert))

                // 清空课程统计列表
                courseStatisticAdapter.submitList(emptyList())

                // 显示错误Toast
                showToast("加载统计数据失败")
            }
        }
    }


    /**
     * 显示Toast消息
     */
    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}