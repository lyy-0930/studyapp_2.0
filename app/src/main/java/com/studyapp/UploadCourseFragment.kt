package com.studyapp

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.studyapp.util.ImageLoaderUtil
import com.studyapp.manager.ApiService
import com.studyapp.manager.CourseManager
import com.studyapp.manager.OSSConfig
import com.studyapp.manager.OSSUploadManager
import com.studyapp.manager.VideoStorageManager
import com.studyapp.model.Category
import com.studyapp.model.Course
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class UploadCourseFragment : Fragment() {

    private lateinit var selectVideoButton: Button
    private lateinit var uploadButton: Button
    private lateinit var networkTestButton: Button
    private lateinit var videoFileNameTextView: TextView
    private lateinit var videoFileSizeTextView: TextView
    private lateinit var videoInfoContainer: LinearLayout
    private lateinit var courseTitleEditText: EditText
    private lateinit var courseDescriptionEditText: EditText
    private lateinit var progressContainer: LinearLayout
    private lateinit var uploadProgressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var progressPercent: TextView
    private lateinit var courseCoverPreview: ImageView
    private lateinit var aiGenerateCoverButton: Button
    private lateinit var coverPromptEditText: EditText
    private lateinit var categorySpinner: Spinner
    private var categories = listOf<Category>()
    private var selectedCategoryId: Int? = null

    // PPT 相关
    private lateinit var selectPptButton: Button
    private lateinit var pptInfoContainer: LinearLayout
    private lateinit var pptFileNameTextView: TextView
    private lateinit var questionCountHint: TextView
    private var selectedPptUri: Uri? = null
    private var selectedPptName: String? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val ossUploadManager = OSSUploadManager.getInstance()
    private lateinit var apiService: ApiService

    private var isUploading = false
    private var selectedVideoUri: Uri? = null
    private var selectedFileName: String? = null
    private var selectedFileSize: Long = 0
    private var generatedCoverBitmap: Bitmap? = null
    private var uploadedImageUrl: String? = null

    private var currentUserId: Int = 0
    private var currentUserRole: String = ""
    private var currentUsername: String = ""

    private val MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024
    private val ALLOWED_EXTENSIONS = arrayOf("mp4", "avi", "mov", "mkv", "wmv")

    private val selectVideoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedVideoUri = uri
                selectedFileName = getFileNameFromUri(uri)
                selectedFileSize = getFileSizeFromUri(uri)

                if (!validateFile(selectedFileName, selectedFileSize)) {
                    resetSelection()
                    return@let
                }

                updateVideoInfoUI()
                uploadButton.isEnabled = true
                Toast.makeText(requireContext(), "视频选择成功", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "视频选择取消", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchVideoPicker()
        } else {
            Toast.makeText(requireContext(), "需要存储权限才能选择视频文件", Toast.LENGTH_LONG).show()
            showPermissionDeniedDialog()
        }
    }

    private val selectPptLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val name = getFileNameFromUri(uri)
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext != "pptx") {
                    Toast.makeText(requireContext(), "请选择 .pptx 格式的PPT文件", Toast.LENGTH_LONG).show()
                    return@let
                }
                selectedPptUri = uri
                selectedPptName = name
                pptFileNameTextView.text = name
                questionCountHint.text = "已选择PPT，将在上传时自动生成选择题"
                pptInfoContainer.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "PPT选择成功", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadCurrentUserInfo()
        apiService = ApiService.getInstance(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_upload_course, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupClickListeners()
        uploadButton.isEnabled = false
        loadCategories()
    }

    private fun initViews(view: View) {
        selectVideoButton = view.findViewById(R.id.selectVideoButton)
        uploadButton = view.findViewById(R.id.uploadButton)
        networkTestButton = view.findViewById(R.id.networkTestButton)
        videoFileNameTextView = view.findViewById(R.id.videoFileNameTextView)
        videoFileSizeTextView = view.findViewById(R.id.videoFileSizeTextView)
        videoInfoContainer = view.findViewById(R.id.videoInfoContainer)
        courseTitleEditText = view.findViewById(R.id.courseTitleEditText)
        courseDescriptionEditText = view.findViewById(R.id.courseDescriptionEditText)
        progressContainer = view.findViewById(R.id.progressContainer)
        uploadProgressBar = view.findViewById(R.id.uploadProgressBar)
        progressText = view.findViewById(R.id.progressText)
        progressPercent = view.findViewById(R.id.progressPercent)
        courseCoverPreview = view.findViewById(R.id.courseCoverPreview)
        aiGenerateCoverButton = view.findViewById(R.id.aiGenerateCoverButton)
        coverPromptEditText = view.findViewById(R.id.coverPromptEditText)
        categorySpinner = view.findViewById(R.id.categorySpinner)
        selectPptButton = view.findViewById(R.id.selectPptButton)
        pptInfoContainer = view.findViewById(R.id.pptInfoContainer)
        pptFileNameTextView = view.findViewById(R.id.pptFileNameTextView)
        questionCountHint = view.findViewById(R.id.questionCountHint)
    }

    private fun setupClickListeners() {
        selectVideoButton.setOnClickListener { openVideoPicker() }
        uploadButton.setOnClickListener { uploadVideo() }
        networkTestButton.setOnClickListener {
            startActivity(Intent(requireContext(), NetworkTestActivity::class.java))
        }
        aiGenerateCoverButton.setOnClickListener { generateCourseCover() }
        selectPptButton.setOnClickListener { openPptPicker() }
    }

    // ==================== Category Selection ====================

    private fun loadCategories() {
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) { apiService.getCategories() }
            if (result.isSuccess) {
                categories = result.getOrThrow()
                val names = mutableListOf("未选择分类")
                names.addAll(categories.map { it.name })
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                categorySpinner.adapter = adapter
                categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                        selectedCategoryId = if (pos == 0) null else categories[pos - 1].id
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) { selectedCategoryId = null }
                }
            }
        }
    }

    // ==================== Video Selection ====================

    private fun openVideoPicker() {
        if (shouldCheckStoragePermission()) {
            val permissions = getRequiredPermissions()
            if (permissions.isNotEmpty()) {
                requestPermissionLauncher.launch(permissions[0])
            } else {
                launchVideoPicker()
            }
        } else {
            launchVideoPicker()
        }
    }

    private fun launchVideoPicker() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "video/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            selectVideoLauncher.launch(Intent.createChooser(intent, "选择视频文件"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法打开文件选择器: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openPptPicker() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            selectPptLauncher.launch(Intent.createChooser(intent, "选择PPT文件"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法打开文件选择器: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateVideoInfoUI() {
        videoInfoContainer.visibility = View.VISIBLE
        videoFileNameTextView.text = selectedFileName ?: "未知文件"
        videoFileSizeTextView.text = if (selectedFileSize > 0) formatFileSize(selectedFileSize) else "大小未知"
    }

    // ==================== Upload Progress ====================

    private fun showUploadProgress() {
        progressContainer.visibility = View.VISIBLE
        uploadProgressBar.progress = 0
        progressPercent.text = "0%"
        progressText.text = "准备上传..."
    }

    private fun hideUploadProgress() {
        progressContainer.visibility = View.GONE
    }

    private fun updateProgress(progress: Int, currentSize: Long, totalSize: Long) {
        uploadProgressBar.progress = progress
        progressPercent.text = "$progress%"
        val currentSizeMB = String.format("%.2f", currentSize / (1024.0 * 1024.0))
        val totalSizeMB = String.format("%.2f", totalSize / (1024.0 * 1024.0))
        progressText.text = "上传中: $currentSizeMB / $totalSizeMB MB"
        uploadButton.text = "上传中... $progress%"
    }

    private fun showUploadSuccess(fileUrl: String, ossPath: String, fileSize: Long) {
        progressText.text = "上传完成!"
        progressText.setTextColor(requireContext().getColor(R.color.tech_green_data))
        uploadProgressBar.progress = 100
        progressPercent.text = "100%"
        Toast.makeText(requireContext(), "视频上传成功！", Toast.LENGTH_LONG).show()
        view?.postDelayed({
            hideUploadProgress()
            resetUploadState()
            uploadButton.text = "上传完成"
            uploadButton.isEnabled = false
        }, 2000)
    }

    private fun showUploadError(errorMessage: String) {
        progressText.text = "上传失败: $errorMessage"
        progressText.setTextColor(requireContext().getColor(R.color.tech_red_alert))
        uploadProgressBar.progressTintList = android.content.res.ColorStateList.valueOf(
            requireContext().getColor(R.color.tech_red_alert)
        )
        Toast.makeText(requireContext(), "上传失败: $errorMessage", Toast.LENGTH_LONG).show()
        view?.postDelayed({
            hideUploadProgress()
            resetUploadState()
            uploadButton.text = "重新上传"
            uploadButton.isEnabled = true
            selectVideoButton.isEnabled = true
        }, 3000)
    }

    private fun resetUploadState() {
        isUploading = false
        uploadButton.text = "上传视频"
        uploadProgressBar.progress = 0
        uploadProgressBar.progressTintList = android.content.res.ColorStateList.valueOf(
            requireContext().getColor(R.color.tsinghua_purple)
        )
        progressText.setTextColor(requireContext().getColor(R.color.tsinghua_purple_dark))
    }

    // ==================== AI Course Cover Generation ====================

    private fun generateCourseCover() {
        val courseTitle = courseTitleEditText.text?.toString()?.trim() ?: ""
        if (courseTitle.isEmpty()) {
            Toast.makeText(requireContext(), "请先输入课程标题", Toast.LENGTH_SHORT).show()
            courseTitleEditText.requestFocus()
            return
        }

        val prompt = coverPromptEditText.text?.toString()?.trim() ?: ""

        aiGenerateCoverButton.isEnabled = false
        aiGenerateCoverButton.text = "AI生成中..."
        coroutineScope.launch {
            try {
                // 1. 尝试通过后端调用 Pollinations AI 生成
                val aiImageUrl = withContext(Dispatchers.IO) {
                    try {
                        callAiImageGeneration(courseTitle, prompt)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (aiImageUrl != null) {
                    // AI生成成功 — 下载图片，叠加课程名称文字
                    val fullUrl = if (aiImageUrl.startsWith("http")) aiImageUrl
                        else "${ApiService.BASE_URL}$aiImageUrl"
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            // 下载AI生成的图片（使用原生HttpURLConnection）
                            val url = URL(fullUrl)
                            val conn = url.openConnection() as HttpURLConnection
                            conn.connectTimeout = 15000
                            conn.readTimeout = 15000
                            val bytes = try {
                                conn.inputStream.readBytes()
                            } finally {
                                conn.disconnect()
                            }
                            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (src != null) overlayTextOnImage(src, courseTitle) else null
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (bitmap != null) {
                        // 使用叠加文字后的本地图片
                        generatedCoverBitmap = bitmap
                        uploadedImageUrl = null // 需要重新上传
                        courseCoverPreview.setImageBitmap(bitmap)
                        courseCoverPreview.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), "AI封面生成成功", Toast.LENGTH_SHORT).show()
                    } else {
                        // 下载失败，直接使用服务器上的图片URL
                        uploadedImageUrl = aiImageUrl
                        generatedCoverBitmap = null
                        withContext(Dispatchers.Main) {
                            courseCoverPreview.visibility = View.VISIBLE
                            ImageLoaderUtil.load(courseCoverPreview, fullUrl, crossfade = true)
                        }
                        Toast.makeText(requireContext(), "AI封面生成成功", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // 2. 回退到本地 Canvas 绘制
                    val bitmap = withContext(Dispatchers.IO) {
                        CourseImageGenerator.generate(courseTitle, prompt)
                    }
                    generatedCoverBitmap = bitmap
                    uploadedImageUrl = null
                    courseCoverPreview.setImageBitmap(bitmap)
                    courseCoverPreview.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "封面已生成（主题匹配）", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "封面生成失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                aiGenerateCoverButton.isEnabled = true
                aiGenerateCoverButton.text = "🎨 AI 生成课程封面"
            }
        }
    }

    private fun overlayTextOnImage(bitmap: Bitmap, courseName: String): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val w = result.width.toFloat()
        val h = result.height.toFloat()

        // 底部半透明遮罩条，增强文字可读性
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            alpha = 60
        }
        canvas.drawRoundRect(w * 0.05f, h * 0.68f, w * 0.95f, h * 0.93f, 12f, 12f, barPaint)

        // 课程名称
        val name = if (courseName.length > 16) courseName.substring(0, 16) + "..." else courseName
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = h * 0.09f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            setShadowLayer(3f, 0f, 2f, android.graphics.Color.argb(100, 0, 0, 0))
        }
        // 自适应字号
        val maxWidth = w * 0.85f
        if (textPaint.measureText(name) > maxWidth) {
            textPaint.textSize = textPaint.textSize * (maxWidth / textPaint.measureText(name))
        }
        canvas.drawText(name, w / 2f, h * 0.83f, textPaint)

        // 副标题
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            alpha = 180
            textSize = h * 0.04f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
        canvas.drawText("COURSE", w / 2f, h * 0.91f, subPaint)

        return result
    }

    private fun callAiImageGeneration(courseTitle: String, prompt: String): String? {
        return try {
            val body = JSONObject().apply {
                put("courseName", courseTitle)
                put("prompt", prompt.ifEmpty { courseTitle })
            }.toString()

            val url = URL("${ApiService.BASE_URL}/generate-course-image")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.connectTimeout = 10000
            conn.readTimeout = 50000
            ApiService.addAuthToConnection(conn, requireContext())
            try {
                conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val respBody = conn.inputStream.bufferedReader().readText()
                    val root = JSONObject(respBody)
                    if (root.optBoolean("success", false)) {
                        val data = root.optJSONObject("data")
                        data?.optString("imageUrl", "")?.takeIf { it.isNotEmpty() }
                    } else null
                } else null
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    // ==================== Upload Core Logic ====================

    private fun checkOSSConfiguration(): Boolean {
        if (!OSSConfig.isConfigured()) {
            val unconfiguredFields = OSSConfig.getUnconfiguredFields()
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("OSS配置错误")
                .setMessage("阿里云OSS未正确配置，请检查以下字段：\n${unconfiguredFields.joinToString(", ")}\n\n请在OSSConfig.kt中配置你的阿里云OSS信息。")
                .setPositiveButton("确定") { dialog, _ -> dialog.dismiss() }
                .show()
            return false
        }
        return true
    }

    private fun uploadVideo() {
        if (isUploading) {
            Toast.makeText(requireContext(), "正在上传中，请稍候...", Toast.LENGTH_SHORT).show()
            return
        }

        val courseTitle = courseTitleEditText.text?.toString()?.trim() ?: ""
        val courseDescription = courseDescriptionEditText.text?.toString()?.trim() ?: ""

        if (courseTitle.isEmpty()) {
            Toast.makeText(requireContext(), "请输入课程标题", Toast.LENGTH_SHORT).show()
            courseTitleEditText.requestFocus()
            return
        }
        if (courseDescription.isEmpty()) {
            Toast.makeText(requireContext(), "请输入课程描述", Toast.LENGTH_SHORT).show()
            courseDescriptionEditText.requestFocus()
            return
        }
        if (selectedVideoUri == null) {
            Toast.makeText(requireContext(), "请先选择视频文件", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = selectedFileName ?: "unknown_video.mp4"
        if (!checkOSSConfiguration()) return

        val teacherName = if (currentUsername.isNotEmpty()) currentUsername else "未知教师"

        isUploading = true
        uploadButton.text = "上传中..."
        uploadButton.isEnabled = false
        selectVideoButton.isEnabled = false
        showUploadProgress()

        val progressListener = object : OSSUploadManager.UploadProgressListener {
            override fun onProgress(progress: Int, currentSize: Long, totalSize: Long) {
                requireActivity().runOnUiThread { updateProgress(progress, currentSize, totalSize) }
            }
            override fun onStart() {
                requireActivity().runOnUiThread { progressText.text = "开始上传..." }
            }
            override fun onComplete() {
                requireActivity().runOnUiThread { progressText.text = "上传完成，正在处理..." }
            }
            override fun onError(error: String) {
                requireActivity().runOnUiThread { showUploadError(error) }
            }
        }

        coroutineScope.launch {
            try {
                val uploadResult = ossUploadManager.uploadVideo(
                    context = requireContext(),
                    videoUri = selectedVideoUri!!,
                    teacherName = teacherName,
                    originalFileName = fileName,
                    progressListener = progressListener
                )

                if (uploadResult.success) {
                    VideoStorageManager.saveVideo(
                        context = requireContext(),
                        videoUrl = uploadResult.fileUrl!!,
                        videoTitle = courseTitle,
                        videoDescription = courseDescription,
                        teacher = teacherName
                    )

                    if (currentUserId == 0) {
                        requireActivity().runOnUiThread {
                            showUploadError("用户信息无效，无法创建课程")
                        }
                        return@launch
                    }
                    if (currentUserRole != "teacher") {
                        requireActivity().runOnUiThread {
                            showUploadError("只有教师可以创建课程")
                        }
                        return@launch
                    }

                    try {
                        // 上传AI生成的课程封面（如果有）
                        var coverUrl = uploadedImageUrl
                        if (generatedCoverBitmap != null && coverUrl == null) {
                            requireActivity().runOnUiThread {
                                progressText.text = "上传课程封面..."
                            }
                            coverUrl = uploadCourseCover(generatedCoverBitmap!!, courseTitle)
                            uploadedImageUrl = coverUrl
                        }

                        val createResult = apiService.createCourse(
                            name = courseTitle,
                            description = courseDescription,
                            teacherId = currentUserId,
                            teacherName = teacherName,
                            credit = 2,
                            videoUrl = uploadResult.fileUrl,
                            imageUrl = uploadedImageUrl,
                            categoryId = selectedCategoryId
                        )

                        if (createResult.isSuccess) {
                            val createResponse = createResult.getOrThrow()
                            val courseData = createResponse.data
                            if (createResponse.success && courseData != null) {
                                val course = Course(
                                    id = courseData.courseId,
                                    name = courseTitle,
                                    description = courseDescription,
                                    teacher = teacherName,
                                    credit = 2,
                                    videoUrl = uploadResult.fileUrl,
                                    imageUrl = uploadedImageUrl,
                                    isSelected = false
                                )
                                CourseManager.saveCourse(requireContext(), course)

                                // 如果有PPT，解析并保存文本 + AI生成题目
                                var generatedQuestionCount = 0
                                var questionGenError: String? = null
                                if (selectedPptUri != null) {
                                    try {
                                        requireActivity().runOnUiThread {
                                            progressText.text = "正在解析PPT..."
                                        }
                                        val pptStream = requireContext().contentResolver.openInputStream(selectedPptUri!!)
                                        if (pptStream != null) {
                                            val slideTexts = PPTXParser.parse(pptStream)
                                            pptStream.close()

                                            if (slideTexts.isNotEmpty()) {
                                                // 1. 保存幻灯片文本到服务器
                                                requireActivity().runOnUiThread {
                                                    progressText.text = "正在保存幻灯片文本..."
                                                }
                                                val saveTextsResult = apiService.saveSlideTexts(
                                                    courseData.courseId, slideTexts
                                                )
                                                if (saveTextsResult.isSuccess) {
                                                    // 2. 调用AI生成题目
                                                    requireActivity().runOnUiThread {
                                                        progressText.text = "AI正在根据PPT内容生成题目..."
                                                    }
                                                    val aiResult = apiService.aiGenerateQuestions(
                                                        courseData.courseId
                                                    )
                                                    if (aiResult.isSuccess) {
                                                        generatedQuestionCount = -1 // 标记成功
                                                    } else {
                                                        questionGenError = "AI出题失败: ${aiResult.exceptionOrNull()?.message}"
                                                    }
                                                } else {
                                                    questionGenError = "保存PPT文本失败"
                                                }
                                            } else {
                                                questionGenError = "PPT中未提取到文本内容（可能全是图片/公式）"
                                            }
                                        } else {
                                            questionGenError = "无法读取PPT文件"
                                        }
                                    } catch (e: Exception) {
                                        questionGenError = "PPT解析失败: ${e.message}"
                                        android.util.Log.w("UploadCourse", "PPT解析失败: ${e.message}", e)
                                    }
                                }

                                val finalQuestionCount = generatedQuestionCount
                                val finalError = questionGenError
                                requireActivity().runOnUiThread {
                                    if (finalQuestionCount != 0) {
                                        val msg = if (finalQuestionCount < 0) {
                                            "PPT已上传，AI题目正在生成中，请在题目管理中审核发布"
                                        } else {
                                            "已从PPT中生成${finalQuestionCount}道选择题"
                                        }
                                        Toast.makeText(
                                            requireContext(),
                                            msg,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else if (finalError != null) {
                                        Toast.makeText(
                                            requireContext(),
                                            "题目生成失败: $finalError",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    showUploadSuccess(
                                        uploadResult.fileUrl!!,
                                        uploadResult.ossPath!!,
                                        uploadResult.fileSize
                                    )
                                }
                            } else {
                                requireActivity().runOnUiThread {
                                    showUploadError("创建课程失败: ${createResponse.message}")
                                }
                            }
                        } else {
                            val error = createResult.exceptionOrNull()?.message ?: "未知错误"
                            requireActivity().runOnUiThread { showUploadError("创建课程失败: $error") }
                        }
                    } catch (e: Exception) {
                        requireActivity().runOnUiThread { showUploadError("创建课程异常: ${e.message}") }
                    }
                } else {
                    requireActivity().runOnUiThread { showUploadError(uploadResult.errorMessage ?: "未知错误") }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread { showUploadError("上传异常: ${e.message}") }
            } finally {
                requireActivity().runOnUiThread { isUploading = false }
            }
        }
    }

    // ==================== Course Cover Upload ====================

    private suspend fun uploadCourseCover(bitmap: Bitmap, courseName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val cacheDir = requireContext().cacheDir
                val tempFile = File(cacheDir, "course_cover_${System.currentTimeMillis()}.jpg")
                FileOutputStream(tempFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                val fileBytes = tempFile.readBytes()
                val boundary = "Boundary_${System.currentTimeMillis()}"
                val lineEnd = "\r\n"
                val twoHyphens = "--"

                val url = URL("${ApiService.BASE_URL}/upload/course-image")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.connectTimeout = 30000
                conn.readTimeout = 30000
                ApiService.addAuthToConnection(conn, requireContext())
                try {
                    val out = conn.outputStream
                    // courseName field
                    out.write("$twoHyphens$boundary$lineEnd".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"courseName\"$lineEnd$lineEnd".toByteArray())
                    out.write("$courseName$lineEnd".toByteArray())
                    // image file
                    out.write("$twoHyphens$boundary$lineEnd".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"image\"; filename=\"${tempFile.name}\"$lineEnd".toByteArray())
                    out.write("Content-Type: image/jpeg$lineEnd$lineEnd".toByteArray())
                    out.write(fileBytes)
                    out.write(lineEnd.toByteArray())
                    out.write("$twoHyphens$boundary$twoHyphens$lineEnd".toByteArray())
                    out.flush()
                    out.close()

                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val responseBody = conn.inputStream.bufferedReader().readText()
                        val root = JSONObject(responseBody)
                        if (root.optBoolean("success", false)) {
                            val data = root.optJSONObject("data")
                            data?.optString("imageUrl", "")?.takeIf { it.isNotEmpty() }
                        } else null
                    } else null
                } finally {
                    conn.disconnect()
                    tempFile.delete()
                }
            } catch (e: Exception) {
                null
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

    private fun getFileNameFromUri(uri: Uri): String {
        return try {
            var displayName: String? = null
            requireActivity().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) displayName = cursor.getString(idx)
                }
            }
            displayName ?: uri.path?.split("/")?.lastOrNull() ?: "video_file"
        } catch (_: Exception) { "video_file" }
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        return try {
            var fileSize: Long = 0
            requireActivity().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (idx != -1) fileSize = cursor.getLong(idx)
                }
            }
            fileSize
        } catch (_: Exception) { 0L }
    }

    private fun validateFileSize(fileSize: Long): Boolean = fileSize > 0 && fileSize <= MAX_FILE_SIZE

    private fun validateFileExtension(fileName: String?): Boolean {
        if (fileName.isNullOrEmpty()) return false
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in ALLOWED_EXTENSIONS
    }

    private fun validateFile(fileName: String?, fileSize: Long): Boolean {
        if (!validateFileSize(fileSize)) {
            Toast.makeText(requireContext(), "文件大小不能超过2GB", Toast.LENGTH_LONG).show()
            return false
        }
        if (!validateFileExtension(fileName)) {
            Toast.makeText(requireContext(), "只支持以下格式: ${ALLOWED_EXTENSIONS.joinToString(", ")}", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun formatFileSize(size: Long): String = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun shouldCheckStoragePermission(): Boolean = true

    private fun loadCurrentUserInfo() {
        val sharedPref = requireActivity().getSharedPreferences("login_prefs", android.content.Context.MODE_PRIVATE)
        currentUserId = sharedPref.getInt("user_id", 0)
        currentUserRole = sharedPref.getString("role", "") ?: ""
        currentUsername = sharedPref.getString("username", "") ?: ""
    }

    private fun showPermissionDeniedDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("权限被拒绝")
            .setMessage("需要存储权限才能选择视频文件。\n\n你可以在设置中手动授予权限。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
            .setNegativeButton("取消") { d, _ -> d.dismiss() }
            .setCancelable(true)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Avoid leaking the fragment if upload is in progress
        if (::uploadButton.isInitialized) {
            uploadButton.text = "上传视频"
        }
    }
}
