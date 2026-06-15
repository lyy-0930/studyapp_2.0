package com.studyapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.adapter.CourseStatisticAdapter
import com.studyapp.manager.StatisticsManager
import com.studyapp.model.CourseStatistic
import com.studyapp.model.TeacherStatsData
import com.studyapp.model.TeacherCourseStats
import com.studyapp.model.TeacherStatsResponse
import com.studyapp.model.StudentStatsData
import com.studyapp.model.QuestionAccuracyData
import com.studyapp.manager.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

class StatisticsFragment : Fragment() {

    private lateinit var teacherNameTextView: TextView
    private lateinit var totalCoursesTextView: TextView
    private lateinit var totalStudentsTextView: TextView
    private lateinit var totalWatchTimeTextView: TextView
    private lateinit var averageDurationTextView: TextView
    private lateinit var averageClickRateTextView: TextView
    private lateinit var averageCompletionRateTextView: TextView
    private lateinit var averageQuizAccuracyTextView: TextView
    private lateinit var statisticsRecyclerView: RecyclerView
    private lateinit var statisticsDescriptionTextView: TextView
    private lateinit var overallStatsCard: CardView
    private lateinit var detailStatsTitle: TextView

    private lateinit var courseStatisticAdapter: CourseStatisticAdapter
    private var courseStatistics: List<CourseStatistic> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_statistics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)

        val teacherName = arguments?.getString("username") ?: "教师"
        teacherNameTextView.text = "教师：$teacherName"

        loadStatistics(teacherName)
    }

    override fun onResume() {
        super.onResume()
        val teacherName = arguments?.getString("username") ?: "教师"
        loadStatistics(teacherName)
    }

    private fun initViews(view: View) {
        teacherNameTextView = view.findViewById(R.id.teacherNameTextView)
        totalCoursesTextView = view.findViewById(R.id.totalCoursesTextView)
        totalStudentsTextView = view.findViewById(R.id.totalStudentsTextView)
        totalWatchTimeTextView = view.findViewById(R.id.totalWatchTimeTextView)
        averageDurationTextView = view.findViewById(R.id.averageDurationTextView)
        averageClickRateTextView = view.findViewById(R.id.averageClickRateTextView)
        averageCompletionRateTextView = view.findViewById(R.id.averageCompletionRateTextView)
        averageQuizAccuracyTextView = view.findViewById(R.id.averageQuizAccuracyTextView)
        statisticsRecyclerView = view.findViewById(R.id.statisticsRecyclerView)
        statisticsDescriptionTextView = view.findViewById(R.id.statisticsDescriptionTextView)
        overallStatsCard = view.findViewById(R.id.overallStatsCard)
        detailStatsTitle = view.findViewById(R.id.detailStatsTitle)

        courseStatisticAdapter = CourseStatisticAdapter(onCourseClick = { courseStatistic ->
            showCourseStatsDetail(courseStatistic)
        })
        statisticsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        statisticsRecyclerView.adapter = courseStatisticAdapter
    }

    private suspend fun fetchTeacherStatsFromApi(teacherName: String): TeacherStatsResponse? {
        return try {
            val url = URL("${ApiService.BASE_URL}/teacher/stats?username=${java.net.URLEncoder.encode(teacherName, "UTF-8")}")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            try {
                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val body = conn.inputStream.bufferedReader().readText()
                    parseTeacherStatsResponse(body)
                } else null
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e("TeacherStatsAPI", "获取教师统计数据异常: ${e.message}", e)
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

    private fun TeacherCourseStats.toCourseStatistic(): CourseStatistic {
        return CourseStatistic(
            id = this.courseId,
            courseName = this.courseName,
            teacherName = this.teacherName,
            enrolledStudents = this.enrolledStudents,
            averageWatchDuration = this.averageWatchTime,
            totalWatchTime = this.totalWatchTime.toDouble(),
            clickRate = this.averageClickCount,
            completionRate = this.averageProgress,
            totalViews = this.enrolledStudents * 2,
            uploadDate = Date()
        )
    }

    private fun TeacherStatsData.toOverallStatistics(): StatisticsManager.OverallStatistics {
        return StatisticsManager.OverallStatistics(
            totalCourses = this.totalCourses,
            totalStudents = this.totalStudents,
            totalWatchTime = this.totalWatchTime.toDouble(),
            averageWatchDuration = this.averageWatchDuration,
            averageClickRate = this.averageClickCount,
            averageCompletionRate = this.averageProgress,
            averageQuizAccuracy = this.averageQuizAccuracy
        )
    }

    private fun loadStatistics(teacherName: String) {
        statisticsDescriptionTextView.text = "正在加载统计数据..."
        statisticsDescriptionTextView.setTextColor(requireContext().getColor(R.color.darker_gray))
        overallStatsCard.visibility = View.GONE
        detailStatsTitle.visibility = View.GONE

        lifecycleScope.launch {
            val apiResponse = fetchTeacherStatsFromApi(teacherName)

            if (apiResponse?.success == true && apiResponse.data != null) {
                val teacherStatsData = apiResponse.data

                if (teacherStatsData.totalCourses > 0) {
                    courseStatistics = teacherStatsData.courses.map { it.toCourseStatistic() }
                    val overallStats = teacherStatsData.toOverallStatistics()

                    totalCoursesTextView.text = overallStats.totalCourses.toString()
                    totalStudentsTextView.text = overallStats.totalStudents.toString()
                    totalWatchTimeTextView.text = overallStats.getFormattedTotalWatchTime()
                    averageDurationTextView.text = overallStats.getFormattedAverageDuration()
                    averageClickRateTextView.text = overallStats.getFormattedAverageClickRate()
                    averageCompletionRateTextView.text = overallStats.getFormattedAverageCompletionRate()
                    averageQuizAccuracyTextView.text = overallStats.getFormattedAverageQuizAccuracy()

                    overallStatsCard.visibility = View.VISIBLE
                    detailStatsTitle.visibility = View.VISIBLE
                    statisticsDescriptionTextView.text = "以下是您上传课程的统计数据分析"
                    statisticsDescriptionTextView.setTextColor(requireContext().getColor(R.color.darker_gray))

                    courseStatisticAdapter.submitList(courseStatistics)
                } else {
                    courseStatistics = emptyList()
                    overallStatsCard.visibility = View.GONE
                    detailStatsTitle.visibility = View.GONE
                    statisticsDescriptionTextView.text = "您尚未上传任何教学视频，请先上传视频以查看统计数据。"
                    statisticsDescriptionTextView.setTextColor(requireContext().getColor(R.color.tech_red_alert))
                    courseStatisticAdapter.submitList(emptyList())
                    showToast("未上传教学视频，暂无统计数据")
                }
            } else {
                courseStatistics = emptyList()
                overallStatsCard.visibility = View.GONE
                detailStatsTitle.visibility = View.GONE
                val errorMessage = apiResponse?.message ?: "获取统计数据失败"
                statisticsDescriptionTextView.text = "数据加载失败: $errorMessage"
                statisticsDescriptionTextView.setTextColor(requireContext().getColor(R.color.tech_red_alert))
                courseStatisticAdapter.submitList(emptyList())
                showToast("加载统计数据失败")
            }
        }
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    // ==================== 课程统计详情弹框 ====================

    /**
     * 显示课程统计详情弹框（学生明细 + 题目正确率）
     */
    private fun showCourseStatsDetail(courseStatistic: CourseStatistic) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_course_stats_detail, null)
        val courseNameText: TextView = dialogView.findViewById(R.id.statsDetailCourseName)
        val tabStudents: TextView = dialogView.findViewById(R.id.tabStudentsBtn)
        val tabAccuracy: TextView = dialogView.findViewById(R.id.tabAccuracyBtn)
        val studentsPanel: LinearLayout = dialogView.findViewById(R.id.studentsPanel)
        val accuracyPanel: LinearLayout = dialogView.findViewById(R.id.accuracyPanel)
        val studentsLoading: TextView = dialogView.findViewById(R.id.studentsLoadingText)
        val accuracyLoading: TextView = dialogView.findViewById(R.id.accuracyLoadingText)
        val studentsContainer: LinearLayout = dialogView.findViewById(R.id.studentsListContainer)
        val accuracyContainer: LinearLayout = dialogView.findViewById(R.id.accuracyListContainer)

        courseNameText.text = courseStatistic.courseName

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("关闭") { d, _ -> d.dismiss() }
            .show()

        // Tab切换
        fun switchTab(tab: Int) {
            if (tab == 0) {
                tabStudents.background = requireContext().getDrawable(R.drawable.gradient_tsinghua_purple)
                tabStudents.setTextColor(requireContext().getColor(R.color.white))
                tabAccuracy.background = null
                tabAccuracy.setBackgroundColor(0xFFE8E8E8.toInt())
                tabAccuracy.setTextColor(requireContext().getColor(R.color.tsinghua_purple_dark))
                studentsPanel.visibility = View.VISIBLE
                accuracyPanel.visibility = View.GONE
            } else {
                tabAccuracy.background = requireContext().getDrawable(R.drawable.gradient_tsinghua_purple)
                tabAccuracy.setTextColor(requireContext().getColor(R.color.white))
                tabStudents.background = null
                tabStudents.setBackgroundColor(0xFFE8E8E8.toInt())
                tabStudents.setTextColor(requireContext().getColor(R.color.tsinghua_purple_dark))
                accuracyPanel.visibility = View.VISIBLE
                studentsPanel.visibility = View.GONE
            }
        }

        tabStudents.setOnClickListener { switchTab(0) }
        tabAccuracy.setOnClickListener { switchTab(1) }

        // 加载学生统计
        studentsLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val result: Result<StudentStatsData> = withContext(Dispatchers.IO) {
                    val api = ApiService.getInstance(requireContext())
                    api.getCourseStudentStats(courseStatistic.id)
                }
                studentsLoading.visibility = View.GONE
                result.onSuccess { data ->
                    if (data.students.isEmpty()) {
                        studentsContainer.addView(TextView(requireContext()).apply {
                            text = "暂无学生学习记录"
                            textSize = 14f
                            setTextColor(requireContext().getColor(R.color.darker_gray))
                            gravity = android.view.Gravity.CENTER
                            setPadding(0, 24, 0, 24)
                        })
                    } else {
                        // 表头
                        studentsContainer.addView(createStudentStatsHeader())
                        for (stu in data.students) {
                            studentsContainer.addView(createStudentStatsRow(stu))
                        }
                    }
                }.onFailure {
                    studentsContainer.addView(TextView(requireContext()).apply {
                        text = "加载失败: ${it.message}"
                        textSize = 14f
                    })
                }
            } catch (e: Exception) {
                studentsLoading.visibility = View.GONE
                studentsContainer.addView(TextView(requireContext()).apply {
                    text = "加载失败: ${e.message}"
                    textSize = 14f
                })
            }
        }

        // 加载题目正确率
        accuracyLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val result: Result<QuestionAccuracyData> = withContext(Dispatchers.IO) {
                    val api = ApiService.getInstance(requireContext())
                    api.getCourseQuestionAccuracy(courseStatistic.id)
                }
                accuracyLoading.visibility = View.GONE
                result.onSuccess { data ->
                    if (data.questions.isEmpty()) {
                        accuracyContainer.addView(TextView(requireContext()).apply {
                            text = "该课程暂无题目"
                            textSize = 14f
                            setTextColor(requireContext().getColor(R.color.darker_gray))
                            gravity = android.view.Gravity.CENTER
                            setPadding(0, 24, 0, 24)
                        })
                    } else {
                        // 总体正确率
                        val avgAccuracy = data.questions
                            .filter { it.answeredCount > 0 }
                            .map { it.accuracy }
                            .average()
                        accuracyContainer.addView(createAccuracySummaryView(data.totalQuestions, data.totalAttempts, avgAccuracy))
                        // 表头
                        accuracyContainer.addView(createAccuracyHeader())
                        for (q in data.questions) {
                            accuracyContainer.addView(createAccuracyRow(q))
                        }
                    }
                }.onFailure {
                    accuracyContainer.addView(TextView(requireContext()).apply {
                        text = "加载失败: ${it.message}"
                        textSize = 14f
                    })
                }
            } catch (e: Exception) {
                accuracyLoading.visibility = View.GONE
                accuracyContainer.addView(TextView(requireContext()).apply {
                    text = "加载失败: ${e.message}"
                    textSize = 14f
                })
            }
        }
    }

    private fun createStudentStatsHeader(): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 8, 4, 8)
            background = requireContext().getDrawable(R.drawable.edit_text_background)
        }
        row.addView(TextView(requireContext()).apply {
            text = "姓名"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.tsinghua_purple_dark))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(requireContext()).apply {
            text = "观看"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.tsinghua_purple_dark))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER
        })
        row.addView(TextView(requireContext()).apply {
            text = "点击"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.tsinghua_purple_dark))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER
        })
        row.addView(TextView(requireContext()).apply {
            text = "进度"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.tsinghua_purple_dark))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER
        })
        row.addView(TextView(requireContext()).apply {
            text = "测验"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.tsinghua_purple_dark))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER
        })
        return row
    }

    private fun createStudentStatsRow(stu: com.studyapp.model.StudentStatItem): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 10, 4, 10)
        }
        val watchTime = if (stu.totalWatchTime < 60) "${stu.totalWatchTime}分钟" else "${stu.totalWatchTime / 60}小时${stu.totalWatchTime % 60}分钟"
        val quizScore = if (stu.quiz != null) "${stu.quiz.score}分" else "-"

        row.addView(TextView(requireContext()).apply {
            text = stu.studentName
            textSize = 13f
            setTextColor(requireContext().getColor(R.color.black))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(requireContext()).apply {
            text = watchTime
            textSize = 13f
            setTextColor(requireContext().getColor(R.color.tech_cyan_info))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER
        })
        row.addView(TextView(requireContext()).apply {
            text = stu.totalClickCount.toString()
            textSize = 13f
            setTextColor(requireContext().getColor(R.color.tech_red_alert))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER
        })
        row.addView(TextView(requireContext()).apply {
            text = "${stu.averageProgress.toInt()}%"
            textSize = 13f
            setTextColor(requireContext().getColor(R.color.tech_orange_warning))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER
        })
        row.addView(TextView(requireContext()).apply {
            text = quizScore
            textSize = 13f
            setTextColor(requireContext().getColor(R.color.tech_green_data))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER
        })
        return row
    }

    private fun createAccuracySummaryView(totalQ: Int, totalAttempts: Int, avgAccuracy: Double): View {
        val card = androidx.cardview.widget.CardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, 8) }
            radius = 8f
            cardElevation = 1f
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
        }
        content.addView(TextView(requireContext()).apply {
            text = "共${totalQ}题 · ${totalAttempts}人答题 · 平均正确率 ${"%.1f".format(avgAccuracy)}%"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.tsinghua_purple_dark))
        })
        card.addView(content)
        return card
    }

    private fun createAccuracyHeader(): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 8, 4, 8)
            background = requireContext().getDrawable(R.drawable.edit_text_background)
        }
        row.addView(TextView(requireContext()).apply {
            text = "题目"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.tsinghua_purple_dark))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
        })
        row.addView(TextView(requireContext()).apply {
            text = "答对/答题"
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.tsinghua_purple_dark))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER
        })
        row.addView(TextView(requireContext()).apply {
            text = "正确率"
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(requireContext().getColor(R.color.tsinghua_purple_dark))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER
        })
        return row
    }

    private fun createAccuracyRow(q: com.studyapp.model.QuestionAccuracyItem): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 10, 4, 10)
        }
        val shortText = if (q.questionText.length > 30) q.questionText.take(30) + "..." else q.questionText
        val accuracyColor = when {
            q.accuracy >= 80 -> requireContext().getColor(R.color.tech_green_data)
            q.accuracy >= 50 -> requireContext().getColor(R.color.tech_orange_warning)
            else -> requireContext().getColor(R.color.tech_red_alert)
        }

        row.addView(TextView(requireContext()).apply {
            text = shortText
            textSize = 12f
            maxLines = 2
            setTextColor(requireContext().getColor(R.color.black))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
        })
        row.addView(TextView(requireContext()).apply {
            text = "${q.correctCount}/${q.answeredCount}"
            textSize = 12f
            setTextColor(requireContext().getColor(R.color.darker_gray))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER
        })
        row.addView(TextView(requireContext()).apply {
            text = "${"%.1f".format(q.accuracy)}%"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(accuracyColor)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER
        })
        return row
    }
}
