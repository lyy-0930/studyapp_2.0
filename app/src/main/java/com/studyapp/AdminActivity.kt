package com.studyapp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.edit
import com.studyapp.util.ImageLoaderUtil
import com.studyapp.manager.ApiService
import com.studyapp.model.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminActivity : AppCompatActivity() {

    // ==================== 侧边栏 ====================
    private lateinit var navDashboard: LinearLayout
    private lateinit var navUserManagement: LinearLayout
    private lateinit var navActivityRanking: LinearLayout
    private lateinit var navLearningStats: LinearLayout
    private lateinit var navCourseMastery: LinearLayout
    private lateinit var navCategoryManagement: LinearLayout
    private lateinit var navMonitor: LinearLayout
    private lateinit var navDataReset: LinearLayout
    private lateinit var navLogout: LinearLayout

    // ==================== 顶部栏 ====================
    private lateinit var headerUserAvatarText: TextView
    private lateinit var headerUserAvatarImage: ImageView
    private lateinit var headerUsername: TextView

    // ==================== 面板 ====================
    private lateinit var panelDashboard: View
    private lateinit var panelUserManagement: View
    private lateinit var panelActivityRanking: View
    private lateinit var panelLearningStats: View
    private lateinit var panelCourseMastery: View
    private lateinit var panelCategoryManagement: View
    private lateinit var panelDataReset: View

    // ==================== 仪表盘统计卡片 ====================
    private lateinit var statTotalUsers: TextView
    private lateinit var statOnlineUsers: TextView
    private lateinit var statOnlineRate: TextView
    private lateinit var statTotalCourses: TextView
    private lateinit var statAverageCompletion: TextView
    private lateinit var statTotalWatchTime: TextView
    private lateinit var statActiveUsers: TextView

    // ==================== 快捷入口 ====================
    private lateinit var quickOnlineUsers: View
    private lateinit var quickActivityRanking: View
    private lateinit var quickLearningStats: View
    private lateinit var quickCourseMastery: View
    private lateinit var quickUserManagement: View
    private lateinit var quickDataReset: View

    // ==================== 面板1：用户管理 ====================
    private lateinit var adminStatTotalUsers: TextView
    private lateinit var adminStatTeachers: TextView
    private lateinit var adminStatStudents: TextView
    private lateinit var userListContainer: LinearLayout
    private lateinit var userSearchEditText: EditText
    private lateinit var filterAll: TextView
    private lateinit var filterAdmin: TextView
    private lateinit var filterTeacher: TextView
    private lateinit var filterStudent: TextView
    private var currentUserFilterRole: String = "all"
    private var currentUserSearchQuery: String = ""

    // ==================== 面板2：活跃度排行 ====================
    private lateinit var rankTotalUsers: TextView
    private lateinit var rankAverageScore: TextView
    private lateinit var rankMaxScore: TextView
    private lateinit var rankingListContainer: LinearLayout

    // ==================== 面板3：学习统计 ====================
    private lateinit var learnTotalStudents: TextView
    private lateinit var learnAvgProgress: TextView
    private lateinit var learnCompletedCourses: TextView
    private lateinit var studentListContainer: LinearLayout

    // ==================== 面板4：课程掌握度 ====================
    private lateinit var masteryTotalCourses: TextView
    private lateinit var masteryAvgProgress: TextView
    private lateinit var masteryCompletionRate: TextView
    private lateinit var courseListContainer: LinearLayout

    // ==================== 面板5：分类管理 ====================
    private lateinit var categoryStatsLayout: LinearLayout
    private lateinit var categoryNameInput: EditText
    private lateinit var btnAddCategory: Button
    private lateinit var categoryListContainer: LinearLayout
    private val categories = mutableListOf<Category>()

    // ==================== 面板6：数据重置 ====================
    private lateinit var btnDataReset: Button

    // ==================== 面板7：监控面板 ====================
    private lateinit var panelMonitor: FrameLayout
    private var monitorMediaPlayer: android.media.MediaPlayer? = null
    private var monitorVideoProgressBar: ProgressBar? = null
    private var monitorPanelView: View? = null

    // ==================== 数据 ====================
    private lateinit var username: String
    private lateinit var role: String
    private var userId: Int = 0
    private lateinit var apiService: ApiService
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var currentPanel: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        username = intent.getStringExtra("username") ?: "管理员"
        role = intent.getStringExtra("role") ?: "admin"

        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        userId = sharedPref.getInt("user_id", 0)

        apiService = ApiService.getInstance(this)

        initViews()
        setupAvatar()
        setupSidebarNavigation()
        setupQuickActions()
        setupButtonListeners()
        showPanel(0)
        loadDashboardStats()
    }

    override fun onResume() {
        super.onResume()
        if (currentPanel == 7) {
            monitorMediaPlayer?.start()
            return
        }
        if (currentPanel != 0) {
            showPanel(0)
            setActiveNavItem(navDashboard)
        } else {
            loadDashboardStats()
        }
    }

    override fun onPause() {
        super.onPause()
        monitorMediaPlayer?.pause()
    }

    override fun onDestroy() {
        monitorMediaPlayer?.stop()
        monitorMediaPlayer?.release()
        monitorMediaPlayer = null
        monitorVideoProgressBar = null
        super.onDestroy()
    }

    private fun initViews() {
        // 侧边栏
        navDashboard = findViewById(R.id.navDashboard)
        navUserManagement = findViewById(R.id.navUserManagement)
        navActivityRanking = findViewById(R.id.navActivityRanking)
        navLearningStats = findViewById(R.id.navLearningStats)
        navCourseMastery = findViewById(R.id.navCourseMastery)
        navCategoryManagement = findViewById(R.id.navCategoryManagement)
        navDataReset = findViewById(R.id.navDataReset)
        navMonitor = findViewById(R.id.navMonitor)
        navLogout = findViewById(R.id.navLogout)

        // 顶部栏
        headerUserAvatarText = findViewById(R.id.headerUserAvatarText)
        headerUserAvatarImage = findViewById(R.id.headerUserAvatarImage)
        headerUsername = findViewById(R.id.headerUsername)

        // 面板
        panelDashboard = findViewById(R.id.panelDashboard)
        panelUserManagement = findViewById(R.id.panelUserManagement)
        panelActivityRanking = findViewById(R.id.panelActivityRanking)
        panelLearningStats = findViewById(R.id.panelLearningStats)
        panelCourseMastery = findViewById(R.id.panelCourseMastery)
        panelCategoryManagement = findViewById(R.id.panelCategoryManagement)
        panelDataReset = findViewById(R.id.panelDataReset)

        // 仪表盘统计卡片
        statTotalUsers = findViewById(R.id.statTotalUsers)
        statOnlineUsers = findViewById(R.id.statOnlineUsers)
        statOnlineRate = findViewById(R.id.statOnlineRate)
        statTotalCourses = findViewById(R.id.statTotalCourses)
        statAverageCompletion = findViewById(R.id.statAverageCompletion)
        statTotalWatchTime = findViewById(R.id.statTotalWatchTime)
        statActiveUsers = findViewById(R.id.statActiveUsers)

        // 快捷入口
        quickOnlineUsers = findViewById(R.id.quickOnlineUsers)
        quickActivityRanking = findViewById(R.id.quickActivityRanking)
        quickLearningStats = findViewById(R.id.quickLearningStats)
        quickCourseMastery = findViewById(R.id.quickCourseMastery)
        quickUserManagement = findViewById(R.id.quickUserManagement)
        quickDataReset = findViewById(R.id.quickDataReset)

        // 面板1：用户管理
        adminStatTotalUsers = findViewById(R.id.adminStatTotalUsers)
        adminStatTeachers = findViewById(R.id.adminStatTeachers)
        adminStatStudents = findViewById(R.id.adminStatStudents)
        userListContainer = findViewById(R.id.userListContainer)
        userSearchEditText = findViewById(R.id.userSearchEditText)
        filterAll = findViewById(R.id.filterAll)
        filterAdmin = findViewById(R.id.filterAdmin)
        filterTeacher = findViewById(R.id.filterTeacher)
        filterStudent = findViewById(R.id.filterStudent)

        // 搜索框文本变化监听
        userSearchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentUserSearchQuery = s?.toString() ?: ""
                loadUsersList()
            }
        })

        // 角色筛选标签点击事件
        val filterTabs = listOf(
            filterAll to "all",
            filterAdmin to "admin",
            filterTeacher to "teacher",
            filterStudent to "student"
        )
        for ((tab, role) in filterTabs) {
            tab.setOnClickListener {
                currentUserFilterRole = role
                updateFilterTabStyles()
                loadUsersList()
            }
        }

        // 添加"管理用户"按钮（打开UserManagementActivity）
        val manageUsersBtn = Button(this).apply {
            text = "管理用户（删除/重置密码）"
            textSize = 14f
            setTextColor(resources.getColor(R.color.white))
            setBackgroundResource(R.drawable.ic_button_background_selector)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 12, 0, 0) }
            setOnClickListener {
                startActivity(Intent(this@AdminActivity, UserManagementActivity::class.java))
            }
        }
        // 将按钮添加到 panelUserManagement 的内容容器末尾
        val userManagementPanel = findViewById<androidx.core.widget.NestedScrollView>(R.id.panelUserManagement)
        val panelContent = userManagementPanel.getChildAt(0) as? LinearLayout
        panelContent?.addView(manageUsersBtn)

        // 面板2：活跃度排行
        rankTotalUsers = findViewById(R.id.rankTotalUsers)
        rankAverageScore = findViewById(R.id.rankAverageScore)
        rankMaxScore = findViewById(R.id.rankMaxScore)
        rankingListContainer = findViewById(R.id.rankingListContainer)

        // 面板3：学习统计
        learnTotalStudents = findViewById(R.id.learnTotalStudents)
        learnAvgProgress = findViewById(R.id.learnAvgProgress)
        learnCompletedCourses = findViewById(R.id.learnCompletedCourses)
        studentListContainer = findViewById(R.id.studentListContainer)

        // 面板4：课程掌握度
        masteryTotalCourses = findViewById(R.id.masteryTotalCourses)
        masteryAvgProgress = findViewById(R.id.masteryAvgProgress)
        masteryCompletionRate = findViewById(R.id.masteryCompletionRate)
        courseListContainer = findViewById(R.id.courseListContainer)

        // 面板5：分类管理
        categoryStatsLayout = findViewById(R.id.categoryStatsLayout)
        categoryNameInput = findViewById(R.id.categoryNameInput)
        btnAddCategory = findViewById(R.id.btnAddCategory)
        categoryListContainer = findViewById(R.id.categoryListContainer)

        // 面板6：数据重置
        btnDataReset = findViewById(R.id.btnDataReset)

        // 面板7：监控面板
        panelMonitor = findViewById(R.id.panelMonitor)
    }

    private fun setupAvatar() {
        headerUsername.text = username
        val letter = if (username.isNotEmpty()) username.first().toString() else "管"
        headerUserAvatarText.text = letter

        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val avatarUrl = sharedPref.getString("avatar_url", null)

        if (!avatarUrl.isNullOrEmpty()) {
            val fullUrl = "${ApiService.BASE_URL}${avatarUrl}"
            headerUserAvatarImage.visibility = View.VISIBLE
            headerUserAvatarText.visibility = View.GONE
            headerUserAvatarImage.setImageResource(R.drawable.circle_avatar)
            ImageLoaderUtil.load(headerUserAvatarImage, fullUrl, crossfade = true, circleCrop = true)
        } else {
            headerUserAvatarImage.visibility = View.GONE
            headerUserAvatarText.visibility = View.VISIBLE
        }
    }

    // ==================== 面板切换 ====================

    private fun showPanel(panelIndex: Int) {
        val oldPanel = currentPanel
        currentPanel = panelIndex

        // 离开监控面板时暂停视频
        if (oldPanel == 7) {
            monitorMediaPlayer?.pause()
        }

        panelDashboard.visibility = if (panelIndex == 0) View.VISIBLE else View.GONE
        panelUserManagement.visibility = if (panelIndex == 1) View.VISIBLE else View.GONE
        panelActivityRanking.visibility = if (panelIndex == 2) View.VISIBLE else View.GONE
        panelLearningStats.visibility = if (panelIndex == 3) View.VISIBLE else View.GONE
        panelCourseMastery.visibility = if (panelIndex == 4) View.VISIBLE else View.GONE
        panelCategoryManagement.visibility = if (panelIndex == 5) View.VISIBLE else View.GONE
        panelDataReset.visibility = if (panelIndex == 6) View.VISIBLE else View.GONE
        panelMonitor.visibility = if (panelIndex == 7) View.VISIBLE else View.GONE

        when (panelIndex) {
            0 -> loadDashboardStats()
            1 -> loadUsersList()
            2 -> loadRankingList()
            3 -> loadStudentList()
            4 -> loadCourseList()
            5 -> loadCategoriesForAdmin()
            7 -> {
                loadMonitorPanel()
                monitorMediaPlayer?.start()
            }
        }
    }

    // ==================== 侧边栏导航 ====================

    private fun setupSidebarNavigation() {
        setActiveNavItem(navDashboard)

        navDashboard.setOnClickListener { showPanel(0); setActiveNavItem(navDashboard) }
        navUserManagement.setOnClickListener { showPanel(1); setActiveNavItem(navUserManagement) }
        navActivityRanking.setOnClickListener { showPanel(2); setActiveNavItem(navActivityRanking) }
        navLearningStats.setOnClickListener { showPanel(3); setActiveNavItem(navLearningStats) }
        navCourseMastery.setOnClickListener { showPanel(4); setActiveNavItem(navCourseMastery) }
        navCategoryManagement.setOnClickListener { showPanel(5); setActiveNavItem(navCategoryManagement) }
        navMonitor.setOnClickListener {
            showPanel(7)
            setActiveNavItem(navMonitor)
        }
        navDataReset.setOnClickListener { showPanel(6); setActiveNavItem(navDataReset) }
        navLogout.setOnClickListener { showLogoutConfirmationDialog() }
    }

    private fun setActiveNavItem(selectedItem: LinearLayout) {
        val navItems = listOf(navDashboard, navUserManagement, navActivityRanking,
            navLearningStats, navCourseMastery, navCategoryManagement, navMonitor, navDataReset)
        for (item in navItems) {
            item.setBackgroundColor(Color.TRANSPARENT)
            val icon = item.getChildAt(0) as? TextView
            icon?.alpha = 0.5f
            val textView = item.getChildAt(1) as? TextView
            textView?.setTextColor(resources.getColor(R.color.darker_gray))
        }
        selectedItem.setBackgroundColor(0x1A7D2181.toInt())
        val selectedIcon = selectedItem.getChildAt(0) as? TextView
        selectedIcon?.alpha = 1.0f
        val selectedText = selectedItem.getChildAt(1) as? TextView
        selectedText?.setTextColor(resources.getColor(R.color.tsinghua_purple))
    }

    // ==================== 快捷入口（打开对应面板） ====================

    private fun setupQuickActions() {
        quickOnlineUsers.setOnClickListener { showPanel(1); setActiveNavItem(navUserManagement) }
        quickActivityRanking.setOnClickListener { showPanel(2); setActiveNavItem(navActivityRanking) }
        quickLearningStats.setOnClickListener { showPanel(3); setActiveNavItem(navLearningStats) }
        quickCourseMastery.setOnClickListener { showPanel(4); setActiveNavItem(navCourseMastery) }
        quickUserManagement.setOnClickListener { showPanel(1); setActiveNavItem(navUserManagement) }
        quickDataReset.setOnClickListener { showPanel(6); setActiveNavItem(navDataReset) }
    }

    // ==================== 按钮监听器 ====================

    private fun setupButtonListeners() {
        btnDataReset.setOnClickListener { showDataResetConfirmationDialog() }
        btnAddCategory.setOnClickListener { addCategory() }
    }

    // ==================== 分类管理 ====================

    private fun addCategory() {
        val name = categoryNameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入分类名称", Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) { apiService.createCategory(name) }
            if (result.isSuccess) {
                categoryNameInput.text.clear()
                loadCategoriesForAdmin()
                Toast.makeText(this@AdminActivity, "分类添加成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@AdminActivity, "添加失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCategoriesForAdmin() {
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                try { apiService.getCategories() } catch (e: Exception) { null }
            }
            if (result?.isSuccess == true) {
                categories.clear()
                categories.addAll(result.getOrNull() ?: emptyList())
                updateCategoryStats()
                buildCategoryList()
            } else {
                categoryListContainer.removeAllViews()
                categoryListContainer.addView(createEmptyView("加载分类失败"))
            }
        }
    }

    private fun updateCategoryStats() {
        categoryStatsLayout.removeAllViews()
        val totalCount = categories.size

        // 总数
        categoryStatsLayout.addView(createStatCard("分类总数", totalCount.toString()))
    }

    private fun createStatCard(label: String, value: String): View {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(4, 0, 4, 0)
            }
            radius = 12f
            elevation = 2f
            setCardBackgroundColor(resources.getColor(R.color.tech_blue_primary))
        }
        val innerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        innerLayout.addView(TextView(this).apply {
            text = value
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        innerLayout.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(resources.getColor(R.color.tech_blue_accent))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4 }
        })
        card.addView(innerLayout)
        return card
    }

    private fun buildCategoryList() {
        categoryListContainer.removeAllViews()
        if (categories.isEmpty()) {
            categoryListContainer.addView(createEmptyView("暂无分类"))
            return
        }
        for (category in categories) {
            categoryListContainer.addView(createCategoryListItem(category))
        }
    }

    private fun createCategoryListItem(category: Category): View {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
            radius = 12f
            elevation = 2f
            setCardBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
        }
        // 分类名称
        row.addView(TextView(this).apply {
            text = category.name
            textSize = 16f
            setTextColor(resources.getColor(R.color.tech_blue_dark))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        // 课程数标签
        row.addView(TextView(this).apply {
            text = "${category.courseCount ?: 0}门课程"
            textSize = 12f
            setTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 16, 0) }
        })
        // 编辑按钮
        row.addView(Button(this).apply {
            text = "编辑"
            textSize = 12f
            setTextColor(resources.getColor(R.color.tsinghua_purple))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 4, 0) }
            setOnClickListener { showEditCategoryDialog(category) }
        })
        // 删除按钮
        row.addView(Button(this).apply {
            text = "删除"
            textSize = 12f
            setTextColor(resources.getColor(R.color.tech_red_alert))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { showDeleteCategoryDialog(category) }
        })
        card.addView(row)
        return card
    }

    private fun showEditCategoryDialog(category: Category) {
        val input = EditText(this).apply {
            setText(category.name)
            setSelection(category.name.length)
        }
        AlertDialog.Builder(this)
            .setTitle("编辑分类")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.IO) { apiService.updateCategoryName(category.id, newName) }
                        if (result.isSuccess) {
                            loadCategoriesForAdmin()
                            Toast.makeText(this@AdminActivity, "分类已更新", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@AdminActivity, "更新失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteCategoryDialog(category: Category) {
        AlertDialog.Builder(this)
            .setTitle("删除分类")
            .setMessage("确定要删除「${category.name}」吗？该分类下的课程将变为未分类状态。")
            .setPositiveButton("删除") { _, _ ->
                coroutineScope.launch {
                    val result = withContext(Dispatchers.IO) { apiService.deleteCategory(category.id) }
                    if (result.isSuccess) {
                        loadCategoriesForAdmin()
                        Toast.makeText(this@AdminActivity, "分类已删除", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@AdminActivity, "删除失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createEmptyView(message: String): View {
        return TextView(this).apply {
            text = message
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                200
            )
        }
    }

    // ==================== 数据加载：仪表盘 ====================

    private fun loadDashboardStats() {
        coroutineScope.launch {
            val onlineResult = withContext(Dispatchers.IO) {
                try { apiService.getOnlineUsers() } catch (e: Exception) { null }
            }
            val masteryResult = withContext(Dispatchers.IO) {
                try { apiService.getCourseMasteryStats() } catch (e: Exception) { null }
            }
            val learningResult = withContext(Dispatchers.IO) {
                try { apiService.getLearningStats() } catch (e: Exception) { null }
            }
            val activityResult = withContext(Dispatchers.IO) {
                try { apiService.getActivityRanking() } catch (e: Exception) { null }
            }

            if (onlineResult?.isSuccess == true) {
                val stats = onlineResult.getOrNull()?.stats
                setStatValue(statTotalUsers, stats?.totalUsers?.toString())
                setStatValue(statOnlineUsers, stats?.onlineCount?.toString())
                statOnlineRate.text = if (stats != null) String.format("%.1f%%", stats.onlineRate) else ""
            } else {
                setStatValue(statTotalUsers, null)
                setStatValue(statOnlineUsers, null)
            }

            if (masteryResult?.isSuccess == true) {
                val stats = masteryResult.getOrNull()?.stats
                setStatValue(statTotalCourses, stats?.totalCourses?.toString())
                setStatValue(statAverageCompletion,
                    if (stats != null) String.format("%.1f%%", minOf(stats.averageCompletionRate, 100.0)) else null)
            } else {
                setStatValue(statTotalCourses, null)
                setStatValue(statAverageCompletion, null)
            }

            if (learningResult?.isSuccess == true) {
                val stats = learningResult.getOrNull()?.stats
                val watchTime = stats?.totalWatchTime
                setStatValue(statTotalWatchTime,
                    if (watchTime != null) formatWatchTime(watchTime) else null)
            } else {
                setStatValue(statTotalWatchTime, null)
            }

            if (activityResult?.isSuccess == true) {
                val stats = activityResult.getOrNull()?.stats
                val high = stats?.activityDistribution?.high
                setStatValue(statActiveUsers, high?.toString())
            } else {
                setStatValue(statActiveUsers, null)
            }
        }
    }

    private fun setStatValue(textView: TextView, value: String?) {
        textView.text = value ?: "--"
        textView.alpha = if (value == null) 0.5f else 1.0f
    }

    private fun formatWatchTime(minutes: Int): String {
        return if (minutes >= 60) "${minutes / 60}h${minutes % 60}m" else "${minutes}分钟"
    }

    // ==================== 数据加载：用户管理 ====================

    private fun updateFilterTabStyles() {
        val tabs = listOf(
            filterAll to "all",
            filterAdmin to "admin",
            filterTeacher to "teacher",
            filterStudent to "student"
        )
        for ((tab, role) in tabs) {
            val isActive = currentUserFilterRole == role
            tab.setTextColor(if (isActive) android.graphics.Color.WHITE else resources.getColor(R.color.darker_gray))
            tab.setBackgroundResource(if (isActive) R.drawable.filter_tab_active else R.drawable.filter_tab_inactive)
        }
    }

    private fun loadUsersList() {
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val roleParam = if (currentUserFilterRole == "all") null else currentUserFilterRole
                    val searchParam = currentUserSearchQuery.ifBlank { null }
                    apiService.getAllUsers(role = roleParam, search = searchParam)
                } catch (e: Exception) { null }
            }

            if (result?.isSuccess == true) {
                val users = result.getOrNull() ?: emptyList()
                val totalUsers = users.size
                val teacherCount = users.count { it.role.lowercase() == "teacher" }
                val studentCount = users.count { it.role.lowercase() == "student" }

                adminStatTotalUsers.text = totalUsers.toString()
                adminStatTeachers.text = teacherCount.toString()
                adminStatStudents.text = studentCount.toString()

                userListContainer.removeAllViews()
                if (users.isEmpty()) {
                    userListContainer.addView(createEmptyView("没有匹配的用户"))
                } else {
                    for (user in users) {
                        userListContainer.addView(createUserListItem(user.username, user.role, user.createdAt))
                    }
                }
            } else {
                adminStatTotalUsers.text = "--"
                adminStatTeachers.text = "--"
                adminStatStudents.text = "--"
                userListContainer.removeAllViews()
                userListContainer.addView(createEmptyView("加载用户失败"))
            }
        }
    }

    // ==================== 数据加载：活跃度排行 ====================

    private fun loadRankingList() {
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                try { apiService.getActivityRanking(7, 20) } catch (e: Exception) { null }
            }

            if (result?.isSuccess == true) {
                val response = result.getOrNull()
                val stats = response?.stats
                val ranking = response?.ranking ?: emptyList()

                rankTotalUsers.text = stats?.totalUsers?.toString() ?: "--"
                rankAverageScore.text = stats?.let { String.format("%.1f", it.averageActivityScore) } ?: "--"
                rankMaxScore.text = stats?.let { String.format("%.1f", it.maxActivityScore) } ?: "--"

                rankingListContainer.removeAllViews()
                for (item in ranking) {
                    rankingListContainer.addView(createRankingItem(item))
                }
            } else {
                rankTotalUsers.text = "--"
                rankAverageScore.text = "--"
                rankMaxScore.text = "--"
                rankingListContainer.removeAllViews()
                rankingListContainer.addView(createEmptyView("加载排行失败"))
            }
        }
    }

    // ==================== 数据加载：学习统计 ====================

    private fun loadStudentList() {
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                try { apiService.getLearningStats(20) } catch (e: Exception) { null }
            }

            if (result?.isSuccess == true) {
                val response = result.getOrNull()
                val stats = response?.stats
                val students = response?.students ?: emptyList()

                learnTotalStudents.text = stats?.totalStudents?.toString() ?: "--"
                learnAvgProgress.text = stats?.let { String.format("%.1f%%", it.averageProgress) } ?: "--"
                learnCompletedCourses.text = stats?.totalCompletedCourses?.toString() ?: "--"

                studentListContainer.removeAllViews()
                for (student in students) {
                    studentListContainer.addView(createStudentItem(
                        student.username,
                        student.averageProgress,
                        student.totalWatchTime
                    ))
                }
            } else {
                learnTotalStudents.text = "--"
                learnAvgProgress.text = "--"
                learnCompletedCourses.text = "--"
                studentListContainer.removeAllViews()
                studentListContainer.addView(createEmptyView("加载学生数据失败"))
            }
        }
    }

    // ==================== 面板7：监控面板 ====================

    private fun loadMonitorPanel() {
        if (monitorPanelView != null) return

        // ==================== TextureView 视频背景 ====================
        val textureView = TextureView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            id = View.generateViewId()
        }

        // 视频加载进度
        val videoProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                2.dpToPx()
            ).apply { gravity = Gravity.TOP }
            progressTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#00D4FF")
            )
        }

        // 视频加载失败/缓冲中显示的静态背景
        val bgView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#0A1628"))
        }

        panelMonitor.addView(bgView, 0)
        panelMonitor.addView(textureView, 1)
        panelMonitor.addView(videoProgressBar)

        // 异步准备 MediaPlayer
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, w: Int, h: Int) {
                try {
                    monitorMediaPlayer?.release()
                    monitorMediaPlayer = android.media.MediaPlayer().apply {
                        setSurface(android.view.Surface(surface))

                        val afd = resources.openRawResourceFd(R.raw.monitor_background)
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()

                        isLooping = true
                        setVolume(0f, 0f)

                        setOnPreparedListener { mp ->
                            // 计算 center-crop 缩放
                            val vr = mp.videoWidth.toFloat() / mp.videoHeight.toFloat()
                            val ar = w.toFloat() / h.toFloat()
                            if (vr > ar) {
                                textureView.scaleX = 1f
                                textureView.scaleY = vr / ar
                            } else {
                                textureView.scaleX = ar / vr
                                textureView.scaleY = 1f
                            }

                            mp.start()
                            videoProgressBar.visibility = View.GONE
                        }

                        setOnErrorListener { _, _, _ ->
                            videoProgressBar.visibility = View.GONE
                            textureView.visibility = View.GONE
                            true
                        }

                        prepareAsync()
                    }
                } catch (e: Exception) {
                    videoProgressBar.visibility = View.GONE
                    textureView.visibility = View.GONE
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                monitorMediaPlayer?.release()
                monitorMediaPlayer = null
                return true
            }
            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
        }

        monitorVideoProgressBar = videoProgressBar

        // ==================== 原生内容层 ====================
        val contentView = layoutInflater.inflate(R.layout.panel_admin_monitor, panelMonitor, false)
        panelMonitor.addView(contentView)
        monitorPanelView = contentView

        // 刷新按钮
        contentView.findViewById<TextView>(R.id.monitorRefreshBtn).setOnClickListener {
            refreshMonitorPanelData()
        }

        // 加载数据
        refreshMonitorPanelData()
    }

    private fun refreshMonitorPanelData() {
        val contentView = monitorPanelView ?: return

        val loadingText = contentView.findViewById<TextView>(R.id.monitorLoadingText)
        val tvTodayStudy = contentView.findViewById<TextView>(R.id.dashTodayStudy)
        val tvTodayNew = contentView.findViewById<TextView>(R.id.dashTodayNewUsers)
        val tvOnline = contentView.findViewById<TextView>(R.id.dashOnlineCount)
        val tvAvgTime = contentView.findViewById<TextView>(R.id.dashAvgStudyTime)
        val rankActivity = contentView.findViewById<LinearLayout>(R.id.dashActivityRanking)
        val rankCourses = contentView.findViewById<LinearLayout>(R.id.dashCourseRanking)
        val trendContainer = contentView.findViewById<LinearLayout>(R.id.dashLearningTrend)
        val masteryContainer = contentView.findViewById<LinearLayout>(R.id.dashMasteryAnalysis)
        val completionContainer = contentView.findViewById<LinearLayout>(R.id.dashCompletionRate)
        val hourlyContainer = contentView.findViewById<LinearLayout>(R.id.dashHourlyDist)
        val refreshTime = contentView.findViewById<TextView>(R.id.monitorRefreshTime)

        loadingText?.visibility = View.VISIBLE

        coroutineScope.launch {
            val dashResult = withContext(Dispatchers.IO) {
                try { apiService.getDashboardOverview() } catch (e: Exception) { null }
            }

            loadingText?.visibility = View.GONE

            if (dashResult?.isSuccess == true) {
                val data = dashResult.getOrNull()!!

                // — 顶部4个统计数字 —
                val stats = data.stats
                tvTodayStudy.text = formatNumber(stats.todayStudyCount)
                tvTodayNew.text = formatNumber(stats.todayNewUsers)
                tvOnline.text = formatNumber(stats.onlineCount)
                tvAvgTime.text = formatStudyTime(stats.averageStudyTime.toLong())

                // — 活跃排行榜 —
                rankActivity.removeAllViews()
                for (user in data.activeUsers.take(5)) {
                    rankActivity.addView(createDashRankItem(
                        user.rank, user.username,
                        "${user.loginCount}次登录 · ${formatStudyTime(user.totalWatchTime.toLong())}"
                    ))
                }
                if (data.activeUsers.isEmpty()) {
                    rankActivity.addView(createEmptyView("暂无活跃用户"))
                }

                // — 课程热度排行 —
                rankCourses.removeAllViews()
                for (course in data.courseRanking) {
                    rankCourses.addView(createDashRankItem(
                        course.rank, course.courseName,
                        "${course.enrollCount}人选课"
                    ))
                }
                if (data.courseRanking.isEmpty()) {
                    rankCourses.addView(createEmptyView("暂无课程数据"))
                }

                // — 学习趋势柱状图 —
                trendContainer.removeAllViews()
                val maxCount = data.learningTrend.maxOfOrNull { it.studyCount } ?: 1
                for (point in data.learningTrend) {
                    val barLayout = LinearLayout(this@AdminActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                    }

                    val ratio = if (maxCount > 0) point.studyCount.toFloat() / maxCount else 0f
                    val barWeight = ratio.coerceAtLeast(0.04f)

                    // 人数数字
                    val countLabel = TextView(this@AdminActivity).apply {
                        text = "${point.studyCount}"
                        textSize = 9f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(android.graphics.Color.parseColor("#4DA3FF"))
                        gravity = Gravity.CENTER_HORIZONTAL
                    }

                    val bar = View(this@AdminActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            14.dpToPx(),
                            0.dpToPx(),
                            barWeight
                        )
                        setBackgroundColor(android.graphics.Color.parseColor("#4DA3FF"))
                        alpha = 0.8f
                    }

                    val dateLabel = TextView(this@AdminActivity).apply {
                        text = point.label
                        textSize = 8f
                        setTextColor(android.graphics.Color.parseColor("#7A9AB5"))
                        gravity = Gravity.CENTER_HORIZONTAL
                    }

                    barLayout.addView(countLabel)
                    barLayout.addView(bar)
                    barLayout.addView(dateLabel)
                    trendContainer.addView(barLayout)
                }

                // — 课程掌握度分析 —
                masteryContainer.removeAllViews()
                val mastery = data.masteryOverview
                masteryContainer.addView(createDashStatItem("课程总数", formatNumber(mastery.totalCourses)))
                masteryContainer.addView(createDashStatItem("平均进度", String.format("%.1f%%", mastery.averageProgress)))
                masteryContainer.addView(createDashStatItem("完成率", String.format("%.1f%%", mastery.averageCompletionRate)))

                // — 学生完成率 —
                completionContainer.removeAllViews()
                completionContainer.addView(createDashStatItem("已完成", formatNumber(stats.completedStudents)))
                completionContainer.addView(createDashStatItem("总学习人数", formatNumber(stats.totalStudentsWithRecords)))
                completionContainer.addView(createDashStatItem("完成率", String.format("%.1f%%", stats.studentCompletionRate)))

                // — 学习时段热力图 —
                hourlyContainer.removeAllViews()
                val maxHourly = data.hourlyDistribution.maxOfOrNull { it.studentCount } ?: 1
                val hourlyGrid = LinearLayout(this@AdminActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                    orientation = LinearLayout.VERTICAL
                }
                // 分4行显示，每行6小时
                for (row in 0 until 4) {
                    val rowLayout = LinearLayout(this@AdminActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            0.dpToPx(),
                            1f
                        )
                        orientation = LinearLayout.HORIZONTAL
                    }
                    for (col in 0 until 6) {
                        val idx = row * 6 + col
                        if (idx < data.hourlyDistribution.size) {
                            val point = data.hourlyDistribution[idx]
                            val ratio = if (maxHourly > 0) point.studentCount.toFloat() / maxHourly else 0f
                            val alphaVal = (0.15f + ratio * 0.7f).coerceIn(0.1f, 0.85f)
                            val alphaHex = String.format("%02X", (alphaVal * 255).toInt())

                            val cell = TextView(this@AdminActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    0.dpToPx(),
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    1f
                                ).apply { setMargins(1, 1, 1, 1) }
                                text = "${point.hour}"
                                textSize = 7f
                                gravity = Gravity.CENTER
                                setTextColor(android.graphics.Color.parseColor("#CCD6E0"))
                                setBackgroundColor(
                                    android.graphics.Color.parseColor("#$alphaHex" + "4DA3FF")
                                )
                            }
                            rowLayout.addView(cell)
                        }
                    }
                    hourlyGrid.addView(rowLayout)
                }
                hourlyContainer.addView(hourlyGrid)

            } else {
                // 加载失败
                tvTodayStudy.text = "--"
                tvTodayNew.text = "--"
                tvOnline.text = "--"
                tvAvgTime.text = "--"

                rankActivity.removeAllViews()
                rankActivity.addView(createEmptyView("加载失败"))

                rankCourses.removeAllViews()
                rankCourses.addView(createEmptyView("加载失败"))

                trendContainer.removeAllViews()
                masteryContainer.removeAllViews()
                completionContainer.removeAllViews()
                hourlyContainer.removeAllViews()
            }

            // — 刷新时间 —
            val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            refreshTime.text = "最后刷新: $now"
        }
    }

    private fun formatNumber(n: Int): String {
        return when {
            n >= 10000 -> String.format("%.1fw", n / 10000.0)
            n >= 1000 -> String.format("%.1fk", n / 1000.0)
            else -> n.toString()
        }
    }

    private fun formatStudyTime(minutes: Long): String {
        return when {
            minutes >= 60 -> "${minutes / 60}h${minutes % 60}m"
            else -> "${minutes}min"
        }
    }

    private fun createDashRankItem(rank: Int, name: String, subtitle: String): View {
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 4) }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6, 4, 6, 4)
        }

        val rankText = TextView(this).apply {
            text = "$rank"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (rank <= 3) Color.parseColor("#F9C96A") else Color.parseColor("#7A9AB5"))
            minWidth = 20.dpToPx()
        }

        val infoLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
            setPadding(6, 0, 0, 0)
        }

        val nameLabel = TextView(this).apply {
            text = name
            textSize = 11f
            setTextColor(Color.parseColor("#CCD6E0"))
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val subLabel = TextView(this).apply {
            text = subtitle
            textSize = 9f
            setTextColor(Color.parseColor("#7A9AB5"))
            alpha = 0.7f
        }

        infoLayout.addView(nameLabel)
        infoLayout.addView(subLabel)

        row.addView(rankText)
        row.addView(infoLayout)
        return row
    }

    private fun createDashStatItem(label: String, value: String): View {
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 6) }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 2, 4, 2)
        }

        val labelText = TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.parseColor("#8AAAC0"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valueText = TextView(this).apply {
            text = value
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#CCD6E0"))
        }

        row.addView(labelText)
        row.addView(valueText)
        return row
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    private fun loadCourseList() {
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                try { apiService.getCourseMasteryStats(20) } catch (e: Exception) { null }
            }

            if (result?.isSuccess == true) {
                val response = result.getOrNull()
                val stats = response?.stats
                val courses = response?.courses ?: emptyList()

                masteryTotalCourses.text = stats?.totalCourses?.toString() ?: "--"
                masteryAvgProgress.text = stats?.let { String.format("%.1f%%", it.averageProgress) } ?: "--"
                masteryCompletionRate.text = stats?.let { String.format("%.1f%%", minOf(it.averageCompletionRate, 100.0)) } ?: "--"

                courseListContainer.removeAllViews()
                for (course in courses) {
                    courseListContainer.addView(createCourseItem(course))
                }
            } else {
                masteryTotalCourses.text = "--"
                masteryAvgProgress.text = "--"
                masteryCompletionRate.text = "--"
                courseListContainer.removeAllViews()
                courseListContainer.addView(createEmptyView("加载课程数据失败"))
            }
        }
    }

    // ==================== 列表项视图创建 ====================

    private fun createUserListItem(username: String, role: String, createdAt: String?): View {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
            radius = 12f
            cardElevation = 2f
            setCardBackgroundColor(0xFFF5F5F5.toInt())
        }

        val root = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
        }

        val nameText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = username
            textSize = 15f
            setTextColor(resources.getColor(R.color.darker_gray))
            setTypeface(null, Typeface.BOLD)
        }

        val roleBadge = TextView(this).apply {
            val roleColor = when (role.lowercase()) {
                "admin" -> R.color.tech_red_alert
                "teacher" -> R.color.tech_cyan_info
                else -> R.color.tsinghua_purple
            }
            text = when (role.lowercase()) {
                "admin" -> "管理员"
                "teacher" -> "教师"
                else -> "学生"
            }
            textSize = 12f
            setTextColor(resources.getColor(roleColor))
            setPadding(8, 4, 8, 4)
            setBackgroundResource(R.drawable.edit_text_background)
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                0x1A000000.toInt()
            )
        }

        root.addView(nameText)
        root.addView(roleBadge)
        card.addView(root)
        return card
    }

    private fun createRankingItem(item: ApiService.ActivityRankingItem): View {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
            radius = 12f
            cardElevation = 2f
            setCardBackgroundColor(0xFFF5F5F5.toInt())
        }

        val root = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
        }

        // 排名
        val rankText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = "#${item.rank}"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(when (item.rank) {
                1 -> resources.getColor(R.color.tech_red_alert)
                2, 3 -> resources.getColor(R.color.tech_cyan_info)
                else -> resources.getColor(R.color.darker_gray)
            })
        }

        // 用户名 + 分数构成
        val infoLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
        }

        val nameText = TextView(this).apply {
            text = item.username
            textSize = 15f
            setTextColor(resources.getColor(R.color.darker_gray))
        }

        // 分数构成小字：登录×1 + 时长×0.5 + 课程×20 + 答题×1
        val details = item.details
        val breakdownText = TextView(this).apply {
            text = "登录${details.loginCount}次 + 时长${details.totalWatchTime}min + 完成${details.completedCourses}课 + 答题${details.totalQuizScore}分"
            textSize = 10f
            setTextColor(resources.getColor(R.color.darker_gray))
            alpha = 0.6f
        }

        infoLayout.addView(nameText)
        infoLayout.addView(breakdownText)

        val scoreText = TextView(this).apply {
            text = String.format("%.1f分", item.activityScore)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(resources.getColor(R.color.tsinghua_purple))
        }

        root.addView(rankText)
        root.addView(infoLayout)
        root.addView(scoreText)
        card.addView(root)
        return card
    }

    private fun createStudentItem(username: String, progress: Double, watchTime: Int): View {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
            radius = 12f
            cardElevation = 2f
            setCardBackgroundColor(0xFFF5F5F5.toInt())
        }

        val root = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
        }

        val nameText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = username
            textSize = 15f
            setTextColor(resources.getColor(R.color.darker_gray))
        }

        val progressText = TextView(this).apply {
            text = String.format("%.1f%%", progress)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(resources.getColor(R.color.tech_green_data))
        }

        val timeText = TextView(this).apply {
            text = formatWatchTime(watchTime)
            textSize = 12f
            setTextColor(resources.getColor(R.color.darker_gray))
            alpha = 0.7f
            setPadding(12, 0, 0, 0)
        }

        root.addView(nameText)
        root.addView(progressText)
        root.addView(timeText)
        card.addView(root)
        return card
    }

    private fun createCourseItem(item: ApiService.CourseMasteryItem): View {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
            radius = 12f
            cardElevation = 2f
            setCardBackgroundColor(0xFFF5F5F5.toInt())
        }

        val root = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
        }

        val infoLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
        }

        val nameText = TextView(this).apply {
            text = item.courseName
            textSize = 15f
            setTextColor(resources.getColor(R.color.darker_gray))
            setTypeface(null, Typeface.BOLD)
        }

        val teacherText = TextView(this).apply {
            text = "教师: ${item.teacherName ?: ""}"
            textSize = 12f
            setTextColor(resources.getColor(R.color.darker_gray))
            alpha = 0.6f
        }

        val mastery = item.compositeMastery ?: item.averageProgress
        val subText = TextView(this).apply {
            text = "视频${String.format("%.0f", item.averageProgress)}% + 测试${String.format("%.0f", item.averageQuizAccuracy)}%"
            textSize = 11f
            setTextColor(resources.getColor(R.color.darker_gray))
            alpha = 0.5f
        }

        infoLayout.addView(nameText)
        infoLayout.addView(teacherText)
        infoLayout.addView(subText)

        val masteryText = TextView(this).apply {
            text = String.format("%.1f%%", mastery)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(resources.getColor(R.color.tsinghua_purple))
        }

        root.addView(infoLayout)
        root.addView(masteryText)
        card.addView(root)
        return card
    }

    // ==================== 数据重置 ====================

    private fun showDataResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("确认数据重置")
            .setMessage("此操作将清除所有本地数据，恢复到初始状态。该操作不可撤销，确定要继续吗？")
            .setPositiveButton("确认重置") { _, _ ->
                performDataReset()
            }
            .setNegativeButton("取消") { d, _ -> d.dismiss() }
            .setCancelable(true)
            .show()
    }

    private fun performDataReset() {
        Toast.makeText(this, "数据重置功能需要后端API支持", Toast.LENGTH_SHORT).show()
    }

    // ==================== 返回键处理 ====================

    override fun onBackPressed() {
        super.onBackPressed()
    }

    // ==================== 退出登录 ====================

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("是") { _, _ ->
                getSharedPreferences("login_prefs", MODE_PRIVATE).edit {
                    putBoolean("is_logged_in", false)
                    remove("username")
                    remove("login_time")
                }
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("否") { d, _ -> d.dismiss() }
            .setCancelable(true).show()
    }
}
