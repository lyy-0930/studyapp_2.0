package com.studyapp.model

/**
 * 登录响应数据模型
 * 对应后端API返回格式：{success: true, message: "...", data: {...}}
 */
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val data: UserData?  // 后端返回的是data字段
)

/**
 * 注册响应数据模型
 * 对应后端API返回格式：{success: true, message: "...", data: {...}}
 */
data class RegisterResponse(
    val success: Boolean,
    val message: String,
    val data: UserData?  // 后端返回的是data字段
)

/**
 * 用户数据模型（用于API响应）
 * 对应后端API返回的用户信息
 */
data class UserData(
    val id: Int,
    val username: String,
    val role: String,
    val createdAt: String? = null,
    val avatarUrl: String? = null,
    val mustChangePassword: Boolean = false,  // 后端返回是否需要修改密码
    val access_token: String? = null,           // JWT 访问令牌
    val refresh_token: String? = null           // JWT 刷新令牌
)