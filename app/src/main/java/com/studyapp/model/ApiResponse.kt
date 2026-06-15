package com.studyapp.model


/**
 * 通用API响应包装类
 */
data class ApiResponse<T>(
    val success: Boolean,

    val message: String,

    val data: T? = null,

    val user: T? = null  // 用于登录接口的特殊字段
)