# Android Kotlin 示例代码 - StudyApp 用户注册登录

## 目录
1. [添加依赖](#添加依赖)
2. [数据模型类](#数据模型类)
3. [OkHttp注册接口调用示例](#okhttp注册接口调用示例)
4. [OkHttp登录接口调用示例](#okhttp登录接口调用示例)
5. [登录成功后跳转页面示例](#登录成功后跳转页面示例)
6. [完整Activity示例](#完整activity示例)

## 添加依赖

在 `app/build.gradle.kts` 中添加以下依赖：

```kotlin
dependencies {
    // OkHttp - 网络请求库
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Gson - JSON解析库
    implementation("com.google.code.gson:gson:2.10.1")
    
    // 协程 - 异步处理
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // AndroidX相关
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
```

## 数据模型类

创建数据模型类来映射后端API的响应：

```kotlin
// UserData.kt - 用户数据模型
package com.example.studyapp.model

/**
 * 用户数据模型类
 * 对应后端API返回的用户信息
 */
data class UserData(
    val id: Int,              // 用户ID
    val username: String,     // 用户名
    val role: String,         // 角色：student/teacher/admin
    val createdAt: String     // 创建时间
)

// LoginResponse.kt - 登录响应模型
package com.example.studyapp.model

/**
 * 登录响应模型
 * 对应后端 /login 接口的响应
 */
data class LoginResponse(
    val success: Boolean,     // 是否成功
    val message: String,      // 消息
    val user: UserData?       // 用户信息（登录成功时返回）
)

// RegisterResponse.kt - 注册响应模型
package com.example.studyapp.model

/**
 * 注册响应模型
 * 对应后端 /register 接口的响应
 */
data class RegisterResponse(
    val success: Boolean,     // 是否成功
    val message: String,      // 消息
    val user: UserData?       // 用户信息（注册成功时返回）
)
```

## OkHttp注册接口调用示例

### 1. 简单的OkHttp注册方法（同步调用）

```kotlin
package com.example.studyapp.utils

import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * 网络请求工具类 - 简单版
 * 包含注册和登录方法
 */
class NetworkUtils {
    
    companion object {
        private const val TAG = "NetworkUtils"
        private const val BASE_URL = "http://10.0.2.2:3000" // Android模拟器使用
        // 注意：真机测试时需要改为电脑的IP地址，如 "http://192.168.1.100:3000"
        
        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)    // 连接超时15秒
            .readTimeout(15, TimeUnit.SECONDS)       // 读取超时15秒
            .writeTimeout(15, TimeUnit.SECONDS)      // 写入超时15秒
            .build()
        
        private val gson = Gson() // JSON解析工具
        
        /**
         * 用户注册方法 - 同步调用（需要在后台线程执行）
         * @param username 用户名
         * @param password 密码
         * @param role 角色：student/teacher/admin
         * @return RegisterResponse 注册响应
         */
        fun registerSync(username: String, password: String, role: String): RegisterResponse {
            try {
                // 1. 创建JSON请求体
                val requestBody = """
                    {
                        "username": "$username",
                        "password": "$password",
                        "role": "$role"
                    }
                """.trimIndent()
                
                // 2. 将字符串转换为RequestBody
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = requestBody.toRequestBody(mediaType)
                
                // 3. 创建HTTP请求
                val request = Request.Builder()
                    .url("$BASE_URL/register")      // 请求URL
                    .post(body)                     // POST方法
                    .header("Content-Type", "application/json") // 设置请求头
                    .build()
                
                // 4. 发送请求并获取响应
                client.newCall(request).execute().use { response ->
                    // 5. 读取响应体
                    val responseBody = response.body?.string()
                    
                    // 6. 检查响应状态
                    if (!response.isSuccessful) {
                        Log.e(TAG, "注册失败: HTTP ${response.code}")
                        return RegisterResponse(
                            success = false,
                            message = "注册失败: HTTP ${response.code}",
                            user = null
                        )
                    }
                    
                    // 7. 检查响应体是否为空
                    if (responseBody.isNullOrEmpty()) {
                        Log.e(TAG, "注册失败: 响应体为空")
                        return RegisterResponse(
                            success = false,
                            message = "注册失败: 服务器返回空响应",
                            user = null
                        )
                    }
                    
                    // 8. 解析JSON响应
                    return try {
                        val registerResponse = gson.fromJson(responseBody, RegisterResponse::class.java)
                        Log.d(TAG, "注册响应: ${registerResponse.message}")
                        registerResponse
                    } catch (e: Exception) {
                        Log.e(TAG, "JSON解析失败: ${e.message}")
                        RegisterResponse(
                            success = false,
                            message = "注册失败: 响应格式错误",
                            user = null
                        )
                    }
                }
            } catch (e: IOException) {
                // 网络连接异常
                Log.e(TAG, "网络请求失败: ${e.message}")
                return RegisterResponse(
                    success = false,
                    message = "网络连接失败: ${e.message}",
                    user = null
                )
            } catch (e: Exception) {
                // 其他异常
                Log.e(TAG, "注册异常: ${e.message}")
                return RegisterResponse(
                    success = false,
                    message = "注册失败: ${e.message}",
                    user = null
                )
            }
        }
        
        /**
         * 异步注册方法（推荐使用）
         * 使用回调方式处理结果，避免阻塞UI线程
         * @param username 用户名
         * @param password 密码
         * @param role 角色
         * @param callback 回调接口
         */
        fun registerAsync(
            username: String,
            password: String,
            role: String,
            callback: (RegisterResponse) -> Unit
        ) {
            // 1. 创建JSON请求体
            val requestBody = """
                {
                    "username": "$username",
                    "password": "$password",
                    "role": "$role"
                }
            """.trimIndent()
            
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestBody.toRequestBody(mediaType)
            
            // 2. 创建HTTP请求
            val request = Request.Builder()
                .url("$BASE_URL/register")
                .post(body)
                .header("Content-Type", "application/json")
                .build()
            
            // 3. 异步发送请求
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // 网络请求失败
                    Log.e(TAG, "注册请求失败: ${e.message}")
                    val errorResponse = RegisterResponse(
                        success = false,
                        message = "网络连接失败: ${e.message}",
                        user = null
                    )
                    callback(errorResponse)
                }
                
                override fun onResponse(call: Call, response: Response) {
                    // 网络请求成功
                    try {
                        val responseBody = response.body?.string()
                        
                        if (!response.isSuccessful) {
                            Log.e(TAG, "注册失败: HTTP ${response.code}")
                            val errorResponse = RegisterResponse(
                                success = false,
                                message = "注册失败: HTTP ${response.code}",
                                user = null
                            )
                            callback(errorResponse)
                            return
                        }
                        
                        if (responseBody.isNullOrEmpty()) {
                            Log.e(TAG, "注册失败: 响应体为空")
                            val errorResponse = RegisterResponse(
                                success = false,
                                message = "注册失败: 服务器返回空响应",
                                user = null
                            )
                            callback(errorResponse)
                            return
                        }
                        
                        // 解析JSON响应
                        val registerResponse = gson.fromJson(responseBody, RegisterResponse::class.java)
                        Log.d(TAG, "注册成功: ${registerResponse.message}")
                        callback(registerResponse)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "注册响应处理失败: ${e.message}")
                        val errorResponse = RegisterResponse(
                            success = false,
                            message = "注册失败: ${e.message}",
                            user = null
                        )
                        callback(errorResponse)
                    }
                }
            })
        }
    }
}
```

### 2. 使用协程的注册方法（推荐）

```kotlin
package com.example.studyapp.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import com.google.gson.Gson

/**
 * 使用协程的注册方法
 * 在协程中执行网络请求，避免阻塞UI线程
 */
suspend fun registerWithCoroutine(
    username: String,
    password: String,
    role: String
): RegisterResponse = withContext(Dispatchers.IO) {
    // 切换到IO线程执行网络请求
    try {
        // 创建JSON请求体
        val requestBody = """
            {
                "username": "$username",
                "password": "$password",
                "role": "$role"
            }
        """.trimIndent()
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestBody.toRequestBody(mediaType)
        
        // 创建HTTP请求
        val request = Request.Builder()
            .url("$BASE_URL/register")
            .post(body)
            .header("Content-Type", "application/json")
            .build()
        
        // 发送请求
        val response = client.newCall(request).execute()
        
        // 处理响应
        val responseBody = response.body?.string()
        
        if (!response.isSuccessful) {
            Log.e(TAG, "注册失败: HTTP ${response.code}")
            return@withContext RegisterResponse(
                success = false,
                message = "注册失败: HTTP ${response.code}",
                user = null
            )
        }
        
        if (responseBody.isNullOrEmpty()) {
            Log.e(TAG, "注册失败: 响应体为空")
            return@withContext RegisterResponse(
                success = false,
                message = "注册失败: 服务器返回空响应",
                user = null
            )
        }
        
        // 解析JSON响应
        val gson = Gson()
        val registerResponse = gson.fromJson(responseBody, RegisterResponse::class.java)
        Log.d(TAG, "注册响应: ${registerResponse.message}")
        return@withContext registerResponse
        
    } catch (e: IOException) {
        Log.e(TAG, "网络请求失败: ${e.message}")
        return@withContext RegisterResponse(
            success = false,
            message = "网络连接失败: ${e.message}",
            user = null
        )
    } catch (e: Exception) {
        Log.e(TAG, "注册异常: ${e.message}")
        return@withContext RegisterResponse(
            success = false,
            message = "注册失败: ${e.message}",
            user = null
        )
    }
}
```

## OkHttp登录接口调用示例

### 1. 简单的OkHttp登录方法

```kotlin
package com.example.studyapp.utils

/**
 * 登录相关方法
 */
class NetworkUtils {
    // ... 之前的代码 ...
    
    companion object {
        // ... 之前的代码 ...
        
        /**
         * 用户登录方法 - 同步调用（需要在后台线程执行）
         * @param username 用户名
         * @param password 密码
         * @return LoginResponse 登录响应
         */
        fun loginSync(username: String, password: String): LoginResponse {
            try {
                // 1. 创建JSON请求体
                val requestBody = """
                    {
                        "username": "$username",
                        "password": "$password"
                    }
                """.trimIndent()
                
                // 2. 将字符串转换为RequestBody
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = requestBody.toRequestBody(mediaType)
                
                // 3. 创建HTTP请求
                val request = Request.Builder()
                    .url("$BASE_URL/login")        // 请求URL
                    .post(body)                    // POST方法
                    .header("Content-Type", "application/json") // 设置请求头
                    .build()
                
                // 4. 发送请求并获取响应
                client.newCall(request).execute().use { response ->
                    // 5. 读取响应体
                    val responseBody = response.body?.string()
                    
                    // 6. 检查响应状态
                    if (!response.isSuccessful) {
                        Log.e(TAG, "登录失败: HTTP ${response.code}")
                        return LoginResponse(
                            success = false,
                            message = "登录失败: HTTP ${response.code}",
                            user = null
                        )
                    }
                    
                    // 7. 检查响应体是否为空
                    if (responseBody.isNullOrEmpty()) {
                        Log.e(TAG, "登录失败: 响应体为空")
                        return LoginResponse(
                            success = false,
                            message = "登录失败: 服务器返回空响应",
                            user = null
                        )
                    }
                    
                    // 8. 解析JSON响应
                    return try {
                        val loginResponse = gson.fromJson(responseBody, LoginResponse::class.java)
                        Log.d(TAG, "登录响应: ${loginResponse.message}")
                        loginResponse
                    } catch (e: Exception) {
                        Log.e(TAG, "JSON解析失败: ${e.message}")
                        LoginResponse(
                            success = false,
                            message = "登录失败: 响应格式错误",
                            user = null
                        )
                    }
                }
            } catch (e: IOException) {
                // 网络连接异常
                Log.e(TAG, "网络请求失败: ${e.message}")
                return LoginResponse(
                    success = false,
                    message = "网络连接失败: ${e.message}",
                    user = null
                )
            } catch (e: Exception) {
                // 其他异常
                Log.e(TAG, "登录异常: ${e.message}")
                return LoginResponse(
                    success = false,
                    message = "登录失败: ${e.message}",
                    user = null
                )
            }
        }
        
        /**
         * 异步登录方法（推荐使用）
         * 使用回调方式处理结果，避免阻塞UI线程
         * @param username 用户名
         * @param password 密码
         * @param callback 回调接口
         */
        fun loginAsync(
            username: String,
            password: String,
            callback: (LoginResponse) -> Unit
        ) {
            // 1. 创建JSON请求体
            val requestBody = """
                {
                    "username": "$username",
                    "password": "$password"
                }
            """.trimIndent()
            
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestBody.toRequestBody(mediaType)
            
            // 2. 创建HTTP请求
            val request = Request.Builder()
                .url("$BASE_URL/login")
                .post(body)
                .header("Content-Type", "application/json")
                .build()
            
            // 3. 异步发送请求
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // 网络请求失败
                    Log.e(TAG, "登录请求失败: ${e.message}")
                    val errorResponse = LoginResponse(
                        success = false,
                        message = "网络连接失败: ${e.message}",
                        user = null
                    )
                    callback(errorResponse)
                }
                
                override fun onResponse(call: Call, response: Response) {
                    // 网络请求成功
                    try {
                        val responseBody = response.body?.string()
                        
                        if (!response.isSuccessful) {
                            Log.e(TAG, "登录失败: HTTP ${response.code}")
                            val errorResponse = LoginResponse(
                                success = false,
                                message = "登录失败: HTTP ${response.code}",
                                user = null
                            )
                            callback(errorResponse)
                            return
                        }
                        
                        if (responseBody.isNullOrEmpty()) {
                            Log.e(TAG, "登录失败: 响应体为空")
                            val errorResponse = LoginResponse(
                                success = false,
                                message = "登录失败: 服务器返回空响应",
                                user = null
                            )
                            callback(errorResponse)
                            return
                        }
                        
                        // 解析JSON响应
                        val loginResponse = gson.fromJson(responseBody, LoginResponse::class.java)
                        Log.d(TAG, "登录成功: ${loginResponse.message}")
                        callback(loginResponse)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "登录响应处理失败: ${e.message}")
                        val errorResponse = LoginResponse(
                            success = false,
                            message = "登录失败: ${e.message}",
                            user = null
                        )
                        callback(errorResponse)
                    }
                }
            })
        }
    }
}
```

### 2. 使用协程的登录方法（推荐）

```kotlin
package com.example.studyapp.utils

/**
 * 使用协程的登录方法
 * 在协程中执行网络请求，避免阻塞UI线程
 */
suspend fun loginWithCoroutine(
    username: String,
    password: String
): LoginResponse = withContext(Dispatchers.IO) {
    // 切换到IO线程执行网络请求
    try {
        // 创建JSON请求体
        val requestBody = """
            {
                "username": "$username",
                "password": "$password"
            }
        """.trimIndent()
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestBody.toRequestBody(mediaType)
        
        // 创建HTTP请求
        val request = Request.Builder()
            .url("$BASE_URL/login")
            .post(body)
            .header("Content-Type", "application/json")
            .build()
        
        // 发送请求
        val response = client.newCall(request).execute()
        
        // 处理响应
        val responseBody = response.body?.string()
        
        if (!response.isSuccessful) {
            Log.e(TAG, "登录失败: HTTP ${response.code}")
            return@withContext LoginResponse(
                success = false,
                message = "登录失败: HTTP ${response.code}",
                user = null
            )
        }
        
        if (responseBody.isNullOrEmpty()) {
            Log.e(TAG, "登录失败: 响应体为空")
            return@withContext LoginResponse(
                success = false,
                message = "登录失败: 服务器返回空响应",
                user = null
            )
        }
        
        // 解析JSON响应
        val gson = Gson()
        val loginResponse = gson.fromJson(responseBody, LoginResponse::class.java)
        Log.d(TAG, "登录响应: ${loginResponse.message}")
        return@withContext loginResponse
        
    } catch (e: IOException) {
        Log.e(TAG, "网络请求失败: ${e.message}")
        return@withContext LoginResponse(
            success = false,
            message = "网络连接失败: ${e.message}",
            user = null
        )
    } catch (e: Exception) {
        Log.e(TAG, "登录异常: ${e.message}")
        return@withContext LoginResponse(
            success = false,
            message = "登录失败: ${e.message}",
            user = null
        )
    }
}
```

## 登录成功后跳转页面示例

### 1. 根据用户角色跳转到不同页面

```kotlin
package com.example.studyapp.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.studyapp.model.UserData
import com.example.studyapp.StudentActivity
import com.example.studyapp.TeacherActivity
import com.example.studyapp.AdminActivity

/**
 * 页面跳转工具类
 * 根据用户角色跳转到不同的主页面
 */
class NavigationUtils {
    
    companion object {
        /**
         * 根据用户角色跳转到对应的主页面
         * @param context 上下文
         * @param user 用户数据
         */
        fun navigateToMainPage(context: Context, user: UserData) {
            // 创建跳转意图
            val intent = when (user.role.lowercase()) {
                "student" -> {
                    // 跳转到学生主页
                    Intent(context, StudentActivity::class.java)
                }
                "teacher" -> {
                    // 跳转到教师主页
                    Intent(context, TeacherActivity::class.java)
                }
                "admin" -> {
                    // 跳转到管理员主页
                    Intent(context, AdminActivity::class.java)
                }
                else -> {
                    // 默认跳转到学生主页
                    Intent(context, StudentActivity::class.java)
                }
            }
            
            // 传递用户信息给下一个页面
            intent.putExtra("user_id", user.id)
            intent.putExtra("username", user.username)
            intent.putExtra("role", user.role)
            
            // 启动页面
            context.startActivity(intent)
            
            // 显示欢迎消息
            val welcomeMessage = when (user.role.lowercase()) {
                "student" -> "欢迎学生 ${user.username}！"
                "teacher" -> "欢迎教师 ${user.username}！"
                "admin" -> "欢迎管理员 ${user.username}！"
                else -> "欢迎 ${user.username}！"
            }
            Toast.makeText(context, welcomeMessage, Toast.LENGTH_LONG).show()
        }
        
        /**
         * 简单的页面跳转示例（直接在Activity中使用）
         * @param activity 当前Activity
         * @param user 用户数据
         */
        fun simpleNavigate(activity: android.app.Activity, user: UserData) {
            when (user.role.lowercase()) {
                "student" -> {
                    // 跳转到学生主页
                    val intent = Intent(activity, StudentActivity::class.java)
                    intent.putExtra("user", user)
                    activity.startActivity(intent)
                    activity.finish() // 结束当前登录页面
                }
                "teacher" -> {
                    // 跳转到教师主页
                    val intent = Intent(activity, TeacherActivity::class.java)
                    intent.putExtra("user", user)
                    activity.startActivity(intent)
                    activity.finish()
                }
                "admin" -> {
                    // 跳转到管理员主页
                    val intent = Intent(activity, AdminActivity::class.java)
                    intent.putExtra("user", user)
                    activity.startActivity(intent)
                    activity.finish()
                }
                else -> {
                    // 默认跳转
                    val intent = Intent(activity, StudentActivity::class.java)
                    intent.putExtra("user", user)
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
        }
        
        /**
         * 检查用户角色并执行相应的操作
         * @param user 用户数据
         * @param studentAction 学生角色的回调
         * @param teacherAction 教师角色的回调
         * @param adminAction 管理员角色的回调
         */
        fun checkUserRole(
            user: UserData,
            studentAction: () -> Unit,
            teacherAction: () -> Unit,
            adminAction: () -> Unit
        ) {
            when (user.role.lowercase()) {
                "student" -> studentAction()
                "teacher" -> teacherAction()
                "admin" -> adminAction()
                else -> studentAction() // 默认执行学生操作
            }
        }
    }
}
```

### 2. 保存登录状态并跳转

```kotlin
package com.example.studyapp.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * 登录状态管理工具类
 * 使用SharedPreferences保存用户登录状态
 */
class LoginManager private constructor(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "studyapp_login"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_ROLE = "role"
        private const val KEY_LOGIN_TIME = "login_time"
        
        @Volatile
        private var INSTANCE: LoginManager? = null
        
        fun getInstance(context: Context): LoginManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LoginManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPref: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * 保存登录状态
     * @param user 用户数据
     */
    fun saveLoginStatus(user: UserData) {
        sharedPref.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putInt(KEY_USER_ID, user.id)
            putString(KEY_USERNAME, user.username)
            putString(KEY_ROLE, user.role)
            putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
            apply() // 异步保存
        }
    }
    
    /**
     * 检查是否已登录
     * @return Boolean 是否已登录
     */
    fun isLoggedIn(): Boolean {
        return sharedPref.getBoolean(KEY_IS_LOGGED_IN, false)
    }
    
    /**
     * 获取当前登录用户信息
     * @return UserData? 用户数据，未登录时返回null
     */
    fun getCurrentUser(): UserData? {
        return if (isLoggedIn()) {
            UserData(
                id = sharedPref.getInt(KEY_USER_ID, -1),
                username = sharedPref.getString(KEY_USERNAME, "") ?: "",
                role = sharedPref.getString(KEY_ROLE, "student") ?: "student",
                createdAt = "" // 创建时间需要从服务器获取
            )
        } else {
            null
        }
    }
    
    /**
     * 退出登录
     */
    fun logout() {
        sharedPref.edit().apply {
            clear() // 清除所有登录信息
            apply()
        }
    }
    
    /**
     * 根据保存的登录状态自动跳转
     * 如果已登录，直接跳转到对应的主页面
     * @param context 上下文
     * @param loginActivity 登录页面的Class（用于未登录时跳转回登录页）
     */
    fun autoNavigate(context: Context, loginActivity: Class<*>) {
        if (isLoggedIn()) {
            val user = getCurrentUser()
            if (user != null) {
                // 已登录，跳转到对应主页面
                NavigationUtils.navigateToMainPage(context, user)
            } else {
                // 用户信息异常，跳转回登录页
                val intent = Intent(context, loginActivity)
                context.startActivity(intent)
            }
        } else {
            // 未登录，跳转回登录页
            val intent = Intent(context, loginActivity)
            context.startActivity(intent)
        }
    }
}
```

## 完整Activity示例

### 1. 注册Activity示例

```kotlin
package com.example.studyapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.studyapp.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 注册页面Activity
 * 功能：用户注册，选择角色（student/teacher/admin）
 */
class RegisterActivity : AppCompatActivity() {
    
    // 视图组件
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var roleRadioGroup: RadioGroup
    private lateinit var studentRadioButton: RadioButton
    private lateinit var teacherRadioButton: RadioButton
    private lateinit var adminRadioButton: RadioButton
    private lateinit var registerButton: Button
    
    // 协程作用域
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register) // 需要创建对应的布局文件
        
        // 初始化视图
        initViews()
        
        // 设置事件监听器
        setupEventListeners()
    }
    
    private fun initViews() {
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText)
        roleRadioGroup = findViewById(R.id.roleRadioGroup)
        studentRadioButton = findViewById(R.id.studentRadioButton)
        teacherRadioButton = findViewById(R.id.teacherRadioButton)
        adminRadioButton = findViewById(R.id.adminRadioButton)
        registerButton = findViewById(R.id.registerButton)
    }
    
    private fun setupEventListeners() {
        // 注册按钮点击事件
        registerButton.setOnClickListener {
            attemptRegister()
        }
    }
    
    private fun attemptRegister() {
        // 获取输入值
        val username = usernameEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        val confirmPassword = confirmPasswordEditText.text.toString().trim()
        
        // 获取选择的角色
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
        
        // 输入验证
        if (!validateInput(username, password, confirmPassword)) {
            return
        }
        
        // 执行注册
        performRegister(username, password, role)
    }
    
    private fun validateInput(
        username: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        // 检查用户名是否为空
        if (username.isEmpty()) {
            usernameEditText.error = "请输入用户名"
            usernameEditText.requestFocus()
            return false
        }
        
        // 检查用户名长度
        if (username.length < 2 || username.length > 20) {
            usernameEditText.error = "用户名长度应为2-20位"
            usernameEditText.requestFocus()
            return false
        }
        
        // 检查密码是否为空
        if (password.isEmpty()) {
            passwordEditText.error = "请输入密码"
            passwordEditText.requestFocus()
            return false
        }
        
        // 检查密码长度
        if (password.length < 6) {
            passwordEditText.error = "密码至少需要6位"
            passwordEditText.requestFocus()
            return false
        }
        
        // 检查确认密码
        if (confirmPassword.isEmpty()) {
            confirmPasswordEditText.error = "请确认密码"
            confirmPasswordEditText.requestFocus()
            return false
        }
        
        // 检查两次输入的密码是否一致
        if (password != confirmPassword) {
            confirmPasswordEditText.error = "两次输入的密码不一致"
            confirmPasswordEditText.requestFocus()
            return false
        }
        
        return true
    }
    
    private fun performRegister(username: String, password: String, role: String) {
        // 显示加载状态
        registerButton.isEnabled = false
        registerButton.text = "注册中..."
        
        // 使用协程执行网络请求
        coroutineScope.launch {
            try {
                // 使用NetworkUtils的同步方法（在IO线程执行）
                val result = withContext(Dispatchers.IO) {
                    NetworkUtils.registerSync(username, password, role)
                }
                
                if (result.success) {
                    // 注册成功
                    onRegisterSuccess(result.user!!)
                } else {
                    // 注册失败
                    onRegisterFailure(result.message)
                }
            } catch (e: Exception) {
                // 异常处理
                onRegisterFailure("注册失败: ${e.message}")
            } finally {
                // 恢复按钮状态
                registerButton.isEnabled = true
                registerButton.text = "注册"
            }
        }
    }
    
    private fun onRegisterSuccess(user: UserData) {
        // 显示成功消息
        Toast.makeText(this, "注册成功！欢迎 ${user.username}", Toast.LENGTH_LONG).show()
        
        // 可以跳转到登录页面，或者直接登录
        // 这里我们跳转到登录页面
        val intent = Intent(this, LoginActivity::class.java)
        intent.putExtra("username", user.username)
        startActivity(intent)
        
        // 结束当前页面
        finish()
    }
    
    private fun onRegisterFailure(errorMessage: String) {
        // 显示错误消息
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        
        // 清空密码框
        passwordEditText.text.clear()
        confirmPasswordEditText.text.clear()
        passwordEditText.requestFocus()
    }
}
```

### 2. 登录Activity示例（简化版）

```kotlin
package com.example.studyapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.studyapp.utils.LoginManager
import com.example.studyapp.utils.NetworkUtils
import com.example.studyapp.utils.NavigationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 登录页面Activity - 简化版
 */
class SimpleLoginActivity : AppCompatActivity() {
    
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var loginManager: LoginManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_login)
        
        // 初始化登录管理器
        loginManager = LoginManager.getInstance(this)
        
        // 初始化视图
        initViews()
        
        // 检查是否已登录，如果已登录则直接跳转
        checkAutoLogin()
    }
    
    private fun initViews() {
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
        
        // 设置事件监听器
        loginButton.setOnClickListener { attemptLogin() }
        registerButton.setOnClickListener { 
            // 跳转到注册页面
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
        
        // 检查Intent中是否有传递过来的用户名（从注册页面跳转过来）
        val usernameFromIntent = intent.getStringExtra("username")
        if (!usernameFromIntent.isNullOrEmpty()) {
            usernameEditText.setText(usernameFromIntent)
            passwordEditText.requestFocus()
        }
    }
    
    private fun checkAutoLogin() {
        if (loginManager.isLoggedIn()) {
            // 已登录，自动跳转到主页面
            loginManager.autoNavigate(this, SimpleLoginActivity::class.java)
        }
    }
    
    private fun attemptLogin() {
        val username = usernameEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        
        // 简单验证
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 执行登录
        performLogin(username, password)
    }
    
    private fun performLogin(username: String, password: String) {
        // 显示加载状态
        loginButton.isEnabled = false
        loginButton.text = "登录中..."
        
        coroutineScope.launch {
            try {
                // 使用NetworkUtils的同步方法（在IO线程执行）
                val result = withContext(Dispatchers.IO) {
                    NetworkUtils.loginSync(username, password)
                }
                
                if (result.success && result.user != null) {
                    // 登录成功
                    onLoginSuccess(result.user!!)
                } else {
                    // 登录失败
                    onLoginFailure(result.message)
                }
            } catch (e: Exception) {
                // 异常处理
                onLoginFailure("登录失败: ${e.message}")
            } finally {
                // 恢复按钮状态
                loginButton.isEnabled = true
                loginButton.text = "登录"
            }
        }
    }
    
    private fun onLoginSuccess(user: UserData) {
        // 保存登录状态
        loginManager.saveLoginStatus(user)
        
        // 根据角色跳转到对应的主页面
        NavigationUtils.navigateToMainPage(this, user)
        
        // 结束当前页面
        finish()
    }
    
    private fun onLoginFailure(errorMessage: String) {
        // 显示错误消息
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        
        // 清空密码框
        passwordEditText.text.clear()
        passwordEditText.requestFocus()
    }
}
```

## 使用说明

### 1. 创建布局文件
需要创建以下布局文件：
- `activity_register.xml` - 注册页面布局
- `activity_simple_login.xml` - 登录页面布局
- `activity_student.xml` - 学生主页布局
- `activity_teacher.xml` - 教师主页布局  
- `activity_admin.xml` - 管理员主页布局

### 2. 在AndroidManifest.xml中添加权限
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 3. 在AndroidManifest.xml中注册Activity
```xml
<activity
    android:name=".RegisterActivity"
    android:label="注册" />
<activity
    android:name=".SimpleLoginActivity"
    android:label="登录">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
<activity
    android:name=".StudentActivity"
    android:label="学生主页" />
<activity
    android:name=".TeacherActivity"
    android:label="教师主页" />
<activity
    android:name=".AdminActivity"
    android:label="管理员主页" />
```

## 注意事项

1. **网络请求必须在后台线程执行**，不能在主线程（UI线程）执行网络请求
2. **Android模拟器访问本地服务器**需要使用 `http://10.0.2.2:3000`
3. **真机测试**需要将IP地址改为电脑的IP地址，并确保手机和电脑在同一网络
4. **添加网络权限**，否则无法访问网络
5. **处理网络异常**，如网络不可用、服务器宕机等情况
6. **密码安全**：实际应用中应该加密传输和存储密码