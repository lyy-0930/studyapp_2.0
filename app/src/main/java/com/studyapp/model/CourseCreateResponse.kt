package com.studyapp.model


/**
 * 课程创建响应数据模型
 * 对应后端API: POST /courses
 */
data class CourseCreateResponse(
    val success: Boolean,

    val message: String,

    val data: CourseCreateData?
)

/**
 * 课程创建数据
 */
data class CourseCreateData(
    val courseId: Int,

    val name: String,

    val teacherName: String
)