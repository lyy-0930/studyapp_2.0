package com.studyapp

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.util.ImageLoaderUtil
import com.studyapp.adapter.CourseAdapter
import com.studyapp.manager.ApiService
import com.studyapp.manager.CourseManager
import com.studyapp.manager.CourseSelectionManager
import com.studyapp.manager.StudyRecordManager
import com.studyapp.model.Category
import com.studyapp.model.Course
import com.studyapp.model.CourseMaterial
import com.studyapp.model.Question
import com.studyapp.model.QuizResult
import com.studyapp.model.toCourse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.fragment.app.Fragment

class StudentActivity : AppCompatActivity(),
    CourseAdapter.OnCourseSelectListener,
    CourseAdapter.OnCoursePlayListener,
    CourseAdapter.OnCourseClickListener {

    // ==================== 侧边栏 ====================
    private lateinit var sidebarUsername: TextView
    private lateinit var userAvatarText: TextView
    private lateinit var userAvatarImage: ImageView
    private lateinit var avatarFrame: FrameLayout
    private var currentAvatarUrl: String? = null
    private lateinit var navHome: LinearLayout
    private lateinit var navMyCourses: LinearLayout
    private lateinit var navSelectCourses: LinearLayout
    private lateinit var navLogout: LinearLayout

    // ==================== 内容容器 ====================
    private lateinit var homeContent: NestedScrollView
    private lateinit var myCoursesContent: NestedScrollView
    private lateinit var selectCoursesContent: NestedScrollView
    private lateinit var courseDetailContent: NestedScrollView
    private lateinit var navMessage: LinearLayout
    private lateinit var chatFragmentContainer: FrameLayout
    private var chatFragment: Fragment? = null

    // ==================== 首页（日历 + 学习计划） ====================
    private lateinit var currentMonthText: TextView
    private lateinit var prevMonthBtn: TextView
    private lateinit var nextMonthBtn: TextView
    private lateinit var calendarGrid: GridLayout
    private lateinit var selectedDateTitle: TextView
    private lateinit var selectedDateDisplay: TextView
    private lateinit var todayTotalMinutes: TextView
    private lateinit var todayCourseCount: TextView
    private lateinit var selectedCourseName: TextView
    private lateinit var studyMinutesInput: EditText
    private lateinit var addStudyRecordBtn: Button
    private lateinit var emptyRecordsLayout: LinearLayout
    private lateinit var recordsList: LinearLayout

    // ==================== 我的课程 ====================
    private lateinit var myCoursesSelectedCount: TextView
    private lateinit var myCoursesTotalCredits: TextView
    private lateinit var emptyMyCoursesLayout: LinearLayout
    private lateinit var myCoursesRecyclerView: RecyclerView
    private lateinit var myCoursesAdapter: CourseAdapter

    // ==================== 选择课程 ====================
    private lateinit var selectCoursesSelectedCount: TextView
    private lateinit var selectCoursesTotalCount: TextView
    private lateinit var selectCoursesRecyclerView: RecyclerView
    private lateinit var selectCoursesAdapter: CourseAdapter

    // ==================== 课程详情 ====================
    private lateinit var courseDetailBackBtn: TextView
    private lateinit var courseDetailTitle: TextView
    private lateinit var courseDetailTeacher: TextView
    private lateinit var detailTabQuiz: TextView
    private lateinit var detailTabMaterial: TextView
    private lateinit var courseDetailQuizPanel: LinearLayout
    private lateinit var quizResultCard: androidx.cardview.widget.CardView
    private lateinit var quizScoreText: TextView
    private lateinit var quizCorrectCountText: TextView
    private lateinit var quizSubmittedAtText: TextView
    private lateinit var quizLoadingText: TextView
    private lateinit var quizEmptyText: TextView
    private lateinit var quizQuestionsContainer: LinearLayout
    private lateinit var quizSubmitBtn: Button
    private lateinit var courseDetailMaterialPanel: LinearLayout
    private lateinit var materialLoadingText: TextView
    private lateinit var materialListLayout: LinearLayout
    private lateinit var materialEmptyText: TextView

    // ==================== 数据 ====================
    private lateinit var username: String
    private var userId: Int = 0
    private var role: String = "student"
    private lateinit var apiService: ApiService
    private lateinit var studyRecordManager: StudyRecordManager
    private lateinit var courseSelectionManager: CourseSelectionManager
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // 筛选状态
    private lateinit var myCoursesCategoryFilter: Spinner
    private lateinit var myCoursesYearFilter: Spinner
    private lateinit var selectCoursesCategoryFilter: Spinner
    private lateinit var selectCoursesYearFilter: Spinner
    private var filterCategories = listOf<Category>()
    private var allCoursesFull = listOf<Course>()   // 选择课程完整列表
    private var myCoursesFull = listOf<Course>()    // 我的课程完整列表

    // 日历状态
    private var calendarYear: Int = 0
    private var calendarMonth: Int = 0
    private var selectedDay: Int = -1
    private var selectedDateStr: String = ""

    // 课程数据
    private var enrolledCourses: List<Course> = emptyList()
    private var selectedCourseForRecord: Course? = null
    private var allCourses: List<Course> = emptyList()

    // 课程详情状态
    private var currentDetailCourse: Course? = null
    private var currentQuizQuestions: List<Question> = emptyList()
    private var currentQuizResult: QuizResult? = null
    private var currentQuizAnswers: Map<String, String> = emptyMap()
    private var currentDetailTab: Int = 0 // 0=测验, 1=资料

    // 当前选中的侧边栏项
    private var currentNavItem: Int = R.id.navHome

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student)

        // 获取用户信息
        username = intent.getStringExtra("username") ?: "学生"
        role = intent.getStringExtra("role") ?: "student"
        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        userId = sharedPref.getInt("user_id", 0)

        // 初始化服务
        apiService = ApiService.getInstance(this)
        studyRecordManager = StudyRecordManager(this)
        courseSelectionManager = CourseSelectionManager.forUser(this, username)

        // 初始化日历为当前月份
        val cal = Calendar.getInstance()
        calendarYear = cal.get(Calendar.YEAR)
        calendarMonth = cal.get(Calendar.MONTH) + 1
        selectedDay = cal.get(Calendar.DAY_OF_MONTH)
        updateSelectedDateStr()

        // 初始化视图
        initViews()
        setupAvatar()
        setupClickListeners()
        setupSidebarNavigation()
        setupAdapters()
        setupFilterSpinners()

        // 默认显示首页
        showPanel(0)
    }

    // ==================== 初始化 ====================

    private fun initViews() {
        // 侧边栏
        sidebarUsername = findViewById(R.id.sidebarUsername)
        userAvatarText = findViewById(R.id.userAvatarText)
        userAvatarImage = findViewById(R.id.userAvatarImage)
        avatarFrame = findViewById(R.id.avatarFrame)
        navHome = findViewById(R.id.navHome)
        navMyCourses = findViewById(R.id.navMyCourses)
        navSelectCourses = findViewById(R.id.navSelectCourses)
        navLogout = findViewById(R.id.navLogout)

        // 内容容器
        homeContent = findViewById(R.id.homeContent)
        myCoursesContent = findViewById(R.id.myCoursesContent)
        selectCoursesContent = findViewById(R.id.selectCoursesContent)
        navMessage = findViewById(R.id.navMessage)
        chatFragmentContainer = findViewById(R.id.chatFragmentContainer)

        // 首页
        currentMonthText = findViewById(R.id.currentMonthText)
        prevMonthBtn = findViewById(R.id.prevMonthBtn)
        nextMonthBtn = findViewById(R.id.nextMonthBtn)
        calendarGrid = findViewById(R.id.calendarGrid)
        selectedDateTitle = findViewById(R.id.selectedDateTitle)
        selectedDateDisplay = findViewById(R.id.selectedDateDisplay)
        todayTotalMinutes = findViewById(R.id.todayTotalMinutes)
        todayCourseCount = findViewById(R.id.todayCourseCount)
        selectedCourseName = findViewById(R.id.selectedCourseName)
        studyMinutesInput = findViewById(R.id.studyMinutesInput)
        addStudyRecordBtn = findViewById(R.id.addStudyRecordBtn)
        emptyRecordsLayout = findViewById(R.id.emptyRecordsLayout)
        recordsList = findViewById(R.id.recordsList)

        // 我的课程
        myCoursesSelectedCount = findViewById(R.id.myCoursesSelectedCount)
        myCoursesTotalCredits = findViewById(R.id.myCoursesTotalCredits)
        emptyMyCoursesLayout = findViewById(R.id.emptyMyCoursesLayout)
        myCoursesRecyclerView = findViewById(R.id.myCoursesRecyclerView)
        myCoursesCategoryFilter = findViewById(R.id.myCoursesCategoryFilter)
        myCoursesYearFilter = findViewById(R.id.myCoursesYearFilter)

        // 选择课程
        selectCoursesSelectedCount = findViewById(R.id.selectCoursesSelectedCount)
        selectCoursesTotalCount = findViewById(R.id.selectCoursesTotalCount)
        selectCoursesRecyclerView = findViewById(R.id.selectCoursesRecyclerView)
        selectCoursesCategoryFilter = findViewById(R.id.selectCoursesCategoryFilter)
        selectCoursesYearFilter = findViewById(R.id.selectCoursesYearFilter)

        // 课程详情
        courseDetailContent = findViewById(R.id.courseDetailContent)
        courseDetailBackBtn = findViewById(R.id.courseDetailBackBtn)
        courseDetailTitle = findViewById(R.id.courseDetailTitle)
        courseDetailTeacher = findViewById(R.id.courseDetailTeacher)
        detailTabQuiz = findViewById(R.id.detailTabQuiz)
        detailTabMaterial = findViewById(R.id.detailTabMaterial)
        courseDetailQuizPanel = findViewById(R.id.courseDetailQuizPanel)
        quizResultCard = findViewById(R.id.quizResultCard)
        quizScoreText = findViewById(R.id.quizScoreText)
        quizCorrectCountText = findViewById(R.id.quizCorrectCountText)
        quizSubmittedAtText = findViewById(R.id.quizSubmittedAtText)
        quizLoadingText = findViewById(R.id.quizLoadingText)
        quizEmptyText = findViewById(R.id.quizEmptyText)
        quizQuestionsContainer = findViewById(R.id.quizQuestionsContainer)
        quizSubmitBtn = findViewById(R.id.quizSubmitBtn)
        courseDetailMaterialPanel = findViewById(R.id.courseDetailMaterialPanel)
        materialLoadingText = findViewById(R.id.materialLoadingText)
        materialListLayout = findViewById(R.id.materialListLayout)
        materialEmptyText = findViewById(R.id.materialEmptyText)
    }

    private fun setupAvatar() {
        sidebarUsername.text = username
        val letter = if (username.isNotEmpty()) username.first().toString() else "学"
        userAvatarText.text = letter

        // 从 SharedPreferences 读取缓存的头像 URL
        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        currentAvatarUrl = sharedPref.getString("avatar_url", null)

        if (!currentAvatarUrl.isNullOrEmpty()) {
            val fullUrl = "${ApiService.BASE_URL}${currentAvatarUrl}"
            userAvatarImage.visibility = View.VISIBLE
            userAvatarText.visibility = View.GONE
            userAvatarImage.setImageResource(R.drawable.circle_avatar)
            ImageLoaderUtil.load(userAvatarImage, fullUrl, crossfade = true, circleCrop = true)
        } else {
            userAvatarImage.visibility = View.GONE
            userAvatarText.visibility = View.VISIBLE
        }

        // 点击头像选择图片
        avatarFrame.setOnClickListener { pickImage() }
    }

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            compressAndUploadAvatar(uri)
        }
    }

    private fun pickImage() {
        imagePickerLauncher.launch("image/*")
    }

    private fun compressAndUploadAvatar(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            val compressed = compressBitmap(bitmap, 512, 512)
            val file = java.io.File(cacheDir, "avatar_${userId}_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                compressed.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            }

            Toast.makeText(this, "正在上传头像...", Toast.LENGTH_SHORT).show()

            coroutineScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        apiService.uploadAvatar(userId, file)
                    }
                    if (result.isSuccess) {
                        val avatarUrl = result.getOrThrow()
                        currentAvatarUrl = avatarUrl

                        // 更新缓存
                        getSharedPreferences("login_prefs", MODE_PRIVATE).edit {
                            putString("avatar_url", avatarUrl)
                        }

                        // 刷新显示
                        val fullUrl = "${ApiService.BASE_URL}${avatarUrl}"
                        userAvatarImage.visibility = View.VISIBLE
                        userAvatarText.visibility = View.GONE
                        ImageLoaderUtil.load(userAvatarImage, fullUrl, crossfade = true, circleCrop = true)
                        Toast.makeText(this@StudentActivity, "头像上传成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@StudentActivity, "头像上传失败", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@StudentActivity, "上传出错: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "图片处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compressBitmap(bitmap: android.graphics.Bitmap, maxWidth: Int, maxHeight: Int): android.graphics.Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        if (ratio >= 1f) return bitmap
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun setupAdapters() {
        // 我的课程 - 播放模式
        myCoursesAdapter = CourseAdapter(emptyList())
        myCoursesAdapter.setButtonMode(CourseAdapter.ButtonMode.PLAY)
        myCoursesAdapter.setOnCoursePlayListener(this)
        myCoursesAdapter.setOnCourseClickListener(this)
        myCoursesRecyclerView.layoutManager = LinearLayoutManager(this)
        myCoursesRecyclerView.adapter = myCoursesAdapter

        // 选择课程 - 选课模式
        selectCoursesAdapter = CourseAdapter(emptyList())
        selectCoursesAdapter.setButtonMode(CourseAdapter.ButtonMode.SELECT)
        selectCoursesAdapter.setOnCourseSelectListener(this)
        selectCoursesRecyclerView.layoutManager = LinearLayoutManager(this)
        selectCoursesRecyclerView.adapter = selectCoursesAdapter
    }

    // ==================== 筛选功能 ====================

    private fun setupFilterSpinners() {
        // 加载分类到筛选 Spinner
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) { apiService.getCategories() }
            if (result.isSuccess) {
                filterCategories = result.getOrThrow()
                val names = listOf("全部") + filterCategories.map { it.name }

                val adapter = ArrayAdapter(this@StudentActivity, android.R.layout.simple_spinner_item, names).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                myCoursesCategoryFilter.adapter = adapter
                selectCoursesCategoryFilter.adapter = adapter

                // 设置筛选监听
                myCoursesCategoryFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                        applyMyCoursesFilter()
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
                myCoursesYearFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                        applyMyCoursesFilter()
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
                selectCoursesCategoryFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                        applySelectCoursesFilter()
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
                selectCoursesYearFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                        applySelectCoursesFilter()
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                }
            }
        }
    }

    private fun populateYearFilter(spinner: Spinner, courses: List<Course>) {
        val years = courses.mapNotNull { course ->
            course.createdAt?.let { if (it.length >= 4) it.substring(0, 4) else null }
        }.distinct().sortedDescending()

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("全部") + years).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.adapter = adapter
    }

    // ==================== 面板切换 ====================

    private fun showPanel(panelIndex: Int) {
        // 0=首页, 1=我的课程, 2=选择课程, 3=消息, 4=课程详情
        homeContent.visibility = if (panelIndex == 0) View.VISIBLE else View.GONE
        myCoursesContent.visibility = if (panelIndex == 1) View.VISIBLE else View.GONE
        selectCoursesContent.visibility = if (panelIndex == 2) View.VISIBLE else View.GONE
        chatFragmentContainer.visibility = if (panelIndex == 3) View.VISIBLE else View.GONE
        courseDetailContent.visibility = if (panelIndex == 4) View.VISIBLE else View.GONE

        if (panelIndex != 3 && chatFragment != null && chatFragment!!.isAdded) {
            supportFragmentManager.beginTransaction()
                .remove(chatFragment!!)
                .commitAllowingStateLoss()
            chatFragment = null
        }

        when (panelIndex) {
            0 -> {
                // 每次进入首页重置到当天
                val today = Calendar.getInstance()
                calendarYear = today.get(Calendar.YEAR)
                calendarMonth = today.get(Calendar.MONTH) + 1
                selectedDay = today.get(Calendar.DAY_OF_MONTH)
                updateSelectedDateStr()
                renderCalendar()
                updateStudyPlan()
                loadEnrolledCourses()
            }
            1 -> {
                loadMyCourses()
            }
            2 -> {
                loadSelectCourses()
            }
            3 -> {
                showChat()
            }
        }
    }

    private fun showHomeContent() {
        showPanel(0)
    }

    private fun showChat() {
        // 先同步本地选课到后端，确保会话已创建
        syncLocalEnrollments()

        if (chatFragment == null) {
            chatFragment = ChatFragment().apply {
                arguments = Bundle().apply {
                    putInt("user_id", userId)
                    putString("username", username)
                }
            }
        }
        if (chatFragment != null && !chatFragment!!.isAdded) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.chatFragmentContainer, chatFragment!!)
                .commitAllowingStateLoss()
        }
    }

    private fun syncLocalEnrollments() {
        val localIds = courseSelectionManager.getSelectedCourseIds()
        if (localIds.isEmpty()) return
        // 从后端获取已选课程ID列表，找出未同步的课程
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { apiService.getUserCourses(userId) }
                if (result.isSuccess) {
                    val res = result.getOrThrow()
                    val serverIds = if (res.success && res.data != null)
                        res.data.map { it.id }.toSet() else emptySet()
                    for (courseId in localIds) {
                        if (courseId !in serverIds) {
                            val course = CourseManager.getCourses(this@StudentActivity)
                                .find { it.id == courseId } ?: continue
                            withContext(Dispatchers.IO) {
                                apiService.enrollCourse(courseId, userId, username)
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    // ==================== 侧边栏导航 ====================

    private fun setupSidebarNavigation() {
        setActiveNavItem(navHome)

        navHome.setOnClickListener {
            showPanel(0)
            setActiveNavItem(navHome)
            currentNavItem = R.id.navHome
        }

        navMyCourses.setOnClickListener {
            showPanel(1)
            setActiveNavItem(navMyCourses)
            currentNavItem = R.id.navMyCourses
        }

        navSelectCourses.setOnClickListener {
            showPanel(2)
            setActiveNavItem(navSelectCourses)
            currentNavItem = R.id.navSelectCourses
        }

        navMessage.setOnClickListener {
            showPanel(3)
            setActiveNavItem(navMessage)
            currentNavItem = R.id.navMessage
        }

        navLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun setActiveNavItem(selectedItem: LinearLayout) {
        val navItems = listOf(navHome, navMyCourses, navSelectCourses, navMessage)
        for (item in navItems) {
            item.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            val textView = item.getChildAt(1) as? TextView
            textView?.setTextColor(resources.getColor(R.color.tsinghua_purple_dark))
        }
        selectedItem.setBackgroundColor(0x1A7D2181.toInt())
        val textView = selectedItem.getChildAt(1) as? TextView
        textView?.setTextColor(resources.getColor(R.color.tsinghua_purple))
    }

    // ==================== 点击事件 ====================

    private fun setupClickListeners() {
        prevMonthBtn.setOnClickListener {
            calendarMonth--
            if (calendarMonth < 1) { calendarMonth = 12; calendarYear-- }
            selectedDay = -1
            renderCalendar()
            clearStudyPlan()
        }

        nextMonthBtn.setOnClickListener {
            calendarMonth++
            if (calendarMonth > 12) { calendarMonth = 1; calendarYear++ }
            selectedDay = -1
            renderCalendar()
            clearStudyPlan()
        }

        selectedCourseName.setOnClickListener { showCourseSelectionDialog() }

        addStudyRecordBtn.setOnClickListener { addStudyRecord() }

        // 课程详情
        courseDetailBackBtn.setOnClickListener { showPanel(1) }
        detailTabQuiz.setOnClickListener { switchDetailTab(0) }
        detailTabMaterial.setOnClickListener { switchDetailTab(1) }
        quizSubmitBtn.setOnClickListener { submitQuiz() }
    }

    // ==================== 日历 ====================

    private fun renderCalendar() {
        currentMonthText.text = "${calendarYear}年 ${calendarMonth}月"
        calendarGrid.removeAllViews()

        val cal = Calendar.getInstance()
        cal.set(calendarYear, calendarMonth - 1, 1)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val today = Calendar.getInstance()
        val isCurrentMonth = calendarYear == today.get(Calendar.YEAR) &&
                calendarMonth == (today.get(Calendar.MONTH) + 1)
        val todayDay = if (isCurrentMonth) today.get(Calendar.DAY_OF_MONTH) else -1

        // 检测有记录的日期
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val daysWithRecords = mutableSetOf<Int>()
        for (day in 1..maxDay) {
            cal.set(calendarYear, calendarMonth - 1, day)
            if (studyRecordManager.getCourseCountForDate(sdf.format(cal.time)) > 0) {
                daysWithRecords.add(day)
            }
        }

        val totalCells = firstDayOfWeek + maxDay
        calendarGrid.rowCount = (totalCells + 6) / 7

        // 空白占位
        for (i in 0 until firstDayOfWeek) {
            calendarGrid.addView(createDayCell("", isPlaceholder = true))
        }

        // 日期格子
        for (day in 1..maxDay) {
            val isSel = day == selectedDay
            val isToday = day == todayDay
            val hasRec = day in daysWithRecords
            val cell = createDayCell(day.toString(), isSel, isToday, hasRec)

            val dayValue = day
            cell.setOnClickListener {
                selectedDay = dayValue
                updateSelectedDateStr()
                renderCalendar()
                updateStudyPlan()
            }
            calendarGrid.addView(cell)
        }
    }

    private fun createDayCell(
        text: String, isSelected: Boolean = false, isToday: Boolean = false,
        hasRecord: Boolean = false, isPlaceholder: Boolean = false
    ): LinearLayout {
        val cell = LinearLayout(this)
        cell.layoutParams = GridLayout.LayoutParams().apply {
            width = 0; height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(2, 2, 2, 2)
        }
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER
        cell.setPadding(4, 8, 4, 8)

        if (isSelected) cell.setBackgroundResource(R.drawable.circle_day)
        else if (isToday) cell.setBackgroundResource(R.drawable.circle_day_today)

        val dayText = TextView(this)
        dayText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        dayText.text = text
        dayText.textSize = if (isPlaceholder) 0f else 15f
        dayText.gravity = Gravity.CENTER
        dayText.setTextColor(
            when {
                isSelected -> resources.getColor(R.color.white)
                isToday -> resources.getColor(R.color.tsinghua_purple)
                else -> resources.getColor(R.color.darker_gray)
            }
        )
        if (isSelected || isToday) dayText.setTypeface(null, android.graphics.Typeface.BOLD)
        cell.addView(dayText)

        if (hasRecord && !isPlaceholder) {
            val dot = TextView(this)
            dot.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            dot.text = "•"; dot.textSize = 10f
            dot.setTextColor(resources.getColor(R.color.tsinghua_purple))
            dot.gravity = Gravity.CENTER
            cell.addView(dot)
        }
        return cell
    }

    // ==================== 学习计划 ====================

    private fun updateSelectedDateStr() {
        val cal = Calendar.getInstance()
        cal.set(calendarYear, calendarMonth - 1, selectedDay)
        selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    private fun updateStudyPlan() {
        if (selectedDay < 0) { clearStudyPlan(); return }

        val cal = Calendar.getInstance()
        cal.set(calendarYear, calendarMonth - 1, selectedDay)
        val displaySdf = SimpleDateFormat("M月d日 EEEE", Locale.CHINESE)
        selectedDateTitle.text = "📅 ${displaySdf.format(cal.time)} 学习计划"
        selectedDateDisplay.text = selectedDateStr

        todayTotalMinutes.text = studyRecordManager.getTotalMinutesForDate(selectedDateStr).toString()
        todayCourseCount.text = studyRecordManager.getCourseCountForDate(selectedDateStr).toString()

        val records = studyRecordManager.getRecordsForDate(selectedDateStr)
        if (records.isEmpty()) {
            emptyRecordsLayout.visibility = View.VISIBLE
            recordsList.visibility = View.GONE
        } else {
            emptyRecordsLayout.visibility = View.GONE
            recordsList.visibility = View.VISIBLE
            recordsList.removeAllViews()
            for (rec in records) {
                val v = layoutInflater.inflate(R.layout.item_study_record, recordsList, false)
                v.findViewById<TextView>(R.id.recordCourseName).text = rec.courseName
                v.findViewById<TextView>(R.id.recordMinutes).text = studyRecordManager.formatMinutes(rec.minutes)
                v.findViewById<ImageButton>(R.id.recordDeleteBtn).setOnClickListener {
                    studyRecordManager.removeRecord(selectedDateStr, rec.courseId)
                    updateStudyPlan(); renderCalendar()
                    Toast.makeText(this, "记录已删除", Toast.LENGTH_SHORT).show()
                }
                recordsList.addView(v)
            }
        }
    }

    private fun clearStudyPlan() {
        selectedDateTitle.text = "📅 选择日期查看学习计划"
        selectedDateDisplay.text = ""
        todayTotalMinutes.text = "0"; todayCourseCount.text = "0"
        emptyRecordsLayout.visibility = View.VISIBLE
        recordsList.visibility = View.GONE
    }

    // ==================== 选择课程（首页） ====================

    private fun showCourseSelectionDialog() {
        if (enrolledCourses.isEmpty()) {
            Toast.makeText(this, "暂无已选课程，请先去选择课程", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("选择课程")
            .setItems(enrolledCourses.map { it.name }.toTypedArray()) { _, which ->
                selectedCourseForRecord = enrolledCourses[which]
                selectedCourseName.text = enrolledCourses[which].name
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addStudyRecord() {
        if (selectedDay < 0) { Toast.makeText(this, "请先在日历中选择日期", Toast.LENGTH_SHORT).show(); return }
        val course = selectedCourseForRecord ?: run {
            Toast.makeText(this, "请先选择课程", Toast.LENGTH_SHORT).show(); return
        }
        val minutesStr = studyMinutesInput.text.toString().trim()
        if (minutesStr.isEmpty()) { Toast.makeText(this, "请输入学习分钟数", Toast.LENGTH_SHORT).show(); return }
        val minutes = minutesStr.toIntOrNull()
        if (minutes == null || minutes <= 0) { Toast.makeText(this, "请输入有效的分钟数", Toast.LENGTH_SHORT).show(); return }

        studyRecordManager.addRecord(selectedDateStr, course.id, course.name, minutes)
        syncStudyRecordToBackend(course.id, minutes)
        updateStudyPlan(); renderCalendar()
        studyMinutesInput.text.clear()
        selectedCourseForRecord = null; selectedCourseName.text = "请先选择课程"
        Toast.makeText(this, "学习记录已添加", Toast.LENGTH_SHORT).show()
    }

    private fun syncStudyRecordToBackend(courseId: Int, minutes: Int) {
        coroutineScope.launch {
            try { withContext(Dispatchers.IO) { apiService.recordStudyProgress(courseId, userId, minutes, 0, 0) } }
            catch (_: Exception) { }
        }
    }

    // ==================== 加载已选课程（首页） ====================

    private fun loadEnrolledCourses() {
        coroutineScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { apiService.getUserCourses(userId) }
                if (r.isSuccess) {
                    val d = r.getOrThrow()
                    if (d.success && d.data != null) {
                        enrolledCourses = d.data.map { it.toCourse(isSelected = true) }
                        return@launch
                    }
                }
            } catch (_: Exception) { }
            loadLocalEnrolledCourses()
        }
    }

    private fun loadLocalEnrolledCourses() {
        val ids = courseSelectionManager.getSelectedCourseIds()
        enrolledCourses = CourseManager.getCourses(this).filter { it.id in ids }
    }

    // ==================== 我的课程 ====================

    private fun loadMyCourses() {
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { apiService.getUserCourses(userId) }
                if (result.isSuccess) {
                    val res = result.getOrThrow()
                    if (res.success && res.data != null) {
                        val courses = res.data.map { it.toCourse(isSelected = true) }
                        updateMyCoursesUI(courses)
                        return@launch
                    }
                }
            } catch (_: Exception) { }
            // 回退本地
            val ids = courseSelectionManager.getSelectedCourseIds()
            val localCourses = CourseManager.getCourses(this@StudentActivity).filter { it.id in ids }.map { it.copy(isSelected = true) }
            updateMyCoursesUI(localCourses)
        }
    }

    private fun updateMyCoursesUI(courses: List<Course>) {
        myCoursesFull = courses
        populateYearFilter(myCoursesYearFilter, courses)
        applyMyCoursesFilter()
    }

    private fun applyMyCoursesFilter() {
        var filtered = myCoursesFull

        val categoryPos = myCoursesCategoryFilter.selectedItemPosition
        if (categoryPos > 0 && categoryPos - 1 < filterCategories.size) {
            val selectedId = filterCategories[categoryPos - 1].id
            filtered = filtered.filter { it.categoryId == selectedId }
        }

        val yearPos = myCoursesYearFilter.selectedItemPosition
        if (yearPos > 0 && myCoursesYearFilter.adapter != null) {
            val selectedYear = myCoursesYearFilter.getItemAtPosition(yearPos).toString()
            filtered = filtered.filter {
                it.createdAt?.let { it.length >= 4 && it.substring(0, 4) == selectedYear } == true
            }
        }

        myCoursesAdapter.updateData(filtered)
        myCoursesSelectedCount.text = filtered.size.toString()
        myCoursesTotalCredits.text = filtered.sumOf { it.credit }.toString()

        if (filtered.isEmpty()) {
            emptyMyCoursesLayout.visibility = View.VISIBLE
            myCoursesRecyclerView.visibility = View.GONE
        } else {
            emptyMyCoursesLayout.visibility = View.GONE
            myCoursesRecyclerView.visibility = View.VISIBLE
        }
    }

    // ==================== 选择课程 ====================

    private fun loadSelectCourses() {
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { apiService.getAllCourses() }
                if (result.isSuccess) {
                    val res = result.getOrThrow()
                    if (res.success && res.data != null) {
                        allCourses = res.data.map { apiCourse ->
                            val sel = courseSelectionManager.isCourseSelected(apiCourse.id)
                            apiCourse.toCourse(isSelected = sel)
                        }
                        updateSelectCoursesUI()
                        return@launch
                    }
                }
            } catch (_: Exception) { }
            // 回退本地
            val localCourses = CourseManager.getCourses(this@StudentActivity).map { course ->
                val sel = courseSelectionManager.isCourseSelected(course.id)
                course.copy(isSelected = sel)
            }
            allCourses = localCourses
            updateSelectCoursesUI()
        }
    }

    private fun updateSelectCoursesUI() {
        allCoursesFull = allCourses
        populateYearFilter(selectCoursesYearFilter, allCourses)
        applySelectCoursesFilter()
    }

    private fun applySelectCoursesFilter() {
        var filtered = allCoursesFull

        val categoryPos = selectCoursesCategoryFilter.selectedItemPosition
        if (categoryPos > 0 && categoryPos - 1 < filterCategories.size) {
            val selectedId = filterCategories[categoryPos - 1].id
            filtered = filtered.filter { it.categoryId == selectedId }
        }

        val yearPos = selectCoursesYearFilter.selectedItemPosition
        if (yearPos > 0 && selectCoursesYearFilter.adapter != null) {
            val selectedYear = selectCoursesYearFilter.getItemAtPosition(yearPos).toString()
            filtered = filtered.filter {
                it.createdAt?.let { it.length >= 4 && it.substring(0, 4) == selectedYear } == true
            }
        }

        selectCoursesAdapter.updateData(filtered)
        val selectedCount = filtered.count { it.isSelected }
        selectCoursesSelectedCount.text = "已选课程：${selectedCount}门"
        selectCoursesTotalCount.text = "总课程数：${filtered.size}门"
    }

    // ==================== CourseAdapter 回调 ====================

    override fun onCourseSelected(course: Course, position: Int) {
        val newState = !course.isSelected
        courseSelectionManager.updateCourseSelection(course.id, newState)

        // 更新本地列表状态
        if (position in allCourses.indices) {
            allCourses = allCourses.toMutableList().apply {
                this[position] = this[position].copy(isSelected = newState)
            }
            selectCoursesAdapter.updateData(allCourses)
            updateSelectCoursesUI()
        }

        val action = if (newState) "选课" else "退选"
        Toast.makeText(this, "${action}成功: ${course.name}", Toast.LENGTH_SHORT).show()

        // 同步后端
        if (newState) enrollCourseToBackend(course)
    }

    private fun enrollCourseToBackend(course: Course) {
        if (userId == 0) return
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) { apiService.enrollCourse(course.id, userId, username) }
            } catch (_: Exception) { }
        }
    }

    override fun onCoursePlay(course: Course, position: Int) {
        if (course.videoUrl.isNullOrEmpty()) {
            Toast.makeText(this, "该课程暂无视频内容", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, VideoPlayerActivity::class.java)
        intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, course.videoUrl)
        intent.putExtra(VideoPlayerActivity.EXTRA_COURSE_NAME, course.name)
        intent.putExtra("course_id", course.id)
        startActivity(intent)
    }

    override fun onCourseClick(course: Course, position: Int) {
        showCourseDetail(course)
    }

    // ==================== 学习资料查看 ====================

    private fun showCourseMaterialsDialog(course: Course) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_student_materials, null)
        val courseNameText: TextView = dialogView.findViewById(R.id.studentDialogCourseName)
        val courseTeacherText: TextView = dialogView.findViewById(R.id.studentDialogCourseTeacher)
        val playVideoBtn: Button = dialogView.findViewById(R.id.studentPlayVideoBtn)
        val materialListLayout: LinearLayout = dialogView.findViewById(R.id.studentMaterialListLayout)
        val emptyMaterialText: TextView = dialogView.findViewById(R.id.studentEmptyMaterialText)
        val loadingText: TextView = dialogView.findViewById(R.id.studentMaterialLoadingText)

        courseNameText.text = course.name
        courseTeacherText.text = "授课教师：${course.teacher}"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("关闭") { d, _ -> d.dismiss() }
            .show()

        // 播放视频按钮
        playVideoBtn.setOnClickListener {
            dialog.dismiss()
            onCoursePlay(course, 0)
        }

        // 加载资料列表
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    apiService.getCourseMaterials(course.id)
                }
                loadingText.visibility = View.GONE
                if (result.isSuccess) {
                    val materials = result.getOrThrow()
                    if (materials.isEmpty()) {
                        emptyMaterialText.visibility = View.VISIBLE
                    } else {
                        emptyMaterialText.visibility = View.GONE
                        for (mat in materials) {
                            addStudentMaterialItem(materialListLayout, mat)
                        }
                    }
                } else {
                    emptyMaterialText.visibility = View.VISIBLE
                    emptyMaterialText.text = "加载失败"
                }
            } catch (_: Exception) {
                loadingText.visibility = View.GONE
                emptyMaterialText.visibility = View.VISIBLE
                emptyMaterialText.text = "加载失败"
            }
        }
    }

    private fun addStudentMaterialItem(container: LinearLayout, material: CourseMaterial) {
        val itemView = LayoutInflater.from(this).inflate(R.layout.item_student_material, container, false)
        val iconText: TextView = itemView.findViewById(R.id.studentMaterialIcon)
        val nameText: TextView = itemView.findViewById(R.id.studentMaterialName)
        val sizeText: TextView = itemView.findViewById(R.id.studentMaterialSize)

        val ext = material.fileType?.lowercase() ?: ""
        iconText.text = when (ext) {
            "pdf" -> "📕"
            "doc", "docx" -> "📘"
            "ppt", "pptx" -> "📙"
            "xls", "xlsx" -> "📗"
            "txt" -> "📄"
            "zip", "rar", "7z" -> "📦"
            "mp4", "avi", "mov" -> "🎬"
            "png", "jpg", "jpeg", "gif" -> "🖼️"
            else -> "📎"
        }

        nameText.text = material.fileName
        sizeText.text = formatMaterialSize(material.fileSize)

        itemView.setOnClickListener {
            openMaterialFile(material)
        }

        container.addView(itemView)
    }

    private fun openMaterialFile(material: CourseMaterial) {
        val url = "${ApiService.BASE_URL}${material.fileUrl}"
        val fileName = material.fileName
        val fileType = material.fileType?.lowercase() ?: ""

        // 所有文件统一先下载到缓存，再用 FileProvider 打开
        // 这样 Android 会弹出"打开方式"选择器，用户可选择 WPS 等应用
        val safeName = fileName.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val cacheFile = java.io.File(cacheDir, safeName)
        if (cacheFile.exists() && cacheFile.length() > 0) {
            openCachedFile(cacheFile, fileType)
            return
        }

        android.widget.Toast.makeText(this, "正在下载「$fileName」...", android.widget.Toast.LENGTH_SHORT).show()
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 30000
                    conn.readTimeout = 60000
                    try {
                        if (conn.responseCode != java.net.HttpURLConnection.HTTP_OK) throw java.io.IOException("下载失败: HTTP ${conn.responseCode}")
                        conn.inputStream.use { input -> cacheFile.outputStream().use { output -> input.copyTo(output) } }
                    } finally { conn.disconnect() }
                }
                openCachedFile(cacheFile, fileType)
            } catch (e: Exception) {
                android.widget.Toast.makeText(this@StudentActivity, "下载失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openCachedFile(file: java.io.File, fileType: String) {
        val mimeType = when (fileType) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "ppt", "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "txt" -> "text/plain"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            else -> "application/octet-stream"
        }
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this@StudentActivity, "${packageName}.fileprovider", file
            )
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(
                this@StudentActivity,
                "无法打开此文件，请在手机安装 WPS 等应用",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun formatMaterialSize(size: Long): String = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
    }

    // ==================== 课程详情 ====================

    private fun showCourseDetail(course: Course) {
        currentDetailCourse = course
        currentDetailTab = 0
        currentQuizResult = null
        currentQuizQuestions = emptyList()
        currentQuizAnswers = emptyMap()

        courseDetailTitle.text = course.name
        courseDetailTeacher.text = "授课教师：${course.teacher}"

        showPanel(4)
        switchDetailTab(0)
        loadQuizData()
        loadMaterialData()
    }

    private fun switchDetailTab(tabIndex: Int) {
        currentDetailTab = tabIndex
        if (tabIndex == 0) {
            detailTabQuiz.setBackgroundResource(R.drawable.gradient_tsinghua_purple)
            detailTabQuiz.setTextColor(resources.getColor(R.color.white))
            detailTabMaterial.background = null
            detailTabMaterial.setBackgroundResource(R.drawable.sidebar_item_selector)
            detailTabMaterial.setTextColor(resources.getColor(R.color.tsinghua_purple_dark))
            courseDetailQuizPanel.visibility = View.VISIBLE
            courseDetailMaterialPanel.visibility = View.GONE
        } else {
            detailTabMaterial.setBackgroundResource(R.drawable.gradient_tsinghua_purple)
            detailTabMaterial.setTextColor(resources.getColor(R.color.white))
            detailTabQuiz.background = null
            detailTabQuiz.setBackgroundResource(R.drawable.sidebar_item_selector)
            detailTabQuiz.setTextColor(resources.getColor(R.color.tsinghua_purple_dark))
            courseDetailQuizPanel.visibility = View.GONE
            courseDetailMaterialPanel.visibility = View.VISIBLE
        }
    }

    private fun loadQuizData() {
        val course = currentDetailCourse ?: return
        quizResultCard.visibility = View.GONE
        quizQuestionsContainer.visibility = View.GONE
        quizSubmitBtn.visibility = View.GONE
        quizEmptyText.visibility = View.GONE
        quizLoadingText.visibility = View.VISIBLE

        coroutineScope.launch {
            // 先查询是否有已有成绩
            val result = withContext(Dispatchers.IO) {
                apiService.getQuizResult(course.id, userId)
            }
            result.onSuccess { quizResult ->
                if (quizResult != null) {
                    currentQuizResult = quizResult
                    quizLoadingText.visibility = View.GONE
                    showQuizResult(quizResult)
                    return@launch
                }
            }

            // 无成绩，加载题目
            val questionsResult = withContext(Dispatchers.IO) {
                apiService.getQuestions(course.id, status = "published")
            }
            questionsResult.onSuccess { questions ->
                quizLoadingText.visibility = View.GONE
                if (questions.isEmpty()) {
                    quizEmptyText.visibility = View.VISIBLE
                } else {
                    currentQuizQuestions = questions
                    showQuizQuestions(questions)
                }
            }.onFailure {
                quizLoadingText.visibility = View.GONE
                quizEmptyText.visibility = View.VISIBLE
                quizEmptyText.text = "加载题目失败"
            }
        }
    }

    private fun showQuizResult(result: QuizResult) {
        quizResultCard.visibility = View.VISIBLE
        quizScoreText.text = "${result.score}"
        quizCorrectCountText.text = "答对 ${result.correctCount} / ${result.totalQuestions} 题"
        if (!result.submittedAt.isNullOrEmpty()) {
            quizSubmittedAtText.visibility = View.VISIBLE
            quizSubmittedAtText.text = "提交时间：${result.submittedAt}"
        } else {
            quizSubmittedAtText.visibility = View.GONE
        }
        // 若有题目详情，显示答题回顾（正确答案对比）
        if (!result.questions.isNullOrEmpty()) {
            showQuizReview(result)
        }
    }

    /**
     * 去掉选项中的字母前缀，如 "A. xxx" → "xxx"，避免重复显示 "A. A. xxx"
     */
    private fun cleanOptionText(option: String): String {
        return option.replaceFirst(Regex("^[A-Za-z]\\.\\s*"), "")
    }

    /**
     * 标准化答案字母：处理 "A. 选项A" → "A"
     */
    private fun normalizeLetter(answer: String?): String? {
        if (answer == null) return null
        val s = answer.trim()
        val match = Regex("^([A-Za-z])\\.").find(s)
        return match?.groupValues?.get(1) ?: s
    }

    /**
     * 显示答题回顾：展示题目、学生答案和正确答案对比
     */
    private fun showQuizReview(result: QuizResult) {
        val questions = result.questions ?: return

        quizQuestionsContainer.removeAllViews()
        quizQuestionsContainer.visibility = View.VISIBLE
        quizSubmitBtn.visibility = View.GONE

        val letters = arrayOf("A", "B", "C", "D", "E", "F")

        for ((index, question) in questions.withIndex()) {
            val itemView = layoutInflater.inflate(R.layout.item_quiz_question, quizQuestionsContainer, false)
            val numberText = itemView.findViewById<TextView>(R.id.quizQuestionNumber)
            val questionText = itemView.findViewById<TextView>(R.id.quizQuestionText)
            val optionsGroup = itemView.findViewById<RadioGroup>(R.id.quizOptionsGroup)
            val resultIndicator = itemView.findViewById<TextView>(R.id.quizResultIndicator)
            val correctAnswerText = itemView.findViewById<TextView>(R.id.quizCorrectAnswerText)

            numberText.text = "第 ${index + 1} 题"
            questionText.text = question.questionText

            // 学生答案：优先用服务端字段，服务端未匹配时用本地保存的答案
            val studentAnswer = normalizeLetter(question.studentAnswer
                ?: currentQuizAnswers[question.id.toString()])
            val correctAnswer = normalizeLetter(question.correctAnswer)
            val isCorrect = studentAnswer != null && studentAnswer == correctAnswer

            // 答题结果指示
            resultIndicator.visibility = View.VISIBLE
            if (studentAnswer == null) {
                resultIndicator.text = "未作答"
                resultIndicator.setTextColor(resources.getColor(R.color.darker_gray))
            } else if (isCorrect) {
                resultIndicator.text = "✓ 正确"
                resultIndicator.setTextColor(resources.getColor(R.color.tsinghua_purple))
            } else {
                resultIndicator.text = "✗ 错误"
                resultIndicator.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            }

            // 生成选项（审核模式，不可交互）
            for ((optIndex, option) in question.options.withIndex()) {
                val letter = letters.getOrElse(optIndex) { "${optIndex + 1}" }
                val radioButton = RadioButton(this)
                radioButton.id = View.generateViewId()
                // 去掉选项中可能带有的 "A. " 前缀，避免重复显示
                radioButton.text = "$letter. ${cleanOptionText(option)}"
                radioButton.setTextSize(14f)

                // 高亮：正确答案→绿色，学生选错→红色，其他→灰色
                when {
                    letter == correctAnswer -> {
                        radioButton.setTextColor(resources.getColor(R.color.tsinghua_purple))
                    }
                    letter == studentAnswer && !isCorrect -> {
                        radioButton.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                    }
                    else -> {
                        radioButton.setTextColor(resources.getColor(R.color.darker_gray))
                    }
                }

                // 预选学生答案
                if (letter == studentAnswer) {
                    radioButton.isChecked = true
                }

                radioButton.isEnabled = false
                radioButton.setPadding(8, 8, 8, 8)
                optionsGroup.addView(radioButton)
            }

            // 正确答案提示：只在答错或未作答时显示（答对时绿色选项已标明）
            if (studentAnswer == null || !isCorrect) {
                correctAnswerText.visibility = View.VISIBLE
                val correctOptionIdx = letters.indexOf(correctAnswer)
                val correctOptionText = if (correctOptionIdx >= 0 && correctOptionIdx < question.options.size) {
                    cleanOptionText(question.options[correctOptionIdx])
                } else {
                    correctAnswer
                }
                correctAnswerText.text = "正确答案：$correctAnswer. $correctOptionText"
            } else {
                correctAnswerText.visibility = View.GONE
            }

            quizQuestionsContainer.addView(itemView)
        }
    }

    /**
     * 提交后加载答题详情（含题目和答案）
     */
    private fun loadQuizDetailAndShowReview(courseId: Int) {
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                apiService.getQuizResult(courseId, userId)
            }
            result.onSuccess { quizResult ->
                if (quizResult != null) {
                    currentQuizResult = quizResult
                    showQuizResult(quizResult)
                }
            }
        }
    }

    private fun showQuizQuestions(questions: List<Question>) {
        quizQuestionsContainer.removeAllViews()
        quizQuestionsContainer.visibility = View.VISIBLE

        for ((index, question) in questions.withIndex()) {
            val itemView = layoutInflater.inflate(R.layout.item_quiz_question, quizQuestionsContainer, false)
            val numberText = itemView.findViewById<TextView>(R.id.quizQuestionNumber)
            val questionText = itemView.findViewById<TextView>(R.id.quizQuestionText)
            val optionsGroup = itemView.findViewById<RadioGroup>(R.id.quizOptionsGroup)

            numberText.text = "第 ${index + 1} 题"
            questionText.text = question.questionText

            // 动态生成选项（去掉数据库中可能带有的 "A. " 前缀）
            val letters = arrayOf("A", "B", "C", "D", "E", "F")
            for ((optIndex, option) in question.options.withIndex()) {
                val radioButton = RadioButton(this)
                radioButton.id = View.generateViewId()
                radioButton.text = "${letters.getOrElse(optIndex) { "${optIndex + 1}" }}. ${cleanOptionText(option)}"
                radioButton.setTextSize(14f)
                radioButton.setTextColor(resources.getColor(R.color.darker_gray))
                radioButton.setPadding(8, 8, 8, 8)
                optionsGroup.addView(radioButton)
            }

            // 用 tag 记录题目ID，提交时作为答案key（比用题目文本更可靠）
            optionsGroup.tag = question.id

            quizQuestionsContainer.addView(itemView)
        }

        quizSubmitBtn.visibility = View.VISIBLE
    }

    private fun submitQuiz() {
        val course = currentDetailCourse ?: return
        if (currentQuizQuestions.isEmpty()) return

        // 收集所有答案
        val answers = mutableMapOf<String, String>()
        var allAnswered = true

        // 遍历所有 question item 中的 RadioGroup
        for (i in 0 until quizQuestionsContainer.childCount) {
            val itemView = quizQuestionsContainer.getChildAt(i)
            val optionsGroup = itemView.findViewById<RadioGroup>(R.id.quizOptionsGroup)
            val questionId = optionsGroup.tag as? Int ?: continue

            val selectedId = optionsGroup.checkedRadioButtonId
            if (selectedId == -1) {
                allAnswered = false
                continue
            }
            val selectedRadio = optionsGroup.findViewById<RadioButton>(selectedId)
            val selectedText = selectedRadio?.text?.toString() ?: continue

            // 提取选项字母（"A. xxx" -> "A"）
            val letter = selectedText.substringBefore(".").trim()
            answers[questionId.toString()] = letter
        }

        if (!allAnswered) {
            Toast.makeText(this, "请回答所有题目后再提交", Toast.LENGTH_SHORT).show()
            return
        }

        quizSubmitBtn.isEnabled = false
        quizSubmitBtn.text = "提交中..."

        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                apiService.submitQuiz(course.id, userId, answers)
            }
            quizSubmitBtn.isEnabled = true
            quizSubmitBtn.text = "提交答案"

            result.onSuccess { quizResult ->
                currentQuizResult = quizResult
                // 保存本地答案副本，以防服务端匹配失败时仍可显示
                currentQuizAnswers = answers
                // 隐藏提交按钮，加载答题回顾
                quizSubmitBtn.visibility = View.GONE
                loadQuizDetailAndShowReview(course.id)
                Toast.makeText(this@StudentActivity, "提交成功！得分：${quizResult.score}分", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(this@StudentActivity, "提交失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadMaterialData() {
        val course = currentDetailCourse ?: return
        materialListLayout.removeAllViews()
        materialListLayout.visibility = View.GONE
        materialEmptyText.visibility = View.GONE
        materialLoadingText.visibility = View.VISIBLE

        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                apiService.getCourseMaterials(course.id)
            }
            materialLoadingText.visibility = View.GONE
            result.onSuccess { materials ->
                if (materials.isEmpty()) {
                    materialEmptyText.visibility = View.VISIBLE
                } else {
                    materialListLayout.visibility = View.VISIBLE
                    for (mat in materials) {
                        addStudentMaterialItem(materialListLayout, mat)
                    }
                }
            }.onFailure {
                materialEmptyText.visibility = View.VISIBLE
                materialEmptyText.text = "加载资料失败"
            }
        }
    }

    // ==================== 退出登录 ====================

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("是") { _, _ ->
                getSharedPreferences("login_prefs", MODE_PRIVATE).edit {
                    putBoolean("is_logged_in", false); remove("username"); remove("login_time")
                }
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent); finish()
            }
            .setNegativeButton("否") { d, _ -> d.dismiss() }
            .setCancelable(true).show()
    }

    // ==================== 生命周期 ====================

    override fun onResume() {
        super.onResume()
        // 根据当前面板刷新数据
        when (currentNavItem) {
            R.id.navHome -> { showPanel(0) }
            R.id.navMyCourses -> { showPanel(1) }
            R.id.navSelectCourses -> { showPanel(2) }
            R.id.navMessage -> { showPanel(3) }
        }
    }
}
