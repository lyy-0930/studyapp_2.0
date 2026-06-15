package com.studyapp

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.adapter.TopStudentsAdapter
import com.studyapp.model.ClassStatsResponse
import com.studyapp.model.Student
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 教室数据统计Activity
 * 展示班级的学习数据统计和优秀学生排行榜
 */
class ClassroomActivity : AppCompatActivity() {

    // UI组件
    private lateinit var classNameTextView: TextView
    private lateinit var totalStudentsTextView: TextView
    private lateinit var activeStudentsTextView: TextView
    private lateinit var averageWatchTimeTextView: TextView
    private lateinit var completionRateTextView: TextView
    private lateinit var topStudentsRecyclerView: RecyclerView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var backButton: ImageButton
    private lateinit var refreshButton: ImageButton

    // 适配器
    private lateinit var topStudentsAdapter: TopStudentsAdapter

    // 协程
    private var loadJob: Job? = null

    // 班级ID（实际应从Intent获取）
    private val classId = "1" // 示例班级ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_classroom)

        // 初始化UI组件
        initViews()

        // 设置适配器
        setupRecyclerView()

        // 设置点击监听器
        setupClickListeners()

        // 加载数据
        loadClassStats()
    }

    private fun initViews() {
        classNameTextView = findViewById(R.id.classNameTextView)
        totalStudentsTextView = findViewById(R.id.totalStudentsTextView)
        activeStudentsTextView = findViewById(R.id.activeStudentsTextView)
        averageWatchTimeTextView = findViewById(R.id.averageWatchTimeTextView)
        completionRateTextView = findViewById(R.id.completionRateTextView)
        topStudentsRecyclerView = findViewById(R.id.topStudentsRecyclerView)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        backButton = findViewById(R.id.backButton)
        refreshButton = findViewById(R.id.refreshButton)

        // 设置班级名称
        classNameTextView.text = "班级：$classId"
    }

    private fun setupRecyclerView() {
        topStudentsAdapter = TopStudentsAdapter()
        topStudentsRecyclerView.layoutManager = LinearLayoutManager(this)
        topStudentsRecyclerView.adapter = topStudentsAdapter

        // 设置学生点击监听器
        topStudentsAdapter.setOnStudentClickListener(object : TopStudentsAdapter.OnStudentClickListener {
            override fun onStudentClick(student: com.studyapp.model.Student, position: Int) {
                Toast.makeText(
                    this@ClassroomActivity,
                    "点击了学生：${student.name}，进度：${student.getFormattedProgress()}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun setupClickListeners() {
        // 返回按钮
        backButton.setOnClickListener {
            finish()
        }

        // 刷新按钮
        refreshButton.setOnClickListener {
            loadClassStats()
        }
    }

    /**
     * 从API获取班级统计数据
     */
    private suspend fun fetchClassStats(classId: String): ClassStatsResponse {
        val url = URL("${com.studyapp.manager.ApiService.BASE_URL}/api/stats/class/$classId")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        return try {
            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val topStudentsArray = json.getJSONArray("topStudents")
                val students = mutableListOf<Student>()
                for (i in 0 until topStudentsArray.length()) {
                    val s = topStudentsArray.getJSONObject(i)
                    students.add(Student(
                        name = s.optString("name", ""),
                        progress = s.optInt("progress", 0)
                    ))
                }
                ClassStatsResponse(
                    totalStudents = json.getInt("totalStudents"),
                    activeStudents = json.getInt("activeStudents"),
                    averageWatchTime = json.getInt("averageWatchTime"),
                    completionRate = json.getInt("completionRate"),
                    topStudents = students
                )
            } else {
                throw java.io.IOException("HTTP $responseCode")
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 加载班级统计数据
     */
    private fun loadClassStats() {
        // 取消之前的请求
        loadJob?.cancel()

        // 显示加载中
        showLoading(true)

        loadJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                // 在IO线程执行网络请求
                val response = withContext(Dispatchers.IO) {
                    fetchClassStats(classId)
                }

                // 更新UI
                updateUI(response)

                // 显示成功提示
                Toast.makeText(
                    this@ClassroomActivity,
                    "数据加载成功",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                // 处理错误
                handleError(e)

            } finally {
                // 隐藏加载中
                showLoading(false)
            }
        }
    }

    /**
     * 更新UI显示统计数据
     */
    private fun updateUI(stats: ClassStatsResponse) {
        // 更新总体统计
        totalStudentsTextView.text = stats.totalStudents.toString()
        activeStudentsTextView.text = stats.activeStudents.toString()
        averageWatchTimeTextView.text = stats.getFormattedAverageWatchTime()
        completionRateTextView.text = stats.getFormattedCompletionRate()

        // 更新优秀学生列表
        topStudentsAdapter.updateData(stats.topStudents)

        // 如果没有优秀学生，显示提示
        if (stats.topStudents.isEmpty()) {
            Toast.makeText(
                this,
                "暂无优秀学生数据",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 处理错误
     */
    private fun handleError(e: Throwable) {
        val errorMessage = when {
            e.message?.contains("Unable to resolve host") == true -> "网络连接失败，请检查网络设置"
            e.message?.contains("timeout") == true -> "请求超时，请稍后重试"
            e.message?.contains("404") == true -> "班级数据不存在"
            e.message?.contains("500") == true -> "服务器内部错误"
            else -> "数据加载失败：${e.message}"
        }

        Toast.makeText(
            this,
            errorMessage,
            Toast.LENGTH_LONG
        ).show()

        // 显示空数据状态
        totalStudentsTextView.text = "0"
        activeStudentsTextView.text = "0"
        averageWatchTimeTextView.text = "0分钟"
        completionRateTextView.text = "0%"
        topStudentsAdapter.updateData(emptyList())
    }

    /**
     * 显示/隐藏加载中状态
     */
    private fun showLoading(show: Boolean) {
        loadingProgressBar.visibility = if (show) View.VISIBLE else View.GONE
        topStudentsRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        // 取消所有协程任务
        loadJob?.cancel()
    }
}