package com.studyapp.model

/**
 * 合集（Collection）数据模型
 * 教师自建，收录多门课程；课程↔合集为多对多
 * 对应后端 collections 表
 */
data class CourseCollection(
    val id: Int = 0,
    val name: String = "",
    val description: String? = null,
    val coverUrl: String? = null,
    val categoryId: Int? = null,
    val categoryName: String? = null,
    val teacherName: String = "",
    val courseCount: Int = 0,
    val isEnrolled: Boolean = false,
    val createdAt: String? = null
)
