package com.studyapp

import android.content.Context
import android.content.DialogInterface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.adapter.UserAdapter
import com.studyapp.manager.ApiService
import com.studyapp.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 用户管理界面
 * 管理员可以查看所有用户，并删除用户（不能删除当前登录用户）
 */
class UserManagementActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var titleTextView: TextView
    private lateinit var totalUsersTextView: TextView
    private lateinit var teacherCountTextView: TextView
    private lateinit var studentCountTextView: TextView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var userRecyclerView: RecyclerView
    private lateinit var userListTitle: TextView

    private lateinit var apiService: ApiService
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var userAdapter: UserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_management)

        // 初始化API服务
        apiService = ApiService.getInstance(this)

        // 初始化视图
        initViews()

        // 设置按钮点击事件
        setupButtonListeners()

        // 加载用户数据
        loadUsers()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        refreshButton = findViewById(R.id.refreshButton)
        titleTextView = findViewById(R.id.titleTextView)
        totalUsersTextView = findViewById(R.id.totalUsersTextView)
        teacherCountTextView = findViewById(R.id.teacherCountTextView)
        studentCountTextView = findViewById(R.id.studentCountTextView)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        userRecyclerView = findViewById(R.id.userRecyclerView)
        userListTitle = findViewById(R.id.userListTitle)

        // 设置RecyclerView
        userRecyclerView.layoutManager = LinearLayoutManager(this)
        userAdapter = UserAdapter()
        userAdapter.setOnDeleteClickListener(object : UserAdapter.OnDeleteClickListener {
            override fun onDeleteClick(user: User, position: Int) {
                showDeleteConfirmationDialog(user, position)
            }
        })
        userRecyclerView.adapter = userAdapter
    }

    private fun setupButtonListeners() {
        // 返回按钮
        backButton.setOnClickListener {
            finish()
        }

        // 刷新按钮
        refreshButton.setOnClickListener {
            loadUsers()
        }
    }

    /**
     * 检查网络连接
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo != null && networkInfo.isConnected
        }
    }

    /**
     * 加载用户数据
     */
    private fun loadUsers() {
        // 显示加载状态
        refreshButton.isEnabled = false
        emptyStateLayout.visibility = LinearLayout.GONE
        userRecyclerView.visibility = RecyclerView.VISIBLE

        // 检查网络连接
        if (!isNetworkAvailable()) {
            showToast("网络连接不可用，请检查网络设置")
            refreshButton.isEnabled = true
            showEmptyState()
            return
        }

        coroutineScope.launch {
            try {
                Log.d("UserManagement", "开始获取用户列表...")
                Log.d("UserManagement", "API服务实例: $apiService")

                // 先测试API连接
                Log.d("UserManagement", "测试API连接...")
                val connectionResult = withContext(Dispatchers.IO) {
                    apiService.testConnection()
                }
                Log.d("UserManagement", "API连接测试结果: $connectionResult")

                if (!connectionResult) {
                    withContext(Dispatchers.Main) {
                        showToast("无法连接到后端服务器，请确保服务正在运行 (http://10.0.2.2:3001)")
                        showEmptyState()
                        refreshButton.isEnabled = true
                    }
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    Log.d("UserManagement", "在IO线程中调用getAllUsers()...")
                    apiService.getAllUsers()
                }
                Log.d("UserManagement", "getAllUsers()调用完成，结果: ${if (result.isSuccess) "成功" else "失败"}")

                if (result.isSuccess) {
                    val adminUsers = result.getOrThrow()
                    Log.d("UserManagement", "获取到 ${adminUsers.size} 个用户")
                    // 将AdminUser转换为User模型
                    val users = adminUsers.map { adminUser ->
                        User(
                            id = adminUser.id,
                            username = adminUser.username,
                            role = adminUser.role,
                            email = "", // API未返回邮箱
                            registrationDate = parseDate(adminUser.createdAt),
                            lastLoginDate = Date(), // 默认当前时间
                            isOnline = false,
                            totalStudyTime = 0.0,
                            loginCount = 0,
                            courseCompletionRate = 0.0
                        )
                    }
                    Log.d("UserManagement", "转换完成，得到 ${users.size} 个User对象")


                    // 更新UI
                    withContext(Dispatchers.Main) {
                        updateUserList(users)
                        updateStatistics(users)
                        Log.d("UserManagement", "用户列表更新完成")
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("UserManagement", "获取用户列表失败: ${error?.message}", error)
                    // 打印完整的异常堆栈
                    error?.printStackTrace()

                    val errorMessage = when {
                        error?.message?.contains("Failed to connect") == true ->
                            "无法连接到服务器，请确保后端服务正在运行 (http://10.0.2.2:3001)"
                        error?.message?.contains("timeout") == true ->
                            "连接超时，请检查网络连接"
                        error?.message?.contains("404") == true ->
                            "API接口不存在，请检查后端服务版本"
                        error?.message?.contains("500") == true ->
                            "服务器内部错误，请检查后端日志"
                        else -> "获取用户列表失败: ${error?.message ?: "未知错误"}"
                    }

                    Log.d("UserManagement", "错误消息: $errorMessage")
                    withContext(Dispatchers.Main) {
                        showToast(errorMessage)
                        showEmptyState()
                    }
                }
            } catch (e: Exception) {
                Log.e("UserManagement", "加载用户异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val errorMsg = if (e.message?.contains("Network is unreachable") == true) {
                        "网络不可达，请检查服务器地址和后端服务状态"
                    } else {
                        "加载用户失败: ${e.message}"
                    }
                    showToast(errorMsg)
                    showEmptyState()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    refreshButton.isEnabled = true
                }
            }
        }
    }

    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmationDialog(user: User, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("删除用户")
            .setMessage("确定要删除用户 \"${user.username}\" 吗？此操作不可恢复。")
            .setPositiveButton("删除") { dialog, which ->
                deleteUser(user.id, position)
            }
            .setNegativeButton("取消") { dialog, which ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    /**
     * 删除用户
     */
    private fun deleteUser(userId: Int, position: Int) {
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    apiService.deleteUser(userId)
                }

                if (result.isSuccess) {
                    withContext(Dispatchers.Main) {
                        showToast("用户删除成功")
                        // 重新加载用户列表
                        loadUsers()
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("UserManagement", "删除用户失败: ${error?.message}", error)
                    withContext(Dispatchers.Main) {
                        showToast("删除用户失败: ${error?.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("UserManagement", "删除用户异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showToast("删除用户失败")
                }
            }
        }
    }

    /**
     * 更新用户列表UI
     */
    private fun updateUserList(users: List<User>) {
        userAdapter.updateData(users)

        // 显示/隐藏空状态和用户列表
        if (users.isEmpty()) {
            emptyStateLayout.visibility = LinearLayout.VISIBLE
            userRecyclerView.visibility = RecyclerView.GONE
        } else {
            emptyStateLayout.visibility = LinearLayout.GONE
            userRecyclerView.visibility = RecyclerView.VISIBLE
        }
    }

    /**
     * 更新统计信息
     */
    private fun updateStatistics(users: List<User>) {
        val totalUsers = users.size
        val teacherCount = users.count { it.role.lowercase() == "teacher" }
        val studentCount = users.count { it.role.lowercase() == "student" }

        totalUsersTextView.text = totalUsers.toString()
        teacherCountTextView.text = teacherCount.toString()
        studentCountTextView.text = studentCount.toString()
    }

    /**
     * 显示空状态
     */
    private fun showEmptyState() {
        emptyStateLayout.visibility = LinearLayout.VISIBLE
        userRecyclerView.visibility = RecyclerView.GONE
    }

    /**
     * 解析日期字符串
     */
    private fun parseDate(dateString: String?): Date {
        if (dateString.isNullOrEmpty()) {
            return Date() // 空字符串或null返回当前日期
        }
        return try {
            // 尝试解析ISO格式日期
            val formats = listOf(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA),
                SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            )
            for (format in formats) {
                try {
                    return format.parse(dateString) ?: Date()
                } catch (e: Exception) {
                    continue
                }
            }
            Date() // 解析失败返回当前日期
        } catch (e: Exception) {
            Date()
        }
    }

    /**
     * 显示Toast消息
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}