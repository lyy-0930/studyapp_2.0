package com.studyapp.manager

import android.content.Context
import android.util.Log
import com.studyapp.model.ApiResponse
import com.studyapp.model.LoginResponse
import com.studyapp.model.RegisterResponse
import com.studyapp.model.UserData
import com.studyapp.model.ApiCourse
import com.studyapp.model.Category
import com.studyapp.model.ChatMessage
import com.studyapp.model.Conversation
import com.studyapp.model.CourseMaterial
import com.studyapp.model.QuestionAccuracyData
import com.studyapp.model.QuestionAccuracyResponse
import com.studyapp.model.Question
import com.studyapp.model.QuizResult
import com.studyapp.model.StudentStatsData
import com.studyapp.model.StudentStatsResponse
import com.studyapp.model.CourseCreateResponse
import com.studyapp.model.EnrollCourseResponse
import com.studyapp.model.StudyRecordResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 忘记密码查询响应
 */
data class ForgotPasswordQueryResponse(
    val success: Boolean,
    val message: String,
    val data: ForgotPasswordQueryData?
)

/**
 * 忘记密码查询数据
 */
data class ForgotPasswordQueryData(
    val fullName: String?,
    val birthday: String?,
    val securityQuestion: String?
)

/**
 * StudyApp后端API服务类
 * 提供与Node.js后端服务器的通信接口
 * 使用 HttpURLConnection + org.json 替代第三方库
 */
class ApiService private constructor(val context: Context) {

    /**
     * 管理员用户数据模型
     */
    data class AdminUser(
        val id: Int,
        val username: String,
        val role: String,
        val createdAt: String? = ""
    )

    /**
     * 在线用户统计数据模型
     */
    data class OnlineUserStats(
        val onlineCount: Int,
        val totalUsers: Int,
        val onlineRate: Double,
        val lastUpdated: String
    )

    /**
     * 在线用户数据模型
     */
    data class OnlineUser(
        val id: Int,
        val username: String,
        val role: String,
        val lastActiveAt: String,
        val isOnline: Boolean = true
    )

    /**
     * 在线用户响应数据模型
     */
    data class OnlineUsersResponse(
        val stats: OnlineUserStats,
        val onlineUsers: List<OnlineUser>
    )

    /**
     * 活跃度排行榜统计数据模型
     */
    data class ActivityRankingStats(
        val days: Int,
        val totalUsers: Int,
        val totalWatchTime: Int,
        val totalClickCount: Int,
        val averageActivityScore: Double,
        val maxActivityScore: Double,
        val activityDistribution: ActivityDistribution,
        val lastUpdated: String
    )

    /**
     * 活跃度分布数据模型
     */
    data class ActivityDistribution(
        val high: Int,
        val medium: Int,
        val low: Int
    )

    /**
     * 活跃度分数详情数据模型
     */
    data class ActivityScoreDetails(
        val totalWatchTime: Int,
        val avgProgress: Double,
        val totalClickCount: Int,
        val studyRecordsCount: Int,
        val loginCount: Int = 0,
        val completedCourses: Int = 0,
        val totalQuizScore: Int = 0,
        val scores: ActivityScores
    )

    /**
     * 活跃度各项分数数据模型
     */
    data class ActivityScores(
        val login: Double = 0.0,
        val watchTime: Double = 0.0,
        val completedCourse: Double = 0.0,
        val quiz: Double = 0.0
    )

    /**
     * 活跃度排行榜项目数据模型
     */
    data class ActivityRankingItem(
        val rank: Int,
        val userId: Int,
        val username: String,
        val role: String,
        val lastActiveAt: String,
        val activityScore: Double,
        val details: ActivityScoreDetails
    )

    /**
     * 活跃度排行榜响应数据模型
     */
    data class ActivityRankingResponse(
        val stats: ActivityRankingStats,
        val ranking: List<ActivityRankingItem>
    )

    /**
     * 学生学习统计数据模型
     */
    data class LearningStats(
        val totalStudents: Int,
        val totalWatchTime: Int,
        val totalLearningRecords: Int,
        val averageProgress: Double,
        val averageClickCount: Double,
        val totalCompletedCourses: Int,
        val averageCompletedCourses: Double,
        val lastUpdated: String
    )

    /**
     * 学生个人学习统计项目数据模型
     */
    data class StudentLearningStatsItem(
        val rank: Int,
        val studentId: Int,
        val username: String,
        val totalWatchTime: Int,
        val averageProgress: Double,
        val averageClickCount: Double,
        val enrolledCourseCount: Int,
        val completedCourseCount: Int,
        val totalLearningRecords: Int,
        val lastStudyTime: String
    )

    /**
     * 学生学习统计响应数据模型
     */
    data class LearningStatsResponse(
        val stats: LearningStats,
        val students: List<StudentLearningStatsItem>
    )

    /**
     * 课程掌握度分布数据模型
     */
    data class CourseMasteryDistribution(
        val proficient: Int,
        val good: Int,
        val medium: Int,
        val basic: Int,
        val beginner: Int
    )

    /**
     * 课程掌握度统计数据模型
     */
    data class CourseMasteryStats(
        val totalCourses: Int,
        val totalStudents: Int,
        val totalLearningRecords: Int,
        val averageProgress: Double,
        val averageClickCount: Double,
        val averageCompletionRate: Double,
        val masteryDistribution: CourseMasteryDistribution,
        val lastUpdated: String
    )

    /**
     * 课程掌握度项目数据模型
     */
    data class CourseMasteryItem(
        val rank: Int,
        val courseId: Int,
        val courseName: String,
        val description: String? = "",
        val teacherName: String? = "",
        val credit: Int,
        val createdAt: String,
        val totalStudents: Int,
        val totalLearningRecords: Int,
        val averageProgress: Double,
        val averageClickCount: Double,
        val averageQuizAccuracy: Double,
        val averageCompletionRate: Double,
        val masteryLevel: String? = "",
        val compositeMastery: Double? = null
    )

    /**
     * 课程掌握度统计响应数据模型
     */
    data class CourseMasteryResponse(
        val stats: CourseMasteryStats,
        val courses: List<CourseMasteryItem>
    )

    /**
     * 仪表盘概览统计数据
     */
    data class DashboardStats(
        val todayStudyCount: Int,
        val todayNewUsers: Int,
        val onlineCount: Int,
        val averageStudyTime: Double,
        val studentCompletionRate: Double,
        val completedStudents: Int,
        val totalStudentsWithRecords: Int
    )

    /**
     * 学习趋势数据点
     */
    data class LearningTrendPoint(
        val date: String,
        val label: String,
        val studyCount: Int,
        val totalWatchTime: Long
    )

    /**
     * 时段分布数据点
     */
    data class HourlyDistribution(
        val hour: Int,
        val studentCount: Int
    )

    /**
     * 课程热度排行项
     */
    data class CourseRankItem(
        val rank: Int,
        val courseId: Int,
        val courseName: String,
        val teacherName: String? = "",
        val enrollCount: Int
    )

    /**
     * 活跃用户排行项
     */
    data class ActiveUserItem(
        val rank: Int,
        val userId: Int,
        val username: String,
        val totalWatchTime: Int,
        val loginCount: Int
    )

    /**
     * 掌握度概览
     */
    data class MasteryOverview(
        val totalCourses: Int,
        val averageProgress: Double,
        val averageCompletionRate: Double
    )

    /**
     * 仪表盘概览响应
     */
    data class DashboardOverviewResponse(
        val stats: DashboardStats,
        val learningTrend: List<LearningTrendPoint>,
        val hourlyDistribution: List<HourlyDistribution>,
        val courseRanking: List<CourseRankItem>,
        val activeUsers: List<ActiveUserItem>,
        val masteryOverview: MasteryOverview
    )

    // ==================== JWT 令牌管理 ====================
    // 令牌由登录接口返回，ApiService 自动为所有请求添加 Authorization 头

    companion object {
        private const val TAG = "ApiService"
        const val BASE_URL = "http://127.0.0.1:3001"
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"

        @Volatile
        private var INSTANCE: ApiService? = null
        private var cachedAccessToken: String? = null

        fun getInstance(context: Context): ApiService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiService(context.applicationContext).also { INSTANCE = it }
            }
        }

        /**
         * 保存登录令牌到内存和 SharedPreferences
         */
        fun saveTokens(context: Context, accessToken: String, refreshToken: String) {
            cachedAccessToken = accessToken
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply()
        }

        /**
         * 获取缓存的访问令牌
         */
        fun getAccessToken(context: Context): String? {
            if (cachedAccessToken == null) {
                cachedAccessToken = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_ACCESS_TOKEN, null)
            }
            return cachedAccessToken
        }

        /**
         * 获取刷新令牌
         */
        fun getRefreshToken(context: Context): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_REFRESH_TOKEN, null)
        }

        /**
         * 清除所有令牌（登出时调用）
         */
        fun clearTokens(context: Context) {
            cachedAccessToken = null
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .apply()
        }

        /**
         * 检查是否已登录（有令牌）
         */
        fun isLoggedIn(context: Context): Boolean {
            return getAccessToken(context) != null
        }

        /**
         * 为任意 HttpURLConnection 添加 JWT 授权头
         * 供外部类（UploadActivity、UploadCourseFragment 等）直接调用
         */
        fun addAuthToConnection(conn: HttpURLConnection, context: Context) {
            val token = getAccessToken(context)
            if (token != null) {
                conn.setRequestProperty("Authorization", "Bearer $token")
            }
        }
    }

    // ==================== HTTP辅助方法 ====================

    /**
     * 为 HTTP 连接添加 Authorization: Bearer 头
     */
    private fun addAuthHeader(conn: HttpURLConnection, context: android.content.Context) {
        ApiService.addAuthToConnection(conn, context)
    }

    /**
     * 发送HTTP GET请求（自动携带令牌）
     */
    private fun httpGet(fullUrl: String): String {
        val url = URL(fullUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        addAuthHeader(conn, context)
        try {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                return conn.inputStream.bufferedReader().readText()
            }
            throw IOException("HTTP $code")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 发送HTTP POST请求（自动携带令牌）
     */
    private fun httpPost(fullUrl: String, jsonBody: String): String {
        val url = URL(fullUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        addAuthHeader(conn, context)
        try {
            conn.outputStream.write(jsonBody.toByteArray(Charsets.UTF_8))
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_CREATED) {
                return conn.inputStream.bufferedReader().readText()
            }
            // 读取错误响应体，获取服务器返回的具体错误信息
            val errorBody = try {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            } catch (e: Exception) { "" }
            throw IOException("HTTP $code: $errorBody")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 发送HTTP PUT请求（自动携带令牌）
     */
    private fun httpPut(fullUrl: String, jsonBody: String): String {
        val url = URL(fullUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.requestMethod = "PUT"
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        addAuthHeader(conn, context)
        try {
            conn.outputStream.write(jsonBody.toByteArray(Charsets.UTF_8))
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_CREATED) {
                return conn.inputStream.bufferedReader().readText()
            }
            val errorBody = try {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            } catch (e: Exception) { "" }
            throw IOException("HTTP $code: $errorBody")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 发送HTTP DELETE请求（自动携带令牌）
     */
    private fun httpDelete(fullUrl: String): Int {
        val url = URL(fullUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        addAuthHeader(conn, context)
        try {
            return conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 发送HTTP PATCH请求（自动携带令牌）
     */
    private fun httpPatch(fullUrl: String, jsonBody: String): String {
        val url = URL(fullUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.requestMethod = "PATCH"
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        addAuthHeader(conn, context)
        try {
            conn.outputStream.write(jsonBody.toByteArray(Charsets.UTF_8))
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_CREATED) {
                return conn.inputStream.bufferedReader().readText()
            }
            val errorBody = try {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            } catch (e: Exception) { "" }
            throw IOException("HTTP $code: $errorBody")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 发送HTTP POST multipart/form-data 请求（文件上传，自动携带令牌）
     */
    private fun httpPostMultipart(fullUrl: String, fields: Map<String, String>, fileField: String, fileName: String, fileBytes: ByteArray, fileMimeType: String): String {
        val boundary = "Boundary_${System.currentTimeMillis()}"
        val lineEnd = "\r\n"
        val twoHyphens = "--"
        val url = URL(fullUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        addAuthHeader(conn, context)
        try {
            val out = conn.outputStream
            // 添加文本字段
            for ((key, value) in fields) {
                out.write("$twoHyphens$boundary$lineEnd".toByteArray())
                out.write("Content-Disposition: form-data; name=\"$key\"$lineEnd$lineEnd".toByteArray())
                out.write("$value$lineEnd".toByteArray())
            }
            // 添加文件字段
            out.write("$twoHyphens$boundary$lineEnd".toByteArray())
            out.write("Content-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"$lineEnd".toByteArray())
            out.write("Content-Type: $fileMimeType$lineEnd$lineEnd".toByteArray())
            out.write(fileBytes)
            out.write(lineEnd.toByteArray())
            out.write("$twoHyphens$boundary$twoHyphens$lineEnd".toByteArray())
            out.flush()
            out.close()

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_CREATED) {
                return conn.inputStream.bufferedReader().readText()
            }
            throw IOException("HTTP $code")
        } finally {
            conn.disconnect()
        }
    }

    // ==================== JSON解析辅助 ====================

    /**
     * 解析通用ApiResponse外层
     */
    private fun parseApiResponse(jsonStr: String): JSONObject {
        return JSONObject(jsonStr)
    }

    private fun isSuccess(jsonStr: String): Boolean {
        return JSONObject(jsonStr).optBoolean("success", false)
    }

    private fun getMessage(jsonStr: String): String {
        return JSONObject(jsonStr).optString("message", "")
    }

    /**
     * 安全地获取可空字符串字段
     * JSONObject.optString(String, String)不接受null作为fallback
     */
    private fun JSONObject.optStringOrNull(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name) else null
    }

    /**
     * 将Map转为JSON字符串
     */
    private fun buildJson(vararg pairs: Pair<String, Any?>): String {
        val obj = JSONObject()
        for ((key, value) in pairs) {
            when (value) {
                is String -> obj.put(key, value)
                is Int -> obj.put(key, value)
                is Long -> obj.put(key, value)
                is Double -> obj.put(key, value)
                is Boolean -> obj.put(key, value)
                null -> obj.put(key, JSONObject.NULL)
                else -> obj.put(key, value.toString())
            }
        }
        return obj.toString()
    }

    // ==================== API方法 ====================

    /**
     * 用户登录
     */
    suspend fun login(username: String, password: String): Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val body = buildJson("username" to username, "password" to password)
                val responseBody = httpPost("$BASE_URL/login", body)
                val root = JSONObject(responseBody)
                val success = root.optBoolean("success", false)
                val message = root.optString("message", "")
                val userObj = root.optJSONObject("data")
                val userData = if (userObj != null) {
                    val accessToken = userObj.optString("access_token", "")
                    val refreshToken = userObj.optString("refresh_token", "")
                    // 保存令牌到 SharedPreferences
                    if (accessToken.isNotEmpty() && refreshToken.isNotEmpty()) {
                        saveTokens(context, accessToken, refreshToken)
                        Log.d(TAG, "登录成功: JWT 令牌已保存")
                    }
                    com.studyapp.model.UserData(
                        id = userObj.getInt("id"),
                        username = userObj.getString("username"),
                        role = userObj.optString("role", ""),
                        createdAt = userObj.optString("created_at", ""),
                        mustChangePassword = userObj.optBoolean("mustChangePassword", false),
                        access_token = accessToken.ifEmpty { null },
                        refresh_token = refreshToken.ifEmpty { null }
                    )
                } else null
                if (success && userData != null) {
                    Result.success(LoginResponse(success = true, message = message, data = userData))
                } else {
                    Result.failure(IOException(message))
                }
            } catch (e: Exception) {
                Log.e(TAG, "登录失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 获取用户已选课程
     */
    suspend fun getUserCourses(userId: Int): Result<ApiResponse<List<ApiCourse>>> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet("$BASE_URL/getCourses?userId=$userId")
                val root = JSONObject(responseBody)
                val success = root.optBoolean("success", false)
                val message = root.optString("message", "")
                val dataArray = root.optJSONArray("data")
                val courses = mutableListOf<ApiCourse>()
                if (dataArray != null) {
                    for (i in 0 until dataArray.length()) {
                        val c = dataArray.getJSONObject(i)
                        courses.add(ApiCourse(
                            id = c.getInt("id"),
                            name = c.optString("name", ""),
                            description = c.optString("description", ""),
                            teacher = c.optString("teacher", ""),
                            credit = c.optInt("credit", 0),
                            createdAt = c.optStringOrNull("created_at"),
                            selectedAt = c.optStringOrNull("selected_at"),
                            videoUrl = c.optStringOrNull("video_url"),
                            teacherName = c.optString("teacher_name", c.optString("teacher", "")),
                            imageUrl = c.optStringOrNull("image_url"),
                            categoryId = if (c.has("category_id") && !c.isNull("category_id")) c.optInt("category_id") else null,
                            categoryName = c.optStringOrNull("category_name"),
                            progress = c.optInt("progress", 0)
                        ))
                    }
                }
                Result.success(ApiResponse(success = success, message = message, data = courses))
            } catch (e: Exception) {
                Log.e(TAG, "获取课程失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 获取所有课程列表
     */
    suspend fun getAllCourses(): Result<ApiResponse<List<ApiCourse>>> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet("$BASE_URL/courses")
                val root = JSONObject(responseBody)
                val success = root.optBoolean("success", false)
                val message = root.optString("message", "")
                val dataArray = root.optJSONArray("data")
                val courses = mutableListOf<ApiCourse>()
                if (dataArray != null) {
                    for (i in 0 until dataArray.length()) {
                        val c = dataArray.getJSONObject(i)
                        courses.add(ApiCourse(
                            id = c.getInt("id"),
                            name = c.optString("name", ""),
                            description = c.optString("description", ""),
                            teacher = c.optString("teacher", ""),
                            credit = c.optInt("credit", 0),
                            createdAt = c.optStringOrNull("created_at"),
                            selectedAt = c.optStringOrNull("selected_at"),
                            videoUrl = c.optStringOrNull("video_url"),
                            teacherName = c.optString("teacher_name", c.optString("teacher", "")),
                            imageUrl = c.optStringOrNull("image_url"),
                            categoryId = if (c.has("category_id") && !c.isNull("category_id")) c.optInt("category_id") else null,
                            categoryName = c.optStringOrNull("category_name"),
                            progress = c.optInt("progress", 0)
                        ))
                    }
                }
                Result.success(ApiResponse(success = success, message = message, data = courses))
            } catch (e: Exception) {
                Log.e(TAG, "获取课程列表失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 获取课程视频列表
     */
    suspend fun getCourseVideos(courseId: Int): Result<ApiResponse<List<Any>>> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet("$BASE_URL/getVideosByCourse?courseId=$courseId")
                val root = JSONObject(responseBody)
                val success = root.optBoolean("success", false)
                val message = root.optString("message", "")
                Result.success(ApiResponse(success = success, message = message, data = emptyList()))
            } catch (e: Exception) {
                Log.e(TAG, "获取视频失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 测试API连接
     */
    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                httpGet(BASE_URL)
                true
            } catch (e: Exception) {
                Log.e(TAG, "测试连接失败: ${e.message}", e)
                false
            }
        }
    }

    /**
     * 简化版的HTTP GET请求
     */
    suspend fun get(url: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet(url)
                Result.success(responseBody)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 用户注册
     */
    suspend fun register(
        username: String, password: String, role: String,
        fullName: String? = null, birthday: String? = null,
        securityQuestion: String? = null, securityAnswer: String? = null
    ): Result<RegisterResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val map = mutableMapOf<String, Any?>(
                    "username" to username, "password" to password, "role" to role
                )
                fullName?.let { map["fullName"] = it }
                birthday?.let { map["birthday"] = it }
                securityQuestion?.let { map["securityQuestion"] = it }
                securityAnswer?.let { map["securityAnswer"] = it }
                val body = JSONObject(map.toMap()).toString()
                val responseBody = httpPost("$BASE_URL/register", body)
                val root = JSONObject(responseBody)
                val success = root.optBoolean("success", false)
                val message = root.optString("message", "")
                val userObj = root.optJSONObject("data")
                val userData = if (userObj != null) {
                    com.studyapp.model.UserData(
                        id = userObj.getInt("id"),
                        username = userObj.getString("username"),
                        role = userObj.optString("role", ""),
                        createdAt = userObj.optString("created_at", ""),
                        mustChangePassword = userObj.optBoolean("mustChangePassword", false)
                    )
                } else null
                if (success && userData != null) {
                    Result.success(RegisterResponse(success = true, message = message, data = userData))
                } else {
                    Result.failure(IOException(message))
                }
            } catch (e: Exception) {
                Log.e(TAG, "注册失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // ==================== 忘记密码 ====================

    /**
     * 查询密保问题
     */
    suspend fun forgotPasswordQuery(username: String): Result<ForgotPasswordQueryResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val body = buildJson("username" to username)
                val responseBody = httpPost("$BASE_URL/forgot-password/query", body)
                val root = JSONObject(responseBody)
                val success = root.optBoolean("success", false)
                val message = root.optString("message", "")
                val dataObj = root.optJSONObject("data")
                val data = if (dataObj != null) {
                    ForgotPasswordQueryData(
                        fullName = dataObj.optString("fullName", ""),
                        birthday = dataObj.optString("birthday", ""),
                        securityQuestion = dataObj.optString("securityQuestion", "")
                    )
                } else null
                if (success && data != null) {
                    Result.success(ForgotPasswordQueryResponse(success = true, message = message, data = data))
                } else {
                    Result.failure(IOException(message))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 重置密码
     */
    suspend fun forgotPasswordReset(
        username: String, fullName: String, birthday: String,
        securityAnswer: String, newPassword: String
    ): Result<ApiResponse<Unit>> {
        return withContext(Dispatchers.IO) {
            try {
                val body = buildJson(
                    "username" to username, "fullName" to fullName,
                    "birthday" to birthday, "securityAnswer" to securityAnswer,
                    "newPassword" to newPassword
                )
                val responseBody = httpPost("$BASE_URL/forgot-password/reset", body)
                val root = JSONObject(responseBody)
                val success = root.optBoolean("success", false)
                if (success) {
                    @Suppress("UNCHECKED_CAST")
                    Result.success(ApiResponse(success = true, message = root.optString("message", ""), data = null) as ApiResponse<Unit>)
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 创建课程
     */
    suspend fun createCourse(
        name: String, description: String, teacherId: Int,
        teacherName: String, credit: Int = 2,
        videoUrl: String? = null, imageUrl: String? = null,
        categoryId: Int? = null
    ): Result<CourseCreateResponse> {
        return withContext(Dispatchers.IO) {
            try {
                // 基础字段 + 可选字段（视频URL是上传后才有的）
                val pairs = mutableListOf<Pair<String, Any?>>()
                pairs.add("name" to name)
                pairs.add("description" to description)
                pairs.add("teacherId" to teacherId)
                pairs.add("teacherName" to teacherName)
                pairs.add("credit" to credit)
                if (!videoUrl.isNullOrEmpty()) pairs.add("videoUrl" to videoUrl)
                if (!imageUrl.isNullOrEmpty()) pairs.add("imageUrl" to imageUrl)
                if (categoryId != null) pairs.add("categoryId" to categoryId)
                val body = buildJson(*pairs.toTypedArray())
                val responseBody = httpPost("$BASE_URL/courses", body)
                val root = JSONObject(responseBody)
                val success = root.optBoolean("success", false)
                val message = root.optString("message", "")
                val dataObj = root.optJSONObject("data")
                val courseData = if (dataObj != null) {
                    com.studyapp.model.CourseCreateData(
                        courseId = dataObj.getInt("courseId"),
                        name = dataObj.optString("name", ""),
                        teacherName = dataObj.optString("teacherName", "")
                    )
                } else null
                if (success && courseData != null) {
                    Result.success(com.studyapp.model.CourseCreateResponse(success = true, message = message, data = courseData))
                } else {
                    Result.failure(IOException(message))
                }
            } catch (e: Exception) {
                Log.e(TAG, "创建课程失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // ==================== 分类管理 API ====================

    suspend fun getCategories(): Result<List<Category>> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet("$BASE_URL/categories")
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataArray = root.optJSONArray("data")
                    val list = mutableListOf<Category>()
                    if (dataArray != null) {
                        for (i in 0 until dataArray.length()) {
                            val c = dataArray.getJSONObject(i)
                            list.add(Category(
                                id = c.getInt("id"),
                                name = c.getString("name")
                            ))
                        }
                    }
                    Result.success(list)
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun createCategory(name: String): Result<Category> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpPost("$BASE_URL/categories", buildJson("name" to name))
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataObj = root.optJSONObject("data")
                    if (dataObj != null) {
                        Result.success(Category(id = dataObj.getInt("id"), name = dataObj.getString("name")))
                    } else {
                        Result.failure(IOException("创建分类失败"))
                    }
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateCategoryName(id: Int, name: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                httpPut("$BASE_URL/categories/$id", buildJson("name" to name))
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteCategory(id: Int): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val code = httpDelete("$BASE_URL/categories/$id")
                Result.success(code == HttpURLConnection.HTTP_OK)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ==================== 题目管理 API（PPT生成的选择题） ====================

    suspend fun saveQuestions(courseId: Int, questions: List<Question>): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val qArray = JSONArray()
                for (q in questions) {
                    qArray.put(JSONObject().apply {
                        put("questionText", q.questionText)
                        put("options", JSONArray(q.options))
                        put("correctAnswer", q.correctAnswer)
                    })
                }
                val body = buildJson("questions" to qArray.toString())
                val responseBody = httpPost("$BASE_URL/courses/$courseId/questions", body)
                Result.success(JSONObject(responseBody).optBoolean("success", false))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getQuestions(courseId: Int, status: String? = null): Result<List<Question>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = if (status != null) "$BASE_URL/courses/$courseId/questions?status=$status"
                          else "$BASE_URL/courses/$courseId/questions"
                val responseBody = httpGet(url)
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataArray = root.optJSONArray("data")
                    val list = mutableListOf<Question>()
                    if (dataArray != null) {
                        for (i in 0 until dataArray.length()) {
                            val q = dataArray.getJSONObject(i)
                            val optsArray = q.optJSONArray("options")
                            val opts = mutableListOf<String>()
                            if (optsArray != null) {
                                for (j in 0 until optsArray.length()) opts.add(optsArray.getString(j))
                            }
                            list.add(com.studyapp.model.Question(
                                id = q.optInt("id", 0),
                                questionText = q.optString("questionText", ""),
                                options = opts,
                                correctAnswer = q.optString("correctAnswer", ""),
                                status = q.optString("status", "published")
                            ))
                        }
                    }
                    Result.success(list)
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun saveSlideTexts(courseId: Int, slideTexts: List<String>): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val textsArray = JSONArray(slideTexts)
                val body = buildJson("slideTexts" to textsArray.toString())
                val responseBody = httpPost("$BASE_URL/courses/$courseId/slide-texts", body)
                Result.success(JSONObject(responseBody).optBoolean("success", false))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun aiGenerateQuestions(courseId: Int, count: Int = 10): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpPost("$BASE_URL/courses/$courseId/questions/ai-generate", buildJson("count" to count))
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) Result.success(true)
                else Result.failure(IOException(root.optString("message", "")))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateQuestion(courseId: Int, question: Question): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val body = buildJson(
                    "question_text" to question.questionText,
                    "options" to JSONArray(question.options).toString(),
                    "correct_answer" to question.correctAnswer,
                    "status" to question.status
                )
                val responseBody = httpPut("$BASE_URL/courses/$courseId/questions/${question.id}", body)
                Result.success(JSONObject(responseBody).optBoolean("success", false))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteQuestion(courseId: Int, questionId: Int): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                httpDelete("$BASE_URL/courses/$courseId/questions/$questionId")
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateQuestionStatus(courseId: Int, questionId: Int, status: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpPatch("$BASE_URL/courses/$courseId/questions/$questionId/status", buildJson("status" to status))
                Result.success(JSONObject(responseBody).optBoolean("success", false))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun addManualQuestion(courseId: Int, questionText: String, options: List<String>, correctAnswer: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val body = buildJson(
                    "question_text" to questionText,
                    "options" to JSONArray(options).toString(),
                    "correct_answer" to correctAnswer
                )
                val responseBody = httpPost("$BASE_URL/courses/$courseId/questions/manual", body)
                Result.success(JSONObject(responseBody).optBoolean("success", false))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ==================== 课程资料管理 API ====================

    suspend fun getTeacherCourses(teacherId: Int): Result<List<ApiCourse>> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet("$BASE_URL/courses?teacherId=$teacherId")
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataArray = root.optJSONArray("data")
                    val list = mutableListOf<ApiCourse>()
                    if (dataArray != null) {
                        for (i in 0 until dataArray.length()) {
                            val c = dataArray.getJSONObject(i)
                            list.add(ApiCourse(
                                id = c.getInt("id"),
                                name = c.optString("name", ""),
                                description = c.optString("description", ""),
                                teacher = c.optString("teacher", ""),
                                credit = c.optInt("credit", 0),
                                createdAt = c.optStringOrNull("created_at"),
                                selectedAt = c.optStringOrNull("selected_at"),
                                videoUrl = c.optStringOrNull("video_url"),
                                teacherName = c.optString("teacher_name", c.optString("teacher", "")),
                                imageUrl = c.optStringOrNull("image_url"),
                                categoryId = if (c.has("category_id") && !c.isNull("category_id")) c.optInt("category_id") else null,
                                categoryName = c.optStringOrNull("category_name"),
                                progress = c.optInt("progress", 0)
                            ))
                        }
                    }
                    Result.success(list)
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getCourseMaterials(courseId: Int): Result<List<CourseMaterial>> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet("$BASE_URL/courses/$courseId/materials")
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataArray = root.optJSONArray("data")
                    val list = mutableListOf<CourseMaterial>()
                    if (dataArray != null) {
                        for (i in 0 until dataArray.length()) {
                            val m = dataArray.getJSONObject(i)
                            list.add(CourseMaterial(
                                id = m.optInt("id", 0),
                                courseId = m.optInt("courseId", 0),
                                fileName = m.optString("fileName", ""),
                                fileUrl = m.optString("fileUrl", ""),
                                uploadedAt = m.optString("uploadedAt", "")
                            ))
                        }
                    }
                    Result.success(list)
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun uploadCourseMaterial(courseId: Int, file: File, fileName: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val mimeType = when (fileName.substringAfterLast('.', "*").lowercase()) {
                    "pdf" -> "application/pdf"
                    "doc", "docx" -> "application/msword"
                    "ppt", "pptx" -> "application/vnd.ms-powerpoint"
                    "xls", "xlsx" -> "application/vnd.ms-excel"
                    "txt" -> "text/plain"
                    "zip" -> "application/zip"
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    "mp4" -> "video/mp4"
                    else -> "application/octet-stream"
                }
                val responseBody = httpPostMultipart(
                    "$BASE_URL/courses/$courseId/materials",
                    emptyMap(), "file", fileName, file.readBytes(), mimeType
                )
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    Result.success(root.optString("message", ""))
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteCourseMaterial(materialId: Int, courseId: Int): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                httpDelete("$BASE_URL/courses/$courseId/materials/$materialId")
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteCourse(courseId: Int): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                httpDelete("$BASE_URL/courses/$courseId")
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ==================== 选课 ====================

    suspend fun enrollCourse(courseId: Int, studentId: Int, studentName: String): Result<EnrollCourseResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val body = buildJson("studentId" to studentId, "studentName" to studentName)
                val responseBody = httpPost("$BASE_URL/courses/$courseId/enroll", body)
                val root = JSONObject(responseBody)
                val success = root.optBoolean("success", false)
                val message = root.optString("message", "")
                val dataObj = root.optJSONObject("data")
                val data = if (dataObj != null) {
                    com.studyapp.model.EnrollCourseData(
                        courseId = dataObj.optInt("courseId", 0),
                        studentId = dataObj.optInt("studentId", 0),
                        enrolledAt = dataObj.optString("enrolledAt", "")
                    )
                } else null
                if (success && data != null) {
                    Result.success(EnrollCourseResponse(success = true, message = message, data = data))
                } else {
                    Result.failure(IOException(message))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ==================== 学习记录 ====================

    suspend fun recordStudyProgress(
        courseId: Int, studentId: Int, watchTime: Int = 0, progress: Int = 0, clickCount: Int = 0
    ): Result<StudyRecordResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val body = buildJson(
                    "courseId" to courseId, "studentId" to studentId,
                    "watchTime" to watchTime, "progress" to progress, "clickCount" to clickCount
                )
                val responseBody = httpPost("$BASE_URL/study/record", body)
                val root = JSONObject(responseBody)
                val success = root.optBoolean("success", false)
                val message = root.optString("message", "")
                val dataObj = root.optJSONObject("data")
                val data = if (dataObj != null) {
                    com.studyapp.model.StudyRecordData(
                        courseId = dataObj.optInt("courseId", 0),
                        studentId = dataObj.optInt("studentId", 0),
                        watchTime = dataObj.optInt("watchTime", 0),
                        progress = dataObj.optInt("progress", 0),
                        recordedAt = dataObj.optString("recordedAt", "")
                    )
                } else null
                if (success && data != null) {
                    Result.success(StudyRecordResponse(success = true, message = message, data = data))
                } else {
                    Result.failure(IOException(message))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ==================== 管理员 API ====================

    suspend fun getAllUsers(role: String? = null, search: String? = null): Result<List<AdminUser>> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mutableListOf<String>()
                if (role != null) params.add("role=${java.net.URLEncoder.encode(role, "UTF-8")}")
                if (search != null) params.add("search=${java.net.URLEncoder.encode(search, "UTF-8")}")
                val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
                val responseBody = httpGet("$BASE_URL/admin/users$query")
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataArray = root.optJSONArray("data")
                    val list = mutableListOf<AdminUser>()
                    if (dataArray != null) {
                        for (i in 0 until dataArray.length()) {
                            val u = dataArray.getJSONObject(i)
                            list.add(AdminUser(
                                id = u.getInt("id"),
                                username = u.getString("username"),
                                role = u.optString("role", ""),
                                createdAt = u.optString("created_at", "")
                            ))
                        }
                    }
                    Result.success(list)
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteUser(userId: Int): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpDelete("$BASE_URL/admin/users/$userId")
                if (responseBody == HttpURLConnection.HTTP_OK) {
                    Result.success(true)
                } else {
                    Result.failure(IOException("删除失败"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getOnlineUsers(): Result<OnlineUsersResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet("$BASE_URL/admin/online-users")
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataObj = root.optJSONObject("data")
                    if (dataObj != null) {
                        val statsObj = dataObj.getJSONObject("stats")
                        val usersArray = dataObj.getJSONArray("online_users")
                        val users = mutableListOf<OnlineUser>()
                        for (i in 0 until usersArray.length()) {
                            val u = usersArray.getJSONObject(i)
                            users.add(OnlineUser(
                                id = u.getInt("id"),
                                username = u.getString("username"),
                                role = u.optString("role", ""),
                                lastActiveAt = u.optString("last_active_at", ""),
                                isOnline = u.optBoolean("is_online", true)
                            ))
                        }
                        val stats = OnlineUserStats(
                            onlineCount = statsObj.getInt("online_count"),
                            totalUsers = statsObj.getInt("total_users"),
                            onlineRate = statsObj.optDouble("online_rate", 0.0),
                            lastUpdated = statsObj.optString("last_updated", "")
                        )
                        Result.success(OnlineUsersResponse(stats, users))
                    } else {
                        Result.failure(IOException("数据为空"))
                    }
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getActivityRanking(days: Int = 7, limit: Int = 20): Result<ActivityRankingResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet("$BASE_URL/admin/activity-ranking?days=$days&limit=$limit")
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataObj = root.optJSONObject("data")
                    if (dataObj != null) {
                        val statsObj = dataObj.getJSONObject("stats")
                        val distObj = statsObj.optJSONObject("activity_distribution")
                        val distribution = ActivityDistribution(
                            high = distObj?.optInt("high", 0) ?: 0,
                            medium = distObj?.optInt("medium", 0) ?: 0,
                            low = distObj?.optInt("low", 0) ?: 0
                        )
                        val stats = ActivityRankingStats(
                            days = statsObj.optInt("days", 0),
                            totalUsers = statsObj.getInt("total_users"),
                            totalWatchTime = statsObj.optInt("total_watch_time", 0),
                            totalClickCount = statsObj.optInt("total_click_count", 0),
                            averageActivityScore = statsObj.optDouble("average_activity_score", 0.0),
                            maxActivityScore = statsObj.optDouble("max_activity_score", 0.0),
                            activityDistribution = distribution,
                            lastUpdated = statsObj.optString("last_updated", "")
                        )
                        val rankingArray = dataObj.optJSONArray("ranking")
                        val ranking = mutableListOf<ActivityRankingItem>()
                        if (rankingArray != null) {
                            for (i in 0 until rankingArray.length()) {
                                val r = rankingArray.getJSONObject(i)
                                val detObj = r.optJSONObject("details")
                                val scoresObj = detObj?.optJSONObject("scores")
                                ranking.add(ActivityRankingItem(
                                    rank = r.getInt("rank"),
                                    userId = r.getInt("user_id"),
                                    username = r.getString("username"),
                                    role = r.optString("role", ""),
                                    lastActiveAt = r.optString("last_active_at", ""),
                                    activityScore = r.optDouble("activity_score", 0.0),
                                    details = ActivityScoreDetails(
                                        totalWatchTime = detObj?.optInt("total_watch_time", 0) ?: 0,
                                        avgProgress = detObj?.optDouble("avg_progress", 0.0) ?: 0.0,
                                        totalClickCount = detObj?.optInt("total_click_count", 0) ?: 0,
                                        studyRecordsCount = detObj?.optInt("study_records_count", 0) ?: 0,
                                        loginCount = detObj?.optInt("login_count", 0) ?: 0,
                                        completedCourses = detObj?.optInt("completed_courses", 0) ?: 0,
                                        totalQuizScore = detObj?.optInt("total_quiz_score", 0) ?: 0,
                                        scores = ActivityScores(
                                            login = scoresObj?.optDouble("login", 0.0) ?: 0.0,
                                            watchTime = scoresObj?.optDouble("watch_time", 0.0) ?: 0.0,
                                            completedCourse = scoresObj?.optDouble("completed_course", 0.0) ?: 0.0,
                                            quiz = scoresObj?.optDouble("quiz", 0.0) ?: 0.0
                                        )
                                    )
                                ))
                            }
                        }
                        Result.success(ActivityRankingResponse(stats, ranking))
                    } else {
                        Result.failure(IOException("数据为空"))
                    }
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getLearningStats(limit: Int = 20): Result<LearningStatsResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet("$BASE_URL/admin/learning-stats?limit=$limit")
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataObj = root.optJSONObject("data")
                    if (dataObj != null) {
                        val statsObj = dataObj.getJSONObject("stats")
                        val stats = LearningStats(
                            totalStudents = statsObj.getInt("total_students"),
                            totalWatchTime = statsObj.getInt("total_watch_time"),
                            totalLearningRecords = statsObj.getInt("total_learning_records"),
                            averageProgress = statsObj.optDouble("average_progress", 0.0),
                            averageClickCount = statsObj.optDouble("average_click_count", 0.0),
                            totalCompletedCourses = statsObj.optInt("total_completed_courses", 0),
                            averageCompletedCourses = statsObj.optDouble("average_completed_courses", 0.0),
                            lastUpdated = statsObj.optString("last_updated", "")
                        )
                        val studentsArray = dataObj.optJSONArray("students")
                        val students = mutableListOf<StudentLearningStatsItem>()
                        if (studentsArray != null) {
                            for (i in 0 until studentsArray.length()) {
                                val s = studentsArray.getJSONObject(i)
                                students.add(StudentLearningStatsItem(
                                    rank = s.getInt("rank"),
                                    studentId = s.getInt("student_id"),
                                    username = s.getString("username"),
                                    totalWatchTime = s.optInt("total_watch_time", 0),
                                    averageProgress = s.optDouble("average_progress", 0.0),
                                    averageClickCount = s.optDouble("average_click_count", 0.0),
                                    enrolledCourseCount = s.optInt("enrolled_course_count", 0),
                                    completedCourseCount = s.optInt("completed_course_count", 0),
                                    totalLearningRecords = s.optInt("total_learning_records", 0),
                                    lastStudyTime = s.optString("last_study_time", "")
                                ))
                            }
                        }
                        Result.success(LearningStatsResponse(stats, students))
                    } else {
                        Result.failure(IOException("数据为空"))
                    }
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getCourseMasteryStats(limit: Int = 20): Result<CourseMasteryResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet("$BASE_URL/admin/course-mastery-stats?limit=$limit")
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataObj = root.optJSONObject("data")
                    if (dataObj != null) {
                        val statsObj = dataObj.getJSONObject("stats")
                        val distObj = statsObj.optJSONObject("mastery_distribution")
                        val distribution = CourseMasteryDistribution(
                            proficient = distObj?.optInt("proficient", 0) ?: 0,
                            good = distObj?.optInt("good", 0) ?: 0,
                            medium = distObj?.optInt("medium", 0) ?: 0,
                            basic = distObj?.optInt("basic", 0) ?: 0,
                            beginner = distObj?.optInt("beginner", 0) ?: 0
                        )
                        val stats = CourseMasteryStats(
                            totalCourses = statsObj.getInt("total_courses"),
                            totalStudents = statsObj.getInt("total_students"),
                            totalLearningRecords = statsObj.getInt("total_learning_records"),
                            averageProgress = statsObj.optDouble("average_progress", 0.0),
                            averageClickCount = statsObj.optDouble("average_click_count", 0.0),
                            averageCompletionRate = statsObj.optDouble("average_completion_rate", 0.0),
                            masteryDistribution = distribution,
                            lastUpdated = statsObj.optString("last_updated", "")
                        )
                        val coursesArray = dataObj.optJSONArray("courses")
                        val courses = mutableListOf<CourseMasteryItem>()
                        if (coursesArray != null) {
                            for (i in 0 until coursesArray.length()) {
                                val c = coursesArray.getJSONObject(i)
                                courses.add(CourseMasteryItem(
                                    rank = c.getInt("rank"),
                                    courseId = c.getInt("course_id"),
                                    courseName = c.getString("course_name"),
                                    description = c.optString("description", ""),
                                    teacherName = c.optString("teacher_name", ""),
                                    credit = c.optInt("credit", 0),
                                    createdAt = c.optString("created_at", ""),
                                    totalStudents = c.getInt("total_students"),
                                    totalLearningRecords = c.getInt("total_learning_records"),
                                    averageProgress = c.optDouble("average_progress", 0.0),
                                    averageClickCount = c.optDouble("average_click_count", 0.0),
                                    averageQuizAccuracy = c.optDouble("average_quiz_accuracy", 0.0),
                                    averageCompletionRate = c.optDouble("average_completion_rate", 0.0),
                                    masteryLevel = c.optString("mastery_level", ""),
                                    compositeMastery = if (c.has("composite_mastery") && !c.isNull("composite_mastery")) c.getDouble("composite_mastery") else null
                                ))
                            }
                        }
                        Result.success(CourseMasteryResponse(stats, courses))
                    } else {
                        Result.failure(IOException("数据为空"))
                    }
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getDashboardOverview(): Result<DashboardOverviewResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet("$BASE_URL/admin/dashboard-overview")
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataObj = root.optJSONObject("data")
                    if (dataObj != null) {
                        val statsObj = dataObj.getJSONObject("stats")
                        val trendArray = dataObj.optJSONArray("learning_trend")
                        val hourlyArray = dataObj.optJSONArray("hourly_distribution")
                        val courseRankArray = dataObj.optJSONArray("course_ranking")
                        val activeUsersArray = dataObj.optJSONArray("active_users")
                        val masteryObj = dataObj.optJSONObject("mastery_overview")

                        fun parseTrend(arr: JSONArray?): List<LearningTrendPoint> {
                            val list = mutableListOf<LearningTrendPoint>()
                            if (arr != null) for (i in 0 until arr.length()) {
                                val t = arr.getJSONObject(i)
                                list.add(LearningTrendPoint(t.optString("date", ""), t.optString("label", ""), t.optInt("study_count", 0), t.optLong("total_watch_time", 0)))
                            }
                            return list
                        }
                        fun parseHourly(arr: JSONArray?): List<HourlyDistribution> {
                            val list = mutableListOf<HourlyDistribution>()
                            if (arr != null) for (i in 0 until arr.length()) {
                                val h = arr.getJSONObject(i)
                                list.add(HourlyDistribution(h.optInt("hour", 0), h.optInt("student_count", 0)))
                            }
                            return list
                        }
                        fun parseCourseRank(arr: JSONArray?): List<CourseRankItem> {
                            val list = mutableListOf<CourseRankItem>()
                            if (arr != null) for (i in 0 until arr.length()) {
                                val r = arr.getJSONObject(i)
                                list.add(CourseRankItem(r.optInt("rank", 0), r.optInt("course_id", 0), r.optString("course_name", ""), r.optString("teacher_name", ""), r.optInt("enroll_count", 0)))
                            }
                            return list
                        }
                        fun parseActiveUsers(arr: JSONArray?): List<ActiveUserItem> {
                            val list = mutableListOf<ActiveUserItem>()
                            if (arr != null) for (i in 0 until arr.length()) {
                                val a = arr.getJSONObject(i)
                                list.add(ActiveUserItem(a.optInt("rank", 0), a.optInt("user_id", 0), a.optString("username", ""), a.optInt("total_watch_time", 0), a.optInt("login_count", 0)))
                            }
                            return list
                        }

                        Result.success(DashboardOverviewResponse(
                            stats = DashboardStats(
                                todayStudyCount = statsObj.getInt("today_study_count"),
                                todayNewUsers = statsObj.getInt("today_new_users"),
                                onlineCount = statsObj.getInt("online_count"),
                                averageStudyTime = statsObj.optDouble("average_study_time", 0.0),
                                studentCompletionRate = statsObj.optDouble("student_completion_rate", 0.0),
                                completedStudents = statsObj.optInt("completed_students", 0),
                                totalStudentsWithRecords = statsObj.optInt("total_students_with_records", 0)
                            ),
                            learningTrend = parseTrend(trendArray),
                            hourlyDistribution = parseHourly(hourlyArray),
                            courseRanking = parseCourseRank(courseRankArray),
                            activeUsers = parseActiveUsers(activeUsersArray),
                            masteryOverview = if (masteryObj != null) MasteryOverview(masteryObj.optInt("total_courses", 0), masteryObj.optDouble("average_progress", 0.0), masteryObj.optDouble("average_completion_rate", 0.0)) else MasteryOverview(0, 0.0, 0.0)
                        ))
                    } else {
                        Result.failure(IOException("数据为空"))
                    }
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ==================== 视频播放签名 API ====================

    /**
     * 获取 OSS 视频的预签名播放地址
     * 因为 OSS Bucket 是私有的，需要服务端签名生成有时效的播放 URL
     * @param videoUrl 原始 OSS 视频地址
     * @return 带签名的可播放 URL（1 小时有效）
     */
    suspend fun getVideoPlayUrl(videoUrl: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val body = buildJson("videoUrl" to videoUrl)
                val responseBody = httpPost("$BASE_URL/api/oss/play-url", body)
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val data = root.optJSONObject("data")
                    val playUrl = data?.optString("playUrl", "")
                    if (!playUrl.isNullOrEmpty()) {
                        Result.success(playUrl)
                    } else {
                        Result.failure(IOException("播放地址为空"))
                    }
                } else {
                    Result.failure(IOException(root.optString("message", "获取播放地址失败")))
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取播放地址失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    // ==================== 头像上传 API ====================

    suspend fun uploadAvatar(userId: Int, imageFile: File): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpPostMultipart(
                    "$BASE_URL/upload/avatar",
                    mapOf("userId" to userId.toString()),
                    "avatar", imageFile.name, imageFile.readBytes(), "image/jpeg"
                )
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataObj = root.optJSONObject("data")
                    val url = dataObj?.optString("avatarUrl", "") ?: ""
                    Result.success(url)
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ==================== 聊天消息 API ====================

    suspend fun getConversations(userId: Int): Result<List<Conversation>> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet("$BASE_URL/conversations?userId=$userId")
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataArray = root.optJSONArray("data")
                    val list = mutableListOf<Conversation>()
                    if (dataArray != null) {
                        for (i in 0 until dataArray.length()) {
                            val c = dataArray.getJSONObject(i)
                            list.add(Conversation(
                                id = c.optInt("id", 0),
                                otherUserId = c.optInt("otherUserId", 0),
                                otherUsername = c.optString("otherUsername", ""),
                                otherUserRole = c.optString("otherUserRole", ""),
                                otherUserAvatarUrl = c.optStringOrNull("otherUserAvatarUrl"),
                                lastMessage = c.optString("lastMessage", ""),
                                lastMessageAt = c.optString("lastMessageAt", ""),
                                unreadCount = c.optInt("unreadCount", 0)
                            ))
                        }
                    }
                    Result.success(list)
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getMessages(conversationId: Int, currentUserId: Int): Result<List<ChatMessage>> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet("$BASE_URL/conversations/$conversationId/messages?currentUserId=$currentUserId")
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataArray = root.optJSONArray("data")
                    val list = mutableListOf<ChatMessage>()
                    if (dataArray != null) {
                        for (i in 0 until dataArray.length()) {
                            val m = dataArray.getJSONObject(i)
                            list.add(ChatMessage(
                                id = m.optInt("id", 0),
                                conversationId = m.optInt("conversationId", m.optInt("conversation_id", 0)),
                                senderId = m.optInt("senderId", 0),
                                content = m.optString("content", ""),
                                createdAt = m.optString("createdAt", m.optString("created_at", "")),
                                isRead = m.optBoolean("isRead", m.optBoolean("is_read", false))
                            ))
                        }
                    }
                    Result.success(list)
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendMessage(conversationId: Int, senderId: Int, content: String): Result<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val body = buildJson("conversationId" to conversationId, "senderId" to senderId, "content" to content)
                val responseBody = httpPost("$BASE_URL/messages", body)
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataObj = root.optJSONObject("data")
                    if (dataObj != null) {
                        Result.success(ChatMessage(
                            id = dataObj.optInt("id", 0),
                            conversationId = dataObj.optInt("conversationId", 0),
                            senderId = dataObj.optInt("senderId", 0),
                            content = dataObj.optString("content", ""),
                            createdAt = dataObj.optString("createdAt", ""),
                            isRead = dataObj.optBoolean("isRead", false)
                        ))
                    } else {
                        Result.failure(IOException("发送消息失败"))
                    }
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ==================== 测验 API ====================

    suspend fun submitQuiz(courseId: Int, studentId: Int, answers: Map<String, String>): Result<QuizResult> {
        return withContext(Dispatchers.IO) {
            try {
                val answersObj = JSONObject()
                for ((k, v) in answers) answersObj.put(k, v)
                val body = buildJson("studentId" to studentId, "answers" to answersObj.toString())
                val responseBody = httpPost("$BASE_URL/courses/$courseId/quiz/submit", body)
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataObj = root.optJSONObject("data")
                    if (dataObj != null) {
                        Result.success(QuizResult(
                            score = dataObj.optInt("score", 0),
                            totalQuestions = dataObj.optInt("totalQuestions", dataObj.optInt("total", 0)),
                            correctCount = dataObj.optInt("correctCount", 0),
                            submittedAt = dataObj.optStringOrNull("submittedAt"),
                            questions = null
                        ))
                    } else {
                        Result.failure(IOException("提交测验失败"))
                    }
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getQuizResult(courseId: Int, studentId: Int): Result<QuizResult?> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet("$BASE_URL/courses/$courseId/quiz/result?studentId=$studentId")
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataObj = root.optJSONObject("data")
                    if (dataObj != null) {
                        Result.success(QuizResult(
                            score = dataObj.optInt("score", 0),
                            totalQuestions = dataObj.optInt("totalQuestions", dataObj.optInt("total", 0)),
                            correctCount = dataObj.optInt("correctCount", 0),
                            submittedAt = dataObj.optStringOrNull("submittedAt"),
                            questions = null
                        ))
                    } else {
                        Result.success(null)
                    }
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ==================== 教师统计详情 API ====================

    suspend fun getCourseStudentStats(courseId: Int): Result<StudentStatsData> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet("$BASE_URL/courses/$courseId/students/stats")
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataObj = root.optJSONObject("data")
                    if (dataObj != null) {
                        val studentsArray = dataObj.optJSONArray("students")
                        val students = mutableListOf<com.studyapp.model.StudentStatItem>()
                        if (studentsArray != null) {
                            for (i in 0 until studentsArray.length()) {
                                val s = studentsArray.getJSONObject(i)
                                students.add(com.studyapp.model.StudentStatItem(
                                    studentId = s.optInt("studentId", s.optInt("id", 0)),
                                    studentName = s.optString("studentName", ""),
                                    enrolledAt = s.optStringOrNull("enrolledAt"),
                                    totalWatchTime = s.optInt("totalWatchTime", s.optInt("watchTime", 0)),
                                    averageProgress = s.optDouble("averageProgress", s.optDouble("progress", 0.0)),
                                    totalClickCount = s.optInt("totalClickCount", 0),
                                    studyRecordCount = s.optInt("studyRecordCount", 0),
                                    quiz = null
                                ))
                            }
                        }
                        Result.success(StudentStatsData(
                            courseId = dataObj.optInt("courseId", 0),
                            courseName = dataObj.optString("courseName", ""),
                            totalStudents = dataObj.optInt("totalStudents", 0),
                            totalWatchTime = dataObj.optInt("totalWatchTime", 0),
                            totalClickCount = dataObj.optInt("totalClickCount", 0),
                            students = students
                        ))
                    } else {
                        Result.failure(IOException("数据为空"))
                    }
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getCourseQuestionAccuracy(courseId: Int): Result<QuestionAccuracyData> {
        return withContext(Dispatchers.IO) {
            try {
                val responseBody = httpGet("$BASE_URL/courses/$courseId/questions/accuracy")
                val root = JSONObject(responseBody)
                if (root.optBoolean("success", false)) {
                    val dataObj = root.optJSONObject("data")
                    if (dataObj != null) {
                        val itemsArray = dataObj.optJSONArray("questions")
                        val items = mutableListOf<com.studyapp.model.QuestionAccuracyItem>()
                        if (itemsArray != null) {
                            for (i in 0 until itemsArray.length()) {
                                val a = itemsArray.getJSONObject(i)
                                val optsArray = a.optJSONArray("options")
                                val opts = mutableListOf<String>()
                                if (optsArray != null) for (j in 0 until optsArray.length()) opts.add(optsArray.getString(j))
                                items.add(com.studyapp.model.QuestionAccuracyItem(
                                    questionId = a.optInt("questionId", 0),
                                    questionText = a.optString("questionText", ""),
                                    options = opts,
                                    correctAnswer = a.optString("correctAnswer", ""),
                                    totalAttempts = a.optInt("totalAttempts", 0),
                                    answeredCount = a.optInt("answeredCount", 0),
                                    correctCount = a.optInt("correctCount", 0),
                                    accuracy = a.optDouble("accuracy", 0.0)
                                ))
                            }
                        }
                        Result.success(QuestionAccuracyData(
                            courseId = dataObj.optInt("courseId", 0),
                            totalQuestions = dataObj.optInt("totalQuestions", 0),
                            totalAttempts = dataObj.optInt("totalAttempts", 0),
                            questions = items
                        ))
                    } else {
                        Result.failure(IOException("数据为空"))
                    }
                } else {
                    Result.failure(IOException(root.optString("message", "")))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}