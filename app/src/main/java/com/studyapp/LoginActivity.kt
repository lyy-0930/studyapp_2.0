package com.studyapp

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.studyapp.manager.ApiService
import com.studyapp.manager.ForgotPasswordQueryResponse
import com.studyapp.model.UserData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 登录界面Activity
 * 支持记住密码、忘记密码（密保找回）
 */
class LoginActivity : AppCompatActivity() {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private lateinit var apiService: ApiService

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerLink: TextView
    private lateinit var forgotPasswordLink: TextView
    private lateinit var rememberPasswordCheckbox: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)

        // 清理旧版保存的明文密码（安全清理）
        if (sharedPref.contains("saved_password")) {
            sharedPref.edit {
                remove("saved_password")
                remove("remember_password")
            }
        }

        // 使用 JWT 令牌检查登录状态
        if (ApiService.isLoggedIn(this)) {
            val savedUsername = sharedPref.getString("username", "") ?: ""
            val savedRole = sharedPref.getString("role", "student") ?: "student"
            if (savedUsername.isNotEmpty()) {
                val intent = when (savedRole.lowercase()) {
                    "student" -> Intent(this, StudentActivity::class.java)
                    "teacher" -> Intent(this, TeacherActivity::class.java)
                    "admin" -> Intent(this, AdminActivity::class.java)
                    else -> Intent(this, StudentActivity::class.java)
                }
                intent.putExtra("username", savedUsername)
                intent.putExtra("role", savedRole)
                startActivity(intent)
                finish()
                return
            }
        }

        Log.d("LoginActivity", "onCreate started")
        setContentView(R.layout.activity_login)

        apiService = ApiService.getInstance(this)

        initViews()
        setupEventListeners()
        loadSavedCredentials()
        showHint()
    }

    fun initViews() {
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        registerLink = findViewById(R.id.registerLink)
        forgotPasswordLink = findViewById(R.id.forgotPasswordLink)
        rememberPasswordCheckbox = findViewById(R.id.rememberPasswordCheckbox)
    }

    fun setupEventListeners() {
        loginButton.setOnClickListener { attemptLogin() }

        usernameEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                passwordEditText.requestFocus()
                return@setOnEditorActionListener true
            }
            false
        }

        passwordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                return@setOnEditorActionListener true
            }
            false
        }

        passwordEditText.setOnLongClickListener {
            togglePasswordVisibility()
            true
        }

        registerLink.setOnClickListener { navigateToRegister() }

        forgotPasswordLink.setOnClickListener { showForgotPasswordDialog() }
    }

    /**
     * 加载已保存的用户名（不再保存明文密码）
     */
    private fun loadSavedCredentials() {
        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val savedUsername = sharedPref.getString("saved_username", "") ?: ""
        val rememberChecked = sharedPref.getBoolean("remember_username", false)

        if (rememberChecked && savedUsername.isNotEmpty()) {
            usernameEditText.setText(savedUsername)
            rememberPasswordCheckbox.isChecked = true
        }
    }

    /**
     * 保存用户名（记住用户名），不保存明文密码
     */
    private fun saveCredentials() {
        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val remember = rememberPasswordCheckbox.isChecked
        if (remember) {
            sharedPref.edit {
                putString("saved_username", usernameEditText.text.toString().trim())
                putBoolean("remember_username", true)
            }
        } else {
            sharedPref.edit {
                remove("saved_username")
                putBoolean("remember_username", false)
            }
        }
    }

    fun attemptLogin() {
        val username = usernameEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (!validateInput(username, password)) {
            return
        }

        performLogin(username, password)
    }

    fun validateInput(username: String, password: String): Boolean {
        if (username.isEmpty()) {
            usernameEditText.error = "请输入用户名"
            usernameEditText.requestFocus()
            return false
        }

        if (password.isEmpty()) {
            passwordEditText.error = "请输入密码"
            passwordEditText.requestFocus()
            return false
        }

        if (username.length !in 2..20) {
            usernameEditText.error = "用户名长度应为2-20位"
            usernameEditText.requestFocus()
            return false
        }

        return true
    }

    fun performLogin(username: String, password: String) {
        Log.d("LoginActivity", "=== 点击登录按钮 ===")
        Log.d("LoginActivity", "连接地址: ${ApiService.BASE_URL}/login")
        loginButton.isEnabled = false
        loginButton.text = "登录中..."

        coroutineScope.launch {
            try {
                val result = apiService.login(username, password)

                if (result.isSuccess) {
                    val loginResponse = result.getOrThrow()
                    val user = loginResponse.data

                    if (user != null) {
                        saveCredentials()
                        onLoginSuccess(user)
                    } else {
                        onLoginFailure("登录失败: 用户信息为空")
                    }
                } else {
                    val errorMessage = result.exceptionOrNull()?.message ?: "登录失败"
                    onLoginFailure(errorMessage)
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "登录异常: ${e.message}", e)
                onLoginFailure("网络连接失败，请检查网络设置")
            } finally {
                loginButton.isEnabled = true
                loginButton.text = getString(R.string.login_button)
            }
        }
    }

    fun onLoginSuccess(user: UserData) {
        showToast("登录成功！欢迎 ${user.username}")

        val intent = when (user.role.lowercase()) {
            "student" -> Intent(this, StudentActivity::class.java)
            "teacher" -> Intent(this, TeacherActivity::class.java)
            "admin" -> Intent(this, AdminActivity::class.java)
            else -> Intent(this, StudentActivity::class.java)
        }
        intent.putExtra("username", user.username)
        intent.putExtra("role", user.role)
        startActivity(intent)

        finish()
        saveLoginStatus(user)
    }

    fun onLoginSuccess(username: String) {
        showToast("登录成功！欢迎 $username")

        val role = getRoleFromUsername(username)

        val intent = when (role) {
            "student" -> Intent(this, StudentActivity::class.java)
            "teacher" -> Intent(this, TeacherActivity::class.java)
            "admin" -> Intent(this, AdminActivity::class.java)
            else -> Intent(this, StudentActivity::class.java)
        }
        intent.putExtra("username", username)
        intent.putExtra("role", role)
        startActivity(intent)

        finish()
        saveLoginStatus(username, role)
    }

    private fun getRoleFromUsername(username: String): String {
        return when {
            username.contains("teacher", ignoreCase = true) -> "teacher"
            username.contains("admin", ignoreCase = true) -> "admin"
            else -> "student"
        }
    }

    fun onLoginFailure(errorMessage: String = "用户名或密码错误") {
        showToast(errorMessage)
        passwordEditText.text.clear()
        passwordEditText.requestFocus()
        addErrorAnimation()
    }

    fun saveLoginStatus(user: UserData) {
        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        sharedPref.edit {
            putBoolean("is_logged_in", true)
            putInt("user_id", user.id)
            putString("username", user.username)
            putString("role", user.role)
            putString("avatar_url", user.avatarUrl)
            putLong("login_time", System.currentTimeMillis())
        }
    }

    fun saveLoginStatus(username: String, role: String) {
        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        sharedPref.edit {
            putBoolean("is_logged_in", true)
            putInt("user_id", 0)
            putString("username", username)
            putString("role", role)
            putLong("login_time", System.currentTimeMillis())
        }
        Log.w("LoginActivity", "后备登录: 保存用户信息但用户ID为0 (无效ID), 用户名=$username, 角色=$role")
    }

    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    fun addErrorAnimation() {
        val shake = AnimationUtils.loadAnimation(this, R.anim.shake)
        usernameEditText.startAnimation(shake)
        passwordEditText.startAnimation(shake)
    }

    fun showHint() {
        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val hintShown = sharedPref.getBoolean("hint_shown", false)

        if (!hintShown) {
            loginButton.postDelayed({
                showToast("提示：长按密码框可以显示/隐藏密码")
                sharedPref.edit { putBoolean("hint_shown", true) }
            }, 1000)
        }
    }

    fun togglePasswordVisibility() {
        val currentInputType = passwordEditText.inputType
        val isPasswordHidden = currentInputType == InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD

        if (isPasswordHidden) {
            passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            showToast("密码已显示")
        } else {
            passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
            showToast("密码已隐藏")
        }

        val selection = passwordEditText.selectionEnd
        passwordEditText.setSelection(selection)
    }

    /**
     * 忘记密码对话框
     */
    private fun showForgotPasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null)
        val usernameInput: EditText = dialogView.findViewById(R.id.forgotUsernameInput)
        val queryBtn: Button = dialogView.findViewById(R.id.forgotQueryBtn)
        val verifyGroup = dialogView.findViewById<LinearLayout>(R.id.forgotVerifyGroup)
        val fullNameInput: EditText = dialogView.findViewById(R.id.forgotFullNameInput)
        val birthdayInput: EditText = dialogView.findViewById(R.id.forgotBirthdayInput)
        val questionText: TextView = dialogView.findViewById(R.id.forgotQuestionText)
        val answerInput: EditText = dialogView.findViewById(R.id.forgotAnswerInput)
        val newPasswordInput: EditText = dialogView.findViewById(R.id.forgotNewPasswordInput)
        val confirmNewPasswordInput: EditText = dialogView.findViewById(R.id.forgotConfirmNewPasswordInput)
        val resetBtn: Button = dialogView.findViewById(R.id.forgotResetBtn)

        // 初始隐藏验证区域
        verifyGroup.visibility = View.GONE

        // 生日点击弹出自定义日期选择器（年-月-日 中文字）
        birthdayInput.setOnClickListener {
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
                    birthdayInput.setText("${yearPicker.value}-$ms-$ds")
                }
                .setNegativeButton("取消", null)
                .show()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("关闭") { d, _ -> d.dismiss() }
            .show()

        // 查询密保问题
        queryBtn.setOnClickListener {
            val uname = usernameInput.text.toString().trim()
            if (uname.isEmpty()) {
                Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            queryBtn.isEnabled = false
            queryBtn.text = "查询中..."

            coroutineScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        apiService.forgotPasswordQuery(uname)
                    }
                    if (result.isSuccess) {
                        val data = result.getOrThrow().data
                        if (data != null) {
                            questionText.text = "密保问题：${data.securityQuestion ?: "未设置"}"
                            verifyGroup.visibility = View.VISIBLE
                            queryBtn.text = "已查询"
                            Toast.makeText(this@LoginActivity, "请回答密保问题", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@LoginActivity, "未找到密保信息", Toast.LENGTH_SHORT).show()
                            queryBtn.isEnabled = true
                            queryBtn.text = "查询"
                        }
                    } else {
                        val err = result.exceptionOrNull()?.message ?: "查询失败"
                        Toast.makeText(this@LoginActivity, err, Toast.LENGTH_SHORT).show()
                        queryBtn.isEnabled = true
                        queryBtn.text = "查询"
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
                    queryBtn.isEnabled = true
                    queryBtn.text = "查询"
                }
            }
        }

        // 重置密码
        resetBtn.setOnClickListener {
            val uname = usernameInput.text.toString().trim()
            val fullName = fullNameInput.text.toString().trim()
            val birthday = birthdayInput.text.toString().trim()
            val answer = answerInput.text.toString().trim()
            val newPw = newPasswordInput.text.toString().trim()
            val confirmPw = confirmNewPasswordInput.text.toString().trim()

            if (fullName.isEmpty() || birthday.isEmpty() || answer.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPw.isEmpty()) {
                Toast.makeText(this, "请输入新密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPw.length < 6) {
                Toast.makeText(this, "密码至少需要6位", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPw != confirmPw) {
                Toast.makeText(this, "两次输入的密码不一致", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            resetBtn.isEnabled = false
            resetBtn.text = "重置中..."

            coroutineScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        apiService.forgotPasswordReset(uname, fullName, birthday, answer, newPw)
                    }
                    if (result.isSuccess) {
                        Toast.makeText(this@LoginActivity, "密码重置成功，请使用新密码登录", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    } else {
                        val err = result.exceptionOrNull()?.message ?: "重置失败"
                        Toast.makeText(this@LoginActivity, err, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    resetBtn.isEnabled = true
                    resetBtn.text = "重置密码"
                }
            }
        }
    }

    private fun navigateToRegister() {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }
}
