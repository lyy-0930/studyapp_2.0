package com.studyapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class ProfileActivity : AppCompatActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var logoutButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // 初始化视图
        initViews()

        // 设置事件监听器
        setupEventListeners()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        logoutButton = findViewById(R.id.logoutButton)
    }

    private fun setupEventListeners() {
        // 返回按钮点击事件
        backButton.setOnClickListener {
            finish()
        }

        // 退出登录按钮点击事件
        logoutButton.setOnClickListener {
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        // 清除返回栈，防止按返回键回到登录页面
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
}
