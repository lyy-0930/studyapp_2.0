package com.studyapp.model


/**
 * 课程学生统计列表响应
 */
data class StudentStatsResponse(
    val success: Boolean,
    val message: String,
    val data: StudentStatsData?
)

data class StudentStatsData(
    val courseId: Int,
    val courseName: String,
    val totalStudents: Int,
    val totalWatchTime: Int,
    val totalClickCount: Int,
    val students: List<StudentStatItem>
)

data class StudentStatItem(
    val studentId: Int,
    val studentName: String,
    val enrolledAt: String?,
    val totalWatchTime: Int,
    val averageProgress: Double,
    val totalClickCount: Int,
    val studyRecordCount: Int,
    val quiz: StudentQuizInfo?
)

data class StudentQuizInfo(
    val score: Int,
    val totalQuestions: Int,
    val submittedAt: String?
)
