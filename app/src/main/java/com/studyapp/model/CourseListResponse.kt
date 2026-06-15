package com.studyapp.model


/**
 * 课程列表API响应
 */
data class CourseListResponse(
    val success: Boolean,

    val message: String,

    val data: List<ApiCourse>?
)