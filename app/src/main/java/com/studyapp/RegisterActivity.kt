package com.studyapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.studyapp.manager.ApiService
import com.studyapp.model.UserData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 注册页面Activity
 * 功能：用户注册，支持student/teacher/admin三种角色，包含姓名/生日/密保问题
 */
class RegisterActivity : AppCompatActivity() {

    // 协程作用域
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // API服务实例
    private lateinit var apiService: ApiService

    // 视图组件
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var fullNameEditText: EditText
    private lateinit var birthdayEditText: EditText
    private lateinit var securityQuestionText: TextView
    private lateinit var refreshSecurityQuestionBtn: TextView
    private lateinit var securityAnswerEditText: EditText
    private lateinit var roleRadioGroup: RadioGroup
    private lateinit var studentRadioButton: RadioButton
    private lateinit var teacherRadioButton: RadioButton
    private lateinit var adminRadioButton: RadioButton
    private lateinit var adminCodeContainer: androidx.constraintlayout.widget.ConstraintLayout
    private lateinit var adminCodeEditText: EditText
    private lateinit var registerButton: Button
    private lateinit var loginLink: TextView

    // 密保问题列表
    private val securityQuestions = listOf(
        "你的父亲的名字是什么？",
        "你的母亲的名字是什么？",
        "你最喜欢的学科是什么？",
        "你最喜欢的运动是什么？"
    )

    private var selectedSecurityQuestion: String = securityQuestions[0]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("RegisterActivity", "onCreate started")
        setContentView(R.layout.activity_register)

        apiService = ApiService.getInstance(this)
        initViews()
        setupEventListeners()
    }

    private fun initViews() {
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText)
        fullNameEditText = findViewById(R.id.fullNameEditText)
        birthdayEditText = findViewById(R.id.birthdayEditText)
        securityQuestionText = findViewById(R.id.securityQuestionText)
        refreshSecurityQuestionBtn = findViewById(R.id.refreshSecurityQuestionBtn)
        securityAnswerEditText = findViewById(R.id.securityAnswerEditText)
        roleRadioGroup = findViewById(R.id.roleRadioGroup)
        studentRadioButton = findViewById(R.id.studentRadioButton)
        teacherRadioButton = findViewById(R.id.teacherRadioButton)
        adminRadioButton = findViewById(R.id.adminRadioButton)
        adminCodeContainer = findViewById(R.id.adminCodeContainer)
        adminCodeEditText = findViewById(R.id.adminCodeEditText)
        registerButton = findViewById(R.id.registerButton)
        loginLink = findViewById(R.id.loginLink)

        // 初始化随机密保问题
        randomizeSecurityQuestion()
    }

    private fun setupEventListeners() {
        registerButton.setOnClickListener { attemptRegister() }

        loginLink.setOnClickListener { navigateToLogin() }

        usernameEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                passwordEditText.requestFocus()
                return@setOnEditorActionListener true
            }
            false
        }

        passwordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                confirmPasswordEditText.requestFocus()
                return@setOnEditorActionListener true
            }
            false
        }

        confirmPasswordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                fullNameEditText.requestFocus()
                return@setOnEditorActionListener true
            }
            false
        }

        // 生日点击弹出日期选择器
        birthdayEditText.setOnClickListener { showDatePicker() }

        // 角色切换：管理员时显示验证码
        roleRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            adminCodeContainer.visibility = if (checkedId == R.id.adminRadioButton) View.VISIBLE else View.GONE
            if (checkedId != R.id.adminRadioButton) {
                adminCodeEditText.text.clear()
            }
        }

        // 刷新密保问题
        refreshSecurityQuestionBtn.setOnClickListener { randomizeSecurityQuestion() }
    }

    private fun randomizeSecurityQuestion() {
        selectedSecurityQuestion = securityQuestions.random()
        securityQuestionText.text = selectedSecurityQuestion
    }

    private fun showDatePicker() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_date_picker, null)
        val yearPicker = dialogView.findViewById<NumberPicker>(R.id.yearPicker)
        val monthPicker = dialogView.findViewById<NumberPicker>(R.id.monthPicker)
        val dayPicker = dialogView.findViewById<NumberPicker>(R.id.dayPicker)

        val cal = java.util.Calendar.getInstance()
        val curYear = 2005
        val curMonth = 1
        val curDay = 1

        yearPicker.minValue = 1950
        yearPicker.maxValue = cal.get(java.util.Calendar.YEAR)
        yearPicker.value = curYear
        yearPicker.wrapSelectorWheel = false

        monthPicker.minValue = 1
        monthPicker.maxValue = 12
        monthPicker.value = curMonth
        monthPicker.wrapSelectorWheel = true
        monthPicker.displayedValues = (1..12).map { it.toString().padStart(2, '0') }.toTypedArray()

        dayPicker.minValue = 1
        dayPicker.value = curDay
        dayPicker.wrapSelectorWheel = true
        dayPicker.displayedValues = (1..31).map { it.toString().padStart(2, '0') }.toTypedArray()

        fun updateDays() {
            val maxDay = when (monthPicker.value) {
                1, 3, 5, 7, 8, 10, 12 -> 31
                4, 6, 9, 11 -> 30
                2 -> if (yearPicker.value % 4 == 0 && (yearPicker.value % 100 != 0 || yearPicker.value % 400 == 0)) 29 else 28
                else -> 31
            }
            dayPicker.maxValue = maxDay
            if (dayPicker.value > maxDay) dayPicker.value = maxDay
        }

        yearPicker.setOnValueChangedListener { _, _, _ -> updateDays() }
        monthPicker.setOnValueChangedListener { _, _, _ -> updateDays() }
        updateDays()

        AlertDialog.Builder(this)
            .setTitle("选择生日")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val ms = monthPicker.value.toString().padStart(2, '0')
                val ds = dayPicker.value.toString().padStart(2, '0')
                birthdayEditText.setText("${yearPicker.value}-$ms-$ds")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun attemptRegister() {
        val username = usernameEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        val confirmPassword = confirmPasswordEditText.text.toString().trim()
        val fullName = fullNameEditText.text.toString().trim()
        val birthday = birthdayEditText.text.toString().trim()
        val securityAnswer = securityAnswerEditText.text.toString().trim()

        val selectedRoleId = roleRadioGroup.checkedRadioButtonId
        if (selectedRoleId == -1) {
            Toast.makeText(this, "请选择角色", Toast.LENGTH_SHORT).show()
            return
        }

        val role = when (selectedRoleId) {
            R.id.studentRadioButton -> "student"
            R.id.teacherRadioButton -> "teacher"
            R.id.adminRadioButton -> "admin"
            else -> "student"
        }

        if (!validateInput(username, password, confirmPassword, fullName, birthday, securityAnswer, role)) {
            return
        }

        val adminCode = adminCodeEditText.text.toString().trim()

        performRegister(username, password, role, fullName, birthday, selectedSecurityQuestion, securityAnswer)
    }

    private fun validateInput(
        username: String,
        password: String,
        confirmPassword: String,
        fullName: String,
        birthday: String,
        securityAnswer: String,
        role: String = ""
    ): Boolean {
        if (username.isEmpty()) {
            usernameEditText.error = "请输入用户名"
            usernameEditText.requestFocus()
            return false
        }

        if (username.length < 2 || username.length > 20) {
            usernameEditText.error = "用户名长度应为2-20位"
            usernameEditText.requestFocus()
            return false
        }

        val usernameRegex = "^[a-zA-Z0-9_\\u4e00-\\u9fff]+$".toRegex()
        if (!username.matches(usernameRegex)) {
            usernameEditText.error = "用户名只能包含字母、数字、下划线和中文"
            usernameEditText.requestFocus()
            return false
        }

        if (password.isEmpty()) {
            passwordEditText.error = "请输入密码"
            passwordEditText.requestFocus()
            return false
        }

        if (password.length < 6) {
            passwordEditText.error = "密码至少需要6位"
            passwordEditText.requestFocus()
            return false
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordEditText.error = "请确认密码"
            confirmPasswordEditText.requestFocus()
            return false
        }

        if (password != confirmPassword) {
            confirmPasswordEditText.error = "两次输入的密码不一致"
            confirmPasswordEditText.requestFocus()
            return false
        }

        if (fullName.isEmpty()) {
            fullNameEditText.error = "请输入真实姓名"
            fullNameEditText.requestFocus()
            return false
        }

        if (birthday.isEmpty()) {
            birthdayEditText.error = "请选择生日"
            birthdayEditText.requestFocus()
            return false
        }

        if (securityAnswer.isEmpty()) {
            securityAnswerEditText.error = "请输入密保答案"
            securityAnswerEditText.requestFocus()
            return false
        }

        if (role == "admin") {
            val adminCode = adminCodeEditText.text.toString().trim()
            if (adminCode != "19110426") {
                adminCodeEditText.error = "管理员验证码错误"
                adminCodeEditText.requestFocus()
                return false
            }
        }

        return true
    }

    private fun performRegister(
        username: String,
        password: String,
        role: String,
        fullName: String,
        birthday: String,
        securityQuestion: String,
        securityAnswer: String
    ) {
        registerButton.isEnabled = false
        registerButton.text = "注册中..."

        coroutineScope.launch {
            try {
                val result = apiService.register(
                    username, password, role,
                    fullName = fullName,
                    birthday = birthday,
                    securityQuestion = securityQuestion,
                    securityAnswer = securityAnswer
                )

                if (result.isSuccess) {
                    val registerResponse = result.getOrThrow()
                    if (registerResponse.success && registerResponse.data != null) {
                        onRegisterSuccess(registerResponse.data!!)
                    } else {
                        onRegisterFailure(registerResponse.message)
                    }
                } else {
                    val errorMessage = result.exceptionOrNull()?.message ?: "注册失败"
                    onRegisterFailure(errorMessage)
                }
            } catch (e: Exception) {
                Log.e("RegisterActivity", "注册异常: ${e.message}", e)
                onRegisterFailure("注册失败: ${e.message}")
            } finally {
                registerButton.isEnabled = true
                registerButton.text = getString(R.string.register_button)
            }
        }
    }

    private fun onRegisterSuccess(user: UserData) {
        Toast.makeText(this, "注册成功！欢迎 ${user.username}", Toast.LENGTH_LONG).show()

        val intent = Intent(this, LoginActivity::class.java)
        intent.putExtra("username", user.username)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)

        finish()
    }

    private fun onRegisterFailure(errorMessage: String) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        passwordEditText.text.clear()
        confirmPasswordEditText.text.clear()
        passwordEditText.requestFocus()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.launch { }
    }
}
