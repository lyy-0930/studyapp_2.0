package com.studyapp

import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.adapter.OnlineUserAdapter
import com.studyapp.manager.AdminDataManager
import com.studyapp.manager.ApiService
import com.studyapp.model.User
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 实时在线人数Activity
 */
class OnlineUsersActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var refreshButton: TextView
    private lateinit var titleTextView: TextView
    private lateinit var onlineCountTextView: TextView
    private lateinit var totalUsersTextView: TextView
    private lateinit var onlineRateTextView: TextView
    private lateinit var lastUpdateTextView: TextView
    private lateinit var onlineUsersRecyclerView: RecyclerView

    private lateinit var onlineUserAdapter: OnlineUserAdapter
    private var users: List<User> = emptyList()
    private var onlineUsers: List<User> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online_users)

        // 初始化视图
        initViews()

        // 生成模拟数据
        generateAndDisplayData()

        // 设置返回按钮
        backButton.setOnClickListener {
            finish()
        }

        // 设置刷新按钮
        refreshButton.setOnClickListener {
            refreshData()
        }
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        refreshButton = findViewById(R.id.refreshButton)
        titleTextView = findViewById(R.id.titleTextView)
        onlineCountTextView = findViewById(R.id.onlineCountTextView)
        totalUsersTextView = findViewById(R.id.totalUsersTextView)
        onlineRateTextView = findViewById(R.id.onlineRateTextView)
        lastUpdateTextView = findViewById(R.id.lastUpdateTextView)
        onlineUsersRecyclerView = findViewById(R.id.onlineUsersRecyclerView)

        // 设置RecyclerView
        onlineUserAdapter = OnlineUserAdapter()
        onlineUsersRecyclerView.layoutManager = LinearLayoutManager(this)
        onlineUsersRecyclerView.adapter = onlineUserAdapter
    }

    /**
     * 生成并显示数据（从API获取真实数据）
     */
    private fun generateAndDisplayData() {
        lifecycleScope.launch {
            try {
                val apiService = ApiService.getInstance(this@OnlineUsersActivity)
                val result = apiService.getOnlineUsers()

                if (result.isSuccess) {
                    val onlineUsersResponse = result.getOrNull()
                    if (onlineUsersResponse != null) {
                        val stats = onlineUsersResponse.stats
                        val apiOnlineUsers = onlineUsersResponse.onlineUsers

                        // 转换为User对象列表
                        val userList = convertToUserList(apiOnlineUsers)
                        onlineUsers = userList
                        users = userList // 注意：这里只显示在线用户，但总用户数从stats获取

                        // 更新统计UI
                        onlineCountTextView.text = stats.onlineCount.toString()
                        totalUsersTextView.text = stats.totalUsers.toString()
                        onlineRateTextView.text = String.format("%.1f%%", stats.onlineRate)

                        // 更新最后刷新时间
                        updateLastUpdateTime()

                        // 更新在线用户列表
                        onlineUserAdapter.submitList(onlineUsers)

                        // 显示成功提示（可选）
                        // android.widget.Toast.makeText(this@OnlineUsersActivity, "数据加载成功", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        showError("数据解析失败")
                    }
                } else {
                    val error = result.exceptionOrNull()
                    showError("加载失败: ${error?.message ?: "未知错误"}")
                    // 如果API失败，可以回退到模拟数据（仅用于演示）
                    fallbackToMockData()
                }
            } catch (e: Exception) {
                Log.e("OnlineUsers", "网络请求异常", e)
                showError("网络请求异常: ${e.message}")
                fallbackToMockData()
            }
        }
    }

    /**
     * 将ApiService.OnlineUser转换为User对象
     */
    private fun convertToUserList(apiUsers: List<ApiService.OnlineUser>): List<User> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        return apiUsers.map { apiUser ->
            try {
                val lastLoginDate = dateFormat.parse(apiUser.lastActiveAt) ?: Date()
                User(
                    id = apiUser.id,
                    username = apiUser.username,
                    role = apiUser.role,
                    email = "${apiUser.username}@example.com", // 模拟邮箱
                    registrationDate = Date(), // 模拟注册日期
                    lastLoginDate = lastLoginDate,
                    isOnline = apiUser.isOnline,
                    totalStudyTime = 0.0,
                    loginCount = 0,
                    courseCompletionRate = 0.0
                )
            } catch (e: Exception) {
                // 解析失败使用当前时间
                User(
                    id = apiUser.id,
                    username = apiUser.username,
                    role = apiUser.role,
                    email = "${apiUser.username}@example.com",
                    registrationDate = Date(),
                    lastLoginDate = Date(),
                    isOnline = apiUser.isOnline,
                    totalStudyTime = 0.0,
                    loginCount = 0,
                    courseCompletionRate = 0.0
                )
            }
        }
    }

    /**
     * 显示错误提示
     */
    private fun showError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
        // 也可以更新UI显示错误状态
    }

    /**
     * 回退到模拟数据（当API失败时）
     */
    private fun fallbackToMockData() {
        // 生成模拟用户数据
        users = AdminDataManager.generateUsers()
        // 计算在线用户
        onlineUsers = AdminDataManager.getOnlineUsers(users)
        val onlineCount = onlineUsers.size
        val totalCount = users.size
        val onlineRate = if (totalCount > 0) (onlineCount.toDouble() / totalCount * 100.0) else 0.0

        // 更新统计UI
        onlineCountTextView.text = onlineCount.toString()
        totalUsersTextView.text = totalCount.toString()
        onlineRateTextView.text = String.format("%.1f%%", onlineRate)

        // 更新最后刷新时间
        updateLastUpdateTime()

        // 更新在线用户列表
        onlineUserAdapter.submitList(onlineUsers)

        // 提示使用模拟数据
        android.widget.Toast.makeText(this, "使用模拟数据", android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * 刷新数据
     */
    private fun refreshData() {
        // 从服务器获取最新数据
        generateAndDisplayData()
        // 显示刷新提示
        android.widget.Toast.makeText(this, "数据已刷新", android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * 更新最后刷新时间
     */
    private fun updateLastUpdateTime() {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
        val currentTime = formatter.format(Date())
        lastUpdateTextView.text = "最后更新：$currentTime"
    }

}