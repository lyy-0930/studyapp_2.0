package com.studyapp.model

/**
 * 首页轮播图（Banner）数据模型
 * 对应后端 banners 表；管理员上传，学生端/教师端首页按同一批展示
 */
data class Banner(
    val id: Int = 0,
    val imageUrl: String = "",
    val sortOrder: Int = 0,
    val enabled: Boolean = true
)
