package com.studyapp

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.ImageButton
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.util.Log
import com.studyapp.model.CourseUploadInfo
import com.studyapp.model.VideoItem
import com.studyapp.model.Course
import com.studyapp.manager.OSSUploadManager
import com.studyapp.manager.OSSConfig
import com.studyapp.manager.VideoStorageManager
import com.studyapp.manager.CourseManager
import com.studyapp.manager.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoUploadActivity : AppCompatActivity() {

    private lateinit var selectVideoButton: Button
    private lateinit var uploadButton: Button
    private lateinit var networkTestButton: Button
    private lateinit var videoFileNameTextView: TextView
    private lateinit var videoFileSizeTextView: TextView
    private lateinit var videoInfoContainer: LinearLayout
    private lateinit var backButton: ImageButton
    private lateinit var courseTitleEditText: EditText
    private lateinit var courseDescriptionEditText: EditText

    // 上传进度相关视图
    private lateinit var progressContainer: LinearLayout
    private lateinit var uploadProgressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var progressPercent: TextView

    // 协程作用域
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // 当前登录用户信息
    private var currentUserId: Int = 0
    private var currentUserRole: String = ""
    private var currentUsername: String = ""

    // OSS上传管理器
    private val ossUploadManager = OSSUploadManager.getInstance()

    // API服务
    private lateinit var apiService: ApiService

    // 上传状态
    private var isUploading = false

    // 权限配置
    private val READ_EXTERNAL_STORAGE_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE

    companion object {
        private const val TAG = "VideoUploadActivity"
    }

    // 文件验证配置
    private val MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024 // 2GB (支持长时间视频)
    private val ALLOWED_EXTENSIONS = arrayOf("mp4", "avi", "mov", "mkv", "wmv")

    private var selectedVideoUri: Uri? = null
    private var selectedFileName: String? = null
    private var selectedFileSize: Long = 0

    // 注册Activity Result Launcher用于选择视频文件
    private val selectVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedVideoUri = uri
                // 获取文件名
                selectedFileName = getFileNameFromUri(uri)
                // 获取文件大小
                selectedFileSize = getFileSizeFromUri(uri)

                // 验证文件
                if (!validateFile(selectedFileName, selectedFileSize)) {
                    resetSelection()
                    return@let
                }

                // 更新UI显示选中的视频信息
                updateVideoInfoUI()

                // 启用上传按钮
                uploadButton.isEnabled = true

                Toast.makeText(this, "视频选择成功", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "视频选择取消", Toast.LENGTH_SHORT).show()
        }
    }

    // 注册权限请求Launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 权限已授予，打开文件选择器
            launchVideoPicker()
        } else {
            // 权限被拒绝，显示提示
            Toast.makeText(
                this,
                "需要存储权限才能选择视频文件",
                Toast.LENGTH_LONG
            ).show()

            // 可选：引导用户到设置页面
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_upload)

        // 获取当前登录用户信息
        loadCurrentUserInfo()

        // 初始化API服务
        apiService = ApiService.getInstance(this)

        // 初始化视图
        initViews()

        // 设置返回按钮点击事件
        backButton.setOnClickListener {
            finish()
        }

        // 设置选择视频按钮点击事件
        selectVideoButton.setOnClickListener {
            openVideoPicker()
        }

        // 设置上传按钮点击事件
        uploadButton.setOnClickListener {
            uploadVideo()
        }

        // 设置网络诊断按钮点击事件
        networkTestButton.setOnClickListener {
            val intent = Intent(this, NetworkTestActivity::class.java)
            startActivity(intent)
        }

        // 初始状态下禁用上传按钮
        uploadButton.isEnabled = false
    }

    private fun initViews() {
        selectVideoButton = findViewById(R.id.selectVideoButton)
        uploadButton = findViewById(R.id.uploadButton)
        networkTestButton = findViewById(R.id.networkTestButton)
        videoFileNameTextView = findViewById(R.id.videoFileNameTextView)
        videoFileSizeTextView = findViewById(R.id.videoFileSizeTextView)
        videoInfoContainer = findViewById(R.id.videoInfoContainer)
        backButton = findViewById(R.id.backButton)
        courseTitleEditText = findViewById(R.id.courseTitleEditText)
        courseDescriptionEditText = findViewById(R.id.courseDescriptionEditText)

        // 初始化上传进度相关视图
        progressContainer = findViewById(R.id.progressContainer)
        uploadProgressBar = findViewById(R.id.uploadProgressBar)
        progressText = findViewById(R.id.progressText)
        progressPercent = findViewById(R.id.progressPercent)
    }

    private fun openVideoPicker() {
        Log.d(TAG, "用户点击选择视频文件按钮")
        Log.d(TAG, "Android版本: ${Build.VERSION.SDK_INT} (API级别)")

        // 检查是否需要存储权限
        if (shouldCheckStoragePermission()) {
            Log.d(TAG, "需要检查存储权限，检查权限状态...")

            // 调试：显示当前需要的权限
            val permissions = getRequiredPermissions()
            Log.d(TAG, "需要的权限: ${permissions.joinToString()}")

            permissions.forEach { permission ->
                val status = ContextCompat.checkSelfPermission(this, permission)
                Log.d(TAG, "权限 $permission 状态: ${if (status == PackageManager.PERMISSION_GRANTED) "已授予" else "未授予"}")
            }

            if (hasReadExternalStoragePermission()) {
                Log.d(TAG, "存储权限已授予，打开视频选择器")
                launchVideoPicker()
            } else {
                Log.d(TAG, "存储权限未授予，请求权限")
                // 请求所需的权限
                if (permissions.isNotEmpty()) {
                    Log.d(TAG, "请求权限: ${permissions[0]}")
                    // 使用第一个权限（对于Android版本，只有一个权限）
                    requestPermissionLauncher.launch(permissions[0])
                } else {
                    Log.d(TAG, "没有需要请求的权限，直接打开选择器")
                    // 如果没有需要请求的权限，直接打开选择器
                    launchVideoPicker()
                }
            }
        } else {
            // Android 10+ (API 29+) 使用ACTION_GET_CONTENT不需要存储权限
            Log.d(TAG, "当前Android版本不需要存储权限，直接打开视频选择器")
            launchVideoPicker()
        }
    }

    /**
     * 实际启动视频选择器
     */
    private fun launchVideoPicker() {
        try {
            Log.d(TAG, "启动文件选择器: ACTION_GET_CONTENT")
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "video/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                // 可选：设置选择器标题
                putExtra(Intent.EXTRA_TITLE, "选择视频文件")
            }

            // 创建选择器Intent，让用户选择用什么应用打开
            val chooserIntent = Intent.createChooser(intent, "选择视频文件")
            selectVideoLauncher.launch(chooserIntent)
            Log.d(TAG, "文件选择器已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动文件选择器失败: ${e.message}", e)
            Toast.makeText(
                this,
                "无法打开文件选择器: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun updateVideoInfoUI() {
        // 显示视频信息容器
        videoInfoContainer.visibility = View.VISIBLE

        // 设置文件名
        videoFileNameTextView.text = selectedFileName ?: "未知文件"

        // 设置文件大小
        val fileSizeText = if (selectedFileSize > 0) {
            formatFileSize(selectedFileSize)
        } else {
            "大小未知"
        }
        videoFileSizeTextView.text = fileSizeText
    }

    // ==================== 上传进度控制方法 ====================

    /**
     * 显示上传进度条
     */
    private fun showUploadProgress() {
        progressContainer.visibility = View.VISIBLE
        uploadProgressBar.progress = 0
        progressPercent.text = "0%"
        progressText.text = "准备上传..."
    }

    /**
     * 隐藏上传进度条
     */
    private fun hideUploadProgress() {
        progressContainer.visibility = View.GONE
    }

    /**
     * 更新上传进度
     */
    private fun updateProgress(progress: Int, currentSize: Long, totalSize: Long) {
        uploadProgressBar.progress = progress
        progressPercent.text = "$progress%"

        val currentSizeMB = String.format("%.2f", currentSize / (1024.0 * 1024.0))
        val totalSizeMB = String.format("%.2f", totalSize / (1024.0 * 1024.0))
        progressText.text = "上传中: $currentSizeMB / $totalSizeMB MB"

        // 更新按钮文本显示进度
        uploadButton.text = "上传中... $progress%"
    }

    /**
     * 显示上传成功
     */
    private fun showUploadSuccess(fileUrl: String, ossPath: String, fileSize: Long) {
        progressText.text = "上传完成!"
        progressText.setTextColor(getColor(R.color.tech_green_data))
        uploadProgressBar.progress = 100
        progressPercent.text = "100%"

        // 显示成功消息
        val successMessage = """
            视频上传成功！

            存储路径：${ossPath.substringAfterLast("/")}
            文件大小：${formatFileSize(fileSize)}
            访问地址：${fileUrl.substringBefore("?")}

            视频已安全存储到阿里云OSS。
        """.trimIndent()

        Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()

        // 延迟隐藏进度条
        uploadButton.postDelayed({
            hideUploadProgress()
            resetUploadState()
            uploadButton.text = "上传完成"
            uploadButton.isEnabled = false
        }, 2000)
    }

    /**
     * 显示上传错误
     */
    private fun showUploadError(errorMessage: String) {
        progressText.text = "上传失败: $errorMessage"
        progressText.setTextColor(getColor(R.color.tech_red_alert))
        uploadProgressBar.progressTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tech_red_alert))

        Toast.makeText(this, "上传失败: $errorMessage", Toast.LENGTH_LONG).show()

        // 延迟重置状态
        uploadButton.postDelayed({
            hideUploadProgress()
            resetUploadState()
            uploadButton.text = "重新上传"
            uploadButton.isEnabled = true
            selectVideoButton.isEnabled = true
        }, 3000)
    }

    /**
     * 重置上传状态
     */
    private fun resetUploadState() {
        isUploading = false
        uploadButton.text = "上传视频"
        uploadProgressBar.progress = 0
        uploadProgressBar.progressTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tsinghua_purple))
        progressText.setTextColor(getColor(R.color.tsinghua_purple_dark))
    }

    /**
     * 检查OSS配置
     */
    private fun checkOSSConfiguration(): Boolean {
        if (!OSSConfig.isConfigured()) {
            val unconfiguredFields = OSSConfig.getUnconfiguredFields()
            val errorMessage = "阿里云OSS未正确配置，请检查以下字段：\n${unconfiguredFields.joinToString(", ")}\n\n请在OSSConfig.kt中配置你的阿里云OSS信息。"

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("OSS配置错误")
                .setMessage(errorMessage)
                .setPositiveButton("确定") { dialog, _ -> dialog.dismiss() }
                .show()

            return false
        }
        return true
    }

    // ==================== 真实上传方法 ====================

    private fun uploadVideo() {
        // 检查是否正在上传
        if (isUploading) {
            Toast.makeText(this, "正在上传中，请稍候...", Toast.LENGTH_SHORT).show()
            return
        }

        // 表单验证 - 使用安全调用避免空指针
        val courseTitle = courseTitleEditText.text?.toString()?.trim() ?: ""
        val courseDescription = courseDescriptionEditText.text?.toString()?.trim() ?: ""

        if (courseTitle.isEmpty()) {
            Toast.makeText(this, "请输入课程标题", Toast.LENGTH_SHORT).show()
            courseTitleEditText.requestFocus()
            return
        }

        if (courseDescription.isEmpty()) {
            Toast.makeText(this, "请输入课程描述", Toast.LENGTH_SHORT).show()
            courseDescriptionEditText.requestFocus()
            return
        }

        if (selectedVideoUri == null) {
            Toast.makeText(this, "请先选择视频文件", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedFileName == null) {
            Toast.makeText(this, "视频文件信息无效", Toast.LENGTH_SHORT).show()
            return
        }

        // 确保selectedFileName不为空
        val fileName = selectedFileName ?: "unknown_video.mp4"

        // 检查OSS配置
        if (!checkOSSConfiguration()) {
            return
        }

        // 获取教师信息（从SharedPreferences）
        val teacherName = if (currentUsername.isNotEmpty()) currentUsername else "未知教师"

        // 更新UI状态
        isUploading = true
        uploadButton.text = "上传中..."
        uploadButton.isEnabled = false
        selectVideoButton.isEnabled = false
        showUploadProgress()

        // 创建进度监听器
        val progressListener = object : OSSUploadManager.UploadProgressListener {
            override fun onProgress(progress: Int, currentSize: Long, totalSize: Long) {
                runOnUiThread {
                    updateProgress(progress, currentSize, totalSize)
                }
            }

            override fun onStart() {
                runOnUiThread {
                    progressText.text = "开始上传..."
                }
            }

            override fun onComplete() {
                runOnUiThread {
                    progressText.text = "上传完成，正在处理..."
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    showUploadError(error)
                }
            }
        }

        // 使用协程执行上传
        coroutineScope.launch {
            try {
                // 执行OSS上传
                val uploadResult = ossUploadManager.uploadVideo(
                    context = this@VideoUploadActivity,
                    videoUri = selectedVideoUri!!,
                    teacherName = teacherName,
                    originalFileName = fileName,
                    progressListener = progressListener
                )

                // 处理上传结果
                if (uploadResult.success) {
                    // 保存视频信息到本地存储（包含标题和描述）
                    VideoStorageManager.saveVideo(
                        context = this@VideoUploadActivity,
                        videoUrl = uploadResult.fileUrl!!,
                        videoTitle = courseTitle,  // 从表单获取的课程标题
                        videoDescription = courseDescription,  // 从表单获取的课程描述
                        teacher = teacherName  // 教师用户名
                    )
                    Log.d(TAG, "视频信息已保存到本地: ${uploadResult.fileUrl}, 标题: $courseTitle, 描述: $courseDescription")

                    // 验证用户信息
                    if (currentUserId == 0) {
                        runOnUiThread {
                            Log.e(TAG, "用户ID无效，无法创建课程")
                            showUploadError("用户信息无效，无法创建课程")
                        }
                        return@launch
                    }

                    if (currentUserRole != "teacher") {
                        runOnUiThread {
                            Log.e(TAG, "当前用户不是教师，无法创建课程")
                            showUploadError("只有教师可以创建课程")
                        }
                        return@launch
                    }

                    // 创建课程到后端数据库
                    try {
                        val createResult = apiService.createCourse(
                            name = courseTitle,
                            description = courseDescription,
                            teacherId = currentUserId,
                            teacherName = teacherName,
                            credit = 2,
                            videoUrl = uploadResult.fileUrl
                        )

                        runOnUiThread {
                            if (createResult.isSuccess) {
                                val createResponse = createResult.getOrThrow()
                                val courseData = createResponse.data

                                if (createResponse.success && courseData != null) {
                                    Log.d(TAG, "课程创建成功: ${courseData.name} (ID: ${courseData.courseId})")

                                    // 同时保存到本地CourseManager（用于离线查看），使用数据库返回的课程ID
                                    val course = Course(
                                        id = courseData.courseId, // 使用数据库返回的课程ID
                                        name = courseTitle,
                                        description = courseDescription,
                                        teacher = teacherName,
                                        credit = 2,
                                        videoUrl = uploadResult.fileUrl,
                                        isSelected = false
                                    )
                                    CourseManager.saveCourse(this@VideoUploadActivity, course)
                                    Log.d(TAG, "课程已保存到本地: ${course.name}, ID: ${course.id}")

                                    // 上传成功
                                    showUploadSuccess(
                                        uploadResult.fileUrl!!,
                                        uploadResult.ossPath!!,
                                        uploadResult.fileSize
                                    )

                                    // 记录课程信息
                                    val infoMessage = """
                                        课程信息已保存到数据库：

                                        课程标题：$courseTitle
                                        课程描述：$courseDescription
                                        视频文件：${fileName}
                                        文件大小：${formatFileSize(selectedFileSize)}
                                        OSS路径：${uploadResult.ossPath}
                                        课程ID：${courseData.courseId}
                                    """.trimIndent()
                                    Log.d("VideoUpload", infoMessage)

                                } else {
                                    Log.e(TAG, "创建课程失败: ${createResponse.message}")
                                    showUploadError("创建课程失败: ${createResponse.message}")
                                }
                            } else {
                                val error = createResult.exceptionOrNull()?.message ?: "未知错误"
                                Log.e(TAG, "创建课程API调用失败: $error")
                                showUploadError("创建课程失败: $error")
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Log.e(TAG, "创建课程异常: ${e.message}", e)
                            showUploadError("创建课程异常: ${e.message}")
                        }
                    }
                } else {
                    runOnUiThread {
                        // 上传失败
                        showUploadError(uploadResult.errorMessage ?: "未知错误")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showUploadError("上传异常: ${e.message}")
                }
            } finally {
                runOnUiThread {
                    isUploading = false
                    // 注意：按钮状态在showUploadSuccess/showUploadError中处理
                }
            }
        }
    }

    private fun resetSelection() {
        selectedVideoUri = null
        selectedFileName = null
        selectedFileSize = 0

        videoInfoContainer.visibility = View.GONE
        videoFileNameTextView.text = "未选择视频"
        videoFileSizeTextView.text = ""

        uploadButton.isEnabled = false
    }

    /**
     * 清空表单输入
     */
    private fun clearForm() {
        courseTitleEditText.text.clear()
        courseDescriptionEditText.text.clear()
    }

    private fun getFileNameFromUri(uri: Uri): String {
        return try {
            var displayName: String? = null

            // 尝试通过ContentResolver获取文件名
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        displayName = cursor.getString(displayNameIndex)
                    }
                }
            }

            // 如果无法通过ContentResolver获取，尝试从URI路径中提取
            if (displayName.isNullOrEmpty()) {
                uri.path?.let { path ->
                    val segments = path.split("/")
                    if (segments.isNotEmpty()) {
                        segments.last()
                    } else {
                        "video_file"
                    }
                } ?: "video_file"  // 如果uri.path为null，返回默认值
            } else {
                displayName!!
            }
        } catch (e: Exception) {
            "video_file"
        }
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        return try {
            var fileSize: Long = 0

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            fileSize
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 验证文件大小是否在限制范围内
     */
    private fun validateFileSize(fileSize: Long): Boolean {
        return fileSize > 0 && fileSize <= MAX_FILE_SIZE
    }

    /**
     * 验证文件扩展名是否允许
     */
    private fun validateFileExtension(fileName: String?): Boolean {
        if (fileName.isNullOrEmpty()) return false

        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in ALLOWED_EXTENSIONS
    }

    /**
     * 验证文件（大小和格式）
     * @return 验证通过返回true，否则false
     */
    private fun validateFile(fileName: String?, fileSize: Long): Boolean {
        if (!validateFileSize(fileSize)) {
            Toast.makeText(this, "文件大小不能超过2GB", Toast.LENGTH_LONG).show()
            return false
        }

        if (!validateFileExtension(fileName)) {
            val extensionsText = ALLOWED_EXTENSIONS.joinToString(", ")
            Toast.makeText(this, "只支持以下格式: $extensionsText", Toast.LENGTH_LONG).show()
            return false
        }

        return true
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * 检查当前是否运行在模拟器上
     */
    private fun isRunningOnEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                "google_sdk" == Build.PRODUCT)
    }

    /**
     * 获取所需的存储权限数组
     * Android 13+ (API 33+) 需要 READ_MEDIA_VIDEO
     * Android 10-12 (API 29-32) 理论上不需要权限，但为了兼容性检查 READ_EXTERNAL_STORAGE
     * Android 9- (API 28-) 需要 READ_EXTERNAL_STORAGE
     */
    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33) 使用 READ_MEDIA_VIDEO
            arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            // Android 12 及以下使用 READ_EXTERNAL_STORAGE
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * 检查是否需要检查存储权限
     * Android 10+ (API 29+) 使用 ACTION_GET_CONTENT 不需要存储权限
     * Android 9- (API 28-) 可能需要 READ_EXTERNAL_STORAGE 权限
     * 注意：某些设备/定制系统可能仍然需要权限，这里保守检查
     */
    private fun shouldCheckStoragePermission(): Boolean {
        // Android 10+ (API 29+) 理论上不需要权限，但为了兼容性仍然检查
        // return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

        // 保守方案：始终检查权限，确保兼容性
        return true
    }

    /**
     * 检查是否已授予读取外部存储权限
     */
    private fun hasReadExternalStoragePermission(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 显示模拟器文件选择对话框
     */
    private fun showEmulatorFilePickerDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("模拟器文件选择")
            .setMessage("检测到你在模拟器中运行。模拟器可能没有视频文件或文件管理器。\n\n请选择：")
            .setPositiveButton("尝试真实文件选择器") { dialog, which ->
                // 尝试使用真实文件选择器
                if (hasReadExternalStoragePermission()) {
                    launchVideoPicker()
                } else {
                    val permissions = getRequiredPermissions()
                    if (permissions.isNotEmpty()) {
                        requestPermissionLauncher.launch(permissions[0])
                    } else {
                        launchVideoPicker()
                    }
                }
            }
            .setNegativeButton("使用测试文件") { dialog, which ->
                // 使用测试文件
                useTestVideoFile()
            }
            .setNeutralButton("取消") { dialog, which ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    /**
     * 使用测试视频文件（模拟器专用）
     */
    private fun useTestVideoFile() {
        // 创建模拟的课程上传信息
        val teacherName = intent.getStringExtra("username") ?: "测试教师"

        // 模拟文件信息
        selectedFileName = "test_video_sample.mp4"
        selectedFileSize = 154 * 1024 * 1024L // 154MB
        selectedVideoUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:test_video.mp4")

        // 直接更新UI（不通过ContentResolver查询）
        videoInfoContainer.visibility = View.VISIBLE
        videoFileNameTextView.text = selectedFileName ?: "测试视频文件"
        videoFileSizeTextView.text = formatFileSize(selectedFileSize)

        uploadButton.isEnabled = true

        Toast.makeText(
            this,
            "已使用测试视频文件（模拟器模式）\n文件：$selectedFileName\n大小：${formatFileSize(selectedFileSize)}",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * 加载当前登录用户信息
     */
    private fun loadCurrentUserInfo() {
        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        currentUserId = sharedPref.getInt("user_id", 0)
        currentUserRole = sharedPref.getString("role", "") ?: ""
        currentUsername = sharedPref.getString("username", "") ?: ""
        Log.d(TAG, "当前用户信息: ID=$currentUserId, 角色=$currentUserRole, 用户名=$currentUsername")
    }

    /**
     * 显示权限被拒绝的对话框
     */
    private fun showPermissionDeniedDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("权限被拒绝")
            .setMessage("需要存储权限才能选择视频文件。\n\n你可以在设置中手动授予权限。")
            .setPositiveButton("去设置") { dialog, which ->
                // 打开应用设置页面
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
            .setNegativeButton("取消") { dialog, which ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }
}