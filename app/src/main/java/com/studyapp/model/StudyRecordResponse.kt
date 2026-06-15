package com.studyapp.model


/**
 * 学习记录响应数据模型
 * 对应后端API: POST /study/record
 */
data class StudyRecordResponse(
    val success: Boolean,

    val message: String,

    val data: StudyRecordData?
)

/**
 * 学习记录数据
 */
data class StudyRecordData(
    val courseId: Int,

    val studentId: Int,

    val watchTime: Int,

    val progress: Int,

    val recordedAt: String
)