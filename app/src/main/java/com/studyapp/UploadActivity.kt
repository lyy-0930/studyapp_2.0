@file:Suppress("DEPRECATION")

package com.studyapp

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.studyapp.manager.ApiService
import com.studyapp.manager.OSSConfig
import com.studyapp.manager.VideoStorageManager
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class UploadActivity : AppCompatActivity() {

    // 视图组件
    private lateinit var backButton: ImageButton
    private lateinit var selectFileButton: Button
    private lateinit var selectedFileNameTextView: TextView
    private lateinit var uploadButton: Button
    private lateinit var uploadStatusTextView: TextView
    private lateinit var urlTextView: TextView

    // 选中的视频文件 Uri 和文件名
    private var selectedVideoUri: Uri? = null
    private var selectedVideoName: String = ""

    // 请求码
    private val requestCodeSelectVideo = 1001
    private val requestCodeStoragePermission = 1002

    companion object {
        private const val TAG = "UploadActivity"
    }

    /**
     * 获取所需的存储权限数组
     * Android 13+ (API 33) 需要 READ_MEDIA_VIDEO
     * Android 12- 需要 READ_EXTERNAL_STORAGE
     */
    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * 是否需要检查存储权限
     * Android 10+ (API 29+) 使用 ACTION_GET_CONTENT 不需要存储权限
     * Android 9- (API 28-) 可能需要 READ_EXTERNAL_STORAGE 权限
     */
    private fun shouldCheckStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    }

    /**
     * 检查存储权限是否已授予
     */
    private fun checkStoragePermission(): Boolean {
        if (!shouldCheckStoragePermission()) {
            Log.d(TAG, "Android ${Build.VERSION.SDK_INT} 不需要检查存储权限，直接返回true")
            return true
        }

        val permissions = getRequiredPermissions()
        val allGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        Log.d(TAG, "检查存储权限: ${permissions.joinToString()}")
        Log.d(TAG, "权限状态: ${if (allGranted) "已授予" else "未授予"}")
        Log.d(TAG, "Android版本: ${Build.VERSION.SDK_INT} (API级别)")

        permissions.forEach { permission ->
            val status = ContextCompat.checkSelfPermission(this, permission)
            Log.d(TAG, "权限 $permission: ${if (status == PackageManager.PERMISSION_GRANTED) "已授予" else "未授予"}")
        }

        return allGranted
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)

        // 初始化视图
        initViews()

        // 设置事件监听器
        setupEventListeners()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        selectFileButton = findViewById(R.id.selectFileButton)
        selectedFileNameTextView = findViewById(R.id.selectedFileNameTextView)
        uploadButton = findViewById(R.id.uploadButton)
        uploadStatusTextView = findViewById(R.id.uploadStatusTextView)
        urlTextView = findViewById(R.id.urlTextView)

        // 初始状态
        selectedFileNameTextView.visibility = TextView.GONE
        uploadButton.visibility = TextView.GONE
        uploadStatusTextView.text = ""
        urlTextView.text = ""
    }

    private fun setupEventListeners() {
        // 返回按钮点击事件
        backButton.setOnClickListener {
            finish()
        }

        // 选择文件按钮点击事件
        selectFileButton.setOnClickListener {
            openVideoPicker()
        }

        // 上传按钮点击事件
        uploadButton.setOnClickListener {
            selectedVideoUri?.let { uri ->
                uploadVideoToOSS(uri)
            } ?: run {
                Toast.makeText(this, "请先选择视频文件", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 打开视频选择器
     */
    private fun openVideoPicker() {
        Log.d(TAG, "用户点击选择文件按钮")
        Log.d(TAG, "Android版本: ${Build.VERSION.SDK_INT} (API级别)")

        Log.d(TAG, "直接打开视频选择器（跳过权限检查）")
        launchVideoPicker()
    }

    /**
     * 启动视频选择器
     */
    @SuppressLint("Deprecation")
    private fun launchVideoPicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
            }

            Log.d(TAG, "启动文件选择器: ACTION_OPEN_DOCUMENT")
            startActivityForResult(intent, requestCodeSelectVideo)
        } catch (e: Exception) {
            Log.e(TAG, "启动文件选择器失败: ${e.message}", e)
            Toast.makeText(this, "无法打开文件选择器: ${e.message}", Toast.LENGTH_LONG).show()

            try {
                val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "video/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                Log.d(TAG, "尝试备用方案: ACTION_GET_CONTENT")
                startActivityForResult(fallbackIntent, requestCodeSelectVideo)
            } catch (e2: Exception) {
                Log.e(TAG, "备用方案也失败: ${e2.message}", e2)
                Toast.makeText(this, "无法选择文件，请检查设备是否支持文件选择", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 请求存储权限
     */
    private fun requestStoragePermission() {
        val permissions = getRequiredPermissions()
        Log.d(TAG, "开始请求权限: ${permissions.joinToString()}")

        val shouldShowRationale = permissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }

        Log.d(TAG, "是否需要显示权限解释: $shouldShowRationale")

        if (shouldShowRationale) {
            Toast.makeText(this, "需要存储权限来访问视频文件，请允许权限", Toast.LENGTH_LONG).show()
            Log.d(TAG, "已显示权限解释提示")
        }

        Log.d(TAG, "调用ActivityCompat.requestPermissions")
        ActivityCompat.requestPermissions(this, permissions, requestCodeStoragePermission)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d(TAG, "onRequestPermissionsResult: requestCode=$requestCode")

        when (requestCode) {
            requestCodeStoragePermission -> {
                val allGranted = grantResults.all { result -> result == PackageManager.PERMISSION_GRANTED }
                Log.d(TAG, "所有权限是否已授予: $allGranted")

                if (allGranted) {
                    Log.d(TAG, "权限已授予，打开视频选择器")
                    launchVideoPicker()
                } else {
                    Log.d(TAG, "权限被拒绝，但仍尝试打开文件选择器")
                    Toast.makeText(this, "权限被拒绝，尝试打开文件选择器...", Toast.LENGTH_SHORT).show()

                    selectFileButton.postDelayed({
                        try {
                            launchVideoPicker()
                        } catch (e: Exception) {
                            Log.e(TAG, "权限被拒绝后尝试打开文件选择器失败: ${e.message}", e)
                            Toast.makeText(this, "无法打开文件选择器，请允许存储权限", Toast.LENGTH_LONG).show()
                        }
                    }, 500)

                    val permanentlyDenied = permissions.indices.any { index ->
                        grantResults[index] == PackageManager.PERMISSION_DENIED &&
                                !ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[index])
                    }

                    if (permanentlyDenied) {
                        Toast.makeText(this, "请在系统设置中手动打开存储权限", Toast.LENGTH_LONG).show()
                        Log.d(TAG, "已提示用户去设置中手动打开权限")
                    }
                }
            }
        }
    }

    @SuppressLint("Deprecation")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == requestCodeSelectVideo && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                selectedVideoUri = uri
                selectedVideoName = getFileNameFromUri(uri) ?: "unknown_video.mp4"
                updateFileSelectionUI()
            }
        }
    }

    /**
     * 从 Uri 获取文件名
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取文件名失败", e)
            null
        }
    }

    /**
     * 更新文件选择后的 UI
     */
    @SuppressLint("SetTextI18n")
    private fun updateFileSelectionUI() {
        selectedFileNameTextView.text = "已选择: $selectedVideoName"
        selectedFileNameTextView.visibility = TextView.VISIBLE
        uploadButton.visibility = TextView.VISIBLE
        uploadStatusTextView.text = "准备上传..."
        urlTextView.text = ""
    }

    /**
     * 上传视频到后端服务器（本地模式）
     * 直接流式 POST 原始文件字节到 /upload/video/raw，不走 multipart
     * 使用 8KB 缓冲区流式读写，防止大文件 OOM
     */
    private fun uploadVideoToOSS(videoUri: Uri) {
        uploadStatusTextView.text = "准备上传..."
        uploadButton.isEnabled = false

        Thread {
            try {
                runOnUiThread { uploadStatusTextView.text = "上传中..." }

                val url = URL("${ApiService.BASE_URL}/upload/video/raw")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "video/mp4")
                conn.setRequestProperty("X-Filename", selectedVideoName)
                conn.setChunkedStreamingMode(0)
                conn.connectTimeout = 30 * 60 * 1000
                conn.readTimeout = 30 * 60 * 1000
                ApiService.addAuthToConnection(conn, this@UploadActivity)

                try {
                    val out = conn.outputStream
                    val inputStream = contentResolver.openInputStream(videoUri)
                        ?: throw Exception("无法读取文件")
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    try {
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                        }
                    } finally {
                        inputStream.close()
                    }
                    out.flush()
                    out.close()

                    val responseCode = conn.responseCode
                    if (responseCode in 200..299) {
                        val response = conn.inputStream.bufferedReader().readText()
                        val json = JSONObject(response)
                        val data = json.optJSONObject("data")
                        val fileUrl = data?.optString("fileUrl", "") ?: ""

                        if (fileUrl.isNotEmpty()) {
                            VideoStorageManager.saveVideo(
                                context = this@UploadActivity,
                                videoUrl = fileUrl,
                                videoTitle = selectedVideoName,
                                videoDescription = "",
                                teacher = "教师"
                            )
                            val finalUrl = fileUrl
                            runOnUiThread {
                                uploadStatusTextView.text = "上传成功！"
                                urlTextView.text = finalUrl
                                uploadButton.isEnabled = true
                                Toast.makeText(this@UploadActivity, "视频上传成功", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            throw Exception("服务器未返回视频地址")
                        }
                    } else {
                        runOnUiThread {
                            uploadStatusTextView.text = "上传失败 (HTTP $responseCode)"
                            uploadButton.isEnabled = true
                            Toast.makeText(this@UploadActivity, "上传失败: HTTP $responseCode", Toast.LENGTH_LONG).show()
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                val errMsg = "上传失败: ${e.message}"
                Log.e(TAG, errMsg, e)
                runOnUiThread {
                    uploadStatusTextView.text = errMsg
                    uploadButton.isEnabled = true
                    Toast.makeText(this@UploadActivity, errMsg, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
