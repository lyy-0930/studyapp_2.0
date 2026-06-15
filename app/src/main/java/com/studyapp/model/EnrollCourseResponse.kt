package com.studyapp.model


/**
 * 选课响应数据模型
 * 对应后端API: POST /courses/:courseId/enroll
 */
data class EnrollCourseResponse(
    val success: Boolean,

    val message: String,

    val data: EnrollCourseData?
)

/**
 * 选课数据
 */
data class EnrollCourseData(
    val courseId: Int,

    val studentId: Int,

    val enrolledAt: String
)