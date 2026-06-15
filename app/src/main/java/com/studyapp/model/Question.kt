package com.studyapp.model


data class Question(
    val id: Int = 0,
    val courseId: Int = 0,
    val questionText: String,
    val options: List<String>,
    val correctAnswer: String,
    val status: String = "published",
    val source: String = "ai",
    val createdAt: String? = null,
    val studentAnswer: String? = null,
    val isCorrect: Boolean = false
)
