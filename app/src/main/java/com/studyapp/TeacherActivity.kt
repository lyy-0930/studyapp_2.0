package com.studyapp

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.util.ImageLoaderUtil
import com.studyapp.VideoPlayerActivity
import com.studyapp.adapter.TeacherCourseAdapter
import com.studyapp.manager.ApiService
import com.studyapp.model.ApiCourse
import com.studyapp.model.CourseMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class TeacherActivity : AppCompatActivity() {

    // ==================== 侧边栏 ====================
    private lateinit var sidebarUsername: TextView
    private lateinit var userAvatarText: TextView
    private lateinit var userAvatarImage: ImageView
    private lateinit var avatarFrame: FrameLayout
    private var currentAvatarUrl: String? = null
    private lateinit var navHome: LinearLayout
    private lateinit var navUploadCourse: LinearLayout
    private lateinit var navMyCourses: LinearLayout
    private lateinit var navStatistics: LinearLayout
    private lateinit var navLogout: LinearLayout

    // ==================== 内容容器 ====================
    private lateinit var homeContent: NestedScrollView
    private lateinit var uploadFragmentContainer: FrameLayout
    private lateinit var myCoursesContent: NestedScrollView
    private lateinit var statsFragmentContainer: FrameLayout
    private lateinit var questionManageContainer: FrameLayout

    // ==================== 首页 ====================
    private lateinit var welcomeTitle: TextView
    private lateinit var welcomeSubtitle: TextView
    private lateinit var quickUpload: LinearLayout
    private lateinit var quickStats: LinearLayout

    // ==================== 我的课程 ====================
    private lateinit var myCoursesRecyclerView: RecyclerView
    private lateinit var myCourseCount: TextView
    private lateinit var emptyMyCoursesLayout: LinearLayout
    private lateinit var teacherCourseAdapter: TeacherCourseAdapter

    // ==================== 数据 ====================
    private var username: String = "教师"
    private var role: String = "teacher"

    private var uploadFragment: Fragment? = null
    private var statsFragment: Fragment? = null
    private lateinit var navMessage: LinearLayout
    private lateinit var chatFragmentContainer: FrameLayout
    private var chatFragment: Fragment? = null
    private var userId: Int = 0
    private lateinit var apiService: ApiService
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // 用于材料上传的变量
    private var selectedMaterialCourseId: Int = 0
    private var selectedMaterialCourseName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher)

        // 获取用户信息
        username = intent.getStringExtra("username") ?: "教师"
        role = intent.getStringExtra("role") ?: "teacher"

        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        userId = sharedPref.getInt("user_id", 0)

        // 初始化服务
        apiService = ApiService.getInstance(this)

        // 初始化视图
        initViews()
        setupAvatar()
        setupSidebarNavigation()
        setupClickListeners()

        // 默认显示首页
        showPanel(0)
    }

    private fun initViews() {
        // 侧边栏
        sidebarUsername = findViewById(R.id.sidebarUsername)
        userAvatarText = findViewById(R.id.userAvatarText)
        userAvatarImage = findViewById(R.id.userAvatarImage)
        avatarFrame = findViewById(R.id.avatarFrame)
        navHome = findViewById(R.id.navHome)
        navUploadCourse = findViewById(R.id.navUploadCourse)
        navMyCourses = findViewById(R.id.navMyCourses)
        navStatistics = findViewById(R.id.navStatistics)
        navLogout = findViewById(R.id.navLogout)

        // 内容容器
        homeContent = findViewById(R.id.homeContent)
        uploadFragmentContainer = findViewById(R.id.uploadFragmentContainer)
        myCoursesContent = findViewById(R.id.myCoursesContent)
        statsFragmentContainer = findViewById(R.id.statsFragmentContainer)
        navMessage = findViewById(R.id.navMessage)
        chatFragmentContainer = findViewById(R.id.chatFragmentContainer)
        questionManageContainer = findViewById(R.id.questionManageContainer)

        // 首页
        welcomeTitle = findViewById(R.id.welcomeTitle)
        welcomeSubtitle = findViewById(R.id.welcomeSubtitle)
        quickUpload = findViewById(R.id.quickUpload)
        quickStats = findViewById(R.id.quickStats)

        // 我的课程
        myCoursesRecyclerView = findViewById(R.id.myCoursesRecyclerView)
        myCourseCount = findViewById(R.id.myCourseCount)
        emptyMyCoursesLayout = findViewById(R.id.emptyMyCoursesLayout)

        // 适配器
        teacherCourseAdapter = TeacherCourseAdapter()
        myCoursesRecyclerView.layoutManager = LinearLayoutManager(this)
        myCoursesRecyclerView.adapter = teacherCourseAdapter
        teacherCourseAdapter.setOnCourseActionListener(object : TeacherCourseAdapter.OnCourseActionListener {
            override fun onPreview(course: ApiCourse, position: Int) = showCourseDetailDialog(course)
            override fun onUploadMaterial(course: ApiCourse, position: Int) = pickMaterialFile(course)
            override fun onDelete(course: ApiCourse, position: Int) = confirmDeleteCourse(course)
        })
    }

    private fun setupAvatar() {
        sidebarUsername.text = username
        val letter = if (username.isNotEmpty()) username.first().toString() else "师"
        userAvatarText.text = letter
        welcomeTitle.text = "欢迎回来，$username"
        welcomeSubtitle.text = "教师功能面板"

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
                compressed.compress(Bitmap.CompressFormat.JPEG, 85, out)
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

                        getSharedPreferences("login_prefs", MODE_PRIVATE).edit {
                            putString("avatar_url", avatarUrl)
                        }

                        val fullUrl = "${ApiService.BASE_URL}${avatarUrl}"
                        userAvatarImage.visibility = View.VISIBLE
                        userAvatarText.visibility = View.GONE
                        ImageLoaderUtil.load(userAvatarImage, fullUrl, crossfade = true, circleCrop = true)
                        Toast.makeText(this@TeacherActivity, "头像上传成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@TeacherActivity, "头像上传失败", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@TeacherActivity, "上传出错: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "图片处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compressBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        if (ratio >= 1f) return bitmap
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // ==================== 面板切换 ====================

    fun showPanel(panelIndex: Int) {
        // 0=首页, 1=课程上传, 2=数据统计, 3=消息, 4=我的课程, 5=题目管理
        homeContent.visibility = if (panelIndex == 0) View.VISIBLE else View.GONE
        uploadFragmentContainer.visibility = if (panelIndex == 1) View.VISIBLE else View.GONE
        myCoursesContent.visibility = if (panelIndex == 4) View.VISIBLE else View.GONE
        statsFragmentContainer.visibility = if (panelIndex == 2) View.VISIBLE else View.GONE
        chatFragmentContainer.visibility = if (panelIndex == 3) View.VISIBLE else View.GONE
        questionManageContainer.visibility = if (panelIndex == 5) View.VISIBLE else View.GONE

        val ft = supportFragmentManager.beginTransaction()

        when (panelIndex) {
            4 -> {
                // 加载我的课程
                loadTeacherCourses()
                // 移除所有Fragment
                if (uploadFragment != null && uploadFragment!!.isAdded) {
                    ft.remove(uploadFragment!!)
                    uploadFragment = null
                }
                if (statsFragment != null && statsFragment!!.isAdded) {
                    ft.remove(statsFragment!!)
                    statsFragment = null
                }
                if (chatFragment != null && chatFragment!!.isAdded) {
                    ft.remove(chatFragment!!)
                    chatFragment = null
                }
            }
            1 -> {
                if (uploadFragment == null) {
                    uploadFragment = UploadCourseFragment()
                }
                if (!uploadFragment!!.isAdded) {
                    ft.replace(R.id.uploadFragmentContainer, uploadFragment!!)
                }
                // 移除统计Fragment
                if (statsFragment != null && statsFragment!!.isAdded) {
                    ft.remove(statsFragment!!)
                    statsFragment = null
                }
                if (chatFragment != null && chatFragment!!.isAdded) {
                    ft.remove(chatFragment!!)
                    chatFragment = null
                }
            }
            2 -> {
                val args = Bundle().apply {
                    putString("username", username)
                    putString("role", role)
                }
                if (statsFragment == null) {
                    statsFragment = StatisticsFragment().apply { arguments = args }
                } else {
                    // 更新参数供 onResume 使用
                    statsFragment?.arguments = args
                }
                if (!statsFragment!!.isAdded) {
                    ft.replace(R.id.statsFragmentContainer, statsFragment!!)
                }
                // 移除上传Fragment
                if (uploadFragment != null && uploadFragment!!.isAdded) {
                    ft.remove(uploadFragment!!)
                    uploadFragment = null
                }
                if (chatFragment != null && chatFragment!!.isAdded) {
                    ft.remove(chatFragment!!)
                }
            }
            3 -> {
                if (chatFragment == null) {
                    chatFragment = ChatFragment().apply {
                        arguments = Bundle().apply {
                            putInt("user_id", userId)
                            putString("username", username)
                        }
                    }
                }
                if (!chatFragment!!.isAdded) {
                    ft.replace(R.id.chatFragmentContainer, chatFragment!!)
                }
                // 移除其他Fragment
                if (uploadFragment != null && uploadFragment!!.isAdded) {
                    ft.remove(uploadFragment!!)
                    uploadFragment = null
                }
                if (statsFragment != null && statsFragment!!.isAdded) {
                    ft.remove(statsFragment!!)
                    statsFragment = null
                }
            }
            0 -> {
                // 移除所有Fragment
                if (uploadFragment != null && uploadFragment!!.isAdded) {
                    ft.remove(uploadFragment!!)
                    uploadFragment = null
                }
                if (statsFragment != null && statsFragment!!.isAdded) {
                    ft.remove(statsFragment!!)
                    statsFragment = null
                }
                if (chatFragment != null && chatFragment!!.isAdded) {
                    ft.remove(chatFragment!!)
                    chatFragment = null
                }
            }
            5 -> {
                // 题目管理Fragment由showQuestionManagement管理
                if (uploadFragment != null && uploadFragment!!.isAdded) {
                    ft.remove(uploadFragment!!)
                    uploadFragment = null
                }
                if (statsFragment != null && statsFragment!!.isAdded) {
                    ft.remove(statsFragment!!)
                    statsFragment = null
                }
                if (chatFragment != null && chatFragment!!.isAdded) {
                    ft.remove(chatFragment!!)
                    chatFragment = null
                }
            }
        }
        ft.commitAllowingStateLoss()
    }

    // ==================== 题目管理 ====================

    private fun showQuestionManagement(courseId: Int, courseName: String) {
        showPanel(5)
        val fragment = QuestionManageFragment.newInstance(courseId, courseName)
        supportFragmentManager.beginTransaction()
            .replace(R.id.questionManageContainer, fragment)
            .addToBackStack("questionManage")
            .commit()
    }

    // ==================== 侧边栏导航 ====================

    private fun setupSidebarNavigation() {
        setActiveNavItem(navHome)

        navHome.setOnClickListener {
            showPanel(0)
            setActiveNavItem(navHome)
        }

        navUploadCourse.setOnClickListener {
            showPanel(1)
            setActiveNavItem(navUploadCourse)
        }

        navMyCourses.setOnClickListener {
            showPanel(4)
            setActiveNavItem(navMyCourses)
        }

        navStatistics.setOnClickListener {
            showPanel(2)
            setActiveNavItem(navStatistics)
        }

        navMessage.setOnClickListener {
            showPanel(3)
            setActiveNavItem(navMessage)
        }

        navLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun setActiveNavItem(selectedItem: LinearLayout) {
        val navItems = listOf(navHome, navUploadCourse, navMyCourses, navStatistics, navMessage)
        for (item in navItems) {
            item.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            val textView = item.getChildAt(1) as? TextView
            textView?.setTextColor(resources.getColor(R.color.tsinghua_purple_dark, theme))
        }
        selectedItem.setBackgroundColor(0x1A7D2181.toInt())
        val textView = selectedItem.getChildAt(1) as? TextView
        textView?.setTextColor(resources.getColor(R.color.tsinghua_purple, theme))
    }

    // ==================== 我的课程 - 数据加载 ====================

    private fun loadTeacherCourses() {
        if (userId == 0) return
        myCoursesContent.visibility = View.VISIBLE
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    apiService.getTeacherCourses(userId)
                }
                if (result.isSuccess) {
                    val courses = result.getOrThrow()
                    myCourseCount.text = "共 ${courses.size} 门课程"
                    if (courses.isEmpty()) {
                        emptyMyCoursesLayout.visibility = View.VISIBLE
                        myCoursesRecyclerView.visibility = View.GONE
                    } else {
                        emptyMyCoursesLayout.visibility = View.GONE
                        myCoursesRecyclerView.visibility = View.VISIBLE
                        teacherCourseAdapter.updateData(courses)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    // ==================== 我的课程 - 预览详情对话框 ====================

    private fun showCourseDetailDialog(course: ApiCourse) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_course_detail, null)
        val courseNameText: TextView = dialogView.findViewById(R.id.dialogCourseName)
        val courseTeacherText: TextView = dialogView.findViewById(R.id.dialogCourseTeacher)
        val courseCreditText: TextView = dialogView.findViewById(R.id.dialogCourseCredit)
        val courseDescText: TextView = dialogView.findViewById(R.id.dialogCourseDesc)
        val courseDateText: TextView = dialogView.findViewById(R.id.dialogCourseDate)
        val materialListLayout: LinearLayout = dialogView.findViewById(R.id.materialListLayout)
        val emptyMaterialText: TextView = dialogView.findViewById(R.id.emptyMaterialText)
        val uploadNewBtn: Button = dialogView.findViewById(R.id.uploadNewMaterialBtn)
        val playVideoBtn: Button = dialogView.findViewById(R.id.dialogPlayVideoBtn)
        val manageQuestionsBtn: Button = dialogView.findViewById(R.id.manageQuestionsBtn)

        courseNameText.text = course.name
        courseTeacherText.text = "授课教师：${course.teacherName ?: course.teacher}"
        courseCreditText.text = "学分：${course.credit}"
        courseDescText.text = course.description
        courseDateText.text = "创建时间：${course.createdAt?.take(10) ?: "未知"}"

        // 播放视频
        playVideoBtn.setOnClickListener {
            if (course.videoUrl.isNullOrEmpty()) {
                Toast.makeText(this, "该课程暂无视频内容", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, VideoPlayerActivity::class.java)
                intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_URL, course.videoUrl)
                intent.putExtra(VideoPlayerActivity.EXTRA_COURSE_NAME, course.name)
                intent.putExtra("course_id", course.id)
                startActivity(intent)
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("关闭") { d, _ -> d.dismiss() }
            .show()

        // 管理题目（在dialog创建后才能引用它）
        manageQuestionsBtn.setOnClickListener {
            dialog.dismiss()
            showQuestionManagement(course.id, course.name)
        }

        // 加载资料列表
        loadMaterialsForDialog(course.id, materialListLayout, emptyMaterialText)

        // 上传新资料
        uploadNewBtn.setOnClickListener {
            dialog.dismiss()
            pickMaterialFile(course)
        }
    }

    private fun loadMaterialsForDialog(
        courseId: Int,
        materialListLayout: LinearLayout,
        emptyMaterialText: TextView
    ) {
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    apiService.getCourseMaterials(courseId)
                }
                if (result.isSuccess) {
                    val materials = result.getOrThrow()
                    if (materials.isEmpty()) {
                        emptyMaterialText.visibility = View.VISIBLE
                    } else {
                        emptyMaterialText.visibility = View.GONE
                        materialListLayout.removeAllViews()
                        for (mat in materials) {
                            addMaterialItemView(materialListLayout, mat, courseId)
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun addMaterialItemView(
        container: LinearLayout,
        material: CourseMaterial,
        courseId: Int
    ) {
        val itemView = LayoutInflater.from(this).inflate(R.layout.item_material, container, false)
        val iconText: TextView = itemView.findViewById(R.id.materialIconText)
        val nameText: TextView = itemView.findViewById(R.id.materialNameText)
        val sizeText: TextView = itemView.findViewById(R.id.materialSizeText)
        val deleteBtn: Button = itemView.findViewById(R.id.deleteMaterialBtn)

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

        deleteBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("删除资料")
                .setMessage("确定要删除「${material.fileName}」吗？")
                .setPositiveButton("删除") { _, _ ->
                    coroutineScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                apiService.deleteCourseMaterial(material.id, courseId)
                            }
                            container.removeView(itemView)
                            if (container.childCount == 0) {
                                container.visibility = View.GONE
                                (container.parent as? View)?.findViewById<TextView>(R.id.emptyMaterialText)?.visibility = View.VISIBLE
                            }
                            Toast.makeText(this@TeacherActivity, "删除成功", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            Toast.makeText(this@TeacherActivity, "删除失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("取消") { d, _ -> d.dismiss() }
                .show()
        }

        // 点击资料项下载到本地
        itemView.setOnClickListener {
            openMaterialFile(material)
        }

        container.addView(itemView)
    }

    private fun openMaterialFile(material: CourseMaterial) {
        val url = "${ApiService.BASE_URL}${material.fileUrl}"
        val fileType = material.fileType?.lowercase() ?: ""

        // 所有文件统一先下载到缓存，再用 FileProvider 打开
        val fileName = material.fileName.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val cacheFile = java.io.File(cacheDir, fileName)

        if (cacheFile.exists() && cacheFile.length() > 0) {
            openLocalFile(cacheFile, fileType, fileName, url)
            return
        }

        Toast.makeText(this, "正在下载「${material.fileName}」...", Toast.LENGTH_SHORT).show()
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
                openLocalFile(cacheFile, fileType, material.fileName, url)
            } catch (e: Exception) {
                Toast.makeText(this@TeacherActivity, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openLocalFile(file: java.io.File, fileType: String, displayName: String, fileUrl: String = "") {
        val mimeType = when (fileType) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
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
                this@TeacherActivity,
                "${packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            try {
                val downloadManager = getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
                val request = android.app.DownloadManager.Request(Uri.parse(fileUrl))
                    .setTitle(displayName)
                    .setDescription("下载完成即可打开")
                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, file.name)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                downloadManager.enqueue(request)
                Toast.makeText(this@TeacherActivity, "本机没有能打开此文件的应用，已保存到「下载」文件夹", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(this@TeacherActivity, "无法打开此文件类型，请在手机安装 WPS 等应用", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this@TeacherActivity, "打开文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 我的课程 - 上传资料 ====================

    private val materialFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadSelectedMaterial(uri)
            }
        }
    }

    private fun pickMaterialFile(course: ApiCourse) {
        selectedMaterialCourseId = course.id
        selectedMaterialCourseName = course.name
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            materialFilePickerLauncher.launch(Intent.createChooser(intent, "选择学习资料"))
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_LONG).show()
        }
    }

    private fun uploadSelectedMaterial(uri: Uri) {
        if (selectedMaterialCourseId == 0) return
        val fileName = getMaterialFileName(uri)
        Toast.makeText(this, "正在上传「$fileName」...", Toast.LENGTH_SHORT).show()

        coroutineScope.launch {
            try {
                // 复制到临时文件
                val tempFile = File(cacheDir, "material_${System.currentTimeMillis()}_${fileName}")
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@TeacherActivity, "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    apiService.uploadCourseMaterial(selectedMaterialCourseId, tempFile, fileName)
                }
                tempFile.delete()

                if (result.isSuccess) {
                    Toast.makeText(this@TeacherActivity, "资料上传成功", Toast.LENGTH_SHORT).show()
                    // 刷新列表
                    loadTeacherCourses()
                } else {
                    Toast.makeText(this@TeacherActivity, "上传失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TeacherActivity, "上传出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getMaterialFileName(uri: Uri): String {
        return try {
            var name: String? = null
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) name = cursor.getString(idx)
                }
            }
            name ?: uri.path?.split("/")?.lastOrNull() ?: "file"
        } catch (_: Exception) { "file" }
    }

    // ==================== 我的课程 - 删除课程 ====================

    private fun confirmDeleteCourse(course: ApiCourse) {
        AlertDialog.Builder(this)
            .setTitle("删除课程")
            .setMessage("确定要删除「${course.name}」吗？\n\n删除后，学生的选课记录、学习资料和题目也将一并删除。")
            .setPositiveButton("删除") { _, _ ->
                doDeleteCourse(course)
            }
            .setNegativeButton("取消") { d, _ -> d.dismiss() }
            .show()
    }

    private fun doDeleteCourse(course: ApiCourse) {
        coroutineScope.launch {
            try {
                Toast.makeText(this@TeacherActivity, "正在删除...", Toast.LENGTH_SHORT).show()
                val result = withContext(Dispatchers.IO) {
                    apiService.deleteCourse(course.id)
                }
                if (result.isSuccess) {
                    Toast.makeText(this@TeacherActivity, "课程已删除", Toast.LENGTH_SHORT).show()
                    loadTeacherCourses()
                } else {
                    Toast.makeText(this@TeacherActivity, "删除失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TeacherActivity, "删除出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== 工具方法 ====================

    private fun formatMaterialSize(size: Long): String = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
    }

    // ==================== 点击事件 ====================

    private fun setupClickListeners() {
        quickUpload.setOnClickListener {
            showPanel(1)
            setActiveNavItem(navUploadCourse)
        }

        quickStats.setOnClickListener {
            showPanel(2)
            setActiveNavItem(navStatistics)
        }
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
            .setCancelable(true)
            .show()
    }
}
