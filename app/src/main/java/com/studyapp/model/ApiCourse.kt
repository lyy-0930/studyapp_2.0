package com.studyapp.model


/**
 * API课程响应数据模型
 * 对应后端API返回的课程数据结构
 */
data class ApiCourse(
    val id: Int,

    val name: String,

    val description: String,

    val teacher: String,

    val credit: Int,

    val createdAt: String?,

    val selectedAt: String?,

    val videoUrl: String?,

    val teacherName: String?,

    val imageUrl: String?,

    val categoryId: Int? = null,

    val categoryName: String? = null,

    val progress: Int = 0
)

/**
 * 将ApiCourse转换为Course
 */
fun ApiCourse.toCourse(isSelected: Boolean = true): Course {
    return Course(
        id = this.id,
        name = this.name,
        description = this.description,
        teacher = this.teacherName ?: this.teacher,
        credit = this.credit,
        videoUrl = this.videoUrl,
        isSelected = isSelected,
        imageUrl = this.imageUrl,
        categoryId = this.categoryId,
        categoryName = this.categoryName,
        createdAt = this.createdAt,
        progress = this.progress
    )
}