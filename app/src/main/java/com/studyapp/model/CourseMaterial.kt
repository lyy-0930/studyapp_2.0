package com.studyapp.model


data class CourseMaterial(
    val id: Int = 0,
    val courseId: Int = 0,
    val fileName: String = "",
    val fileUrl: String = "",
    val fileType: String? = null,
    val fileSize: Long = 0,
    val uploadedAt: String? = null
)
