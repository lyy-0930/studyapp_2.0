package com.studyapp

import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.adapter.CourseAdapter
import com.studyapp.manager.CourseSelectionManager
import com.studyapp.manager.CourseManager
import com.studyapp.manager.ApiService
import com.studyapp.model.Course
import com.studyapp.model.ApiCourse
import com.studyapp.model.toCourse
import android.content.Intent
import android.widget.Toast
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 我的课程界面
 * 显示学生已选择的课程列表
 */
class MyCoursesActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var titleTextView: TextView
    private lateinit var selectedCountValue: TextView
    private lateinit var totalCreditsValue: TextView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var courseRecyclerView: RecyclerView
    private lateinit var courseListTitle: TextView

    private lateinit var username: String
    private var userId: Int = 1 // 默认用户ID，实际应从登录响应获取
    private lateinit var courseSelectionManager: CourseSelectionManager
    private lateinit var courseAdapter: CourseAdapter
    private lateinit var apiService: ApiService
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // 从课程管理器获取所有课程
    private fun getAllCourses(): List<Course> {
        return CourseManager.getCourses(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_courses)

        // 获取用户名和用户ID（从SharedPreferences获取）
        username = intent.getStringExtra("username") ?: "student001"
        val role = intent.getStringExtra("role") ?: "student"

        // 从SharedPreferences获取用户ID（简化处理，实际应从登录响应获取）
        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        userId = sharedPref.getInt("user_id", 1) // 默认使用ID 1（student001）

        // 初始化API服务
        apiService = ApiService.getInstance(this)

        // 初始化课程选择管理器（作为本地缓存备用）
        courseSelectionManager = CourseSelectionManager.forUser(this, username)

        // 初始化视图
        initViews()

        // 设置按钮点击事件
        setupButtonListeners()

        // 加载已选课程
        loadSelectedCourses()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        refreshButton = findViewById(R.id.refreshButton)
        titleTextView = findViewById(R.id.titleTextView)
        selectedCountValue = findViewById(R.id.selectedCountValue)
        totalCreditsValue = findViewById(R.id.totalCreditsValue)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        courseRecyclerView = findViewById(R.id.courseRecyclerView)
        courseListTitle = findViewById(R.id.courseListTitle)

        // 设置RecyclerView
        courseRecyclerView.layoutManager = LinearLayoutManager(this)
        courseAdapter = CourseAdapter(emptyList())
        // 在我的课程界面，设置为播放模式
        courseAdapter.setButtonMode(CourseAdapter.ButtonMode.PLAY)
        // 设置播放监听器
        courseAdapter.setOnCoursePlayListener(object : CourseAdapter.OnCoursePlayListener {
            override fun onCoursePlay(course: Course, position: Int) {
                // 跳转到视频播放界面
                openVideoPlayer(course)
            }
        })
        // 在我的课程界面，禁止修改选课状态
        courseAdapter.setOnCourseSelectListener(null)
        courseRecyclerView.adapter = courseAdapter
    }

    private fun setupButtonListeners() {
        // 返回按钮
        backButton.setOnClickListener {
            finish()
        }

        // 刷新按钮
        refreshButton.setOnClickListener {
            loadSelectedCourses()
        }
    }

    /**
     * 加载已选课程
     */
    private fun loadSelectedCourses() {
        // 显示加载状态
        refreshButton.isEnabled = false

        coroutineScope.launch {
            try {
                // 尝试从API获取课程
                val result = withContext(Dispatchers.IO) {
                    apiService.getUserCourses(userId)
                }

                if (result.isSuccess) {
                    val apiResponse = result.getOrThrow()

                    if (apiResponse.success && apiResponse.data != null) {
                        // 将API课程转换为应用课程
                        val selectedCourses = apiResponse.data.map { apiCourse ->
                            apiCourse.toCourse(isSelected = true)
                        }

                        // 更新UI
                        withContext(Dispatchers.Main) {
                            updateCourseList(selectedCourses)
                        }
                    } else {
                        // API返回失败
                        withContext(Dispatchers.Main) {
                            showToast("获取课程失败: ${apiResponse.message}")
                            loadLocalCourses() // 回退到本地数据
                        }
                    }
                } else {
                    // API调用失败
                    withContext(Dispatchers.Main) {
                        val error = result.exceptionOrNull()
                        Log.e("MyCoursesActivity", "API调用失败: ${error?.message}", error)
                        showToast("网络连接失败，使用本地数据")
                        loadLocalCourses() // 回退到本地数据
                    }
                }
            } catch (e: Exception) {
                Log.e("MyCoursesActivity", "加载课程异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showToast("加载课程失败")
                    loadLocalCourses() // 回退到本地数据
                }
            } finally {
                withContext(Dispatchers.Main) {
                    refreshButton.isEnabled = true
                }
            }
        }
    }


    /**
     * 加载本地课程（后备方案）
     */
    private fun loadLocalCourses() {
        // 获取已选课程ID
        val selectedCourseIds = courseSelectionManager.getSelectedCourseIds()

        // 从所有课程中筛选出已选课程
        val selectedCourses = getAllCourses().filter { course ->
            selectedCourseIds.contains(course.id)
        }.map { course ->
            // 确保课程标记为已选状态
            course.copy(isSelected = true)
        }

        // 更新课程列表
        updateCourseList(selectedCourses)
    }

    /**
     * 更新课程列表UI
     */
    private fun updateCourseList(selectedCourses: List<Course>) {
        // 更新适配器
        courseAdapter.updateData(selectedCourses)

        // 更新统计信息
        updateStatistics(selectedCourses)

        // 显示/隐藏空状态和课程列表
        if (selectedCourses.isEmpty()) {
            emptyStateLayout.visibility = LinearLayout.VISIBLE
            courseRecyclerView.visibility = RecyclerView.GONE
        } else {
            emptyStateLayout.visibility = LinearLayout.GONE
            courseRecyclerView.visibility = RecyclerView.VISIBLE
        }
    }

    /**
     * 更新统计信息
     */
    private fun updateStatistics(selectedCourses: List<Course>) {
        // 已选课程数量
        val selectedCount = selectedCourses.size
        selectedCountValue.text = selectedCount.toString()

        // 总学分
        val totalCredits = selectedCourses.sumOf { it.credit }
        totalCreditsValue.text = totalCredits.toString()
    }

    /**
     * 打开视频播放器
     * @param course 课程对象，包含视频URL
     */
    private fun openVideoPlayer(course: Course) {
        // 检查课程是否有视频URL
        if (course.videoUrl.isNullOrEmpty()) {
            Toast.makeText(this, "该课程暂无视频内容", Toast.LENGTH_SHORT).show()
            return
        }

        // 创建Intent跳转到VideoPlayerActivity（使用VideoView，兼容性更好）
        val intent = Intent(this, VideoPlayerActivity::class.java)
        // 传递视频URL、课程名称和课程ID
        intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, course.videoUrl)
        intent.putExtra(VideoPlayerActivity.EXTRA_COURSE_NAME, course.name)
        // 注意：VideoPlayerActivity可能不支持课程ID，但保留传递以备后用
        intent.putExtra("course_id", course.id) // 添加课程ID用于学习记录
        // 启动播放界面
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // 每次回到此界面时刷新数据
        loadSelectedCourses()
    }

    /**
     * 显示Toast消息
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}