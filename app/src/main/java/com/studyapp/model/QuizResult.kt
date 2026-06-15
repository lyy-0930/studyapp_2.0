package com.studyapp.model


data class QuizResult(
    val score: Int,
    val totalQuestions: Int,
    val correctCount: Int,
    val submittedAt: String? = null,
    val questions: List<Question>? = null
)
