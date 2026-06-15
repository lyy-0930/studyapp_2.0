package com.studyapp

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.adapter.CourseAdapter
import com.studyapp.manager.CourseSelectionManager
import com.studyapp.manager.CourseManager
import com.studyapp.manager.ApiService
import com.studyapp.manager.VideoStorageManager
import com.studyapp.model.ApiCourse
import com.studyapp.model.ApiResponse
import com.studyapp.model.Course
import com.studyapp.model.toCourse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

class CourseListActivity : AppCompatActivity(), CourseAdapter.OnCourseSelectListener {

    private lateinit var courseRecyclerView: RecyclerView
    private lateinit var courseAdapter: CourseAdapter
    private lateinit var courseSelectionManager: CourseSelectionManager
    private lateinit var backButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var selectedCountTextView: TextView
    private lateinit var totalCountTextView: TextView

    private lateinit var username: String
    private lateinit var apiService: ApiService
    private var currentUserId: Int = 0
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // 课程列表（从课程管理器加载）
    private val courseList = mutableListOf<Course>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_list)

        // 获取用户名
        username = intent.getStringExtra("username") ?: "student"
        val role = intent.getStringExtra("role") ?: "student"

        // 初始化API服务
        apiService = ApiService.getInstance(this)

        // 获取当前用户ID
        loadCurrentUserInfo()

        // 初始化课程选择管理器
        courseSelectionManager = CourseSelectionManager.forUser(this, username)

        // 初始化视图
        initViews()

        // 设置返回按钮
        backButton.setOnClickListener {
            finish()
        }

        // 设置刷新按钮
        refreshButton.setOnClickListener {
            loadCoursesFromManager()
            Toast.makeText(this, "正在同步课程列表...", Toast.LENGTH_SHORT).show()
        }

        // 设置RecyclerView
        setupRecyclerView()

        // 更新统计信息
        updateStatistics()

        // 从课程管理器加载课程列表
        loadCoursesFromManager()
    }

    private fun initViews() {
        courseRecyclerView = findViewById<RecyclerView>(R.id.courseRecyclerView)
        backButton = findViewById<ImageButton>(R.id.backButton)
        refreshButton = findViewById<ImageButton>(R.id.refreshButton)
        selectedCountTextView = findViewById<TextView>(R.id.selectedCountTextView)
        totalCountTextView = findViewById<TextView>(R.id.totalCountTextView)
    }

    private fun setupRecyclerView() {
        // 设置布局管理器
        val layoutManager = LinearLayoutManager(this)
        courseRecyclerView.layoutManager = layoutManager

        // 设置适配器
        courseAdapter = CourseAdapter(courseList)
        courseAdapter.setOnCourseSelectListener(this)
        courseRecyclerView.adapter = courseAdapter
    }

    private fun updateStatistics() {
        val totalCount = courseList.size
        val selectedCount = courseSelectionManager.getSelectedCourseCount()
        // 显示课程统计信息
        totalCountTextView.text = "总课程数：${totalCount}门"
        selectedCountTextView.text = "已选课程：${selectedCount}门"
    }


    /**
     * 从服务器加载课程列表，强制同步服务器数据
     * 只有在网络完全不可用时才回退到本地存储
     */
    private fun loadCoursesFromManager() {
        coroutineScope.launch {
            try {
                // 强制从服务器获取最新课程列表
                Log.d("CourseListActivity", "正在从服务器强制同步课程列表...")
                val result = apiService.getAllCourses()

                if (result.isSuccess) {
                    val apiResponse = result.getOrThrow()
                    if (apiResponse.success) {
                        // 服务器返回成功，即使data为null或空列表也要处理
                        val serverCourses = apiResponse.data?.map { apiCourse ->
                            val isSelected = courseSelectionManager.isCourseSelected(apiCourse.id)
                            apiCourse.toCourse(isSelected = isSelected)
                        } ?: emptyList()

                        Log.d("CourseListActivity", "从服务器获取到 ${serverCourses.size} 门课程")

                        courseList.clear()
                        courseList.addAll(serverCourses)

                        // 通知适配器数据已更新
                        courseAdapter.updateData(courseList)
                        updateStatistics()

                        // 同步本地存储：清理服务器不存在的课程
                        cleanupLocalCourses(serverCourses)

                        // 如果服务器返回空列表，也清除本地数据
                        if (serverCourses.isEmpty()) {
                            Log.d("CourseListActivity", "服务器课程列表为空，清理本地数据")
                            CourseManager.clearAllCourses(this@CourseListActivity)
                            VideoStorageManager.clearAllVideos(this@CourseListActivity)
                            // 清理所有选课记录
                            courseSelectionManager.clearAllCourses()
                            Log.d("CourseListActivity", "所有选课记录已清理")
                        }

                        // 显示同步成功提示
                        runOnUiThread {
                            if (serverCourses.isEmpty()) {
                                Toast.makeText(this@CourseListActivity, "服务器无课程数据", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@CourseListActivity, "课程列表已同步", Toast.LENGTH_SHORT).show()
                            }
                        }

                        return@launch
                    } else {
                        Log.e("CourseListActivity", "服务器返回失败: ${apiResponse.message}")
                        runOnUiThread {
                            Toast.makeText(this@CourseListActivity, "服务器错误: ${apiResponse.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("CourseListActivity", "从服务器获取课程失败: ${error?.message}", error)
                    runOnUiThread {
                        Toast.makeText(this@CourseListActivity, "网络连接失败，使用本地缓存", Toast.LENGTH_SHORT).show()
                    }
                }

                // 服务器获取失败，回退到本地存储（带过滤）
                Log.d("CourseListActivity", "回退到本地存储加载课程...")
                loadCoursesFromLocal()

            } catch (e: Exception) {
                Log.e("CourseListActivity", "加载课程异常: ${e.message}", e)
                // 异常情况下也回退到本地存储
                loadCoursesFromLocal()
            }
        }
    }

    /**
     * 从本地存储加载课程（回退方法）
     * 注意：此方法仅在没有网络连接时使用
     * 会过滤掉明显无效的课程（没有视频URL或模拟数据）
     */
    private fun loadCoursesFromLocal() {
        // 从CourseManager加载课程列表
        val courses = CourseManager.getCourses(this)
        courseList.clear()

        // 过滤无效课程（没有视频URL或模拟URL）
        val filteredCourses = courses.filter { course ->
            // 保留有真实视频URL的课程
            course.videoUrl != null &&
            !course.videoUrl.contains("example.com") &&
            !course.videoUrl.contains("mock") &&
            !course.videoUrl.contains("test")
        }

        // 更新每个课程的选课状态
        val updatedCourses = filteredCourses.map { course ->
            val isSelected = courseSelectionManager.isCourseSelected(course.id)
            course.copy(isSelected = isSelected)
        }

        courseList.addAll(updatedCourses)

        // 通知适配器数据已更新
        courseAdapter.updateData(courseList)
        updateStatistics()

        // 清理被过滤课程的选课记录
        if (courses.size > filteredCourses.size) {
            val filteredCount = courses.size - filteredCourses.size
            val filteredCourseIds = courses.filter { course ->
                !(course.videoUrl != null &&
                  !course.videoUrl.contains("example.com") &&
                  !course.videoUrl.contains("mock") &&
                  !course.videoUrl.contains("test"))
            }.map { it.id }

            filteredCourseIds.forEach { courseId ->
                val removed = courseSelectionManager.removeCourseId(courseId)
                if (removed) {
                    Log.d("CourseListActivity", "清理无效课程的选课记录: 课程ID=$courseId")
                }
            }
            Log.d("CourseListActivity", "过滤了 $filteredCount 门无效课程，并清理了相关选课记录")
        }

        // 提示用户网络连接问题
        runOnUiThread {
            Toast.makeText(this, "网络连接失败，使用本地缓存课程", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 清理本地存储中不在服务器列表的课程
     * @param serverCourses 服务器返回的课程列表
     */
    private fun cleanupLocalCourses(serverCourses: List<Course>) {
        try {
            // 获取本地所有课程
            val localCourses = CourseManager.getCourses(this)

            // 找出本地有但服务器没有的课程
            val serverCourseIds = serverCourses.map { it.id }.toSet()
            val coursesToRemove = localCourses.filter { it.id !in serverCourseIds }

            if (coursesToRemove.isNotEmpty()) {
                Log.d("CourseListActivity", "清理本地无效课程: ${coursesToRemove.size} 门")

                // 删除本地无效课程并清理相关选课记录
                coursesToRemove.forEach { course ->
                    CourseManager.deleteCourse(this, course.id)

                    // 清理该课程的选课记录
                    val removed = courseSelectionManager.removeCourseId(course.id)
                    if (removed) {
                        Log.d("CourseListActivity", "清理选课记录: ${course.name} (ID: ${course.id})")
                    }

                    Log.d("CourseListActivity", "删除本地课程: ${course.name} (ID: ${course.id})")

                    // 同时清理VideoStorageManager中的相关视频（如果视频URL匹配）
                    if (!course.videoUrl.isNullOrEmpty()) {
                        // 注意：VideoStorageManager目前没有按URL删除的方法
                        // 但可以清空所有视频重新从有效课程添加
                        // 这里先记录日志，后续可以改进
                        Log.d("CourseListActivity", "需要清理的视频URL: ${course.videoUrl}")
                    }
                }

                // 清空所有视频，然后重新从有效课程添加
                VideoStorageManager.clearAllVideos(this)

                // 从服务器课程重新添加有效视频到VideoStorageManager
                serverCourses.forEach { course ->
                    if (!course.videoUrl.isNullOrEmpty()) {
                        VideoStorageManager.saveVideo(
                            context = this,
                            videoUrl = course.videoUrl,
                            videoTitle = course.name,
                            videoDescription = course.description,
                            teacher = course.teacher
                        )
                    }
                }

                Log.d("CourseListActivity", "本地数据清理完成")
            }
        } catch (e: Exception) {
            Log.e("CourseListActivity", "清理本地课程失败: ${e.message}", e)
        }
    }

    /**
     * 每次回到界面时刷新课程列表
     */
    override fun onResume() {
        super.onResume()
        // 每次回到此界面时刷新课程列表
        loadCoursesFromManager()
    }

    /**
     * 加载当前登录用户信息
     */
    private fun loadCurrentUserInfo() {
        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        currentUserId = sharedPref.getInt("user_id", 0)
        val userRole = sharedPref.getString("role", "") ?: ""
        Log.d("CourseListActivity", "当前用户信息: ID=$currentUserId, 角色=$userRole")
    }

    /**
     * 调用后端API进行选课
     */
    private fun enrollCourseToBackend(course: Course) {
        if (currentUserId == 0) {
            Log.e("CourseListActivity", "用户ID无效，无法选课")
            Toast.makeText(this, "用户信息无效，无法选课", Toast.LENGTH_SHORT).show()
            return
        }

        coroutineScope.launch {
            try {
                val result = apiService.enrollCourse(
                    courseId = course.id,
                    studentId = currentUserId,
                    studentName = username
                )

                if (result.isSuccess) {
                    val enrollResponse = result.getOrThrow()
                    if (enrollResponse.success && enrollResponse.data != null) {
                        Log.d("CourseListActivity", "选课成功: 学生${currentUserId}选择了课程${course.id}")
                        // 选课成功，本地状态已经更新
                    } else {
                        Log.e("CourseListActivity", "选课失败: ${enrollResponse.message}")
                        // 选课失败，回滚本地状态
                        courseSelectionManager.updateCourseSelection(course.id, false)
                        runOnUiThread {
                            // 更新UI状态
                            val index = courseList.indexOfFirst { it.id == course.id }
                            if (index >= 0) {
                                courseList[index] = course.copy(isSelected = false)
                                courseAdapter.updateData(courseList)
                                updateStatistics()
                            }
                            Toast.makeText(this@CourseListActivity, "选课失败: ${enrollResponse.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "未知错误"
                    Log.e("CourseListActivity", "选课API调用失败: $error")
                    // 回滚本地状态
                    courseSelectionManager.updateCourseSelection(course.id, false)
                    runOnUiThread {
                        val index = courseList.indexOfFirst { it.id == course.id }
                        if (index >= 0) {
                            courseList[index] = course.copy(isSelected = false)
                            courseAdapter.updateData(courseList)
                            updateStatistics()
                        }
                        Toast.makeText(this@CourseListActivity, "选课失败: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("CourseListActivity", "选课异常: ${e.message}", e)
                // 回滚本地状态
                courseSelectionManager.updateCourseSelection(course.id, false)
                runOnUiThread {
                    val index = courseList.indexOfFirst { it.id == course.id }
                    if (index >= 0) {
                        courseList[index] = course.copy(isSelected = false)
                        courseAdapter.updateData(courseList)
                        updateStatistics()
                    }
                    Toast.makeText(this@CourseListActivity, "选课异常: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCourseSelected(course: Course, position: Int) {
        // 切换选课状态
        val newSelectedState = !course.isSelected

        // 更新课程选择管理器（本地）
        courseSelectionManager.updateCourseSelection(course.id, newSelectedState)

        // 更新课程列表中的课程状态
        if (position >= 0 && position < courseList.size) {
            courseList[position] = course.copy(isSelected = newSelectedState)
            courseAdapter.updateData(courseList)
        }

        // 更新统计信息
        updateStatistics()

        // 显示提示
        val action = if (newSelectedState) "选课" else "退选"
        Toast.makeText(this, "${action}课程: ${course.name}", Toast.LENGTH_SHORT).show()

        // 调用后端API同步选课状态
        if (newSelectedState) {
            // 选课
            enrollCourseToBackend(course)
        } else {
            // 退选（目前后端可能不支持退课，这里可以记录日志）
            Log.d("CourseListActivity", "学生退课: 课程ID=${course.id}, 学生ID=$currentUserId")
            // 如果需要后端退课功能，可以在这里添加API调用
        }
    }
}