package com.studyapp

import android.media.MediaPlayer
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageButton
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.studyapp.manager.ApiService
import com.studyapp.manager.OSSConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.HashMap

/**
 * 视频播放Activity
 * 使用VideoView播放来自OSS的视频
 */
class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var mediaController: MediaController
    private lateinit var loadingTextView: TextView
    private lateinit var errorTextView: TextView
    private lateinit var hintTextView: TextView
    private lateinit var backButton: ImageButton
    private lateinit var speedButton: ImageButton

    private var videoUrlToPlay: String? = null
    private var courseNameToShow: String? = null
    private var isSurfaceReady = false
    private var isDataSourceSet = false
    private var hasPlaybackStarted = false
    private var hasPlaybackError = false
    private var isFetchingSignedUrl = false // 正在获取签名播放地址

    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private val PLAYBACK_TIMEOUT_MS = 15000L // 15秒超时

    private var currentPlaybackSpeed = 1.0f // 当前播放速度，1.0=正常速度
    private val availableSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) // 可选择的播放速度
    private var mediaPlayerInstance: MediaPlayer? = null // 保存MediaPlayer实例引用

    // 点击记录相关变量
    private var courseId: Int = -1
    private var currentUserId: Int = 0
    private lateinit var apiService: ApiService
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var hasRecordedClick = false // 防止重复记录点击

    // 进度追踪相关
    private var progressRunnable: Runnable? = null
    private var totalWatchTimeMs: Long = 0
    private var lastProgressRecordMs: Long = 0
    private val PROGRESS_RECORD_INTERVAL_MS = 30000L // 每30秒记录一次进度

    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_COURSE_NAME = "extra_course_name"
        private const val TAG = "VideoPlayerActivity"
    }

    /**
     * 检查是否运行在模拟器上
     */
    private fun isRunningOnEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()

        // 常见的模拟器标识
        val isEmulator = fingerprint.contains("generic") ||
               fingerprint.contains("emulator") ||
               fingerprint.contains("test-keys") ||
               fingerprint.contains("unknown") ||
               model.contains("android sdk built for") ||
               model.contains("emulator") ||
               model.contains("google_sdk") ||
               brand.contains("generic") ||
               device.contains("generic") ||
               product.contains("sdk") ||
               product.contains("emulator") ||
               product.contains("google_sdk") ||
               manufacturer.contains("genymotion") ||
               Build.HARDWARE.contains("goldfish") ||
               Build.HARDWARE.contains("ranchu") ||
               Build.HARDWARE.contains("vbox") ||
               Build.HARDWARE.contains("vmware")

        Log.d(TAG, "模拟器检测 - Fingerprint: $fingerprint, Model: $model, Brand: $brand, Device: $device, Product: $product, Hardware: ${Build.HARDWARE}, IsEmulator: $isEmulator")

        return isEmulator
    }

    /**
     * 在浏览器中打开视频URL
     * @param videoUrl 要打开的视频URL
     */
    private fun openVideoInBrowser(videoUrl: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(videoUrl))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

            // 检查是否有浏览器可以处理此Intent
            val packageManager = packageManager
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                Toast.makeText(this, "正在浏览器中打开视频", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "未找到浏览器应用", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "在浏览器中打开视频失败: ${e.message}", e)
            Toast.makeText(this, "打开失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 测试视频URL的网络连接
     * @param videoUrl 要测试的视频URL
     */
    private fun testVideoUrlConnection(videoUrl: String) {
        Log.d(TAG, "开始测试视频URL连接: $videoUrl")

        coroutineScope.launch {
            try {
                showLoading(true)
                val url = java.net.URL(videoUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection

                // 设置超时和请求头
                connection.connectTimeout = 10000 // 10秒
                connection.readTimeout = 10000 // 10秒
                connection.requestMethod = "HEAD" // 只获取头部信息，不下载内容
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")

                val responseCode = connection.responseCode
                val contentLength = connection.contentLength
                val contentType = connection.contentType

                connection.disconnect()

                val result = "URL测试结果:\n" +
                             "响应码: $responseCode\n" +
                             "内容类型: $contentType\n" +
                             "内容长度: $contentLength 字节\n" +
                             "URL: $videoUrl"

                Log.d(TAG, result)

                runOnUiThread {
                    showLoading(false)
                    AlertDialog.Builder(this@VideoPlayerActivity)
                        .setTitle("网络连接测试")
                        .setMessage(result)
                        .setPositiveButton("确定") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "URL连接测试失败: ${e.message}", e)
                runOnUiThread {
                    showLoading(false)
                    AlertDialog.Builder(this@VideoPlayerActivity)
                        .setTitle("网络连接测试失败")
                        .setMessage("无法连接到视频URL: ${e.message}\nURL: $videoUrl")
                        .setPositiveButton("确定") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            }
        }
    }

    /**
     * 显示模拟器网络配置指导
     */
    private fun showEmulatorNetworkGuide() {
        val networkGuide = """
            Android模拟器网络配置指南
            ========================

            您的Android模拟器无法访问网络，这通常是由于：

            1. **检查模拟器网络设置**
               - 在模拟器中打开"设置" > "网络和互联网"
               - 确保Wi-Fi或移动数据已启用
               - 尝试切换飞行模式再关闭

            2. **重启模拟器网络**
               - 关闭模拟器
               - 在Android Studio中: Tools > AVD Manager
               - 点击"Cold Boot Now"重启模拟器

            3. **修改模拟器网络配置**
               - 在AVD Manager中编辑模拟器
               - 点击"Show Advanced Settings"
               - 在"Network"部分选择"Native"
               - 或者尝试不同的网络配置

            4. **检查主机防火墙**
               - 确保Windows防火墙未阻止模拟器网络
               - 尝试暂时禁用防火墙测试

            5. **使用命令行启动模拟器**
               - 关闭当前模拟器
               - 打开命令提示符，运行:
                 emulator -avd <模拟器名称> -dns-server 8.8.8.8

            6. **测试网络连接**
               - 在模拟器中打开浏览器测试访问网页
               - 使用adb命令测试: adb shell ping 8.8.8.8

            请按上述步骤检查后重试播放。
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("模拟器网络问题")
            .setMessage(networkGuide)
            .setPositiveButton("复制指导") { dialog, _ ->
                dialog.dismiss()
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("模拟器网络指导", networkGuide)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "指导已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("测试网络连接") { dialog, _ ->
                dialog.dismiss()
                val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
                if (!videoUrl.isNullOrEmpty()) {
                    testVideoUrlConnection(videoUrl)
                }
            }
            .setNegativeButton("关闭") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        // 初始化视图
        initViews()

        // 获取Intent传递的数据
        videoUrlToPlay = intent.getStringExtra(EXTRA_VIDEO_URL)
        courseNameToShow = intent.getStringExtra(EXTRA_COURSE_NAME) ?: "课程视频"

        // 设置Activity标题为课程名称
        supportActionBar?.title = courseNameToShow

        if (videoUrlToPlay.isNullOrEmpty()) {
            // 视频URL为空，显示错误提示并返回
            showErrorMessage("视频地址无效，无法播放")
            finish()
            return
        }

        // 验证URL格式
        if (!isValidVideoUrl(videoUrlToPlay!!)) {
            showErrorMessage("视频URL格式不正确: $videoUrlToPlay")
            finish()
            return
        }

        // 获取课程ID和学生ID，初始化API服务
        courseId = intent.getIntExtra("course_id", -1)
        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        currentUserId = sharedPref.getInt("user_id", 0)
        apiService = ApiService.getInstance(this)

        // 打印调试信息
        Log.d(TAG, "准备播放视频，URL: $videoUrlToPlay")
        Log.d(TAG, "课程名称: $courseNameToShow")
        Log.d(TAG, "课程ID: $courseId, 用户ID: $currentUserId")

        // 如果是 OSS URL，需要先获取签名的播放地址
        // 本地 URL（http://10.0.2.2:3001/...）直接可播放，无需签名
        if (videoUrlToPlay!!.contains(".aliyuncs.com")) {
            Log.d(TAG, "检测到 OSS URL，获取签名播放地址...")
            isFetchingSignedUrl = true
            coroutineScope.launch {
                try {
                    val result = apiService.getVideoPlayUrl(videoUrlToPlay!!)
                    if (result.isSuccess) {
                        val signedUrl = result.getOrThrow()
                        Log.d(TAG, "签名播放地址获取成功，替换原始URL")
                        videoUrlToPlay = signedUrl
                    } else {
                        Log.w(TAG, "获取签名播放地址失败，使用原始URL: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "获取签名播放地址异常，使用原始URL: ${e.message}")
                } finally {
                    isFetchingSignedUrl = false
                }
                // 尝试设置视频播放（可能在surface准备好后）
                trySetupVideoPlayer()
            }
        } else {
            // 非 OSS URL，直接播放
            // 尝试设置视频播放（可能在surface准备好后）
            trySetupVideoPlayer()
        }
    }

    /**
     * 初始化视图
     */
    private fun initViews() {
        videoView = findViewById(R.id.videoView)
        loadingTextView = findViewById(R.id.loadingTextView)
        errorTextView = findViewById(R.id.errorTextView)
        hintTextView = findViewById(R.id.hintTextView)

        // 创建媒体控制器
        mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)

        // 设置视频视图使用媒体控制器
        videoView.setMediaController(mediaController)

        // 初始化按钮
        backButton = findViewById(R.id.backButton)
        speedButton = findViewById(R.id.speedButton)

        // 设置按钮点击监听器
        backButton.setOnClickListener {
            finish() // 退出播放界面
        }

        speedButton.setOnClickListener {
            showSpeedSelectionDialog()
        }

        // 设置SurfaceHolder回调，确保surface准备好后再设置数据源
        videoView.holder.addCallback(object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                Log.d(TAG, "Surface created, isSurfaceReady = true")
                isSurfaceReady = true
                trySetupVideoPlayer()
            }

            override fun surfaceChanged(
                holder: android.view.SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                Log.d(TAG, "Surface changed: $width x $height")
            }

            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                Log.d(TAG, "Surface destroyed, isSurfaceReady = false")
                isSurfaceReady = false
            }
        })
    }

    /**
     * 尝试设置视频播放器，确保surface准备好
     */
    private fun trySetupVideoPlayer() {
        if (videoUrlToPlay == null) {
            Log.w(TAG, "视频URL为空，无法设置播放器")
            return
        }

        if (!isSurfaceReady) {
            Log.d(TAG, "Surface尚未准备好，等待surfaceCreated回调")
            return
        }

        if (isDataSourceSet) {
            Log.d(TAG, "数据源已设置，跳过重复设置")
            return
        }

        // 正在获取签名地址，等完成后再设置播放器
        if (isFetchingSignedUrl) {
            Log.d(TAG, "正在获取签名播放地址，等待...")
            mainHandler.postDelayed({ trySetupVideoPlayer() }, 500)
            return
        }

        Log.d(TAG, "准备设置视频播放器，URL: $videoUrlToPlay")
        isDataSourceSet = true
        setupVideoPlayer(videoUrlToPlay!!)
    }

    /**
     * 设置视频播放器
     * @param videoUrl 视频URL地址
     */
    private fun setupVideoPlayer(videoUrl: String) {
        try {
            Log.d(TAG, "开始设置视频播放器，URL: $videoUrl")

            // 隐藏之前的错误状态，显示加载状态
            showError(false)
            showLoading(true)

            // 重置播放状态
            hasPlaybackStarted = false
            hasPlaybackError = false

            // 启动超时检查
            startPlaybackTimeout()

            // 创建URI对象
            val videoUri = Uri.parse(videoUrl)
            Log.d(TAG, "解析后的URI: $videoUri")

            // 设置错误监听器（先设置，以便捕获早期错误）
            videoView.setOnErrorListener { mediaPlayer, what, extra ->
                Log.e(TAG, "视频播放错误 - what: $what, extra: $extra")
                // 取消超时检查
                cancelPlaybackTimeout()
                hasPlaybackError = true

                // 隐藏加载状态，显示错误状态
                showLoading(false)
                showError(true, "视频播放失败")
                val errorMessage = when (what) {
                    android.media.MediaPlayer.MEDIA_ERROR_UNKNOWN -> "媒体播放器未知错误"
                    android.media.MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "媒体服务器进程终止"
                    android.media.MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> "视频不支持渐进式播放"
                    android.media.MediaPlayer.MEDIA_ERROR_IO -> "网络或文件I/O错误"
                    android.media.MediaPlayer.MEDIA_ERROR_MALFORMED -> "视频格式错误或损坏"
                    android.media.MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "不支持的视频格式或编码"
                    android.media.MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "网络连接超时"
                    -1004 -> "网络连接失败 (MEDIA_ERROR_IO)"
                    -1007 -> "视频格式不支持 (MEDIA_ERROR_UNSUPPORTED)"
                    else -> "播放错误代码: $what, 额外信息: $extra"
                }
                showErrorMessage("视频播放失败: $errorMessage")
                true // 表示错误已处理
            }

            // 设置信息监听器，获取更多调试信息
            videoView.setOnInfoListener { mediaPlayer, what, extra ->
                when (what) {
                    android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                        Log.d(TAG, "视频开始缓冲...")
                        // 显示缓冲提示
                        showLoading(true)
                    }
                    android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                        Log.d(TAG, "视频缓冲完成")
                        // 如果视频已准备完成，隐藏加载状态
                        if (mediaPlayer.isPlaying) {
                            showLoading(false)
                        }
                    }
                    android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                        Log.d(TAG, "视频开始渲染")
                        // 视频开始渲染，确保加载状态隐藏
                        showLoading(false)
                    }
                    android.media.MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING -> {
                        Log.w(TAG, "视频跟踪滞后，可能卡顿")
                    }
                    android.media.MediaPlayer.MEDIA_INFO_NOT_SEEKABLE -> {
                        Log.w(TAG, "视频不支持跳转 (MEDIA_INFO_NOT_SEEKABLE)")
                    }
                    android.media.MediaPlayer.MEDIA_INFO_METADATA_UPDATE -> {
                        Log.d(TAG, "视频元数据更新 (MEDIA_INFO_METADATA_UPDATE)")
                    }
                    android.media.MediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE -> {
                        Log.w(TAG, "不支持的字幕轨道 (MEDIA_INFO_UNSUPPORTED_SUBTITLE)")
                    }
                    android.media.MediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT -> {
                        Log.w(TAG, "字幕加载超时 (MEDIA_INFO_SUBTITLE_TIMED_OUT)")
                    }
                    else -> Log.d(TAG, "视频信息: what=$what, extra=$extra")
                }
                true
            }

            // 设置准备完成监听器
            videoView.setOnPreparedListener { mediaPlayer ->
                Log.d(TAG, "视频准备完成，开始播放")
                // 取消超时检查
                cancelPlaybackTimeout()
                hasPlaybackStarted = true
                mediaPlayerInstance = mediaPlayer // 保存MediaPlayer引用

                // 隐藏加载状态
                showLoading(false)
                Toast.makeText(this, "视频加载完成，开始播放", Toast.LENGTH_SHORT).show()

                // 自动开始播放
                videoView.start()

                // 记录视频点击事件（只在第一次播放时记录）
                recordVideoClick()

                // 开始定期记录学习进度
                startProgressTracking()

                // 显示视频信息（可选）
                val duration = mediaPlayer.duration
                val width = mediaPlayer.videoWidth
                val height = mediaPlayer.videoHeight
                Log.d(TAG, "视频信息 - 时长: ${duration}ms, 分辨率: ${width}x${height}")

                // 检查视频分辨率是否有效
                if (width <= 0 || height <= 0) {
                    Log.w(TAG, "警告：视频分辨率无效 (${width}x${height})，可能只有音频轨道")
                    runOnUiThread {
                        Toast.makeText(this@VideoPlayerActivity, "视频分辨率异常，可能无法显示画面", Toast.LENGTH_LONG).show()
                    }
                }

                // 应用当前播放速度
                applyPlaybackSpeed(mediaPlayer)
            }

            // 设置播放完成监听器
            videoView.setOnCompletionListener { mediaPlayer ->
                Log.d(TAG, "视频播放完成")
                // 确保超时已取消（可能已经取消）
                cancelPlaybackTimeout()
                stopProgressTracking()
                recordFinalProgress()
                Toast.makeText(this, "视频播放完成", Toast.LENGTH_SHORT).show()
            }

            // 设置视频数据源（这会触发加载）
            Log.d(TAG, "设置视频数据源...")

            // 检测是否运行在模拟器上
            val isEmulator = isRunningOnEmulator()
            Log.d(TAG, "运行环境检测: ${if (isEmulator) "模拟器" else "真实设备"}")

            // 记录URL详细信息（用于调试）
            Log.d(TAG, "视频URL详情 - URL: $videoUrl")
            Log.d(TAG, "视频URL详情 - 是否为OSS URL: ${OSSConfig.isCurrentOssUrl(videoUrl)}")
            Log.d(TAG, "视频URL详情 - OSS Bucket: ${OSSConfig.OSS_BUCKET_NAME}")
            Log.d(TAG, "视频URL详情 - OSS Endpoint: ${OSSConfig.OSS_ENDPOINT}")

            // 创建HTTP头信息（模拟浏览器请求，避免被服务器拒绝）
            val headers = HashMap<String, String>()
            headers["User-Agent"] = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
            headers["Accept"] = "video/mp4,video/webm,video/ogg,video/*;q=0.9,*/*;q=0.8"
            headers["Accept-Language"] = "zh-CN,zh;q=0.9,en;q=0.8"
            headers["Accept-Encoding"] = "identity"  // 不压缩视频
            headers["Connection"] = "keep-alive"
            headers["Range"] = "bytes=0-" // 支持范围请求

            // 如果是OSS URL，添加额外的头信息
            if (OSSConfig.isCurrentOssUrl(videoUrl)) {
                headers["Origin"] = "https://${OSSConfig.OSS_BUCKET_NAME}.${OSSConfig.OSS_ENDPOINT.removePrefix("https://").removePrefix("http://")}"
                headers["Referer"] = "https://${OSSConfig.OSS_BUCKET_NAME}.${OSSConfig.OSS_ENDPOINT.removePrefix("https://").removePrefix("http://")}"
                Log.d(TAG, "检测到OSS URL，添加Origin和Referer头")
            }

            // 方法1：优先使用setVideoURI（支持HTTP头信息，兼容性更好）
            Log.d(TAG, "方法1: 尝试使用setVideoURI，URI: $videoUri，头信息: $headers")
            try {
                videoView.setVideoURI(videoUri, headers)
                Log.d(TAG, "setVideoURI调用成功")
            } catch (e: Exception) {
                Log.e(TAG, "setVideoURI失败: ${e.message}", e)

                // 方法2：作为回退，尝试使用setVideoPath（某些设备可能支持）
                Log.d(TAG, "方法2: 尝试使用setVideoPath作为回退，URL: $videoUrl")
                try {
                    videoView.setVideoPath(videoUrl)
                    Log.d(TAG, "setVideoPath调用成功")
                } catch (e2: Exception) {
                    Log.e(TAG, "setVideoPath也失败: ${e2.message}", e2)

                    // 如果是模拟器，显示网络配置指导
                    if (isEmulator) {
                        Log.e(TAG, "模拟器上两种方法都失败，显示网络配置指导")
                        runOnUiThread {
                            showEmulatorNetworkGuide()
                        }
                    }

                    throw e2 // 重新抛出异常，让外部catch块处理
                }
            }

            // 显示加载提示
            Toast.makeText(this, "正在加载视频...", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            // 异常处理
            Log.e(TAG, "视频播放设置异常: ${e.message}", e)
            showErrorMessage("视频播放设置失败: ${e.message}")
        }
    }

    /**
     * 验证视频URL是否有效
     */
    private fun isValidVideoUrl(url: String): Boolean {
        return try {
            // 检查URL是否为空或空白
            if (url.isBlank()) return false

            // 检查是否以http或https开头
            val lowerUrl = url.lowercase()
            val isValidProtocol = lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://")

            // 检查是否包含视频文件扩展名（可选）
            val hasVideoExtension = lowerUrl.endsWith(".mp4") ||
                                   lowerUrl.endsWith(".avi") ||
                                   lowerUrl.endsWith(".mov") ||
                                   lowerUrl.endsWith(".mkv") ||
                                   lowerUrl.endsWith(".wmv") ||
                                   lowerUrl.contains(".mp4?") || // 带查询参数的URL
                                   lowerUrl.contains(".avi?") ||
                                   url.contains("video/") || // 可能是视频流
                                   url.contains("stream/")

            Log.d(TAG, "URL验证结果 - 协议有效: $isValidProtocol, 视频扩展名: $hasVideoExtension")
            isValidProtocol // 只检查协议，扩展名是可选的
        } catch (e: Exception) {
            Log.e(TAG, "URL验证异常: ${e.message}")
            false
        }
    }

    /**
     * 显示或隐藏加载状态
     * @param show true显示加载状态，false隐藏加载状态
     */
    private fun showLoading(show: Boolean) {
        runOnUiThread {
            loadingTextView.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
            hintTextView.visibility = if (show) android.view.View.GONE else android.view.View.VISIBLE
            // 加载时隐藏错误提示
            if (show) {
                errorTextView.visibility = android.view.View.GONE
            }
            Log.d(TAG, "加载状态: ${if (show) "显示" else "隐藏"}")
        }
    }

    /**
     * 显示或隐藏错误状态
     * @param show true显示错误状态，false隐藏错误状态
     * @param errorMessage 错误消息（可选）
     */
    private fun showError(show: Boolean, errorMessage: String? = null) {
        runOnUiThread {
            errorTextView.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
            if (show && errorMessage != null) {
                errorTextView.text = errorMessage
            }
            // 显示错误时隐藏加载提示
            if (show) {
                loadingTextView.visibility = android.view.View.GONE
                hintTextView.visibility = android.view.View.GONE
            } else {
                hintTextView.visibility = android.view.View.VISIBLE
            }
            Log.d(TAG, "错误状态: ${if (show) "显示 - $errorMessage" else "隐藏"}")
        }
    }

    /**
     * 记录视频点击事件
     * 当用户开始播放视频时调用，记录一次点击
     */
    private fun recordVideoClick() {
        if (hasRecordedClick) {
            Log.d(TAG, "点击已记录，跳过重复记录")
            return
        }

        if (courseId <= 0 || currentUserId <= 0) {
            Log.w(TAG, "课程ID或用户ID无效，无法记录点击: courseId=$courseId, userId=$currentUserId")
            return
        }

        hasRecordedClick = true
        Log.d(TAG, "记录视频点击: 课程ID=$courseId, 用户ID=$currentUserId")

        coroutineScope.launch {
            try {
                val result = apiService.recordStudyProgress(
                    courseId = courseId,
                    studentId = currentUserId,
                    clickCount = 1 // 记录一次点击
                )

                if (result.isSuccess) {
                    val response = result.getOrThrow()
                    if (response.success) {
                        Log.d(TAG, "点击记录成功: ${response.message}")
                    } else {
                        Log.e(TAG, "点击记录失败: ${response.message}")
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "点击记录API调用失败: ${error?.message}", error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "点击记录异常: ${e.message}", e)
            }
        }
    }

    /**
     * 开始定期记录学习进度
     */
    private fun startProgressTracking() {
        stopProgressTracking()
        lastProgressRecordMs = System.currentTimeMillis()
        progressRunnable = Runnable {
            val player = videoView ?: return@Runnable
            val mediaPlayer = mediaPlayerInstance ?: return@Runnable
            if (!mediaPlayer.isPlaying) {
                mainHandler.postDelayed(progressRunnable!!, PROGRESS_RECORD_INTERVAL_MS)
                return@Runnable
            }
            val duration = mediaPlayer.duration
            val currentPosition = mediaPlayer.currentPosition
            if (duration > 0) {
                val progress = (currentPosition * 100 / duration).toInt()
                val now = System.currentTimeMillis()
                totalWatchTimeMs += (now - lastProgressRecordMs)
                lastProgressRecordMs = now
                val watchTimeMinutes = (totalWatchTimeMs / 60000).toInt()
                Log.d(TAG, "记录进度: courseId=$courseId, progress=$progress%, watchTime=${watchTimeMinutes}min")
                coroutineScope.launch {
                    try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            apiService.recordStudyProgress(
                                courseId = courseId,
                                studentId = currentUserId,
                                watchTime = watchTimeMinutes,
                                progress = progress
                            )
                        }
                    } catch (_: Exception) { }
                }
            }
            mainHandler.postDelayed(progressRunnable!!, PROGRESS_RECORD_INTERVAL_MS)
        }
        mainHandler.postDelayed(progressRunnable!!, PROGRESS_RECORD_INTERVAL_MS)
    }

    /**
     * 停止定期记录学习进度
     */
    private fun stopProgressTracking() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    /**
     * 记录最终完成进度（100%）
     */
    private fun recordFinalProgress() {
        val watchTimeMinutes = (totalWatchTimeMs / 60000).toInt()
        Log.d(TAG, "记录完成进度: courseId=$courseId, progress=100%, watchTime=${watchTimeMinutes}min")
        coroutineScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    apiService.recordStudyProgress(
                        courseId = courseId,
                        studentId = currentUserId,
                        watchTime = watchTimeMinutes,
                        progress = 100
                    )
                }
            } catch (_: Exception) { }
        }
    }

    /**
     * 显示错误消息
     */
    private fun showErrorMessage(message: String) {
        Log.e(TAG, "显示错误消息: $message")
        // 标记播放错误
        hasPlaybackError = true
        // 显示错误状态
        showError(true, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // 创建选项列表
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        // 总是有重试选项
        options.add("重试播放")
        actions.add {
            // 隐藏错误状态
            showError(false)
            // 重置播放状态
            hasPlaybackError = false
            hasPlaybackStarted = false
            // 重新尝试播放
            val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
            if (!videoUrl.isNullOrEmpty()) {
                setupVideoPlayer(videoUrl)
            }
        }

        // 如果是模拟器或者错误与网络相关，添加网络测试选项
        if (isRunningOnEmulator() || message.contains("网络") || message.contains("连接") || message.contains("超时")) {
            options.add("测试网络连接")
            actions.add {
                val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
                if (!videoUrl.isNullOrEmpty()) {
                    testVideoUrlConnection(videoUrl)
                }
            }
        }

        // 添加在浏览器中打开的选项
        options.add("在浏览器中打开视频")
        actions.add {
            val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
            if (!videoUrl.isNullOrEmpty()) {
                openVideoInBrowser(videoUrl)
            }
        }

        // 添加关闭选项
        options.add("关闭并返回")
        actions.add {
            finish()
        }

        // 显示选项对话框
        AlertDialog.Builder(this)
            .setTitle("播放错误")
            .setMessage(message)
            .setItems(options.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                if (which >= 0 && which < actions.size) {
                    actions[which]()
                }
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 当Activity恢复时，继续视频播放（如果需要）
     */
    override fun onResume() {
        super.onResume()
        // 如果视频已暂停，可以在这里恢复播放
        // videoView.resume()
    }

    /**
     * 当Activity暂停时，暂停视频播放
     */
    override fun onPause() {
        super.onPause()
        // 暂停视频播放
        videoView.pause()
    }

    /**
     * 启动播放超时检查
     */
    private fun startPlaybackTimeout() {
        Log.d(TAG, "启动播放超时检查 (${PLAYBACK_TIMEOUT_MS}ms)")
        cancelPlaybackTimeout() // 先取消之前的超时

        timeoutRunnable = Runnable {
            if (!hasPlaybackStarted && !hasPlaybackError) {
                Log.e(TAG, "播放超时 (${PLAYBACK_TIMEOUT_MS}ms)，未开始播放")
                showErrorMessage("视频加载超时，请检查网络连接或重试")
                hasPlaybackError = true
            }
        }

        mainHandler.postDelayed(timeoutRunnable!!, PLAYBACK_TIMEOUT_MS)
    }

    /**
     * 取消播放超时检查
     */
    private fun cancelPlaybackTimeout() {
        timeoutRunnable?.let {
            Log.d(TAG, "取消播放超时检查")
            mainHandler.removeCallbacks(it)
            timeoutRunnable = null
        }
    }

    /**
     * 显示播放速度选择对话框
     */
    private fun showSpeedSelectionDialog() {
        // 创建速度选项数组，用于对话框显示
        val speedOptions = arrayOf(
            "0.5x (慢速)",
            "0.75x (较慢)",
            "1.0x (正常)",
            "1.25x (较快)",
            "1.5x (快速)",
            "2.0x (倍速)"
        )

        // 确定当前选中项的索引
        val currentIndex = availableSpeeds.indexOf(currentPlaybackSpeed).takeIf { it >= 0 } ?: 2

        AlertDialog.Builder(this)
            .setTitle("选择播放速度")
            .setSingleChoiceItems(speedOptions, currentIndex) { dialog, which ->
                // 用户选择的速度
                val selectedSpeed = availableSpeeds[which]
                if (selectedSpeed != currentPlaybackSpeed) {
                    currentPlaybackSpeed = selectedSpeed
                    applyPlaybackSpeed()
                    Toast.makeText(this, "已设置为 ${selectedSpeed}x 播放速度", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 应用当前播放速度到视频播放器
     * @param mediaPlayer 可选的MediaPlayer实例，如果为null则尝试通过反射获取
     */
    private fun applyPlaybackSpeed(mediaPlayer: MediaPlayer? = null) {
        if (!hasPlaybackStarted) {
            Log.d(TAG, "视频尚未开始播放，稍后在准备完成时应用速度: ${currentPlaybackSpeed}x")
            return
        }

        try {
            // 对于VideoView，我们需要通过反射获取内部的MediaPlayer来设置播放速度
            // 注意：setPlaybackParams需要API 23+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val playerToUse = mediaPlayer ?: mediaPlayerInstance ?: run {
                    // 尝试通过反射获取VideoView的MediaPlayer实例
                    val mediaPlayerField = VideoView::class.java.getDeclaredField("mMediaPlayer")
                    mediaPlayerField.isAccessible = true
                    mediaPlayerField.get(videoView) as? MediaPlayer
                }

                if (playerToUse != null) {
                    // 创建PlaybackParams并设置速度
                    val playbackParams = playerToUse.playbackParams
                    playbackParams.speed = currentPlaybackSpeed
                    playerToUse.playbackParams = playbackParams
                    Log.d(TAG, "播放速度已设置为: ${currentPlaybackSpeed}x")
                } else {
                    Log.w(TAG, "无法获取MediaPlayer实例，播放速度设置失败")
                    Toast.makeText(this, "无法设置播放速度: 播放器未就绪", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "您的设备不支持调整播放速度", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "API版本低于23，不支持setPlaybackParams")
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置播放速度失败: ${e.message}", e)
            Toast.makeText(this, "设置播放速度失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 当Activity销毁时，释放视频资源
     */
    override fun onDestroy() {
        super.onDestroy()
        // 停止进度追踪
        stopProgressTracking()
        // 取消超时检查
        cancelPlaybackTimeout()
        // 清理MediaPlayer引用
        mediaPlayerInstance = null
        // 释放视频资源
        videoView.stopPlayback()
    }
}