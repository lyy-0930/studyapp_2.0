package com.studyapp.model


/**
 * 题目正确率统计响应
 */
data class QuestionAccuracyResponse(
    val success: Boolean,
    val message: String,
    val data: QuestionAccuracyData?
)

data class QuestionAccuracyData(
    val courseId: Int,
    val totalQuestions: Int,
    val totalAttempts: Int,
    val questions: List<QuestionAccuracyItem>
)

data class QuestionAccuracyItem(
    val questionId: Int,
    val questionText: String,
    val options: List<String>,
    val correctAnswer: String,
    val totalAttempts: Int,
    val answeredCount: Int,
    val correctCount: Int,
    val accuracy: Double
) {
    fun getFormattedAccuracy(): String {
        return String.format("%.1f%%", accuracy)
    }
}
